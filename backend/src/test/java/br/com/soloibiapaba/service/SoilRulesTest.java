package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SoilRulesTest {
    private BigDecimal n(String v) {return v==null?null:new BigDecimal(v);}
    private SoilAnalysis analysis(String ph, String ca,String mg,String k,String na,String al,String h) {
        return new SoilAnalysis(null,n(ph),null,null,null,n(k),n(na),n(ca),n(mg),n(al),n(h),
            null,null,null,null,null,null,null,null,null,null,null,Map.of(),null,null);
    }
    private final SoilInterpretationService reports=new SoilInterpretationService(new InterpretationParameterService(null) {
        @Override public Map<String,Range> ranges(String crop) {
            return Map.of("Saturação por bases",new Range("Saturação por bases",n("50"),n("50")),
                "Fósforo (P) — Resina",new Range("Fósforo (P) — Resina",n("0"),n("1")));
        }
    });
    @Test void shouldClassifyTheExactBoundaryUsingDecimalArithmetic() {
        var report=reports.analyze(analysis("5.4","0.1","0.2","0","0","0","0.3"),"Café");
        assertThat(report.summary()).anySatisfy(item->{assertThat(item.label()).isEqualTo("V%");assertThat(item.value()).isEqualTo("50.00 %");assertThat(item.level()).isEqualTo("Médio");});
    }
    @Test void shouldWarnAboutMissingDataEvenWhenThereIsAnAcidityAlert() {
        var report=reports.analyze(analysis("4.8",null,null,null,null,null,null),"Café");
        assertThat(report.insights()).extracting(i->i.title()).contains("Acidez ativa","Dados insuficientes").doesNotContain("Sem alerta crítico automático");
        assertThat(report.derived()).allSatisfy(i->assertThat(i.value()).isEqualTo("—"));
    }
    @Test void shouldNotTreatZeroDenominatorsAsHealthy() {
        var report=reports.analyze(analysis("5.4","0","0","0","0","0","0"),"Café");
        assertThat(report.derived()).anySatisfy(i->{assertThat(i.label()).isEqualTo("V%");assertThat(i.value()).isEqualTo("—");});
        assertThat(report.insights()).extracting(i->i.title()).contains("Dados insuficientes").doesNotContain("Sem alerta crítico automático");
    }
    @Test void shouldNotAssumeResinWhenMethodIsMissing() {
        var report=reports.analyze(analysis(null,null,null,null,null,null,null),"Café");
        assertThat(report.indicators()).extracting(i->i.label()).contains("Fósforo (P) — Não informado").doesNotContain("Fósforo (P) — Resina");
    }
    @Test void shouldRejectNullUnitsWithAValidationError() {
        var units=new java.util.HashMap<String,String>();units.put("calcium",null);
        assertThatThrownBy(()->SoilUnits.effectiveUnits(units)).isInstanceOf(IllegalArgumentException.class);
    }
}
