package com.creator.identity.web;

import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Actor;

public record UserView(String id, String username, String role) {
    public static UserView from(AccountSummary account) {
        return new UserView(account.id().toString(), account.username(), account.role().name());
    }

    public static UserView from(Actor actor) {
        return new UserView(actor.id().toString(), actor.username(), actor.role().name());
    }
}
