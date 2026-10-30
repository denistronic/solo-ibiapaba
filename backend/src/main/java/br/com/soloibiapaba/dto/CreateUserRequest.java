package br.com.soloibiapaba.dto;

import br.com.soloibiapaba.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String name,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 180, message = "E-mail deve ter no máximo 180 caracteres")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        @br.com.soloibiapaba.validation.Utf8Length
        String password,

        @NotNull(message = "Perfil é obrigatório") Role role
) {
    public CreateUserRequest {
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
        name = name == null ? null : name.strip();
    }
}
