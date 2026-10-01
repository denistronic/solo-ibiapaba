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

    /**
     * Produz os indicadores e cálculos derivados do laudo.
     *
     * Unidades esperadas na análise:
     * - Ca, Mg, K, Na, Al, H+Al e CTC: mmolc/dm³;
     * - matéria orgânica, carbono orgânico e nutrientes: g/dm³;
     * - argila, silte e areia: g/kg.
     *
     * Observação: as faixas convertidas abaixo preservam matematicamente as
     * faixas que já existiam no MVP. Elas devem ser validadas pelo responsável
     * técnico para a cultura, região e método do laboratório antes de serem
     * usadas como recomendação agronômica. Fósforo por resina, carbono orgânico
     * total e ferro são exibidos como informativos, sem classificação automática.
     */
    public SoilReportResponse analyze(SoilAnalysis analysis) {
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

        List<SoilReportResponse.SummaryItem> summary = List.of(
                new SoilReportResponse.SummaryItem(
                        "pH em água (calculado)",
                        phWater > 0.0 ? oneDecimal(phWater) : "—",
                        phWater > 0.0 ? classify(phWater, 5.5, 6.5) : "Sem dado"
                ),
                new SoilReportResponse.SummaryItem(
                        "V%",
                        fmt(baseSaturation, "%"),
                        classify(baseSaturation, 40, 70)
                ),
                new SoilReportResponse.SummaryItem(
                        "m%",
                        fmt(aluminumSaturation, "%"),
                        aluminumSaturation < 10
                                ? "Baixo"
                                : aluminumSaturation <= 30 ? "Médio" : "Alto"
                ),
                new SoilReportResponse.SummaryItem(
                        "CTC pH 7",
                        fmt(cecAtPh7, "mmolc/dm³"),
                        classify(cecAtPh7, 43, 86)
                )
        );

        List<SoilReportResponse.IndicatorItem> indicators = new ArrayList<>();

        // Indicadores com faixas antigas convertidas para as unidades novas.
        addIndicator(indicators, "pH em água (calculado)", phWater, 5.5, 6.5, "");
        addIndicator(indicators, "Matéria orgânica", n(analysis.organicMatter()), 15, 30, "g/dm³");

        // O fósforo passou de Mehlich-1 para resina: não reutilizar as faixas antigas.
        addInformativeIndicator(indicators, "Carbono orgânico total", analysis.organicCarbon(), "g/dm³");
        addInformativeIndicator(indicators, "Fósforo (P) — Resina", analysis.phosphorus(), "g/dm³");

        // Conversões dos cátions para mmolc/dm³.
        // Ca: 1,5–4 cmolc/dm³ -> 15–40 mmolc/dm³.
        // Mg: 0,5–1,5 cmolc/dm³ -> 5–15 mmolc/dm³.
        // Al: 0,2–0,5 cmolc/dm³ -> 2–5 mmolc/dm³.
        // K: 45–120 mg/dm³ -> aproximadamente 1,15–3,07 mmolc/dm³.
        addIndicator(indicators, "Potássio (K)", potassium, 1.15, 3.07, "mmolc/dm³");
        addInformativeIndicator(indicators, "Sódio (Na)", analysis.sodium(), "mmolc/dm³");
        addIndicator(indicators, "Cálcio (Ca)", calcium, 15, 40, "mmolc/dm³");
        addIndicator(indicators, "Magnésio (Mg)", magnesium, 5, 15, "mmolc/dm³");
        addIndicator(indicators, "Alumínio total (Al)", aluminum, 2, 5, "mmolc/dm³");
        addInformativeIndicator(indicators, "Acidez total (H+Al)", analysis.hAl(), "mmolc/dm³");
        addInformativeIndicator(indicators, "CTC informada pelo laboratório", analysis.cecReported(), "mmolc/dm³");
        addInformativeIndicator(indicators, "V informado pelo laboratório", analysis.baseSatReported(), "%");

        addIndicator(indicators, "Saturação por bases", baseSaturation, 40, 70, "%");
        addIndicator(indicators, "Saturação por alumínio", aluminumSaturation, 10, 30, "%");

        // Conversão das faixas de mg/dm³ para g/dm³ (divisão por 1.000).
        // Confirmar essas faixas com o responsável técnico antes do uso operacional.
        addIndicator(indicators, "Enxofre (S)", n(analysis.sulfur()), 0.005, 0.010, "g/dm³");
        addIndicator(indicators, "Boro (B)", n(analysis.boron()), 0.0002, 0.0006, "g/dm³");
        addIndicator(indicators, "Cobre (Cu)", n(analysis.copper()), 0.0004, 0.0012, "g/dm³");
        addInformativeIndicator(indicators, "Ferro (Fe)", analysis.iron(), "g/dm³");
        addIndicator(indicators, "Manganês (Mn)", n(analysis.manganese()), 0.005, 0.012, "g/dm³");
        addIndicator(indicators, "Zinco (Zn)", n(analysis.zinc()), 0.0006, 0.0015, "g/dm³");

        // Textura do solo é registrada em g/kg, sem classificação automática.
        addInformativeIndicator(indicators, "Argila", analysis.clay(), "g/kg");
        addInformativeIndicator(indicators, "Silte", analysis.silt(), "g/kg");
        addInformativeIndicator(indicators, "Areia total", analysis.sand(), "g/kg");

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

    private void addIndicator(
            List<SoilReportResponse.IndicatorItem> indicators,
            String label,
            double value,
            double low,
            double high,
            String unit) {

        if (value > 0.0) {
            indicators.add(new SoilReportResponse.IndicatorItem(
                    label,
                    fmt(value, unit),
                    classify(value, low, high)
            ));
        }
    }

    private void addInformativeIndicator(
            List<SoilReportResponse.IndicatorItem> indicators,
            String label,
            BigDecimal value,
            String unit) {

        if (value != null) {
            indicators.add(new SoilReportResponse.IndicatorItem(
                    label,
                    fmt(value.doubleValue(), unit),
                    "Informativo"
            ));
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
