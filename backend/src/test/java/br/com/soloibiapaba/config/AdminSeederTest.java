package br.com.soloibiapaba.config;

import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.repository.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSeederTest {
    private final UserRepository users=mock(UserRepository.class);
    @Test void shouldNormalizeEmailBeforeFindingExistingSeedAccount() {
        User original=new User("Pessoa","pessoa@example.test","old",Role.USER);
        when(users.findByEmailIgnoreCase("pessoa@example.test")).thenReturn(Optional.of(original));
        try(var f=Validation.buildDefaultValidatorFactory()) {
            new AdminSeeder(users,new BCryptPasswordEncoder(),f.getValidator()," Admin "," PESSOA@EXAMPLE.TEST ","SenhaTeste123").run();
        }
        verify(users).save(original);
        assertThat(original.getEmail()).isEqualTo("pessoa@example.test");
        assertThat(original.getRole()).isEqualTo(Role.ADMIN);
        assertThat(new BCryptPasswordEncoder().matches("SenhaTeste123",original.getPasswordHash())).isTrue();
    }
    @Test void shouldRejectInvalidSeedPasswordBeforeChangingAnyAccount() {
        try(var f=Validation.buildDefaultValidatorFactory()) {
            var seed=new AdminSeeder(users,new BCryptPasswordEncoder(),f.getValidator(),"Admin","p@example.test","é".repeat(37));
            assertThatThrownBy(()->seed.run()).isInstanceOf(IllegalStateException.class).hasMessageContaining("password");
        }
        verify(users,never()).save(any());
        verify(users,never()).findByEmailIgnoreCase(anyString());
    }
    @Test void shouldNeverResetAnExistingAdministratorFromEnvironment() {
        when(users.countByRole(Role.ADMIN)).thenReturn(1L);
        try(var f=Validation.buildDefaultValidatorFactory()) {
            new AdminSeeder(users,new BCryptPasswordEncoder(),f.getValidator(),"Admin","p@example.test","short").run();
        }
        verify(users,never()).save(any());
    }
}
