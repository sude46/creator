package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.ReviewDecision;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BatchReviewService {
    private final ReviewService reviews;
    public BatchReviewService(ReviewService reviews) { this.reviews = reviews; }

    public BatchResult decide(Actor actor, List<Item> items) {
        if (items == null || items.isEmpty() || items.size() > 20) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var results = new ArrayList<Result>();
        for (var item : items) {
            try {
                reviews.decide(actor, item.videoId(), item.version(), item.reviewRound(),
                        new ReviewDecision(item.outcome(), item.reason()));
                results.add(new Result(item.videoId(), true, null, "已处理"));
            } catch (BusinessException exception) {
                results.add(new Result(item.videoId(), false, exception.getCode().name(), exception.getMessage()));
            } catch (RuntimeException exception) {
                results.add(new Result(item.videoId(), false, "INTERNAL_ERROR", "处理失败，请刷新后核对"));
            }
        }
        return new BatchResult(results, (int) results.stream().filter(Result::ok).count(),
                (int) results.stream().filter(result -> !result.ok()).count());
    }
    public record Item(long videoId, long version, long reviewRound, ReviewDecision.Outcome outcome, String reason) { }
    public record Result(long videoId, boolean ok, String code, String message) { }
    public record BatchResult(List<Result> results, int succeeded, int failed) { }
}
