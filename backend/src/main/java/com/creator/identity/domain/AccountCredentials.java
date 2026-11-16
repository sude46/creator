package com.creator.identity.domain;

public record AccountCredentials(AccountSummary account, String passwordHash) {
    @Override
    public String toString() {
        return "AccountCredentials[account=" + account + ", passwordHash=<redacted>]";
    }
}
