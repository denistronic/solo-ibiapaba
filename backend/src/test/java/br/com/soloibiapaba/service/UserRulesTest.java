package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.*;
import br.com.soloibiapaba.dto.*;
import br.com.soloibiapaba.exception.LastAdminException;
import br.com.soloibiapaba.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserRulesTest {
    private final UserRepository users = mock(UserRepository.class,withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    private final UserService service = new UserService(users,new BCryptPasswordEncoder());
    private final UUID id=UUID.randomUUID();
    private User existing(Role role, boolean active) {
        User user = new User("Usuário", "pessoa@example.test","hash",role);
        user.setActive(active);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(inv->inv.getArgument(0));
        when(users.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);
        return user;
    }
    @Test void shouldPreventDisablingLastActiveAdmin() {
        User admin=existing(Role.ADMIN,true);
        assertThatThrownBy(()->service.updateStatus(id,false)).isInstanceOf(LastAdminException.class);
        assertThat(admin.isActive()).isTrue();
        var ordered=inOrder(users);ordered.verify(users).findAllForUpdate();ordered.verify(users).findById(id);
    }
    @Test void shouldPreventDemotingLastActiveAdmin() {
        User admin=existing(Role.ADMIN,true);
        assertThatThrownBy(()->service.updateUser(id,new UpdateUserRequest("Novo", "novo@example.test",Role.USER)))
            .isInstanceOf(LastAdminException.class);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }
    @Test void shouldAllowEditingInactiveAdminWithoutBlockingTheActiveAdmin() {
        User inactive=existing(Role.ADMIN,false);
        service.updateUser(id,new UpdateUserRequest("Novo", "novo@example.test",Role.USER));
        assertThat(inactive.getRole()).isEqualTo(Role.USER);
        verify(users,never()).countByRoleAndActiveTrue(any());
    }
    @Test void shouldAllowRepeatedDeactivationOfInactiveAdmin() {
        existing(Role.ADMIN,false);
        assertThat(service.updateStatus(id,false).active()).isFalse();
        verify(users,never()).countByRoleAndActiveTrue(any());
    }
    @Test void shouldAllowDeactivatingOneOfTwoActiveAdmins() {
        existing(Role.ADMIN,true);when(users.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(2L);
        assertThat(service.updateStatus(id,false).active()).isFalse();
    }
}
