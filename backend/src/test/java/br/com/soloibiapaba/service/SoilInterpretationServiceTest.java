package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.dto.SoilReportResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SoilInterpretationServiceTest {

    private final SoilInterpretationService service = new SoilInterpretationService();

    @Test
    void shouldCalculateDerivedValuesUsingMmolcPerDm3() {
        SoilAnalysis analysis = analysis(
                "5.2", "4.8", "25", "15", "0.02",
                "3", "1", "30", "10", "2", "26",
                "0.008", "0.0004", "0.0008", "0.025", "0.008", "0.001",
                "70", "62.86", "350", "500", "150"
        );

        SoilReportResponse report = service.analyze(analysis);

        assertThat(report.derived())
                .extracting(item -> item.label() + "=" + item.value())
                .contains(
                        "Soma de bases (SB)=44.00 mmolc/dm³",
                        "CTC efetiva (t)=46.00 mmolc/dm³",
                        "CTC pH 7 (T)=70.00 mmolc/dm³",
                        "V%=62.86 %",
                        "m%=4.35 %",
                        "Ca/Mg=3.00",
                        "Ca/K=10.00",
                        "Mg/K=3.33"
                );

        assertThat(report.summary())
                .extracting(item -> item.label() + "=" + item.value())
                .contains("pH em água (calculado)=5.4");

        assertThat(report.insights())
                .extracting(SoilReportResponse.InsightItem::title)
                .contains("Acidez ativa")
                .doesNotContain(
                        "Alumínio relevante",
                        "Relação Ca/Mg desequilibrada",
                        "Competição K × Mg",
                        "Risco de salinidade"
                );
    }

    @Test
    void shouldDeriveWaterPhFromCalciumChloridePhEvenWhenStoredWaterPhIsMissing() {
        SoilAnalysis analysis = analysis(
                null, "4.8", "20", "12", "0.025",
                "3", "1", "30", "10", "0.1", "10",
                "0.008", "0.0004", "0.0008", "0.02", "0.008", "0.001",
                "54", "81.48", "350", "500", "150"
        );

        SoilReportResponse report = service.analyze(analysis);

        assertThat(report.summary())
                .extracting(item -> item.label() + "=" + item.value())
                .contains("pH em água (calculado)=5.4");
        assertThat(report.indicators())
                .anySatisfy(item -> {
                    assertThat(item.label()).isEqualTo("pH em água (calculado)");
                    assertThat(item.formatted()).isEqualTo("5.40");
                });
    }

    @Test
    void shouldShowNewFieldsWithRequestedUnitsAndAvoidMehlichClassification() {
        SoilReportResponse report = service.analyze(analysis(
                "6.4", "5.8", "25", "15", "0.02",
                "3", "1", "30", "10", "0.1", "10",
                "0.008", "0.0004", "0.0008", "0.025", "0.008", "0.001",
                "54", "81.48", "350", "500", "150"
        ));

        List<String> indicators = report.indicators()
                .stream()
                .map(item -> item.label() + "=" + item.formatted() + " [" + item.level() + "]")
                .toList();

        assertThat(indicators).contains(
                "Carbono orgânico total=15.00 g/dm³ [Informativo]",
                "Fósforo (P) — Resina=0.02 g/dm³ [Informativo]",
                "Cálcio (Ca)=30.00 mmolc/dm³ [Médio]",
                "Magnésio (Mg)=10.00 mmolc/dm³ [Médio]",
                "Potássio (K)=3.00 mmolc/dm³ [Médio]",
                "Sódio (Na)=1.00 mmolc/dm³ [Informativo]",
                "Alumínio total (Al)=0.10 mmolc/dm³ [Baixo]",
                "Acidez total (H+Al)=10.00 mmolc/dm³ [Informativo]",
                "CTC informada pelo laboratório=54.00 mmolc/dm³ [Informativo]",
                "Enxofre (S)=0.008 g/dm³ [Médio]",
                "Boro (B)=0.0004 g/dm³ [Médio]",
                "Ferro (Fe)=0.025 g/dm³ [Informativo]",
                "Argila=350.00 g/kg [Informativo]",
                "Silte=150.00 g/kg [Informativo]",
                "Areia total=500.00 g/kg [Informativo]"
        );

        assertThat(indicators)
                .noneMatch(label -> label.contains("Mehlich")
                        || label.contains("Condutividade elétrica"));
    }

    @Test
    void shouldReturnDefaultInsightWhenNoAutomaticAlertIsTriggered() {
        SoilAnalysis analysis = analysis(
                null, "5.8", "20", "12", "0.025",
                "3", "1", "30", "10", "0.1", "10",
                "0.008", "0.0004", "0.0008", "0.02", "0.008", "0.001",
                "54", "81.48", "350", "500", "150"
        );

        SoilReportResponse report = service.analyze(analysis);

        assertThat(report.insights()).hasSize(1);
        assertThat(report.insights().getFirst().title())
                .isEqualTo("Sem alerta crítico automático");
        assertThat(report.insights().getFirst().tone()).isEqualTo("good");
    }

    private SoilAnalysis analysis(
            String phWater,
            String phCaCl2,
            String organicMatter,
            String organicCarbon,
            String phosphorus,
            String potassium,
            String sodium,
            String calcium,
            String magnesium,
            String aluminum,
            String hAl,
            String sulfur,
            String boron,
            String copper,
            String iron,
            String manganese,
            String zinc,
            String cecReported,
            String baseSatReported,
            String clay,
            String sand,
            String silt) {

        return new SoilAnalysis(
                bd(phWater),
                bd(phCaCl2),
                bd(organicMatter),
                bd(organicCarbon),
                bd(phosphorus),
                bd(potassium),
                bd(sodium),
                bd(calcium),
                bd(magnesium),
                bd(aluminum),
                bd(hAl),
                bd(sulfur),
                bd(boron),
                bd(copper),
                bd(iron),
                bd(manganese),
                bd(zinc),
                bd(cecReported),
                bd(baseSatReported),
                bd(clay),
                bd(sand),
                bd(silt)
        );
    }

    private BigDecimal bd(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
