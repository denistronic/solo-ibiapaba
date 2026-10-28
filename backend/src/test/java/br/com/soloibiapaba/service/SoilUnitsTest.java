package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SoilUnitsTest {
    private SoilAnalysis sample(Map<String, String> units, BigDecimal density) {
        return new SoilAnalysis(
                null, new BigDecimal("4.8"), new BigDecimal("25"), null, new BigDecimal("20"),
                new BigDecimal("0.54"), new BigDecimal("0.34"), new BigDecimal("58.63"),
                new BigDecimal("1.56"), new BigDecimal("1.90"), new BigDecimal("25.12"),
                new BigDecimal("51.4"), null, null, null, null, null, null, null,
                new BigDecimal("35"), null, null, units, density, "Mehlich 1");
    }

    @Test void shouldConvertChargeConcentrationAndTextureWithoutChangingOriginal() {
        var original = sample(Map.of("calcium", "cmolc/dm³", "phosphorus", "mg/dm³", "clay", "%"), null);
        var converted = SoilUnits.normalize(original);
        assertThat(converted.calcium()).isEqualByComparingTo("586.3");
        assertThat(converted.phosphorus()).isEqualByComparingTo("0.02");
        assertThat(converted.clay()).isEqualByComparingTo("350");
        assertThat(original.calcium()).isEqualByComparingTo("58.63");
        assertThat(original.units()).containsEntry("calcium", "cmolc/dm³");
        assertThat(converted.phosphorusMethod()).isEqualTo("Mehlich 1");
        assertThat(SoilUnits.normalize(converted)).isEqualTo(converted);
    }

    @Test void shouldRequireDensityForMassToVolumeConversion() {
        assertThatThrownBy(() -> SoilUnits.normalize(sample(Map.of("sulfur", "mg/kg"), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("densidade");
        var converted = SoilUnits.normalize(sample(Map.of("sulfur", "mg/kg", "organicMatter", "g/kg"), new BigDecimal("1.2")));
        assertThat(converted.sulfur()).isEqualByComparingTo("0.06168");
        assertThat(converted.organicMatter()).isEqualByComparingTo("30");
    }

    @Test void shouldRejectIncompatibleUnitsAndInvalidTexture() {
        assertThatThrownBy(() -> SoilUnits.normalize(sample(Map.of("calcium", "g/kg"), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SoilUnits.normalize(sample(Map.of("clay", "mg/dm³"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldPreserveExistingAnalysesWithoutUnitMetadata() {
        var original = sample(null, null);
        var normalized = SoilUnits.normalize(original);
        assertThat(normalized.calcium()).isEqualTo(original.calcium());
        assertThat(normalized.phosphorus()).isEqualTo(original.phosphorus());
    }

    @Test void shouldApplyCultureRangesAfterNormalizingUnitsAndKeepPhosphorusMethodsSeparate() {
        var service = new SoilInterpretationService(new InterpretationParameterService(null) {
            @Override public Map<String, Range> ranges(String crop) {
                return Map.of("Cálcio (Ca)", new Range("Cálcio (Ca)", new BigDecimal("500"), new BigDecimal("600")),
                        "Fósforo (P) — Resina", new Range("Fósforo (P) — Resina", BigDecimal.ZERO, BigDecimal.ONE));
            }
        });
        var report = service.analyze(sample(Map.of("calcium", "cmolc/dm³"), null), "Acerola");
        assertThat(report.indicators()).anySatisfy(i -> {
            assertThat(i.label()).isEqualTo("Cálcio (Ca)");
            assertThat(i.level()).isEqualTo("Médio");
        });
        assertThat(report.indicators()).anySatisfy(i -> {
            assertThat(i.label()).isEqualTo("Fósforo (P) — Mehlich 1");
            assertThat(i.level()).isEqualTo("Não configurado");
        });
    }

    @Test void shouldMatchExampleReportAfterConvertingMixedUnits() {
        var original = sample(Map.of("calcium", "cmolc/dm³", "magnesium", "cmolc/dm³",
                "potassium", "cmolc/dm³", "sodium", "cmolc/dm³", "aluminum", "cmolc/dm³",
                "hAl", "cmolc/dm³"), null);
        var report = new SoilInterpretationService(new InterpretationParameterService(null) {
            @Override public Map<String, Range> ranges(String crop) { return Map.of(); }
        }).analyze(original, "Acerola");
        assertThat(report.derived()).extracting(i -> i.label() + "=" + i.value())
                .contains("Soma de bases (SB)=610.70 mmolc/dm³", "CTC pH 7 (T)=861.90 mmolc/dm³", "V%=70.86 %");
        assertThat(report.indicators()).extracting(i -> i.label()).contains("Fósforo (P) — Mehlich 1");
    }
}
