package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.Sample;
import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.service.SoilUnits;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
@Profile("!test")
public class PostgresSampleRepository implements SampleRepository {
    private static final String SELECT_COLUMNS = "id, code, service_order_number, block, plot, row_name, crop, sampled_at, temperature, depth, created_at, analysis_updated_at, observations, ph_water, ph_cacl2, organic_matter, organic_carbon, phosphorus, potassium, sodium, calcium, magnesium, aluminum, h_al, sulfur, boron, copper, iron, manganese, zinc, cec_reported, base_sat_reported, clay, sand, silt, units, density, phosphorus_method, analysis_schema_version, record_version, created_by, updated_by, updated_at, analysis_created_by, analysis_created_at, analysis_updated_by";
    private static final String UPSERT = """
        INSERT INTO samples (id, code, service_order_number, block, plot, row_name, crop, sampled_at, temperature, depth, created_at, analysis_updated_at, observations, ph_water, ph_cacl2, organic_matter, organic_carbon, phosphorus, potassium, sodium, calcium, magnesium, aluminum, h_al, sulfur, boron, copper, iron, manganese, zinc, cec_reported, base_sat_reported, clay, sand, silt, units, density, phosphorus_method, analysis_schema_version, record_version, created_by, updated_by, updated_at, analysis_created_by, analysis_created_at, analysis_updated_by)
        VALUES (:id, :code, :serviceOrderNumber, :block, :plot, :row, :crop, :sampledAt, :temperature, :depth, :createdAt, :analysisUpdatedAt, :observations, :phWater, :phCaCl2, :organicMatter, :organicCarbon, :phosphorus, :potassium, :sodium, :calcium, :magnesium, :aluminum, :hAl, :sulfur, :boron, :copper, :iron, :manganese, :zinc, :cecReported, :baseSatReported, :clay, :sand, :silt, CAST(:units AS jsonb), :density, :phosphorusMethod, 2, :recordVersion, :createdBy, :updatedBy, :updatedAt, :analysisCreatedBy, :analysisCreatedAt, :analysisUpdatedBy)
        ON CONFLICT (id) DO UPDATE SET
                service_order_number = EXCLUDED.service_order_number,
                block = EXCLUDED.block,
                plot = EXCLUDED.plot,
                row_name = EXCLUDED.row_name,
                crop = EXCLUDED.crop,
                sampled_at = EXCLUDED.sampled_at,
                temperature = EXCLUDED.temperature,
                depth = EXCLUDED.depth,
                analysis_updated_at = EXCLUDED.analysis_updated_at,
                observations = EXCLUDED.observations,
                ph_water = EXCLUDED.ph_water,
                ph_cacl2 = EXCLUDED.ph_cacl2,
                organic_matter = EXCLUDED.organic_matter,
                organic_carbon = EXCLUDED.organic_carbon,
                phosphorus = EXCLUDED.phosphorus,
                potassium = EXCLUDED.potassium,
                sodium = EXCLUDED.sodium,
                calcium = EXCLUDED.calcium,
                magnesium = EXCLUDED.magnesium,
                aluminum = EXCLUDED.aluminum,
                h_al = EXCLUDED.h_al,
                sulfur = EXCLUDED.sulfur,
                boron = EXCLUDED.boron,
                copper = EXCLUDED.copper,
                iron = EXCLUDED.iron,
                manganese = EXCLUDED.manganese,
                zinc = EXCLUDED.zinc,
                cec_reported = EXCLUDED.cec_reported,
                base_sat_reported = EXCLUDED.base_sat_reported,
                clay = EXCLUDED.clay,
                sand = EXCLUDED.sand,
                silt = EXCLUDED.silt,
                units = EXCLUDED.units,
                density = EXCLUDED.density,
                phosphorus_method = EXCLUDED.phosphorus_method,
                analysis_schema_version = EXCLUDED.analysis_schema_version,
                record_version = EXCLUDED.record_version,
                created_by = EXCLUDED.created_by,
                updated_by = EXCLUDED.updated_by,
                updated_at = EXCLUDED.updated_at,
                analysis_created_by = EXCLUDED.analysis_created_by,
                analysis_created_at = EXCLUDED.analysis_created_at,
                analysis_updated_by = EXCLUDED.analysis_updated_by
        """;
    private final JdbcClient jdbcClient;
    private final ObjectMapper json;

