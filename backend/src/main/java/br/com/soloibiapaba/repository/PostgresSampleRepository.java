package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.Sample;
import br.com.soloibiapaba.domain.SoilAnalysis;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class PostgresSampleRepository implements SampleRepository {

    private static final String SELECT_COLUMNS = """
            id, code, block, plot, row_name, crop, sampled_at, temperature, depth,
            created_at, analysis_updated_at, ph_water, ph_cacl2, organic_matter,
            phosphorus, potassium, sodium, calcium, magnesium, aluminum, h_al,
            sulfur, boron, copper, iron, manganese, zinc, cec_reported,
            base_sat_reported, clay, sand, silt, ec
            """;

    private static final String UPSERT = """
            INSERT INTO samples (
                id, code, block, plot, row_name, crop, sampled_at, temperature, depth,
                created_at, analysis_updated_at, ph_water, ph_cacl2, organic_matter,
                phosphorus, potassium, sodium, calcium, magnesium, aluminum, h_al,
                sulfur, boron, copper, iron, manganese, zinc, cec_reported,
                base_sat_reported, clay, sand, silt, ec
            ) VALUES (
                :id, :code, :block, :plot, :rowName, :crop, :sampledAt, :temperature, :depth,
                :createdAt, :analysisUpdatedAt, :phWater, :phCaCl2, :organicMatter,
                :phosphorus, :potassium, :sodium, :calcium, :magnesium, :aluminum, :hAl,
                :sulfur, :boron, :copper, :iron, :manganese, :zinc, :cecReported,
                :baseSatReported, :clay, :sand, :silt, :ec
            )
            ON CONFLICT (id) DO UPDATE SET
                code = EXCLUDED.code,
                block = EXCLUDED.block,
                plot = EXCLUDED.plot,
                row_name = EXCLUDED.row_name,
                crop = EXCLUDED.crop,
                sampled_at = EXCLUDED.sampled_at,
                temperature = EXCLUDED.temperature,
                depth = EXCLUDED.depth,
                analysis_updated_at = EXCLUDED.analysis_updated_at,
                ph_water = EXCLUDED.ph_water,
                ph_cacl2 = EXCLUDED.ph_cacl2,
                organic_matter = EXCLUDED.organic_matter,
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
                ec = EXCLUDED.ec
            """;

    private final JdbcClient jdbcClient;
    private final RowMapper<Sample> rowMapper = this::mapSample;

    public PostgresSampleRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<Sample> findAll() {
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM samples ORDER BY created_at DESC")
                .query(rowMapper)
                .list();
    }

    @Override
    public Optional<Sample> findById(UUID id) {
        return jdbcClient.sql("SELECT " + SELECT_COLUMNS + " FROM samples WHERE id = :id")
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    @Override
    public Sample save(Sample sample) {
        jdbcClient.sql(UPSERT).params(toParameters(sample)).update();
        return sample;
    }

    @Override
    public int nextSequenceForYear(int year) {
        return jdbcClient.sql("""
                        INSERT INTO sample_sequences (year, last_value)
                        VALUES (:year, 1)
                        ON CONFLICT (year) DO UPDATE
                        SET last_value = sample_sequences.last_value + 1
                        RETURNING last_value
                        """)
                .param("year", year)
                .query(Integer.class)
                .single();
    }

    private Map<String, Object> toParameters(Sample sample) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", sample.id());
        values.put("code", sample.code());
        values.put("block", sample.block());
        values.put("plot", sample.plot());
        values.put("rowName", sample.row());
        values.put("crop", sample.crop());
        values.put("sampledAt", sample.sampledAt());
        values.put("temperature", sample.temperature());
        values.put("depth", sample.depth());
        // O driver PostgreSQL não infere o tipo JDBC de java.time.Instant.
        // Timestamp é mapeado corretamente para as colunas TIMESTAMPTZ.
        values.put("createdAt", toSqlTimestamp(sample.createdAt()));
        values.put("analysisUpdatedAt", toSqlTimestamp(sample.analysisUpdatedAt()));

        SoilAnalysis analysis = sample.analysis();
        values.put("phWater", analysis == null ? null : analysis.phWater());
        values.put("phCaCl2", analysis == null ? null : analysis.phCaCl2());
        values.put("organicMatter", analysis == null ? null : analysis.organicMatter());
        values.put("phosphorus", analysis == null ? null : analysis.phosphorus());
        values.put("potassium", analysis == null ? null : analysis.potassium());
        values.put("sodium", analysis == null ? null : analysis.sodium());
        values.put("calcium", analysis == null ? null : analysis.calcium());
        values.put("magnesium", analysis == null ? null : analysis.magnesium());
        values.put("aluminum", analysis == null ? null : analysis.aluminum());
        values.put("hAl", analysis == null ? null : analysis.hAl());
        values.put("sulfur", analysis == null ? null : analysis.sulfur());
        values.put("boron", analysis == null ? null : analysis.boron());
        values.put("copper", analysis == null ? null : analysis.copper());
        values.put("iron", analysis == null ? null : analysis.iron());
        values.put("manganese", analysis == null ? null : analysis.manganese());
        values.put("zinc", analysis == null ? null : analysis.zinc());
        values.put("cecReported", analysis == null ? null : analysis.cecReported());
        values.put("baseSatReported", analysis == null ? null : analysis.baseSatReported());
        values.put("clay", analysis == null ? null : analysis.clay());
        values.put("sand", analysis == null ? null : analysis.sand());
        values.put("silt", analysis == null ? null : analysis.silt());
        values.put("ec", analysis == null ? null : analysis.ec());
        return values;
    }

    static Timestamp toSqlTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Sample mapSample(ResultSet rs, int rowNumber) throws SQLException {
        var updatedAt = rs.getTimestamp("analysis_updated_at");
        SoilAnalysis analysis = updatedAt == null ? null : new SoilAnalysis(
                rs.getBigDecimal("ph_water"),
                rs.getBigDecimal("ph_cacl2"),
                rs.getBigDecimal("organic_matter"),
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
                rs.getBigDecimal("ec")
        );

        return new Sample(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("block"),
                rs.getString("plot"),
                rs.getString("row_name"),
                rs.getString("crop"),
                rs.getObject("sampled_at", java.time.LocalDate.class),
                rs.getBigDecimal("temperature"),
                rs.getString("depth"),
                rs.getTimestamp("created_at").toInstant(),
                analysis,
                updatedAt == null ? null : updatedAt.toInstant()
        );
    }
}
