package br.com.soloibiapaba.dto;

import java.util.List;

public record SoilReportResponse(
        List<SummaryItem> summary,
        List<IndicatorItem> indicators,
        List<InsightItem> insights,
        List<DerivedItem> derived
) {
    public record SummaryItem(String label, String value, String level) {}

    public record IndicatorItem(String label, String formatted, String level) {}

    public record InsightItem(String title, String text, String tone) {}

    public record DerivedItem(String label, String value, String formula) {}
}
