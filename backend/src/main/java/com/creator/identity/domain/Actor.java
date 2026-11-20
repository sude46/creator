package com.creator.identity.domain;

import java.util.Objects;

public record Actor(Long id, String username, Role role, AdminScope adminScope) {
    public Actor(Long id, String username, Role role) { this(id, username, role, null); }

    public boolean adminArea() { return role == Role.ADMIN; }

    public boolean superAdmin() {
        return adminArea() && (adminScope == null || adminScope == AdminScope.SUPER_ADMIN);
    }

    public boolean canReview() {
        return adminArea() && (adminScope == null || adminScope == AdminScope.SUPER_ADMIN
                || adminScope == AdminScope.REVIEWER);
    }

    public boolean canModerate() {
        return adminArea() && (adminScope == null || adminScope == AdminScope.SUPER_ADMIN
                || adminScope == AdminScope.MODERATOR);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Actor actor && Objects.equals(id, actor.id)
                && Objects.equals(username, actor.username) && role == actor.role;
    }

    @Override
    public int hashCode() { return Objects.hash(id, username, role); }
}
