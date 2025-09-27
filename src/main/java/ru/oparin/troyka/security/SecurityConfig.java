package ru.oparin.troyka.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import ru.oparin.troyka.service.JwtService;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }

    @Bean
    @Profile("!dev") // PROD режим - с security
    public SecurityWebFilterChain securityWebFilterChainProd(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .addFilterAt(corsFilter(), SecurityWebFiltersOrder.CORS)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .pathMatchers("/health/**").permitAll()
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/files/upload").authenticated()
                        .pathMatchers("/files/**").permitAll()
                        .pathMatchers("/fal/**").authenticated()
                        .anyExchange().authenticated()
                )
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Profile("dev") // DEV режим - без security
    public SecurityWebFilterChain securityWebFilterChainDev(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .addFilterAt(corsFilter(), SecurityWebFiltersOrder.CORS)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/files/**").permitAll()
                        .anyExchange().permitAll())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }
}