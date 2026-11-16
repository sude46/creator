package com.creator.bootstrap;

import com.creator.identity.application.BootstrapAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final BootstrapAdmin bootstrapAdmin;
    private final String username;
    private final String password;

    public AdminBootstrap(BootstrapAdmin bootstrapAdmin,
            @Value("${creator.bootstrap.admin-username:}") String username,
            @Value("${creator.bootstrap.admin-password:}") String password) {
        this.bootstrapAdmin = bootstrapAdmin;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        bootstrapAdmin.execute(username, password);
    }
}
