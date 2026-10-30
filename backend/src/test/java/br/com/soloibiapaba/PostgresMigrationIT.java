package br.com.soloibiapaba;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Banco DESCARTÁVEL. Cada caso cria e remove apenas um schema com UUID próprio. */
class PostgresMigrationIT {
    private final String url = System.getenv().getOrDefault("SOLO_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55439/postgres");
    private final String user = System.getenv().getOrDefault("SOLO_TEST_DB_USER", "postgres");
    private final String password = System.getenv().getOrDefault("SOLO_TEST_DB_PASSWORD", "postgres");

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target(target);
        if (Boolean.getBoolean("solo.test.pglite")) {
            config.getPluginRegister().getPlugin(org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);
        }
        return config.load();
    }

    private Connection connection(String schema) throws Exception {
        Connection c = DriverManager.getConnection(url, user, password);
        c.createStatement().execute("SET search_path TO " + schema);
        return c;
    }

    private void remove(String schema) throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test void upgradesRealFlywayHistoryAndPreservesLegacyOriginals() throws Exception {
        String schema = "solo_migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, "2").migrate();
            try (Connection c = connection(schema)) {
                c.createStatement().execute("""
                    INSERT INTO sample_sequences VALUES (2026, 41);
                    INSERT INTO users (id,name,email,password_hash,role,active)
                    VALUES ('00000000-0000-0000-0000-000000000041','Legado','legado@example.test','hash-original','USER',false);
                    INSERT INTO samples (id,code,block,plot,row_name,crop,sampled_at,temperature,depth,created_at,
                        analysis_updated_at,ph_water,ph_cacl2,phosphorus,potassium,sodium,calcium,sulfur,clay,sand,silt,ec)
                    VALUES ('00000000-0000-0000-0000-000000000042','AMO-2026-0041','B','Q','L','Café legado',
                        '2026-01-01',25,'0-20 cm',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,5,4.8,20,78.2,46,2.5,8,35,50,15,1.5)
                    """);
            }
            flyway(schema, "latest").migrate();
            try (Connection c = connection(schema)) {
                var rs = c.createStatement().executeQuery("SELECT phosphorus,potassium,sodium,calcium,sulfur,clay,sand,silt,ph_water,ec,analysis_schema_version,units->>'calcium' AS ca_unit FROM samples");
                assertThat(rs.next()).isTrue();
                for (var entry : java.util.Map.of("phosphorus","0.02","potassium","2","sodium","2","calcium","25","sulfur","0.008","clay","350","sand","500","silt","150","ph_water","5.4","ec","1.5").entrySet()) {
                    assertThat(rs.getBigDecimal(entry.getKey())).as(entry.getKey()).isEqualByComparingTo(entry.getValue());
                }
                assertThat(rs.getInt("analysis_schema_version")).isEqualTo(2);
                assertThat(rs.getString("ca_unit")).isEqualTo("mmolc/dm³");
                rs = c.createStatement().executeQuery("SELECT original_record->>'calcium' AS calcium, original_record->>'phosphorus' AS phosphorus FROM sample_analysis_legacy_archive");
                assertThat(rs.next()).isTrue();
                assertThat(new java.math.BigDecimal(rs.getString("calcium"))).isEqualByComparingTo("2.5");
                assertThat(new java.math.BigDecimal(rs.getString("phosphorus"))).isEqualByComparingTo("20");
                rs = c.createStatement().executeQuery("SELECT password_hash,active FROM users WHERE email='legado@example.test'");
                assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("hash-original"); assertThat(rs.getBoolean(2)).isFalse();
                rs = c.createStatement().executeQuery("SELECT last_value FROM sample_sequences WHERE year=2026");
                assertThat(rs.next()).isTrue(); assertThat(rs.getInt(1)).isEqualTo(41);
                rs = c.createStatement().executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success");
                assertThat(rs.next()).isTrue(); assertThat(rs.getInt(1)).isEqualTo(7);
            }
            assertThat(flyway(schema,"latest").migrate().migrationsExecuted).isZero();
        } finally { remove(schema); }
    }

    @Test void invalidLegacyValueRollsBackV6WithoutPartialArchive() throws Exception {
        String schema = "solo_migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, "5").migrate();
            try (Connection c = connection(schema)) {
                c.createStatement().execute("""
                    INSERT INTO samples (id,code,block,plot,row_name,crop,sampled_at,temperature,depth,created_at,phosphorus)
                    VALUES ('00000000-0000-0000-0000-000000000043','INVALIDO','B','Q','L','Café','2026-01-01',25,'0-20 cm',CURRENT_TIMESTAMP,-1)
                    """);
            }
            assertThatThrownBy(() -> flyway(schema,"latest").migrate()).isInstanceOf(RuntimeException.class);
            try (Connection c = connection(schema)) {
                var rs = c.createStatement().executeQuery("SELECT phosphorus,analysis_schema_version FROM samples");
                assertThat(rs.next()).isTrue(); assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("-1"); assertThat(rs.getInt(2)).isEqualTo(1);
                rs = c.createStatement().executeQuery("SELECT to_regclass('sample_analysis_legacy_archive')");
                assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isNull();
                rs = c.createStatement().executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version='6' AND success");
                assertThat(rs.next()).isTrue(); assertThat(rs.getInt(1)).isZero();
            }
        } finally { remove(schema); }
    }
}
