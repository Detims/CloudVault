package dev.cloudvault.security;

import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           @Value("${cloudvault.frontend-origin}") String frontendOrigin,
                                           @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookie));

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/health", "/api/csrf", "/actuator/health/**", "/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, error) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Sign in to continue")))
            .oauth2Login(login -> login.successHandler((request, response, authentication) ->
                response.sendRedirect(frontendOrigin)))
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

    /** Keeps BREACH protection for rendered request attributes while accepting the raw SPA cookie token in a header. */
    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           Supplier<CsrfToken> deferredToken) {
            xor.handle(request, response, deferredToken);
            deferredToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            var handler = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName())) ? plain : xor;
            return handler.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
