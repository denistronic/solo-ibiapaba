package br.com.soloibiapaba.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSampleRequest(
        @NotBlank(message = "Bloco é obrigatório")
        @Size(max = 80, message = "Bloco deve ter no máximo 80 caracteres")
        String block,

        @NotBlank(message = "Quadra é obrigatória")
        @Size(max = 80, message = "Quadra deve ter no máximo 80 caracteres")
        String plot,

        @NotBlank(message = "Linha é obrigatória")
        @Size(max = 80, message = "Linha deve ter no máximo 80 caracteres")
        String row,

        @NotBlank(message = "Cultura é obrigatória")
        @Size(max = 120, message = "Cultura deve ter no máximo 120 caracteres")
        String crop,

        @NotNull(message = "Data da coleta é obrigatória")
        LocalDate sampledAt,

        @NotNull(message = "Temperatura é obrigatória")
        @DecimalMin(value = "-20.0", message = "Temperatura abaixo do limite aceito")
        @DecimalMax(value = "80.0", message = "Temperatura acima do limite aceito")
        BigDecimal temperature,

        @NotBlank(message = "Profundidade é obrigatória")
        @Size(max = 40, message = "Profundidade deve ter no máximo 40 caracteres")
        String depth
) {
}
