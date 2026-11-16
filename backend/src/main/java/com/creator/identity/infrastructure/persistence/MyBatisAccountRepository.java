package com.creator.identity.infrastructure.persistence;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountCredentials;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountStatus;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Role;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAccountRepository implements AccountRepository {
    private final UserAccountMapper mapper;

    public MyBatisAccountRepository(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AccountCredentials> findCredentials(String username) {
        return Optional.ofNullable(mapper.findCredentials(username)).map(row -> new AccountCredentials(
                new AccountSummary(row.getId(), row.getUsername(), Role.valueOf(row.getRole()),
                        AccountStatus.valueOf(row.getStatus())), row.getPasswordHash()));
    }

    @Override
    public Optional<AccountSummary> findSummary(String username) {
        return Optional.ofNullable(mapper.findSummary(username)).map(row -> new AccountSummary(
                row.id(), row.username(), Role.valueOf(row.role()), AccountStatus.valueOf(row.status())));
    }

    @Override
    public AccountSummary insert(String username, String passwordHash, Role role) {
        var row = new UserAccountRow();
        row.setUsername(username);
        row.setPasswordHash(passwordHash);
        row.setRole(role.name());
        row.setStatus(AccountStatus.ACTIVE.name());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }
        return new AccountSummary(row.getId(), username, role, AccountStatus.ACTIVE);
    }
}
