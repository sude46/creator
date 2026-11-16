package com.creator.identity.application;

import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.PasswordPolicy;
import com.creator.identity.domain.Role;
import com.creator.identity.domain.UsernamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterUser {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuthenticationWorkBudget budget;

    public RegisterUser(AccountRepository accounts, PasswordEncoder encoder, AuthenticationWorkBudget budget) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.budget = budget;
    }

    public AccountSummary execute(String username, String password) {
        String normalized = UsernamePolicy.normalize(username);
        PasswordPolicy.validate(password);
        String hash = budget.execute(() -> encoder.encode(password));
        return accounts.insert(normalized, hash, Role.USER);
    }
}
