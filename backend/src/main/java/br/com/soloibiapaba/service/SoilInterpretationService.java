package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.dto.SoilReportResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SoilInterpretationService {
    private final InterpretationParameterService parameters;
    public SoilInterpretationService(InterpretationParameterService parameters) { this.parameters = parameters; }


    /**
     * Produz os indicadores e cálculos derivados do laudo.
     *
     * Unidades esperadas na análise:
     * - Ca, Mg, K, Na, Al, H+Al e CTC: mmolc/dm³;
     * - matéria orgânica, carbono orgânico e nutrientes: g/dm³;
     * - argila, silte e areia: g/kg.
     *
     * A classificação dos indicadores utiliza os limites cadastrados por cultura.
     */
    public SoilReportResponse analyze(SoilAnalysis analysis, String crop) {
        var ranges = parameters.ranges(crop);
        // O pH em água é derivado do pH em CaCl2 conforme orientação do laboratório.
        // O cálculo aqui também protege relatórios antigos/entradas sem phWater.
        double phWater = analysis.phCaCl2() != null
                ? n(analysis.phCaCl2()) + 0.6
                : n(analysis.phWater());

        // Todos os cátions são recebidos diretamente em mmolc/dm³.
        double potassium = n(analysis.potassium());
        double sodium = n(analysis.sodium());
        double calcium = n(analysis.calcium());
        double magnesium = n(analysis.magnesium());
        double aluminum = n(analysis.aluminum());
        double totalAcidity = n(analysis.hAl());

        // Cálculos de fertilidade, mantendo a mesma unidade dos cátions.
        double baseSum = calcium + magnesium + potassium + sodium;
        double effectiveCec = baseSum + aluminum;
        double cecAtPh7 = baseSum + totalAcidity;
        double baseSaturation = cecAtPh7 != 0.0
                ? 100.0 * baseSum / cecAtPh7
                : 0.0;
        double aluminumSaturation = effectiveCec != 0.0
                ? 100.0 * aluminum / effectiveCec
                : 0.0;

        // As relações são adimensionais; todos os valores estão em mmolc/dm³.
        double caMg = magnesium != 0.0 ? calcium / magnesium : 0.0;
        double caK = potassium != 0.0 ? calcium / potassium : 0.0;
        double mgK = potassium != 0.0 ? magnesium / potassium : 0.0;

        BigDecimal indicatorPh = analysis.phCaCl2() != null
                ? analysis.phCaCl2().add(new BigDecimal("0.6")) : analysis.phWater();
        boolean hasBases = analysis.calcium() != null && analysis.magnesium() != null
                && analysis.potassium() != null && analysis.sodium() != null;
        BigDecimal indicatorV = hasBases && analysis.hAl() != null && cecAtPh7 != 0.0
                ? BigDecimal.valueOf(baseSaturation) : null;
        BigDecimal indicatorM = hasBases && analysis.aluminum() != null && effectiveCec != 0.0
                ? BigDecimal.valueOf(aluminumSaturation) : null;

        List<SoilReportResponse.SummaryItem> summary = List.of(
                new SoilReportResponse.SummaryItem(
                        "pH em água (calculado)",
                        phWater > 0.0 ? oneDecimal(phWater) : "—",
                        InterpretationParameterService.classify(indicatorPh, ranges.get("pH em água (calculado)"))
                ),
                new SoilReportResponse.SummaryItem(
                        "V%",
                        indicatorV == null ? "—" : fmt(baseSaturation, "%"),
                        InterpretationParameterService.classify(indicatorV, ranges.get("Saturação por bases"))
                ),
                new SoilReportResponse.SummaryItem(
                        "m%",
                        indicatorM == null ? "—" : fmt(aluminumSaturation, "%"),
                        InterpretationParameterService.classify(indicatorM, ranges.get("Saturação por alumínio"))
                ),
                new SoilReportResponse.SummaryItem(
                        "CTC pH 7",
                        fmt(cecAtPh7, "mmolc/dm³"),
                        "Informativo"
                )
        );

        List<SoilReportResponse.IndicatorItem> indicators = new ArrayList<>();

        addConfiguredIndicator(indicators, ranges, "pH em água (calculado)", indicatorPh, "");
        addConfiguredIndicator(indicators, ranges, "Matéria orgânica", analysis.organicMatter(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Carbono orgânico total", analysis.organicCarbon(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Fósforo (P) — Resina", analysis.phosphorus(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Potássio (K)", analysis.potassium(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "Sódio (Na)", analysis.sodium(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "Cálcio (Ca)", analysis.calcium(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "Magnésio (Mg)", analysis.magnesium(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "Alumínio total (Al)", analysis.aluminum(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "Acidez total (H+Al)", analysis.hAl(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "CTC informada pelo laboratório", analysis.cecReported(), "mmolc/dm³");
        addConfiguredIndicator(indicators, ranges, "V informado pelo laboratório", analysis.baseSatReported(), "%");
        addConfiguredIndicator(indicators, ranges, "Saturação por bases", indicatorV, "%");
        addConfiguredIndicator(indicators, ranges, "Saturação por alumínio", indicatorM, "%");
        addConfiguredIndicator(indicators, ranges, "Enxofre (S)", analysis.sulfur(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Boro (B)", analysis.boron(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Cobre (Cu)", analysis.copper(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Ferro (Fe)", analysis.iron(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Manganês (Mn)", analysis.manganese(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Zinco (Zn)", analysis.zinc(), "g/dm³");
        addConfiguredIndicator(indicators, ranges, "Argila", analysis.clay(), "g/kg");
        addConfiguredIndicator(indicators, ranges, "Silte", analysis.silt(), "g/kg");
        addConfiguredIndicator(indicators, ranges, "Areia total", analysis.sand(), "g/kg");

        List<SoilReportResponse.InsightItem> insights = new ArrayList<>();

        if (phWater > 0.0 && phWater < 5.5) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Acidez ativa",
                    "pH em água calculado abaixo da faixa geral pode limitar nutrientes e elevar o risco associado ao alumínio.",
                    "warning"
            ));
        }

        if (aluminumSaturation > 20) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Alumínio relevante",
                    "A saturação por alumínio calculada é %s%%. Avaliar calagem conforme cultura, camada e PRNT."
                            .formatted(oneDecimal(aluminumSaturation)),
                    "danger"
            ));
        }

        if (caMg != 0.0 && (caMg < 2 || caMg > 8)) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Relação Ca/Mg desequilibrada",
                    "Relação calculada de %s. Use a relação como alerta, não como meta isolada."
                            .formatted(oneDecimal(caMg)),
                    "warning"
            ));
        }

        if (mgK != 0.0 && mgK < 3) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Competição K × Mg",
                    "Potássio proporcionalmente elevado pode agravar a absorção de magnésio em culturas sensíveis.",
                    "warning"
            ));
        }

        // Não há mais indicador nem alerta de condutividade elétrica.
        if (insights.isEmpty()) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Sem alerta crítico automático",
                    "Os principais equilíbrios calculados não ultrapassaram os limites gerais de triagem.",
                    "good"
            ));
        }

        List<SoilReportResponse.DerivedItem> derived = List.of(
                new SoilReportResponse.DerivedItem(
                        "Soma de bases (SB)",
                        fmt(baseSum, "mmolc/dm³"),
                        "Ca + Mg + K + Na"
                ),
                new SoilReportResponse.DerivedItem(
                        "CTC efetiva (t)",
                        fmt(effectiveCec, "mmolc/dm³"),
                        "SB + Al"
                ),
                new SoilReportResponse.DerivedItem(
                        "CTC pH 7 (T)",
                        fmt(cecAtPh7, "mmolc/dm³"),
                        "SB + H+Al"
                ),
                new SoilReportResponse.DerivedItem("V%", fmt(baseSaturation, "%"), "100 × SB / T"),
                new SoilReportResponse.DerivedItem("m%", fmt(aluminumSaturation, "%"), "100 × Al / t"),
                new SoilReportResponse.DerivedItem("Ca/Mg", fmt(caMg, ""), "Ca ÷ Mg"),
                new SoilReportResponse.DerivedItem("Ca/K", fmt(caK, ""), "Ca ÷ K (mmolc)"),
                new SoilReportResponse.DerivedItem("Mg/K", fmt(mgK, ""), "Mg ÷ K (mmolc)")
        );

        return new SoilReportResponse(summary, List.copyOf(indicators), List.copyOf(insights), derived);
    }

    private void addConfiguredIndicator(List<SoilReportResponse.IndicatorItem> indicators,
            java.util.Map<String, InterpretationParameterService.Range> ranges,
            String label, BigDecimal value, String unit) {
        indicators.add(new SoilReportResponse.IndicatorItem(label,
                value == null ? "—" : fmt(value.doubleValue(), unit),
                InterpretationParameterService.classify(value, ranges.get(label))));
    }

    private double n(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String fmt(double value, String unit) {
        String number;
        if (!Double.isFinite(value)) {
            number = "—";
        } else {
            // Mantém duas casas para valores >= 1 e até seis para valores menores,
            // evitando que nutrientes em g/dm³ apareçam como 0,00.
            int decimals = Math.abs(value) >= 1.0 ? 2 : 6;
            number = String.format(Locale.ROOT, "%." + decimals + "f", value).trim();
            if (Math.abs(value) < 1.0 && number.indexOf('.') >= 0) {
                number = number.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
        }
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
