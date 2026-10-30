package br.com.soloibiapaba.validation;

import br.com.soloibiapaba.dto.*;
import br.com.soloibiapaba.domain.Role;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InputValidationTest {
    @Test void shouldValidateBCryptLimitInUtf8Bytes() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertThat(validator.validate(new CreateUserRequest("Pessoa","p@example.test","a".repeat(72),Role.USER))).isEmpty();
            assertThat(validator.validate(new CreateUserRequest("Pessoa","p@example.test","é".repeat(36),Role.USER))).isEmpty();
            assertThat(validator.validate(new CreateUserRequest("Pessoa","p@example.test","é".repeat(37),Role.USER))).isNotEmpty();
            assertThat(validator.validate(new CreateUserRequest("Pessoa","p@example.test","a".repeat(73),Role.USER))).isNotEmpty();
        }
    }
    @Test void shouldNormalizeIdentityFieldsWithoutChangingPassword() {
        var request=new CreateUserRequest("  Pessoa  ","  PESSOA@EXAMPLE.TEST  ","  Senha123  ",Role.USER);
        assertThat(request.name()).isEqualTo("Pessoa");assertThat(request.email()).isEqualTo("pessoa@example.test");
        assertThat(request.password()).isEqualTo("  Senha123  ");
        assertThat(new LoginRequest(" PESSOA@EXAMPLE.TEST ","senha").email()).isEqualTo("pessoa@example.test");
    }
    @Test void shouldRequireAnExplicitStatusIncludingFalseAsValid() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new UpdateStatusRequest(null))).isNotEmpty();
            assertThat(factory.getValidator().validate(new UpdateStatusRequest(false))).isEmpty();
        }
    }
}
