package br.com.soloibiapaba.config;

import br.com.soloibiapaba.domain.Role;
import br.com.soloibiapaba.domain.User;
import br.com.soloibiapaba.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final jakarta.validation.Validator validator;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       jakarta.validation.Validator validator,
                       @Value("${app.admin.name}") String adminName,
                       @Value("${app.admin.email}") String adminEmail,
                       @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        var request = new br.com.soloibiapaba.dto.CreateUserRequest(adminName, adminEmail, adminPassword, Role.ADMIN);
        var errors = validator.validate(request);
        if (!errors.isEmpty()) {
            String fields = errors.stream().map(error -> error.getPropertyPath() + ": " + error.getMessage())
                    .sorted().collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalStateException("Configuração do administrador inicial inválida: " + fields);
        }
        String hash = passwordEncoder.encode(request.password());
        User admin = userRepository.findByEmailIgnoreCase(request.email())
                .orElseGet(() -> new User(request.name(), request.email(), hash, Role.ADMIN));
        admin.setName(request.name());
        admin.setEmail(request.email());
        admin.setPasswordHash(hash);
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        System.out.println("Admin inicial criado: " + request.email());
    }
}
