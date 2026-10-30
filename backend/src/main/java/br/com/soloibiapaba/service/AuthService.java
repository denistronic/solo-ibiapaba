package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.User;
import br.com.soloibiapaba.dto.LoginRequest;
import br.com.soloibiapaba.dto.LoginResponse;
import br.com.soloibiapaba.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, user.getName(), user.getEmail(), user.getRole().name());
    }
}
