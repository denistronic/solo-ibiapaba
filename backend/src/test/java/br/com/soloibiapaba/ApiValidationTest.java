package br.com.soloibiapaba;

import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.repository.UserRepository;
import br.com.soloibiapaba.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class ApiValidationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    private MockMvc mvc;
    private String admin;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        admin=token(person(Role.ADMIN,true));
    }
    private User person(Role role,boolean active) {
        var user=new User("Pessoa","validacao-"+UUID.randomUUID()+"@example.test",encoder.encode("SenhaTeste123"),role);
        user.setActive(active);return users.save(user);
    }
    private String token(User user) {return "Bearer "+jwt.generateToken(user);}
    @Test void shouldNotDeactivateAUserWhenStatusIsMissing() throws Exception {
        var target=person(Role.USER,true);
        for(String body:new String[]{"{}","{\"active\":null}"}) {
            mvc.perform(put("/api/users/"+target.getId()+"/status").header("Authorization",admin).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.active").exists());
            assertThat(users.findById(target.getId()).orElseThrow().isActive()).isTrue();
        }
        mvc.perform(put("/api/users/"+target.getId()+"/status").header("Authorization",admin).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isOk());
        assertThat(users.findById(target.getId()).orElseThrow().isActive()).isFalse();
    }
    @Test void shouldReturnStructuredErrorsForInvalidBodiesAndIdentifiers() throws Exception {
        mvc.perform(post("/api/users").header("Authorization",admin).contentType("application/json").content("{invalid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/users").header("Authorization",admin).contentType("application/json")
            .content("{\"name\":\"Pessoa\",\"email\":\"p@example.test\",\"password\":\"SenhaTeste123\",\"role\":\"ROOT\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/not-a-uuid").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/interpretation-parameters").header("Authorization",admin)).andExpect(status().isBadRequest());
    }
    @Test void shouldReturn401ForInvalidOrInactiveOrRemovedUsers() throws Exception {
        mvc.perform(get("/api/samples").header("Authorization","Bearer invalid"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/samples").header("Authorization",token(person(Role.USER,false))))
            .andExpect(status().isUnauthorized());
        var removed=person(Role.USER,true);String removedToken=token(removed);users.deleteById(removed.getId());
        mvc.perform(get("/api/samples").header("Authorization",removedToken))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").isNotEmpty());
    }
    @Test void shouldReturn401ForTokenWithOldEmailAfterRename() throws Exception {
        var user=person(Role.USER,true);String oldToken=token(user);
        user.setEmail("renomeado-"+UUID.randomUUID()+"@example.test");users.save(user);
        mvc.perform(get("/api/auth/me").header("Authorization",oldToken)).andExpect(status().isUnauthorized());
    }
    @Test void shouldEnforceAdminPermissionsWithStructured403() throws Exception {
        mvc.perform(post("/api/interpretation-parameters").header("Authorization",token(person(Role.USER,true)))
            .contentType("application/json").content("{\"crop\":\"Teste\",\"parameters\":[]}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }
    @Test void shouldRejectLongMultibytePasswordBeforeEncoding() throws Exception {
        String body="{\"name\":\"Pessoa\",\"email\":\"long-"+UUID.randomUUID()+"@example.test\",\"password\":\""+"é".repeat(37)+"\",\"role\":\"USER\"}";
        mvc.perform(post("/api/users").header("Authorization",admin).contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
    }
    @Test void shouldNormalizeEmailAtCreationAndLogin() throws Exception {
        String email="NORMAL-"+UUID.randomUUID()+"@EXAMPLE.TEST";
        String body="{\"name\":\"  Pessoa  \",\"email\":\"  "+email+"  \",\"password\":\"SenhaTeste123\",\"role\":\"USER\"}";
        mvc.perform(post("/api/users").header("Authorization",admin).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Pessoa"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"  "+email+"  \",\"password\":\"SenhaTeste123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
    @Test void shouldRejectUnknownUnitsNegativeValuesAndInvalidTexture() throws Exception {
        String crop="Teste "+UUID.randomUUID();
        mvc.perform(post("/api/interpretation-parameters").header("Authorization",admin).contentType("application/json")
            .content("{\"crop\":\""+crop+"\",\"parameters\":[]}")).andExpect(status().isOk());
        var response=mvc.perform(post("/api/samples").header("Authorization",admin).contentType("application/json")
            .content("{\"serviceOrderNumber\":\"00123456\",\"block\":\"B\",\"plot\":\"Q\",\"row\":\"L\",\"crop\":\""+crop+"\",\"sampledAt\":\"2026-10-29\",\"temperature\":25,\"depth\":\"0-20\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=context.getBean(tools.jackson.databind.ObjectMapper.class).readTree(response).get("id").asText();
        for(String bad:new String[]{"{\"calcium\":-1}","{\"clay\":101,\"units\":{\"clay\":\"%\"}}", "{\"units\":{\"calcium\":null}}", "{\"sulfur\":2,\"units\":{\"sulfur\":\"mg/kg\"}}", "{\"phosphorusMethod\":\"Outro\"}"})
            mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",admin).contentType("application/json").content(bad))
                .andExpect(status().isBadRequest());
    }
    @Test void shouldRejectInvalidCultureAndImpossibleParameterRanges() throws Exception {
        mvc.perform(post("/api/interpretation-parameters").header("Authorization",admin).contentType("application/json")
            .content("{\"crop\":\"́\",\"parameters\":[]}")).andExpect(status().isBadRequest());
        for(String indicator:new String[]{"Saturação por bases","V informado pelo laboratório"})
            mvc.perform(post("/api/interpretation-parameters").header("Authorization",admin).contentType("application/json")
                .content("{\"crop\":\"Inválido "+UUID.randomUUID()+"\",\"parameters\":[{\"indicator\":\""+indicator+"\",\"minimum\":0,\"maximum\":101}]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void shouldAllowConfiguredCorsOriginAndRejectUnlistedOrigin() throws Exception {
        mvc.perform(options("/api/samples").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","POST"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
        mvc.perform(options("/api/samples").header("Origin","https://nao-permitido.example.test").header("Access-Control-Request-Method","POST"))
            .andExpect(status().isForbidden());
    }

}
