package br.com.soloibiapaba.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record SoilAnalysisRequest(
        @DecimalMin(value = "0.0", message = "pH em água não pode ser negativo")
        @DecimalMax(value = "14.0", message = "pH em água não pode ser maior que 14")
        BigDecimal phWater,

        @DecimalMin(value = "0.0", message = "pH CaCl2 não pode ser negativo")
        @DecimalMax(value = "14.0", message = "pH CaCl2 não pode ser maior que 14")
        BigDecimal phCaCl2,

        @PositiveOrZero(message = "Matéria orgânica não pode ser negativa") BigDecimal organicMatter,
        @PositiveOrZero(message = "Fósforo não pode ser negativo") BigDecimal phosphorus,
        @PositiveOrZero(message = "Potássio não pode ser negativo") BigDecimal potassium,
        @PositiveOrZero(message = "Sódio não pode ser negativo") BigDecimal sodium,
        @PositiveOrZero(message = "Cálcio não pode ser negativo") BigDecimal calcium,
        @PositiveOrZero(message = "Magnésio não pode ser negativo") BigDecimal magnesium,
        @PositiveOrZero(message = "Alumínio não pode ser negativo") BigDecimal aluminum,
        @PositiveOrZero(message = "H+Al não pode ser negativo") BigDecimal hAl,
        @PositiveOrZero(message = "Enxofre não pode ser negativo") BigDecimal sulfur,
        @PositiveOrZero(message = "Boro não pode ser negativo") BigDecimal boron,
        @PositiveOrZero(message = "Cobre não pode ser negativo") BigDecimal copper,
        @PositiveOrZero(message = "Ferro não pode ser negativo") BigDecimal iron,
        @PositiveOrZero(message = "Manganês não pode ser negativo") BigDecimal manganese,
        @PositiveOrZero(message = "Zinco não pode ser negativo") BigDecimal zinc,
        @PositiveOrZero(message = "CTC informada não pode ser negativa") BigDecimal cecReported,

        @DecimalMin(value = "0.0", message = "V informado não pode ser negativo")
        @DecimalMax(value = "100.0", message = "V informado não pode ser maior que 100")
        BigDecimal baseSatReported,

        @DecimalMin(value = "0.0", message = "Argila não pode ser negativa")
        @DecimalMax(value = "100.0", message = "Argila não pode ser maior que 100")
        BigDecimal clay,

        @DecimalMin(value = "0.0", message = "Areia não pode ser negativa")
        @DecimalMax(value = "100.0", message = "Areia não pode ser maior que 100")
        BigDecimal sand,

        @DecimalMin(value = "0.0", message = "Silte não pode ser negativo")
        @DecimalMax(value = "100.0", message = "Silte não pode ser maior que 100")
        BigDecimal silt,

        @PositiveOrZero(message = "Condutividade elétrica não pode ser negativa") BigDecimal ec
) {
}
