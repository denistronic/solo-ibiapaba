package br.com.soloibiapaba;
import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.repository.*;
import br.com.soloibiapaba.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${SOLO_TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/postgres}",
    "spring.datasource.username=${SOLO_TEST_DB_USER:postgres}","spring.datasource.password=${SOLO_TEST_DB_PASSWORD:postgres}",
    "spring.datasource.hikari.maximum-pool-size=${SOLO_TEST_DB_POOL_MAX:10}","spring.datasource.hikari.minimum-idle=0",
    "app.admin.email=admin-stage3@example.test","app.admin.password=SenhaTeste123",
    "security.jwt.secret=chave-de-teste-local-com-mais-de-trinta-e-dois-caracteres"
})
@ActiveProfiles("postgres-it")
class SampleAuditPostgresIT {
    @Autowired WebApplicationContext context;@Autowired FilterChainProxy filters;
    @Autowired UserRepository users;@Autowired SampleRepository samples;@Autowired JdbcClient jdbc;
    @Autowired JwtService jwt;@Autowired InterpretationParameterService parameters;@Autowired ObjectMapper json;
    MockMvc mvc;User admin,reader;String a,u,crop,body;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        admin=users.findByEmailIgnoreCase("admin-stage3@example.test").orElseThrow();
        reader=users.save(new User("Autor PostgreSQL","pg-reader-"+UUID.randomUUID()+"@example.test","fixture-only",Role.USER));
        a="Bearer "+jwt.generateToken(admin);u="Bearer "+jwt.generateToken(reader);
        crop="Auditoria PG "+UUID.randomUUID();parameters.create(crop,List.of());
        body="{\"serviceOrderNumber\":\"00123456\",\"block\":\"B1\",\"plot\":\"Q1\",\"row\":\"L1\",\"crop\":\""+crop+"\",\"sampledAt\":\"2026-10-29\",\"temperature\":25,\"depth\":\"0-20\"}";
    }
    JsonNode create() throws Exception {return json.readTree(mvc.perform(post("/api/samples").header("Authorization",u).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    JsonNode getSample(String id) throws Exception {return json.readTree(mvc.perform(get("/api/samples/"+id).header("Authorization",a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    long count(String id) {return jdbc.sql("select count(*) from sample_audit_events where sample_id=:id").param("id",UUID.fromString(id)).query(Long.class).single();}
    @Test void persistsActorsSnapshotsVersionsAndNoOpThroughRealJdbc() throws Exception {
        assertThat(samples).isInstanceOf(PostgresSampleRepository.class);
        var s=create();String id=s.get("id").asText();
        assertThat(getSample(id).get("createdBy").asText()).isEqualTo(reader.getId().toString());
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",u).header("If-Match","1").contentType("application/json")
            .content("{\"calcium\":\"2.50\",\"organicCarbon\":\"0.0000000001\",\"iron\":\"12345678901234.1234567890\"}" )).andExpect(status().isOk());
        mvc.perform(put("/api/samples/"+id).header("Authorization",a).header("If-Match","2").contentType("application/json").content(body.replace("B1","B2"))).andExpect(status().isOk());
        var stored=getSample(id);
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",a).header("If-Match","3").contentType("application/json")
            .content("{\"calcium\":\"2.5\",\"organicCarbon\":\"0.00000000010\",\"iron\":\"12345678901234.123456789\"}" )).andExpect(status().isOk());
        assertThat(getSample(id).get("analysisUpdatedAt")).isEqualTo(stored.get("analysisUpdatedAt"));assertThat(count(id)).isEqualTo(3);
        assertThat(getSample(id).get("recordVersion").asLong()).isEqualTo(3);
        reader.setName("Nome alterado");reader.setEmail("novo-"+UUID.randomUUID()+"@example.test");users.save(reader);
        var response=mvc.perform(get("/api/audit/events").param("sampleId",id).param("actorId",reader.getId().toString()).param("entityType","ANALYSIS").param("action","CREATE").param("size","1").header("Authorization",a))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].actorName").value("Autor PostgreSQL")).andReturn().getResponse().getContentAsString();
        var e=json.readTree(response).get("items").get(0);
        assertThat(e.get("afterData").get("organicCarbon").asText()).isEqualTo("0.0000000001");
        assertThat(e.get("afterData").get("iron").asText()).isEqualTo("12345678901234.123456789");
        assertThat(response).doesNotContain("fixture-only","password_hash","Bearer ");
        mvc.perform(get("/api/audit/events").header("Authorization",u)).andExpect(status().isUnauthorized());
    }
    @Test void auditInsertFailureRollsBackCreationSequenceMetadataAndFirstReport() throws Exception {
        jdbc.sql("""
            CREATE FUNCTION test_reject_sample_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
            BEGIN
                IF NEW.after_data->>'block' = 'ROLLBACK' OR NEW.after_data->>'organicMatter' = '999' THEN
                    RAISE EXCEPTION 'Fixture de falha de auditoria' USING ERRCODE='23514';
                END IF;
                RETURN NEW;
            END; $$;
            CREATE TRIGGER test_reject_sample_audit BEFORE INSERT ON sample_audit_events
                FOR EACH ROW EXECUTE FUNCTION test_reject_sample_audit();
            """).update();
        try {
            int year=java.time.LocalDate.now(java.time.ZoneId.of("America/Fortaleza")).getYear();
            long sequence=jdbc.sql("select coalesce((select last_value from sample_sequences where year=:year),0)").param("year",year).query(Long.class).single();
            mvc.perform(post("/api/samples").header("Authorization",u).contentType("application/json").content(body.replace("B1","ROLLBACK"))).andExpect(status().isConflict());
            assertThat(jdbc.sql("select count(*) from samples where crop=:crop").param("crop",crop).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("select coalesce((select last_value from sample_sequences where year=:year),0)").param("year",year).query(Long.class).single()).isEqualTo(sequence);
            String id=create().get("id").asText();
            mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",u).header("If-Match","1").contentType("application/json").content("{\"organicMatter\":999}" )).andExpect(status().isConflict());
            assertThat(getSample(id).get("analysisUpdatedAt").isNull()).isTrue();assertThat(getSample(id).get("analysisCreatedBy").isNull()).isTrue();
            mvc.perform(put("/api/samples/"+id).header("Authorization",a).header("If-Match","1").contentType("application/json").content(body.replace("B1","ROLLBACK"))).andExpect(status().isConflict());
            assertThat(getSample(id).get("block").asText()).isEqualTo("B1");assertThat(getSample(id).get("recordVersion").asLong()).isEqualTo(1);assertThat(count(id)).isEqualTo(1);
        } finally {jdbc.sql("DROP TRIGGER test_reject_sample_audit ON sample_audit_events; DROP FUNCTION test_reject_sample_audit();").update();}
    }
    @Test void legacyAuthorshipIsUnknownAndUserCannotRecreateLegacyReport() throws Exception {
        UUID id=UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO samples(id,code,block,plot,row_name,crop,sampled_at,temperature,depth,created_at,analysis_updated_at,calcium)
            VALUES(:id,:code,'B','Q','L',:crop,CURRENT_DATE,25,'0-20',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,2.5)
            """).param("id",id).param("code","LEGACY-"+id.toString().substring(0,12)).param("crop",crop).update();
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",u).header("If-Match","0").contentType("application/json").content("{\"calcium\":3}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",a).header("If-Match","0").contentType("application/json").content("{\"calcium\":3}" )).andExpect(status().isOk());
        var stored=getSample(id.toString());assertThat(stored.get("createdBy").isNull()).isTrue();assertThat(stored.get("analysisCreatedBy").isNull()).isTrue();assertThat(stored.get("analysisCreatedAt").isNull()).isTrue();
        assertThat(stored.get("analysisUpdatedBy").asText()).isEqualTo(admin.getId().toString());
        assertThat(jdbc.sql("select action from sample_audit_events where sample_id=:id").param("id",id).query(String.class).single()).isEqualTo("UPDATE");
    }
    @Test void nativeConcurrentFirstReportsHaveOneWinner() throws Exception {
        Assumptions.assumeFalse(Boolean.getBoolean("solo.test.pglite"),"Concorrência exige PostgreSQL nativo e pool > 1");
        var s=create();String id=s.get("id").asText();
        var statuses=race(()->mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",u).header("If-Match","1").contentType("application/json").content("{\"calcium\":2}" )).andReturn().getResponse().getStatus(),
            ()->mvc.perform(put("/api/samples/"+id+"/analysis").header("Authorization",a).header("If-Match","1").contentType("application/json").content("{\"calcium\":3}" )).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200,409);assertThat(count(id)).isEqualTo(2);assertThat(getSample(id).get("recordVersion").asLong()).isEqualTo(2);
    }
    @Test void nativeConcurrentAdminEditsHaveOneWinner() throws Exception {
        Assumptions.assumeFalse(Boolean.getBoolean("solo.test.pglite"),"Concorrência exige PostgreSQL nativo e pool > 1");
        var s=create();String id=s.get("id").asText();
        var statuses=race(()->mvc.perform(put("/api/samples/"+id).header("Authorization",a).header("If-Match","1").contentType("application/json").content(body.replace("B1","B2"))).andReturn().getResponse().getStatus(),
            ()->mvc.perform(put("/api/samples/"+id).header("Authorization",a).header("If-Match","1").contentType("application/json").content(body.replace("B1","B3"))).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200,409);assertThat(count(id)).isEqualTo(2);assertThat(getSample(id).get("recordVersion").asLong()).isEqualTo(2);
    }
    private List<Integer> race(Callable<Integer> left,Callable<Integer> right) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{barrier.await(15,TimeUnit.SECONDS);return left.call();});
            var b=executor.submit(()->{barrier.await(15,TimeUnit.SECONDS);return right.call();});
            return List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS));
        }
    }
}
