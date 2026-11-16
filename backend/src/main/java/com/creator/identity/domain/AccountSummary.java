package com.creator.identity.domain;

public record AccountSummary(Long id, String username, Role role, AccountStatus status) {
}
