package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.User;
import br.com.soloibiapaba.dto.LoginRequest;
import br.com.soloibiapaba.dto.LoginResponse;
import br.com.soloibiapaba.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

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
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, user.getName(), user.getEmail(), user.getRole().name());
    }
}