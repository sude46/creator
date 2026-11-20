package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.HistoryService;
import com.creator.video.domain.HistoryItem;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryService histories;
    public HistoryController(HistoryService histories) { this.histories = histories; }

    @GetMapping
    public PageResult<HistoryView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        var result = histories.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(HistoryView::from).toList(),
                result.total(), result.page(), result.size());
    }
    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute("currentActor") Actor actor, @PathVariable long videoId) {
        histories.remove(actor, videoId);
    }
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@RequestAttribute("currentActor") Actor actor) { histories.clear(actor); }

    public record HistoryView(String videoId, LocalDateTime viewedAt, boolean available, PublicVideoView video) {
        static HistoryView from(HistoryItem item) {
            return new HistoryView(Long.toString(item.videoId()), item.viewedAt(), item.video() != null,
                    item.video() == null ? null : PublicVideoView.from(item.video()));
        }
    }
}
