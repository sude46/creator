package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.AppealService;
import com.creator.video.domain.ContentAppeal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AppealController {
    private final AppealService appeals;
    public AppealController(AppealService appeals) { this.appeals = appeals; }

    @PostMapping("/api/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    public AppealView create(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody CreateRequest request) {
        return AppealView.from(appeals.create(actor, request.targetType(), request.targetId(), request.reason()));
    }

    @GetMapping("/api/appeals")
    public PageResult<AppealView> mine(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(required = false) String targetType, @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = appeals.mine(actor, targetType, targetId, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(AppealView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/api/admin/appeals")
    public PageResult<AppealView> admin(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = appeals.adminList(actor, status, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(AppealView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @PostMapping("/api/admin/appeals/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody ResolveRequest request) {
        appeals.resolve(actor, id, request.action(), request.reason());
    }

    public record CreateRequest(@NotBlank String targetType, @NotNull Long targetId,
            @NotBlank @Size(max = 500) String reason) { }
    public record ResolveRequest(@NotBlank String action, @NotBlank @Size(max = 500) String reason) { }
    public record AppealView(String id, String appellantId, String appellantName, String targetType, String targetId,
            String targetTitle, String reason, String status, String resolverId, String resolutionReason,
            LocalDateTime createdAt, LocalDateTime resolvedAt) {
        static AppealView from(ContentAppeal appeal) {
            return new AppealView(Long.toString(appeal.id()), Long.toString(appeal.appellantId()), appeal.appellantName(),
                    appeal.targetType(), Long.toString(appeal.targetId()), appeal.targetTitle(), appeal.reason(), appeal.status(),
                    appeal.resolverId() == null ? null : Long.toString(appeal.resolverId()),
                    appeal.resolutionReason(), appeal.createdAt(), appeal.resolvedAt());
        }
    }
}
