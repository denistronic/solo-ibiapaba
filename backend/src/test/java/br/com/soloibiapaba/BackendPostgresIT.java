package br.com.soloibiapaba;

import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.repository.*;
import br.com.soloibiapaba.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Executar explicitamente com -Dtest=BackendPostgresIT contra banco DESCARTÁVEL. */
@SpringBootTest(properties = {
    "spring.datasource.url=${SOLO_TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/postgres}",
    "spring.datasource.username=${SOLO_TEST_DB_USER:postgres}",
    "spring.datasource.password=${SOLO_TEST_DB_PASSWORD:postgres}",
    "spring.datasource.hikari.maximum-pool-size=${SOLO_TEST_DB_POOL_MAX:10}", "spring.datasource.hikari.minimum-idle=0",
    "app.admin.email=admin-stage3@example.test", "app.admin.password=SenhaTeste123",
    "security.jwt.secret=chave-de-teste-local-com-mais-de-trinta-e-dois-caracteres"
})
@ActiveProfiles("postgres-it")
class BackendPostgresIT {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired ObjectMapper json;
    @Autowired SampleRepository samples;
    @Autowired JdbcClient jdbc;
    @Autowired UserRepository users;
    @Autowired InterpretationParameterService parameters;

    @Test void shouldIntegrateFlywayUsersPermissionsCulturesSamplesAndUnits() throws Exception {
        assertThat(samples).isInstanceOf(PostgresSampleRepository.class);
        if (!Boolean.getBoolean("solo.test.sqlPreloaded")) {
            assertThat(jdbc.sql("select count(*) from flyway_schema_history where success=true and version is not null").query(Integer.class).single()).isEqualTo(7);
        } else {
            // Apenas para PGlite: SQL aplicado diretamente, sem simular histórico Flyway.
            assertThat(jdbc.sql("select count(*) from information_schema.columns where table_name='samples' and column_name='organic_carbon'").query(Integer.class).single()).isEqualTo(1);
        }
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        String adminLogin=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"admin-stage3@example.test\",\"password\":\"SenhaTeste123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String admin="Bearer "+json.readTree(adminLogin).get("token").asText();
        mvc.perform(post("/api/users").header("Authorization",admin).contentType("application/json")
            .content("{\"name\":\"Leitor\",\"email\":\"leitor-stage3@example.test\",\"password\":\"SenhaTeste123\",\"role\":\"USER\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/users").header("Authorization",admin).contentType("application/json")
            .content("{\"name\":\"Duplicado\",\"email\":\"LEITOR-stage3@example.test\",\"password\":\"SenhaTeste123\",\"role\":\"USER\"}"))
            .andExpect(status().isConflict());
        String login=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"leitor-stage3@example.test\",\"password\":\"SenhaTeste123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String user="Bearer "+json.readTree(login).get("token").asText();
        String profile="""
            {"crop":"Café integração", "parameters":[{"indicator":"Cálcio (Ca)","minimum":20,"maximum":30}]}
            """;
        mvc.perform(post("/api/interpretation-parameters").header("Authorization",user).contentType("application/json").content(profile))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/interpretation-parameters").header("Authorization",admin).contentType("application/json").content(profile))
            .andExpect(status().isOk());
        mvc.perform(get("/api/interpretation-parameters/cultures").header("Authorization",user))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Café integração")));
        mvc.perform(put("/api/interpretation-parameters").header("Authorization",admin).contentType("application/json")
            .content("{\"crop\":\"Café integração\",\"parameters\":[{\"indicator\":\"Cálcio (Ca)\",\"minimum\":30,\"maximum\":20}]}"))
            .andExpect(status().isBadRequest());
        assertThat(parameters.ranges(" CAFÉ INTEGRAÇÃO ").get("Cálcio (Ca)").minimum()).isEqualByComparingTo("20");
        String create="""
            {"serviceOrderNumber":"00123456","block":"B1","plot":"Q1","row":"L1","crop":"Café integração",
             "sampledAt":"2026-10-29","temperature":25,"depth":"0-20 cm","observations":"Após chuva.\\nColeta manual."}
            """;
        mvc.perform(post("/api/samples").header("Authorization",user).contentType("application/json")
            .content(create.replace("Café integração","Não cadastrada"))).andExpect(status().isBadRequest());
        String response=mvc.perform(post("/api/samples").header("Authorization",user).contentType("application/json").content(create))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id=UUID.fromString(json.readTree(response).get("id").asText());
        assertThat(samples.findById(id).orElseThrow().serviceOrderNumber()).isEqualTo("00123456");
        assertThat(samples.findById(id).orElseThrow().observations()).isEqualTo("Após chuva.\nColeta manual.");
        mvc.perform(get("/api/samples/"+id+"/interpretation").header("Authorization",user)).andExpect(status().isConflict());
        String analysis="""
            {"phCaCl2":4.8,"organicMatter":25,"organicCarbon":0.0000000001,"phosphorus":20,
             "calcium":2.5,"magnesium":10,"potassium":2,"sodium":1,"aluminum":2,"hAl":30,
             "clay":35,"sand":50,"silt":15,"sulfur":8,"density":1.2,"phosphorusMethod":"Mehlich 1",
             "units":{"calcium":"cmolc/dm³","phosphorus":"mg/dm³","clay":"%","sand":"%","silt":"%","sulfur":"mg/kg"}}
            """;
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",user).contentType("application/json").content(analysis))
            .andExpect(status().isOk());
        Sample stored=samples.findById(id).orElseThrow();
        assertThat(stored.analysis().calcium()).isEqualByComparingTo("2.5");
        assertThat(stored.analysis().organicCarbon()).isEqualByComparingTo("0.0000000001");
        assertThat(stored.analysis().units()).containsEntry("calcium","cmolc/dm³").containsEntry("potassium","mmolc/dm³");
        assertThat(stored.analysis().phosphorusMethod()).isEqualTo("Mehlich 1");
        assertThat(stored.analysis().density()).isEqualByComparingTo("1.2");
        assertThat(stored.analysisUpdatedAt()).isNotNull();
        String report=mvc.perform(get("/api/samples/"+id+"/interpretation").header("Authorization",user))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(report).contains("25.00 mmolc/dm³","Médio","0.02 g/dm³","350.00 g/kg");
        assertThat(report).contains("Mehlich 1");
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",admin).header("If-Match","2").contentType("application/json")
            .content(analysis.replace("\"density\":1.2", "\"density\":null"))).andExpect(status().isBadRequest());
        assertThat(samples.findById(id).orElseThrow().analysis().density()).isEqualByComparingTo("1.2");
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",admin).header("If-Match","2").contentType("application/json").content("{}"))
            .andExpect(status().isOk());
        String partial=mvc.perform(get("/api/samples/"+id+"/interpretation").header("Authorization",user))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(partial).contains("Dados insuficientes","Sem dado").doesNotContain("Sem alerta crítico automático");
        assertThat(jdbc.sql("select analysis_schema_version from samples where id=:id").param("id",id).query(Integer.class).single()).isEqualTo(2);
        mvc.perform(get("/api/samples").header("Authorization",user)).andExpect(status().isOk());
        User reader=users.findByEmailIgnoreCase("leitor-stage3@example.test").orElseThrow();
        mvc.perform(put("/api/users/"+reader.getId()+"/status").header("Authorization",admin).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        assertThat(users.findById(reader.getId()).orElseThrow().isActive()).isTrue();
        mvc.perform(put("/api/users/"+reader.getId()+"/status").header("Authorization",admin).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/samples").header("Authorization",user)).andExpect(status().isUnauthorized());
        User administrator=users.findByEmailIgnoreCase("admin-stage3@example.test").orElseThrow();
        mvc.perform(put("/api/users/"+administrator.getId()+"/status").header("Authorization",admin).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/users/"+administrator.getId()).header("Authorization",admin).contentType("application/json")
            .content("{\"name\":\"Administrador\",\"email\":\"admin-stage3@example.test\",\"role\":\"USER\"}"))
            .andExpect(status().isConflict());
        assertThat(users.findById(administrator.getId()).orElseThrow().isActive()).isTrue();

    }
}
