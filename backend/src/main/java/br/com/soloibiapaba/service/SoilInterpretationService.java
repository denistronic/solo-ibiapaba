package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.dto.SoilReportResponse;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SoilInterpretationService {
    private final InterpretationParameterService parameters;
    public SoilInterpretationService(InterpretationParameterService parameters) { this.parameters = parameters; }

    public SoilReportResponse analyze(SoilAnalysis original, String crop) {
        SoilAnalysis a = SoilUnits.normalize(original);
        var ranges = parameters.ranges(crop);
        BigDecimal ph = a.phCaCl2() == null ? a.phWater() : a.phCaCl2().add(new BigDecimal("0.6"));
        BigDecimal sb = sum(a.calcium(), a.magnesium(), a.potassium(), a.sodium());
        BigDecimal effective = sum(sb, a.aluminum());
        BigDecimal total = sum(sb, a.hAl());
        BigDecimal v = divide(sb == null ? null : sb.multiply(new BigDecimal("100")), total);
        BigDecimal m = divide(a.aluminum() == null ? null : a.aluminum().multiply(new BigDecimal("100")), effective);
        BigDecimal caMg = divide(a.calcium(), a.magnesium());
        BigDecimal caK = divide(a.calcium(), a.potassium());
        BigDecimal mgK = divide(a.magnesium(), a.potassium());

        var summary = List.of(
            new SoilReportResponse.SummaryItem("pH em água (calculado)", ph == null ? "—" : oneDecimal(ph), level(ph,ranges,"pH em água (calculado)")),
            new SoilReportResponse.SummaryItem("V%", fmt(v,"%"), level(v,ranges,"Saturação por bases")),
            new SoilReportResponse.SummaryItem("m%", fmt(m,"%"), level(m,ranges,"Saturação por alumínio")),
            new SoilReportResponse.SummaryItem("CTC pH 7",fmt(total,"mmolc/dm³"),total == null ? "Sem dado" : "Informativo")
        );
        List<SoilReportResponse.IndicatorItem> indicators = new ArrayList<>();
        add(indicators,ranges,"pH em água (calculado)",ph,"");
        add(indicators,ranges,"Matéria orgânica",a.organicMatter(),"g/dm³");
        add(indicators,ranges,"Carbono orgânico total",a.organicCarbon(),"g/dm³");
        String method = a.phosphorusMethod() == null ? "Não informado" : a.phosphorusMethod();
        add(indicators,ranges,"Fósforo (P) — " + method,a.phosphorus(),"g/dm³");
        add(indicators,ranges,"Potássio (K)",a.potassium(),"mmolc/dm³");
        add(indicators,ranges,"Sódio (Na)",a.sodium(),"mmolc/dm³");
        add(indicators,ranges,"Cálcio (Ca)",a.calcium(),"mmolc/dm³");
        add(indicators,ranges,"Magnésio (Mg)",a.magnesium(),"mmolc/dm³");
        add(indicators,ranges,"Alumínio total (Al)",a.aluminum(),"mmolc/dm³");
        add(indicators,ranges,"Acidez total (H+Al)",a.hAl(),"mmolc/dm³");
        add(indicators,ranges,"CTC informada pelo laboratório",a.cecReported(),"mmolc/dm³");
        add(indicators,ranges,"V informado pelo laboratório",a.baseSatReported(),"%");
        add(indicators,ranges,"Saturação por bases",v,"%");
        add(indicators,ranges,"Saturação por alumínio",m,"%");
        add(indicators,ranges,"Enxofre (S)",a.sulfur(),"g/dm³");
        add(indicators,ranges,"Boro (B)",a.boron(),"g/dm³");
        add(indicators,ranges,"Cobre (Cu)",a.copper(),"g/dm³");
        add(indicators,ranges,"Ferro (Fe)",a.iron(),"g/dm³");
        add(indicators,ranges,"Manganês (Mn)",a.manganese(),"g/dm³");
        add(indicators,ranges,"Zinco (Zn)",a.zinc(),"g/dm³");
        add(indicators,ranges,"Argila",a.clay(),"g/kg");
        add(indicators,ranges,"Silte",a.silt(),"g/kg");
        add(indicators,ranges,"Areia total",a.sand(),"g/kg");

        // Mantém os alertas gerais anteriores; não substituem as faixas por cultura.
        List<SoilReportResponse.InsightItem> insights = new ArrayList<>();
        if (below(ph,"5.5")) insights.add(new SoilReportResponse.InsightItem("Acidez ativa",
            "pH em água calculado abaixo da faixa geral pode limitar nutrientes e elevar o risco associado ao alumínio.","warning"));
        if (above(m,"20")) insights.add(new SoilReportResponse.InsightItem("Alumínio relevante",
            "A saturação por alumínio calculada é %s%%. Avaliar calagem conforme cultura, camada e PRNT.".formatted(oneDecimal(m)),"danger"));
        if (below(caMg,"2") || above(caMg,"8")) insights.add(new SoilReportResponse.InsightItem("Relação Ca/Mg desequilibrada",
            "Relação calculada de %s. Use a relação como alerta, não como meta isolada.".formatted(oneDecimal(caMg)),"warning"));
        if (below(mgK,"3")) insights.add(new SoilReportResponse.InsightItem("Competição K × Mg",
            "Potássio proporcionalmente elevado pode agravar a absorção de magnésio em culturas sensíveis.","warning"));
        if (ph == null || v == null || m == null || caMg == null || caK == null || mgK == null) {
            insights.add(new SoilReportResponse.InsightItem("Dados insuficientes",
                "Há dados ausentes ou divisores iguais a zero; não é possível concluir a triagem dos equilíbrios do solo.","warning"));
        }
        if (insights.isEmpty()) insights.add(new SoilReportResponse.InsightItem("Sem alerta crítico automático",
            "Os principais equilíbrios calculados não ultrapassaram os limites gerais de triagem.","good"));

        var derived = List.of(
            new SoilReportResponse.DerivedItem("Soma de bases (SB)",fmt(sb,"mmolc/dm³"),"Ca + Mg + K + Na"),
            new SoilReportResponse.DerivedItem("CTC efetiva (t)",fmt(effective,"mmolc/dm³"),"SB + Al"),
            new SoilReportResponse.DerivedItem("CTC pH 7 (T)",fmt(total,"mmolc/dm³"),"SB + H+Al"),
            new SoilReportResponse.DerivedItem("V%",fmt(v,"%"),"100 × SB / T"),
            new SoilReportResponse.DerivedItem("m%",fmt(m,"%"),"100 × Al / t"),
            new SoilReportResponse.DerivedItem("Ca/Mg",fmt(caMg,""),"Ca ÷ Mg"),
            new SoilReportResponse.DerivedItem("Ca/K",fmt(caK,""),"Ca ÷ K (mmolc)"),
            new SoilReportResponse.DerivedItem("Mg/K",fmt(mgK,""),"Mg ÷ K (mmolc)")
        );
        return new SoilReportResponse(summary,List.copyOf(indicators),List.copyOf(insights),derived);
    }
    private static BigDecimal sum(BigDecimal... values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) { if (value == null) return null; result = result.add(value); }
        return result;
    }
    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() == 0
                ? null : numerator.divide(denominator, MathContext.DECIMAL128);
    }
    private static boolean below(BigDecimal value,String limit) { return value != null && value.compareTo(new BigDecimal(limit)) < 0; }
    private static boolean above(BigDecimal value,String limit) { return value != null && value.compareTo(new BigDecimal(limit)) > 0; }
    private static String level(BigDecimal value,Map<String,InterpretationParameterService.Range> ranges,String label) {
        return InterpretationParameterService.classify(value,ranges.get(label));
    }
    private static void add(List<SoilReportResponse.IndicatorItem> items,Map<String,InterpretationParameterService.Range> ranges,
            String label,BigDecimal value,String unit) {
        items.add(new SoilReportResponse.IndicatorItem(label,fmt(value,unit),level(value,ranges,label)));
    }
    private static String fmt(BigDecimal value,String unit) {
        if (value == null) return "—";
        BigDecimal rounded = value.setScale(value.abs().compareTo(BigDecimal.ONE) >= 0 ? 2 : 10,RoundingMode.HALF_UP);
        String number;
        if (value.signum() != 0 && rounded.signum() == 0) number = value.stripTrailingZeros().toEngineeringString();
        else if (value.abs().compareTo(BigDecimal.ONE) >= 0) number = rounded.toPlainString();
        else number = rounded.stripTrailingZeros().setScale(Math.max(2,rounded.stripTrailingZeros().scale())).toPlainString();
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }
    private static String oneDecimal(BigDecimal value) { return value.setScale(1,RoundingMode.HALF_UP).toPlainString(); }
}
