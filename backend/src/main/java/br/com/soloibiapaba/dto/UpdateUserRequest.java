package br.com.soloibiapaba.dto;

import br.com.soloibiapaba.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull Role role
) {}