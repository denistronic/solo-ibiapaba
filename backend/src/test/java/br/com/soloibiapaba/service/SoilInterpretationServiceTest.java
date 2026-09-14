package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SoilInterpretationServiceTest {

    private final SoilInterpretationService service = new SoilInterpretationService();

    @Test
    void shouldPreserveTheCoreCalculationsFromTheOriginalTypeScriptMvp() {
        var analysis = new SoilAnalysis(
                bd("5.2"),
                bd("4.8"),
                bd("25"),
                bd("20"),
                bd("117.3"),
                bd("23"),
                bd("3"),
                bd("1"),
                bd("0.2"),
                bd("2.6"),
                bd("8"),
                bd("0.4"),
                bd("0.8"),
                bd("25"),
                bd("8"),
                bd("1"),
                null,
                null,
                bd("35"),
                bd("50"),
                bd("15"),
                bd("0.4")
        );

        var report = service.analyze(analysis);

        assertThat(report.derived()).extracting(item -> item.label() + "=" + item.value())
                .contains(
                        "Soma de bases (SB)=4.40 cmolc/dm³",
                        "CTC efetiva (t)=4.60 cmolc/dm³",
                        "CTC pH 7 (T)=7.00 cmolc/dm³",
                        "V%=62.86 %",
                        "m%=4.35 %",
                        "Ca/Mg=3.00",
                        "Ca/K=10.00",
                        "Mg/K=3.33"
                );

        assertThat(report.insights()).extracting(item -> item.title())
                .contains("Acidez ativa")
                .doesNotContain("Alumínio relevante", "Relação Ca/Mg desequilibrada", "Risco de salinidade");
    }

    @Test
    void shouldReturnDefaultInsightWhenNoCriticalThresholdIsExceeded() {
        var analysis = new SoilAnalysis(
                bd("6.0"), null, bd("20"), bd("20"), bd("100"), null,
                bd("3"), bd("1"), bd("0.1"), bd("2"), bd("8"), bd("0.4"),
                bd("0.8"), null, bd("8"), bd("1"), null, null,
                null, null, null, bd("0.3")
        );

        var report = service.analyze(analysis);

        assertThat(report.insights()).hasSize(1);
        assertThat(report.insights().getFirst().title()).isEqualTo("Sem alerta crítico automático");
        assertThat(report.insights().getFirst().tone()).isEqualTo("good");
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
