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

    public SoilReportResponse analyze(SoilAnalysis analysis) {
        double potassiumCmol = n(analysis.potassium()) / 391.0;
        double sodiumCmol = n(analysis.sodium()) / 230.0;
        double baseSum = n(analysis.calcium()) + n(analysis.magnesium()) + potassiumCmol + sodiumCmol;
        double effectiveCec = baseSum + n(analysis.aluminum());
        double cecAtPh7 = baseSum + n(analysis.hAl());
        double baseSaturation = cecAtPh7 != 0.0 ? 100.0 * baseSum / cecAtPh7 : 0.0;
        double aluminumSaturation = effectiveCec != 0.0 ? 100.0 * n(analysis.aluminum()) / effectiveCec : 0.0;
        double caMg = n(analysis.magnesium()) != 0.0 ? n(analysis.calcium()) / n(analysis.magnesium()) : 0.0;
        double caK = potassiumCmol != 0.0 ? n(analysis.calcium()) / potassiumCmol : 0.0;
        double mgK = potassiumCmol != 0.0 ? n(analysis.magnesium()) / potassiumCmol : 0.0;

        List<SoilReportResponse.SummaryItem> summary = List.of(
                new SoilReportResponse.SummaryItem(
                        "pH",
                        n(analysis.phWater()) != 0.0 ? oneDecimal(n(analysis.phWater())) : "—",
                        n(analysis.phWater()) != 0.0 ? classify(n(analysis.phWater()), 5.5, 6.5) : "Sem dado"
                ),
                new SoilReportResponse.SummaryItem("V%", fmt(baseSaturation, "%"), classify(baseSaturation, 40, 70)),
                new SoilReportResponse.SummaryItem(
                        "m%",
                        fmt(aluminumSaturation, "%"),
                        aluminumSaturation < 10 ? "Baixo" : aluminumSaturation <= 30 ? "Médio" : "Alto"
                ),
                new SoilReportResponse.SummaryItem("CTC pH 7", fmt(cecAtPh7, "cmolc/dm³"), classify(cecAtPh7, 4.3, 8.6))
        );

        List<SoilReportResponse.IndicatorItem> indicators = new ArrayList<>();
        addIndicator(indicators, "pH em água", n(analysis.phWater()), 5.5, 6.5, "");
        addIndicator(indicators, "Matéria orgânica", n(analysis.organicMatter()), 15, 30, "g/dm³");
        addIndicator(indicators, "Fósforo — Mehlich-1*", n(analysis.phosphorus()), 10, 30, "mg/dm³");
        addIndicator(indicators, "Potássio", n(analysis.potassium()), 45, 120, "mg/dm³");
        addIndicator(indicators, "Cálcio", n(analysis.calcium()), 1.5, 4, "cmolc/dm³");
        addIndicator(indicators, "Magnésio", n(analysis.magnesium()), 0.5, 1.5, "cmolc/dm³");
        addIndicator(indicators, "Alumínio", n(analysis.aluminum()), 0.2, 0.5, "cmolc/dm³");
        addIndicator(indicators, "Saturação por bases", baseSaturation, 40, 70, "%");
        addIndicator(indicators, "Saturação por alumínio", aluminumSaturation, 10, 30, "%");
        addIndicator(indicators, "Enxofre", n(analysis.sulfur()), 5, 10, "mg/dm³");
        addIndicator(indicators, "Boro", n(analysis.boron()), 0.2, 0.6, "mg/dm³");
        addIndicator(indicators, "Cobre", n(analysis.copper()), 0.4, 1.2, "mg/dm³");
        addIndicator(indicators, "Manganês", n(analysis.manganese()), 5, 12, "mg/dm³");
        addIndicator(indicators, "Zinco", n(analysis.zinc()), 0.6, 1.5, "mg/dm³");
        addIndicator(indicators, "Condutividade elétrica", n(analysis.ec()), 0.5, 2, "dS/m");

        List<SoilReportResponse.InsightItem> insights = new ArrayList<>();
        if (n(analysis.phWater()) != 0.0 && n(analysis.phWater()) < 5.5) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Acidez ativa",
                    "pH abaixo da faixa geral pode limitar nutrientes e elevar o risco associado ao alumínio.",
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
        if (n(analysis.ec()) > 2) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Risco de salinidade",
                    "Condutividade acima de 2 dS/m merece avaliação específica da cultura e da água de irrigação.",
                    "danger"
            ));
        }
        if (insights.isEmpty()) {
            insights.add(new SoilReportResponse.InsightItem(
                    "Sem alerta crítico automático",
                    "Os principais equilíbrios calculados não ultrapassaram os limites gerais de triagem.",
                    "good"
            ));
        }

        List<SoilReportResponse.DerivedItem> derived = List.of(
                new SoilReportResponse.DerivedItem("Soma de bases (SB)", fmt(baseSum, "cmolc/dm³"), "Ca + Mg + K + Na"),
                new SoilReportResponse.DerivedItem("CTC efetiva (t)", fmt(effectiveCec, "cmolc/dm³"), "SB + Al"),
                new SoilReportResponse.DerivedItem("CTC pH 7 (T)", fmt(cecAtPh7, "cmolc/dm³"), "SB + H+Al"),
                new SoilReportResponse.DerivedItem("V%", fmt(baseSaturation, "%"), "100 × SB / T"),
                new SoilReportResponse.DerivedItem("m%", fmt(aluminumSaturation, "%"), "100 × Al / t"),
                new SoilReportResponse.DerivedItem("Ca/Mg", fmt(caMg, ""), "Ca ÷ Mg"),
                new SoilReportResponse.DerivedItem("Ca/K", fmt(caK, ""), "Ca ÷ K (cmolc)"),
                new SoilReportResponse.DerivedItem("Mg/K", fmt(mgK, ""), "Mg ÷ K (cmolc)")
        );

        return new SoilReportResponse(summary, List.copyOf(indicators), List.copyOf(insights), derived);
    }

    private void addIndicator(
            List<SoilReportResponse.IndicatorItem> indicators,
            String label,
            double value,
            double low,
            double high,
            String unit) {

        if (value > 0) {
            indicators.add(new SoilReportResponse.IndicatorItem(label, fmt(value, unit), classify(value, low, high)));
        }
    }

    private double n(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String classify(double value, double low, double high) {
        if (value < low) {
            return "Baixo";
        }
        if (value > high) {
            return "Alto";
        }
        return "Médio";
    }

    private String fmt(double value, String unit) {
        String number = Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "—";
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
