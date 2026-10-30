package br.com.soloibiapaba.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @jakarta.validation.constraints.Size(max = 180, message = "E-mail deve ter no máximo 180 caracteres")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @br.com.soloibiapaba.validation.Utf8Length
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
