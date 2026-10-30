package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class IntegratedBackendValidationTest {
    private SoilAnalysis empty() {
        return new SoilAnalysis(null,null,null,null,null,null,null,null,null,null,null,
            null,null,null,null,null,null,null,null,null,null,null);
    }
    @Test void shouldNotInventDerivedValuesForAnEmptyAnalysis() {
        var reports = new SoilInterpretationService(new InterpretationParameterService(null) {
            @Override public Map<String,Range> ranges(String crop) { return Map.of(); }
        });
        var report = reports.analyze(empty(), "Café");
        assertThat(report.derived()).allSatisfy(item -> assertThat(item.value()).isEqualTo("—"));
        assertThat(report.insights()).extracting(i->i.title()).containsExactly("Dados insuficientes");
    }
    @Test void shouldCompleteUnitsAndRejectUnknownMetadata() {
        assertThat(SoilUnits.effectiveUnits(null)).hasSize(19).containsEntry("potassium","mmolc/dm³");
        assertThat(SoilUnits.effectiveUnits(Map.of("clay","%"))).containsEntry("clay","%").containsEntry("phosphorus","g/dm³");
        assertThatThrownBy(()->SoilUnits.effectiveUnits(Map.of("ec","dS/m"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void shouldRejectPrecisionLossBeforeWritingToPostgres() {
        var a=empty();
        var tiny = new SoilAnalysis(a.phWater(),a.phCaCl2(),new BigDecimal("0.00000000001"),a.organicCarbon(),a.phosphorus(),
            a.potassium(),a.sodium(),a.calcium(),a.magnesium(),a.aluminum(),a.hAl(),a.sulfur(),a.boron(),a.copper(),a.iron(),
            a.manganese(),a.zinc(),a.cecReported(),a.baseSatReported(),a.clay(),a.sand(),a.silt());
        assertThatThrownBy(()->SoilUnits.normalize(tiny)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precisão");
    }
}
