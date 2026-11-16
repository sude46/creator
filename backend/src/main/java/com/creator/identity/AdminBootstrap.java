package com.creator.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountMapper accounts;
    private final IdentityService identity;
    private final String username;
    private final String password;

    public AdminBootstrap(UserAccountMapper accounts, IdentityService identity,
            @Value("${creator.bootstrap.admin-username:}") String username,
            @Value("${creator.bootstrap.admin-password:}") String password) {
        this.accounts = accounts;
        this.identity = identity;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (!username.matches("[a-zA-Z0-9_]{3,32}")) {
            throw new IllegalStateException("ADMIN_USERNAME must contain 3-32 letters, digits or underscores");
        }
        identity.validatePassword(password);
        var existing = accounts.findByUsername(username);
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) {
                throw new IllegalStateException("Bootstrap username is already used by a non-admin account");
            }
            return;
        }
        identity.create(username, password, "ADMIN");
    }
}
