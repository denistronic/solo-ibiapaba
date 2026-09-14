package br.com.soloibiapaba.domain;

import java.math.BigDecimal;

public record SoilAnalysis(
        BigDecimal phWater,
        BigDecimal phCaCl2,
        BigDecimal organicMatter,
        BigDecimal phosphorus,
        BigDecimal potassium,
        BigDecimal sodium,
        BigDecimal calcium,
        BigDecimal magnesium,
        BigDecimal aluminum,
        BigDecimal hAl,
        BigDecimal sulfur,
        BigDecimal boron,
        BigDecimal copper,
        BigDecimal iron,
        BigDecimal manganese,
        BigDecimal zinc,
        BigDecimal cecReported,
        BigDecimal baseSatReported,
        BigDecimal clay,
        BigDecimal sand,
        BigDecimal silt,
        BigDecimal ec
) {
}
