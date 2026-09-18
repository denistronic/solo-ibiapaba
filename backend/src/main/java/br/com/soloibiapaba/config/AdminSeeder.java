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
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.name}") String adminName,
                       @Value("${app.admin.email}") String adminEmail,
                       @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.countByRoleAndActiveTrue(Role.ADMIN) > 0) {
            return;
        }

        User admin = new User(adminName, adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN);
        userRepository.save(admin);

        System.out.println("Admin inicial criado: " + adminEmail);
    }
}