package br.com.soloibiapaba;
import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.repository.*;
import br.com.soloibiapaba.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import tools.jackson.databind.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test")
class SamplePermissionsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @Autowired UserRepository users;
    @Autowired SampleRepository samples;
    @Autowired JwtService jwt;
    @Autowired InterpretationParameterService parameters;
    @Autowired ObjectMapper json;
    MockMvc mvc;User admin,reader;String a,u,crop,body;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        admin=users.save(new User("Admin teste","admin-"+UUID.randomUUID()+"@example.test","hash-test",Role.ADMIN));
        reader=users.save(new User("Leitor teste","leitor-"+UUID.randomUUID()+"@example.test","hash-test",Role.USER));
        a=token(admin);u=token(reader);crop="Café "+UUID.randomUUID();parameters.create(crop,List.of());
        body="{\"serviceOrderNumber\":\"00123456\",\"block\":\"B1\",\"plot\":\"Q1\",\"row\":\"L1\",\"crop\":\""+crop+"\",\"sampledAt\":\"2026-10-29\",\"temperature\":\"25.25\",\"depth\":\"0-20 cm\",\"observations\":\"Original\"}";
    }
    String token(User user) { return "Bearer "+jwt.generateToken(user); }
    JsonNode create(String auth) throws Exception {
        return json.readTree(mvc.perform(post("/api/samples").header("Authorization",auth).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(header().string("ETag","\"1\"")).andReturn().getResponse().getContentAsString());
    }
    String path(JsonNode sample) {return "/api/samples/"+sample.get("id").asText();}
    JsonNode current(String path) throws Exception {return json.readTree(mvc.perform(get(path).header("Authorization",a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode history(String id) throws Exception {return json.readTree(mvc.perform(get("/api/audit/events").param("sampleId",id).header("Authorization",a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void createsSampleAndFirstReportWithServerAuthorship() throws Exception {
        String forged=body.substring(0,body.length()-1)+",\"createdBy\":\""+admin.getId()+"\",\"recordVersion\":999,\"actorRole\":\"ADMIN\"}";
        var s=json.readTree(mvc.perform(post("/api/samples").header("Authorization",u).contentType("application/json").content(forged))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(s.get("createdBy").asText()).isEqualTo(reader.getId().toString());assertThat(s.get("recordVersion").asLong()).isEqualTo(1);
        mvc.perform(put(path(s)+"/analysis").header("Authorization",u).header("If-Match","\"1\"").contentType("application/json").content("{\"calcium\":\"2.5\"}"))
            .andExpect(status().isOk()).andExpect(header().string("ETag","\"2\"")).andExpect(jsonPath("$.analysisCreatedBy").value(reader.getId().toString()));
        var events=history(s.get("id").asText());assertThat(events.get("total").asLong()).isEqualTo(2);
        assertThat(events.get("items").get(0).get("actorUserId").asText()).isEqualTo(reader.getId().toString());
        mvc.perform(get("/api/samples").header("Authorization",u)).andExpect(status().isOk());
    }
    @Test void userCannotEditExistingRecordsReadAuditOrDelete() throws Exception {
        var s=create(u);String p=path(s);
        mvc.perform(put(p+"/analysis").header("Authorization",u).contentType("application/json").content("{}" )).andExpect(status().isOk());
        mvc.perform(put(p+"/analysis").header("Authorization",u).header("If-Match","2").contentType("application/json").content("{\"calcium\":3}" )).andExpect(status().isForbidden());
        mvc.perform(put(p).header("Authorization",u).header("If-Match","2").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit/events").header("Authorization",u)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit/events")).andExpect(status().isUnauthorized());
        for(String auth:new String[]{a,u})mvc.perform(delete(p).header("Authorization",auth)).andExpect(status().isForbidden());
        assertThat(history(s.get("id").asText()).get("total").asLong()).isEqualTo(2);
    }
    @Test void auditAllowsOnlyAdministrativeReads() throws Exception {
        for (String auth : new String[]{a, u}) {
            for (var method : new org.springframework.http.HttpMethod[]{
                    org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT,
                    org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE}) {
                mvc.perform(request(method, "/api/audit/events").header("Authorization", auth)
                        .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/api/audit/events").header("Authorization", a)).andExpect(status().isOk());
        mvc.perform(get("/api/audit/events").header("Authorization", u)).andExpect(status().isForbidden());
    }
    @Test void protectsLegacyReportWithoutKnownCreator() throws Exception {
        var s=create(a);String p=path(s);
        mvc.perform(put(p+"/analysis").header("Authorization",a).contentType("application/json").content("{\"calcium\":2.5}" )).andExpect(status().isOk());
        UUID id=UUID.fromString(s.get("id").asText());
        // Nova identidade para representar registro histórico, sem eventos/autores anteriores.
        var original=samples.findById(id).orElseThrow();
        var legacy=new Sample(UUID.randomUUID(),"LEGACY-"+UUID.randomUUID(),original.serviceOrderNumber(),original.block(),original.plot(),original.row(),original.crop(),original.sampledAt(),original.temperature(),original.depth(),original.createdAt(),original.analysis(),original.analysisUpdatedAt(),original.observations());
        samples.save(legacy);p="/api/samples/"+legacy.id();
        mvc.perform(put(p+"/analysis").header("Authorization",u).header("If-Match","0").contentType("application/json").content("{\"calcium\":3.5}" )).andExpect(status().isForbidden());
        mvc.perform(put(p+"/analysis").header("Authorization",a).header("If-Match","0").contentType("application/json").content("{\"calcium\":3.5}" )).andExpect(status().isOk())
            .andExpect(jsonPath("$.analysisCreatedBy").isEmpty()).andExpect(jsonPath("$.analysisCreatedAt").isEmpty()).andExpect(jsonPath("$.createdBy").isEmpty());
        var e=history(legacy.id().toString()).get("items").get(0);assertThat(e.get("action").asText()).isEqualTo("UPDATE");assertThat(e.get("beforeData").get("calcium").asText()).isEqualTo("2.5");
    }
    @Test void administratorEditsMetadataAndMustUseCurrentVersion() throws Exception {
        var s=create(a);String p=path(s);String edited=body.replace("B1","B2");
        mvc.perform(put(p).header("Authorization",a).contentType("application/json").content(edited)).andExpect(status().is(428));
        mvc.perform(put(p).header("Authorization",a).header("If-Match","0").contentType("application/json").content(edited)).andExpect(status().isConflict());
        for(String bad:new String[]{"*","W/\"1\"","-1","x","9223372036854775808"})mvc.perform(put(p).header("Authorization",a).header("If-Match",bad).contentType("application/json").content(edited)).andExpect(status().isBadRequest());
        mvc.perform(put(p).header("Authorization",a).header("If-Match","\"1\"").contentType("application/json").content(edited)).andExpect(status().isOk()).andExpect(header().string("ETag","\"2\""));
        var now=current(p);assertThat(now.get("code")).isEqualTo(s.get("code"));assertThat(now.get("createdBy")).isEqualTo(s.get("createdBy"));assertThat(now.get("updatedBy").asText()).isEqualTo(admin.getId().toString());
        var h=history(s.get("id").asText());assertThat(h.get("total").asLong()).isEqualTo(2);assertThat(h.get("items").get(0).get("beforeData").get("block").asText()).isEqualTo("B1");
        assertThat(h.get("items").get(0).get("afterData").get("block").asText()).isEqualTo("B2");
    }
    @Test void noOpDoesNotChangeVersionTimestampOrHistoryAndDecimalsAreExact() throws Exception {
        var s=create(a);String p=path(s);
        String first="{\"calcium\":\"2.50\",\"organicCarbon\":\"0.0000000001\",\"iron\":\"12345678901234.1234567890\"}";
        mvc.perform(put(p+"/analysis").header("Authorization",a).header("If-None-Match","*").contentType("application/json").content(first)).andExpect(status().isOk());
        var before=current(p);
        mvc.perform(put(p+"/analysis").header("Authorization",a).header("If-Match","2").contentType("application/json").content(first.replace("2.50","2.5"))).andExpect(status().isOk());
        var after=current(p);assertThat(after.get("recordVersion").asLong()).isEqualTo(2);assertThat(after.get("analysisUpdatedAt")).isEqualTo(before.get("analysisUpdatedAt"));
        mvc.perform(put(p).header("Authorization",a).header("If-Match","2").contentType("application/json").content(body)).andExpect(status().isOk());
        var h=history(s.get("id").asText());assertThat(h.get("total").asLong()).isEqualTo(2);
        var data=h.get("items").get(0).get("afterData");assertThat(data.get("iron").asText()).isEqualTo("12345678901234.123456789");assertThat(data.get("organicCarbon").asText()).isEqualTo("0.0000000001");
        assertThat(h.toString()).doesNotContain("password_hash","hash-test","Bearer ");
    }
    @Test void firstOnlyAndStaleRequestsCannotOverwriteReport() throws Exception {
        var s=create(u);String p=path(s)+"/analysis";
        mvc.perform(put(p).header("Authorization",u).header("If-Match","1").contentType("application/json").content("{\"calcium\":2}" )).andExpect(status().isOk());
        mvc.perform(put(p).header("Authorization",u).header("If-Match","1").contentType("application/json").content("{\"calcium\":3}" )).andExpect(status().isConflict());
        mvc.perform(put(p).header("Authorization",a).header("If-None-Match","*").contentType("application/json").content("{}" )).andExpect(status().isConflict());
        mvc.perform(put(p).header("Authorization",a).contentType("application/json").content("{}" )).andExpect(status().is(428));
        mvc.perform(put(p).header("Authorization",a).header("If-Match","2").header("If-None-Match","*").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        assertThat(current(path(s)).get("analysis").get("calcium").decimalValue()).isEqualByComparingTo("2");
    }
    @Test void auditIsPaginatedFilteredAndPreservesActorSnapshotAfterRename() throws Exception {
        var s=create(a);String id=s.get("id").asText();
        mvc.perform(put(path(s)+"/analysis").header("Authorization",u).contentType("application/json").content("{}")).andExpect(status().isOk());
        admin.setName("Admin renomeado");admin.setEmail("renomeado-"+UUID.randomUUID()+"@example.test");users.save(admin);a=token(admin);
        mvc.perform(get("/api/audit/events").header("Authorization",a).param("sampleId",id).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/audit/events").header("Authorization",a).param("sampleId",id).param("actorId",admin.getId().toString()).param("entityType","sample").param("action","create").param("from","2000-01-01T00:00:00Z").param("to","2100-01-01T00:00:00Z"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].actorName").value("Admin teste"));
        for(String query:new String[]{"?size=101","?page=-1","?action=DELETE","?entityType=USERS","?from=2100-01-01T00:00:00Z&to=2000-01-01T00:00:00Z"})mvc.perform(get("/api/audit/events"+query).header("Authorization",a)).andExpect(status().isBadRequest());
    }
    @Test void metadataValidationDoesNotGenerateEvents() throws Exception {
        var s=create(a);
        mvc.perform(put(path(s)).header("Authorization",a).header("If-Match","1").contentType("application/json").content(body.replace(crop,"Inexistente"))).andExpect(status().isBadRequest());
        assertThat(current(path(s)).get("recordVersion").asLong()).isEqualTo(1);assertThat(history(s.get("id").asText()).get("total").asLong()).isEqualTo(1);
    }
}
