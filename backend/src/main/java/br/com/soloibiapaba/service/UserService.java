package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.Role;
import br.com.soloibiapaba.domain.User;
import br.com.soloibiapaba.dto.CreateUserRequest;
import br.com.soloibiapaba.dto.UpdateUserRequest;
import br.com.soloibiapaba.dto.UserResponse;
import br.com.soloibiapaba.exception.LastAdminException;
import br.com.soloibiapaba.exception.UserNotFoundException;
import br.com.soloibiapaba.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public UserResponse createUser(CreateUserRequest request) {
        User user = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role());

        return toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);

        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN) {
            ensureNotLastAdmin();
        }

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());

        return toResponse(userRepository.save(user));
    }

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

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.isActive());
    }
}