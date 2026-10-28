package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.SoilAnalysis;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Set;

/** Converte uma cópia para as unidades de cálculo; o laudo original é preservado. */
public final class SoilUnits {
    private SoilUnits() {}

    public static SoilAnalysis normalize(SoilAnalysis a) {
        if (a.units() != null) {
            Set<String> supported = Set.of("calcium", "magnesium", "potassium", "sodium", "aluminum", "hAl", "cecReported", "organicMatter", "organicCarbon", "phosphorus", "sulfur", "boron", "copper", "iron", "manganese", "zinc", "clay", "sand", "silt");
            if (!supported.containsAll(a.units().keySet())) {
                throw new IllegalArgumentException("Parâmetro de unidade desconhecido.");
            }
        }
        if (a.phosphorusMethod() != null && !Set.of("Resina", "Mehlich 1", "Não informado").contains(a.phosphorusMethod())) {
            throw new IllegalArgumentException("Método de fósforo inválido.");
        }
        return new SoilAnalysis(
            a.phWater(),
            a.phCaCl2(),
            convert("organicMatter", a.organicMatter(), a, "mass"),
            convert("organicCarbon", a.organicCarbon(), a, "mass"),
            convert("phosphorus", a.phosphorus(), a, "mass"),
            convert("potassium", a.potassium(), a, "charge"),
            convert("sodium", a.sodium(), a, "charge"),
            convert("calcium", a.calcium(), a, "charge"),
            convert("magnesium", a.magnesium(), a, "charge"),
            convert("aluminum", a.aluminum(), a, "charge"),
            convert("hAl", a.hAl(), a, "charge"),
            convert("sulfur", a.sulfur(), a, "mass"),
            convert("boron", a.boron(), a, "mass"),
            convert("copper", a.copper(), a, "mass"),
            convert("iron", a.iron(), a, "mass"),
            convert("manganese", a.manganese(), a, "mass"),
            convert("zinc", a.zinc(), a, "mass"),
            convert("cecReported", a.cecReported(), a, "charge"),
            a.baseSatReported(),
            convert("clay", a.clay(), a, "texture"),
            convert("sand", a.sand(), a, "texture"),
            convert("silt", a.silt(), a, "texture"),
            Map.of(), null, a.phosphorusMethod());
    }

    private static BigDecimal convert(String field, BigDecimal value, SoilAnalysis a, String group) {
        String fallback = switch (group) {
            case "charge" -> "mmolc/dm³";
            case "texture" -> "g/kg";
            default -> "g/dm³";
        };
        String unit = a.units() == null ? fallback : a.units().getOrDefault(field, fallback);
        if (unit == null) throw new IllegalArgumentException("Unidade não informada para " + field);
        BigDecimal factor;
        if (group.equals("charge")) {
            factor = switch (unit) {
                case "mmolc/dm³" -> BigDecimal.ONE;
                case "cmolc/dm³" -> BigDecimal.TEN;
                default -> throw new IllegalArgumentException("Unidade inválida para " + field);
            };
        } else if (group.equals("texture")) {
            factor = switch (unit) {
                case "g/kg" -> BigDecimal.ONE;
                case "%" -> BigDecimal.TEN;
                default -> throw new IllegalArgumentException("Unidade inválida para " + field);
            };
        } else {
            factor = switch (unit) {
                case "g/dm³", "g/kg" -> BigDecimal.ONE;
                case "mg/dm³", "mg/kg" -> new BigDecimal("0.001");
                default -> throw new IllegalArgumentException("Unidade inválida para " + field);
            };
            if (value != null && unit.endsWith("/kg")) {
                if (a.density() == null || a.density().signum() <= 0) {
                    throw new IllegalArgumentException("Informe a densidade de conversão (kg/dm³) para resultados em " + unit + ".");
                }
                factor = factor.multiply(a.density());
            }
        }
        if (value == null) return null;
        BigDecimal result = value.multiply(factor, MathContext.DECIMAL128);
        if (result.signum() < 0 || (group.equals("texture") && result.compareTo(new BigDecimal("1000")) > 0)) {
            throw new IllegalArgumentException("Resultado fora do intervalo permitido para " + field);
        }
        return result;
    }
}
