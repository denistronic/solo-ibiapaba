package br.com.soloibiapaba.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "Informe se o usuário deve estar ativo") Boolean active
) {}
