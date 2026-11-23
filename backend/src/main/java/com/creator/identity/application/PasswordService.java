package com.creator.identity.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordService {
    private final AccountRepository accounts;
    private final AccountQueryService queries;
    private final PasswordEncoder encoder;
    private final AuthenticationWorkBudget budget;
    private final AuditLog audit;
    private final TransactionTemplate transaction;

    public PasswordService(AccountRepository accounts, AccountQueryService queries, PasswordEncoder encoder,
            AuthenticationWorkBudget budget, AuditLog audit, PlatformTransactionManager transactionManager) {
        this.accounts = accounts; this.queries = queries; this.encoder = encoder;
        this.budget = budget; this.audit = audit; this.transaction = new TransactionTemplate(transactionManager);
    }

    public void change(Actor actor, String currentPassword, String newPassword) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!queries.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        var credentials = accounts.findCredentials(actor.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_INVALID));
        if (currentPassword == null || !encoder.matches(currentPassword, credentials.passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if (currentPassword.equals(newPassword)) throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
        PasswordPolicy.validate(newPassword);
        String hash = budget.execute(() -> encoder.encode(newPassword));
        transaction.execute(status -> {
            if (!accounts.updatePassword(actor.id(), hash)) throw new BusinessException(ErrorCode.STALE_WRITE);
            audit.record(actor, "PASSWORD_CHANGED", "USER", actor.id());
            return null;
        });
    }

    public void reset(Actor actor, long id, String newPassword) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!actor.superAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!queries.current(actor.username()).equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        if (id <= 0 || accounts.findById(id).isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        PasswordPolicy.validate(newPassword);
        String hash = budget.execute(() -> encoder.encode(newPassword));
        transaction.execute(status -> {
            if (!accounts.updatePassword(id, hash)) throw new BusinessException(ErrorCode.STALE_WRITE);
            audit.record(actor, "PASSWORD_RESET", "USER", id);
            return null;
        });
    }
}
