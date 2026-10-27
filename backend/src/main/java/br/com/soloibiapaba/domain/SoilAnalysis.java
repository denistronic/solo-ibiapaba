
package br.com.soloibiapaba.domain;

import java.math.BigDecimal;

public record SoilAnalysis(
        BigDecimal phWater,
        BigDecimal phCaCl2,
        BigDecimal organicMatter,
        BigDecimal organicCarbon,
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
        java.util.Map<String, String> units,
        BigDecimal density,
        String phosphorusMethod
) {
    public SoilAnalysis(BigDecimal phWater, BigDecimal phCaCl2, BigDecimal organicMatter, BigDecimal organicCarbon, BigDecimal phosphorus, BigDecimal potassium, BigDecimal sodium, BigDecimal calcium, BigDecimal magnesium, BigDecimal aluminum, BigDecimal hAl, BigDecimal sulfur, BigDecimal boron, BigDecimal copper, BigDecimal iron, BigDecimal manganese, BigDecimal zinc, BigDecimal cecReported, BigDecimal baseSatReported, BigDecimal clay, BigDecimal sand, BigDecimal silt) {
        this(phWater, phCaCl2, organicMatter, organicCarbon, phosphorus, potassium, sodium, calcium, magnesium, aluminum, hAl, sulfur, boron, copper, iron, manganese, zinc, cecReported, baseSatReported, clay, sand, silt, java.util.Map.of(), null, "Resina");
    }
}
