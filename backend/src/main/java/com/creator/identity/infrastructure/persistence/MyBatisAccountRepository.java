package com.creator.identity.infrastructure.persistence;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountCredentials;
import com.creator.identity.domain.AdminScope;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.AccountStatus;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Role;
import com.creator.common.PageRequest;
import java.util.List;
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
                        AccountStatus.valueOf(row.getStatus()), scope(row.getAdminScope())), row.getPasswordHash()));
    }

    @Override
    public Optional<AccountSummary> findSummary(String username) {
        return Optional.ofNullable(mapper.findSummary(username)).map(row -> new AccountSummary(
                row.id(), row.username(), Role.valueOf(row.role()), AccountStatus.valueOf(row.status()),
                scope(row.adminScope())));
    }

    @Override
    public List<AccountSummary> findPage(PageRequest page) {
        return mapper.findPage(page.size(), page.offset()).stream().map(this::toSummary).toList();
    }

    @Override
    public long count() {
        return mapper.count();
    }

    @Override
    public Optional<AccountSummary> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::toSummary);
    }

    @Override
    public boolean updateUserStatus(long id, AccountStatus status) {
        return mapper.updateUserStatus(id, status.name()) == 1;
    }

    private AccountSummary toSummary(AccountSummaryRow row) {
        return new AccountSummary(row.id(), row.username(), Role.valueOf(row.role()), AccountStatus.valueOf(row.status()),
                scope(row.adminScope()));
    }

    @Override
    public boolean updateAdminScope(long id, Role role, AdminScope adminScope) {
        return mapper.updateAdminScope(id, role.name(), adminScope == null ? null : adminScope.name()) == 1;
    }

    @Override
    public boolean updatePassword(long id, String passwordHash) {
        return mapper.updatePassword(id, passwordHash) == 1;
    }

    @Override
    public long sessionVersion(long id) {
        return mapper.sessionVersion(id);
    }

    @Override
    public boolean incrementSessionVersion(long id) {
        return mapper.incrementSessionVersion(id) == 1;
    }

    @Override
    public AccountSummary insert(String username, String passwordHash, Role role, AdminScope adminScope) {
        var row = new UserAccountRow();
        row.setUsername(username);
        row.setPasswordHash(passwordHash);
        row.setRole(role.name());
        row.setStatus(AccountStatus.ACTIVE.name());
        row.setAdminScope(adminScope == null ? null : adminScope.name());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }
        return new AccountSummary(row.getId(), username, role, AccountStatus.ACTIVE, adminScope);
    }

    private AdminScope scope(String value) {
        return value == null || value.isBlank() ? null : AdminScope.valueOf(value);
    }
}
