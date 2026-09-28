package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.Role;
import br.com.soloibiapaba.domain.User;
import br.com.soloibiapaba.dto.CreateUserRequest;
import br.com.soloibiapaba.dto.UpdateUserRequest;
import br.com.soloibiapaba.dto.UserResponse;
import br.com.soloibiapaba.exception.DuplicateEmailException;
import br.com.soloibiapaba.exception.LastAdminException;
import br.com.soloibiapaba.exception.UserNotFoundException;
import br.com.soloibiapaba.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse getUser(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("Já existe um usuário com este e-mail");
        }

        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                request.role());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateEmailException("Já existe um usuário com este e-mail");
        }

        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN) {
            ensureNotLastAdmin();
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setRole(request.role());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, boolean active) {
        User user = findOrThrow(id);

        if (!active && user.getRole() == Role.ADMIN) {
            ensureNotLastAdmin();
        }

        user.setActive(active);

        return toResponse(userRepository.save(user));
    }

    private void ensureNotLastAdmin() {
        if (userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new LastAdminException("Não é possível remover o último administrador ativo");
        }
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.isActive());
    }
}
