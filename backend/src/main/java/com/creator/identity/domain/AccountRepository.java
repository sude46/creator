package com.creator.identity.domain;

import java.util.Optional;

public interface AccountRepository {
    Optional<AccountCredentials> findCredentials(String username);

    Optional<AccountSummary> findSummary(String username);

    AccountSummary insert(String username, String passwordHash, Role role);
}
