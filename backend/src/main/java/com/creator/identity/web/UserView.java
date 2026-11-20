package com.creator.identity.web;

import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Actor;

public record UserView(String id, String username, String role, String adminScope) {
    public static UserView from(AccountSummary account) {
        return new UserView(account.id().toString(), account.username(), account.role().name(),
                account.adminScope() == null ? null : account.adminScope().name());
    }

    public static UserView from(Actor actor) {
        return new UserView(actor.id().toString(), actor.username(), actor.role().name(),
                actor.adminScope() == null ? null : actor.adminScope().name());
    }
}
