package dev.cloudvault.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/health", "/api/csrf", "/actuator/health/**", "/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, error) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Sign in to continue")))
            .oauth2Login(Customizer.withDefaults())
            .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) ->
                response.setStatus(HttpStatus.NO_CONTENT.value())));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${cloudvault.frontend-origin}") String frontendOrigin) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(frontendOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
