package br.com.soloibiapaba.config;

import br.com.soloibiapaba.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final List<String> allowedOriginPatterns;

    public SecurityConfig(
            @Value("${app.cors.allowed-origin-patterns}") String allowedOriginPatterns) {
        this.allowedOriginPatterns = java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::strip).filter(origin -> !origin.isEmpty()).distinct().toList();
        if (this.allowedOriginPatterns.isEmpty()) throw new IllegalArgumentException("Configure pelo menos uma origem permitida para CORS.");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("ETag"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, tools.jackson.databind.ObjectMapper json) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request,response,error) -> writeAccessError(json, request, response, 401, "Sessão inválida ou expirada"))
                    .accessDeniedHandler((request,response,error) -> writeAccessError(json, request, response, 403, "Você não tem permissão para esta ação")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/health",
                                "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.DELETE,"/api/samples", "/api/samples/**", "/api/audit/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").denyAll()
                        .requestMatchers(HttpMethod.PUT,"/api/samples/{id}").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/interpretation-parameters", "/api/interpretation-parameters/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/interpretation-parameters", "/api/interpretation-parameters/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    private static void writeAccessError(tools.jackson.databind.ObjectMapper json,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
            int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new br.com.soloibiapaba.exception.ApiErrorResponse(
                java.time.Instant.now(), status, status == 401 ? "Unauthorized" : "Forbidden",
                message, request.getRequestURI(), java.util.Map.of()));
    }

}
