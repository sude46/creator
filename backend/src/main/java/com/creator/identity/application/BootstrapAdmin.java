package com.creator.identity.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AdminScope;
import com.creator.identity.domain.AccountStatus;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.PasswordPolicy;
import com.creator.identity.domain.Role;
import com.creator.identity.domain.UsernamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class BootstrapAdmin {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuthenticationWorkBudget budget;

    public BootstrapAdmin(AccountRepository accounts, PasswordEncoder encoder, AuthenticationWorkBudget budget) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.budget = budget;
    }

    public AccountSummary execute(String username, String password) {
        String normalized = UsernamePolicy.normalize(username);
        PasswordPolicy.validate(password);
        var existing = accounts.findSummary(normalized);
        if (existing.isPresent()) {
            var account = existing.get();
            if (account.role() != Role.ADMIN || account.status() != AccountStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.ADMIN_BOOTSTRAP_CONFLICT);
            }
            return account;
        }
        String hash = budget.execute(() -> encoder.encode(password));
        return accounts.insert(normalized, hash, Role.ADMIN, AdminScope.SUPER_ADMIN);
    }
}
