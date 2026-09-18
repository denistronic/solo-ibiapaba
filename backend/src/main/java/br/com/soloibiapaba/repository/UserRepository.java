package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.Role;
import br.com.soloibiapaba.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    long countByRoleAndActiveTrue(Role role);
}