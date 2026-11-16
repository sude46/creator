package com.creator.identity;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final IdentityService identity;
    private final UserAccountMapper accounts;

    public AuthController(IdentityService identity, UserAccountMapper accounts) {
        this.identity = identity;
        this.accounts = accounts;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return identity.register(request);
    }

    @GetMapping("/api/me")
    public UserView me(Principal principal) {
        return UserView.from(accounts.findByUsername(principal.getName()));
    }

    public record CsrfView(String headerName, String token) {
    }
}
