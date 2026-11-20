package com.creator.identity.domain;

public record AccountSummary(Long id, String username, Role role, AccountStatus status, AdminScope adminScope) {
    public AccountSummary(Long id, String username, Role role, AccountStatus status) {
        this(id, username, role, status, null);
    }
}
