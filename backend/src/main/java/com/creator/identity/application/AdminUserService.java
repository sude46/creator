package com.creator.identity.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountStatus;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.PasswordPolicy;
import com.creator.identity.domain.Role;
import com.creator.identity.domain.UsernamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminUserService {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final TransactionTemplate transaction;

    public AdminUserService(AccountRepository accounts, PasswordEncoder encoder, AuditLog audit,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResult<AccountSummary> list(Actor actor, PageRequest page) {
        requireAdmin(actor);
        return new PageResult<>(accounts.findPage(page), accounts.count(), page.page(), page.size());
    }

    public AccountSummary create(Actor actor, String username, String password) {
        requireAdmin(actor);
        String normalized = UsernamePolicy.normalize(username);
        PasswordPolicy.validate(password);
        String hash = encoder.encode(password);
        return transaction.execute(status -> {
            var account = accounts.insert(normalized, hash, Role.USER);
            audit.record(actor, "USER_CREATED", "USER", account.id());
            return account;
        });
    }

    @Transactional(timeout = 5)
    public AccountSummary setEnabled(Actor actor, long id, boolean enabled) {
        requireAdmin(actor);
        if (actor.id() == id) throw new BusinessException(ErrorCode.FORBIDDEN);
        var target = accounts.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (target.role() != Role.USER || target.status() == AccountStatus.DELETED) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        var next = enabled ? AccountStatus.ACTIVE : AccountStatus.DISABLED;
        if (!accounts.updateUserStatus(id, next)) throw new BusinessException(ErrorCode.STALE_WRITE);
        audit.record(actor, enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", id);
        return accounts.findById(id).orElseThrow();
    }

    private void requireAdmin(Actor actor) {
        if (actor == null || actor.role() != Role.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
