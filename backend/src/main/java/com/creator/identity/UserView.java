package com.creator.identity;

public record UserView(String id, String username, String role) {
    public static UserView from(UserAccount account) {
        return new UserView(account.getId().toString(), account.getUsername(), account.getRole());
    }
}
