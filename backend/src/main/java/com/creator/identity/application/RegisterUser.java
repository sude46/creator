package com.creator.identity.application;

import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.PasswordPolicy;
import com.creator.identity.domain.Role;
import com.creator.identity.domain.UsernamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUser {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;

    public RegisterUser(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
    public AccountSummary execute(String username, String password) {
        String normalized = UsernamePolicy.normalize(username);
        PasswordPolicy.validate(password);
        return accounts.insert(normalized, encoder.encode(password), Role.USER);
    }
}
