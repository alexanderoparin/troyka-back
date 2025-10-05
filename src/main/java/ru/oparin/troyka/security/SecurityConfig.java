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
import org.springframework.web.cors.reactive.CorsConfigurationSource;
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
        config.addAllowedOrigin("http://213.171.4.47");
        config.addAllowedOrigin("http://213.171.4.47:3000");
        config.addAllowedOrigin("http://24reshai.ru");
        config.addAllowedOrigin("https://24reshai.ru");
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterAt(corsFilter(), SecurityWebFiltersOrder.CORS)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .pathMatchers("/health/**").permitAll()
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/pricing/**").permitAll()
                        .pathMatchers("/contact/**").permitAll()
                        .pathMatchers("/files/upload").authenticated()
                        .pathMatchers("/files/**").permitAll()
                        .pathMatchers("/fal/**").authenticated()
                        .pathMatchers("/users/**").authenticated()
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
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://213.171.4.47");
        configuration.addAllowedOrigin("http://213.171.4.47:3000");
        configuration.addAllowedOrigin("http://localhost:3000");
        configuration.addAllowedOrigin("http://24reshai.ru");
        configuration.addAllowedOrigin("https://24reshai.ru");
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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