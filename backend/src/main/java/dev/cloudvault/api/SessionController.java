package dev.cloudvault.api;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    @GetMapping("/api/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/api/me")
    UserProfile me(@AuthenticationPrincipal OidcUser user) {
        return new UserProfile(user.getSubject(), user.getFullName(), user.getEmail(), user.getPicture());
    }

    record UserProfile(String id, String name, String email, String picture) {}
}
