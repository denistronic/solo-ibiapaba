package br.com.soloibiapaba.controller;

import br.com.soloibiapaba.domain.User;
import br.com.soloibiapaba.dto.LoginRequest;
import br.com.soloibiapaba.dto.LoginResponse;
import br.com.soloibiapaba.dto.UserResponse;
import br.com.soloibiapaba.repository.UserRepository;
import br.com.soloibiapaba.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.isActive());
    }
}