package com.creator.identity.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountStatus;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.PasswordPolicy;
import com.creator.identity.domain.Role;
import com.creator.identity.domain.UsernamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdmin {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;

    public BootstrapAdmin(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
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
        return accounts.insert(normalized, encoder.encode(password), Role.ADMIN);
    }
}
