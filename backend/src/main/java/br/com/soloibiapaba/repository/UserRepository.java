package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.Role;
import br.com.soloibiapaba.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Serializa alterações de perfil/status para preservar um administrador ativo.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u order by u.id")
    java.util.List<User> findAllForUpdate();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(u.email)=lower(:email)")
    Optional<User> findForAuditByEmail(@org.springframework.data.repository.query.Param("email") String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    long countByRoleAndActiveTrue(Role role);

    long countByRole(Role role);
}
