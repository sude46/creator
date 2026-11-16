package com.creator.identity.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class AuthenticationWorkBudget {
    private final Semaphore permits;

    public AuthenticationWorkBudget(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("Authentication concurrency must be positive");
        }
        this.permits = new Semaphore(maxConcurrent);
    }

    public <Result> Result execute(Supplier<Result> work) {
        if (!permits.tryAcquire()) {
            throw new BusinessException(ErrorCode.AUTH_BUSY);
        }
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }
}
