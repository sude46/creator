package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.ReportService;
import com.creator.video.domain.ContentReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports) { this.reports = reports; }
    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody ReportRequest request) {
        reports.report(actor, request.targetType(), request.targetId(), request.reason());
    }
    @GetMapping("/api/admin/reports")
    public PageResult<ReportView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = reports.list(actor, status, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(ReportView::from).toList(), result.total(), result.page(), result.size());
    }
    @PostMapping("/api/admin/reports/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody ResolveRequest request) {
        reports.resolve(actor, id, request.action(), request.reason());
    }
    public record ReportRequest(@NotBlank String targetType, @NotNull Long targetId,
            @NotBlank @Size(max = 500) String reason) { }
    public record ResolveRequest(@NotBlank String action, @Size(max = 500) String reason) { }
    public record ReportView(String id, String reporterId, String reporterName, String targetType, String targetId,
            String reason, String status, String resolverId, String resolutionReason, LocalDateTime createdAt,
            LocalDateTime resolvedAt) {
        static ReportView from(ContentReport r) {
            return new ReportView(Long.toString(r.id()), Long.toString(r.reporterId()), r.reporterName(),
                    r.targetType(), Long.toString(r.targetId()), r.reason(), r.status(),
                    r.resolverId() == null ? null : Long.toString(r.resolverId()), r.resolutionReason(),
                    r.createdAt(), r.resolvedAt());
        }
    }
}
