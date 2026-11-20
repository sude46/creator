package com.creator.identity.infrastructure.persistence;

public record AccountSummaryRow(Long id, String username, String role, String status, String adminScope) {
}
