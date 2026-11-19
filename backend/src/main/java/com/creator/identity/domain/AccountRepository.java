package com.creator.identity.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    Optional<AccountCredentials> findCredentials(String username);

    Optional<AccountSummary> findSummary(String username);

    AccountSummary insert(String username, String passwordHash, Role role);

    default boolean updateUserStatus(long id, AccountStatus status) {
        throw new UnsupportedOperationException();
    }

    default List<AccountSummary> findPage(PageRequest page) {
        throw new UnsupportedOperationException();
    }

    default long count() {
        throw new UnsupportedOperationException();
    }

    default Optional<AccountSummary> findById(long id) {
        throw new UnsupportedOperationException();
    }
}
