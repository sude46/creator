package com.creator.identity.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountStatus;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.AdminScope;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AccountQueryService {
    private final AccountRepository accounts;

    public AccountQueryService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<Actor> findActive(String username) {
        return accounts.findSummary(username)
                .filter(account -> account.status() == AccountStatus.ACTIVE)
                .map(account -> new Actor(account.id(), account.username(), account.role(), account.adminScope()));
    }

    public AdminScope adminScope(long id) {
        return accounts.findById(id).map(account -> account.adminScope()).orElse(null);
    }

    public Actor current(String username) {
        return findActive(username).orElseThrow(() -> new BusinessException(ErrorCode.SESSION_INVALID));
    }
}
