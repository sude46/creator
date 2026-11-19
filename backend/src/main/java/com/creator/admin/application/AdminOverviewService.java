package com.creator.admin.application;

import com.creator.admin.domain.AdminOverview;
import com.creator.admin.domain.AdminOverviewRepository;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import com.creator.identity.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOverviewService {
    private final AdminOverviewRepository overview;
    private final AccountQueryService accounts;
    private final Clock clock;

    public AdminOverviewService(AdminOverviewRepository overview, AccountQueryService accounts, Clock clock) {
        this.overview = overview;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminOverview get(Actor actor) {
        if (actor == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (actor.role() != Role.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        var current = accounts.current(actor.username());
        if (!current.equals(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        var today = LocalDate.now(clock).atStartOfDay();
        return new AdminOverview(overview.pendingReviews(actor.id()), overview.failedProcessing(),
                overview.usersCreatedSince(today), overview.decisionsSince(today), overview.recentDecisions(5));
    }
}