    public PostgresSampleRepository(JdbcClient jdbcClient, ObjectMapper json) {
        this.jdbcClient = jdbcClient;
        this.json = json;
    }
    @Override public List<Sample> findAll() {
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM samples ORDER BY created_at DESC, code DESC")
                .query(this::mapSample).list();
    }
    @Override public Optional<Sample> findById(UUID id) {
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM samples WHERE id = :id")
                .param("id", id).query(this::mapSample).optional();
    }
    @Override public Optional<Sample> findByIdForUpdate(UUID id) {
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM samples WHERE id = :id FOR UPDATE")
            .param("id",id).query(this::mapSample).optional();
    }
    @Override public Sample save(Sample sample) {
        Map<String,Object> values = new HashMap<>();
        values.put("id", sample.id());
        values.put("code", sample.code());
        values.put("serviceOrderNumber", sample.serviceOrderNumber());
        values.put("block", sample.block());
        values.put("plot", sample.plot());
        values.put("row", sample.row());
        values.put("crop", sample.crop());
        values.put("sampledAt", sample.sampledAt());
        values.put("temperature", sample.temperature());
        values.put("depth", sample.depth());
        values.put("createdAt", toSqlTimestamp(sample.createdAt()));
        values.put("analysisUpdatedAt", toSqlTimestamp(sample.analysisUpdatedAt()));
        values.put("observations", sample.observations());
        var t = sample.tracking();
        values.put("recordVersion",t.recordVersion()); values.put("createdBy",t.createdBy());
        values.put("updatedBy",t.updatedBy()); values.put("updatedAt",toSqlTimestamp(t.updatedAt()));
        values.put("analysisCreatedBy",t.analysisCreatedBy()); values.put("analysisCreatedAt",toSqlTimestamp(t.analysisCreatedAt()));
        values.put("analysisUpdatedBy",t.analysisUpdatedBy());
        SoilAnalysis a = sample.analysis();
        values.put("phWater", a == null ? null : a.phWater());
        values.put("phCaCl2", a == null ? null : a.phCaCl2());
        values.put("organicMatter", a == null ? null : a.organicMatter());
        values.put("organicCarbon", a == null ? null : a.organicCarbon());
        values.put("phosphorus", a == null ? null : a.phosphorus());
        values.put("potassium", a == null ? null : a.potassium());
        values.put("sodium", a == null ? null : a.sodium());
        values.put("calcium", a == null ? null : a.calcium());
        values.put("magnesium", a == null ? null : a.magnesium());
        values.put("aluminum", a == null ? null : a.aluminum());
        values.put("hAl", a == null ? null : a.hAl());
        values.put("sulfur", a == null ? null : a.sulfur());
        values.put("boron", a == null ? null : a.boron());
        values.put("copper", a == null ? null : a.copper());
        values.put("iron", a == null ? null : a.iron());
        values.put("manganese", a == null ? null : a.manganese());
        values.put("zinc", a == null ? null : a.zinc());
        values.put("cecReported", a == null ? null : a.cecReported());
        values.put("baseSatReported", a == null ? null : a.baseSatReported());
        values.put("clay", a == null ? null : a.clay());
        values.put("sand", a == null ? null : a.sand());
        values.put("silt", a == null ? null : a.silt());
        values.put("units", json.writeValueAsString(SoilUnits.effectiveUnits(a == null ? null : a.units())));
        values.put("density", a == null ? null : a.density());
        values.put("phosphorusMethod", a == null || a.phosphorusMethod() == null ? "Não informado" : a.phosphorusMethod());
        jdbcClient.sql(UPSERT).params(values).update();
        return sample;
    }
    @Override public int nextSequenceForYear(int year) {
        return jdbcClient.sql("""
            INSERT INTO sample_sequences (year, last_value) VALUES (:year, 1)
            ON CONFLICT (year) DO UPDATE SET last_value = sample_sequences.last_value + 1
            RETURNING last_value
            """).param("year", year).query(Integer.class).single();
    }
    static Timestamp toSqlTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
    private Instant instant(ResultSet rs, String column) throws SQLException { var value=rs.getTimestamp(column); return value==null?null:value.toInstant(); }
    private Sample mapSample(ResultSet rs, int row) throws SQLException {
        if (rs.getInt("analysis_schema_version") != 2) {
            throw new IllegalStateException("Formato de laudo legado encontrado. A migração V6 deve ser aplicada antes de usar o backend integrado.");
        }
        Timestamp updatedAt = rs.getTimestamp("analysis_updated_at");
        SoilAnalysis analysis = updatedAt == null ? null : new SoilAnalysis(
                rs.getBigDecimal("ph_water"),
                rs.getBigDecimal("ph_cacl2"),
                rs.getBigDecimal("organic_matter"),
                rs.getBigDecimal("organic_carbon"),
                rs.getBigDecimal("phosphorus"),
                rs.getBigDecimal("potassium"),
                rs.getBigDecimal("sodium"),
                rs.getBigDecimal("calcium"),
                rs.getBigDecimal("magnesium"),
                rs.getBigDecimal("aluminum"),
                rs.getBigDecimal("h_al"),
                rs.getBigDecimal("sulfur"),
                rs.getBigDecimal("boron"),
                rs.getBigDecimal("copper"),
                rs.getBigDecimal("iron"),
                rs.getBigDecimal("manganese"),
                rs.getBigDecimal("zinc"),
                rs.getBigDecimal("cec_reported"),
                rs.getBigDecimal("base_sat_reported"),
                rs.getBigDecimal("clay"),
                rs.getBigDecimal("sand"),
                rs.getBigDecimal("silt"),
                json.readValue(rs.getString("units"), new TypeReference<Map<String,String>>() {}),
                rs.getBigDecimal("density"), rs.getString("phosphorus_method"));
        return new Sample(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("service_order_number"), rs.getString("block"), rs.getString("plot"),
                rs.getString("row_name"), rs.getString("crop"), rs.getObject("sampled_at", java.time.LocalDate.class),
                rs.getBigDecimal("temperature"), rs.getString("depth"), rs.getTimestamp("created_at").toInstant(),
                analysis, updatedAt == null ? null : updatedAt.toInstant(), rs.getString("observations"),
                new br.com.soloibiapaba.domain.SampleTracking(rs.getLong("record_version"),rs.getObject("created_by",UUID.class),
                rs.getObject("updated_by",UUID.class),instant(rs,"updated_at"),rs.getObject("analysis_created_by",UUID.class),
                instant(rs,"analysis_created_at"),rs.getObject("analysis_updated_by",UUID.class)));
    }
}
