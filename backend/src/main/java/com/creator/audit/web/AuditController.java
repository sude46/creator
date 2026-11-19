package com.creator.audit.web;

import com.creator.audit.application.AuditQueryService;
import com.creator.audit.domain.AuditRecord;
import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {
    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResult<AuditView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = audit.list(actor, actorId, action, targetType, from, to, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(AuditView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        var records = audit.export(actor, actorId, action, targetType, from, to);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv(records));
    }

    private byte[] csv(List<AuditRecord> records) {
        var text = new StringBuilder("id,actorId,action,targetType,targetId,reason,requestId,createdAt\n");
        for (var record : records) {
            text.append(cell(Long.toString(record.id()))).append(',')
                    .append(cell(Long.toString(record.actorId()))).append(',')
                    .append(cell(record.action())).append(',')
                    .append(cell(record.targetType())).append(',')
                    .append(cell(Long.toString(record.targetId()))).append(',')
                    .append(cell(record.reason())).append(',')
                    .append(cell(record.requestId())).append(',')
                    .append(cell(record.createdAt() == null ? "" : record.createdAt().toString())).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String cell(String value) {
        if (value == null) return "";
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public record AuditView(String id, String actorId, String action, String targetType, String targetId,
            String reason, String requestId, LocalDateTime createdAt) {
        static AuditView from(AuditRecord record) {
            return new AuditView(Long.toString(record.id()), Long.toString(record.actorId()), record.action(),
                    record.targetType(), Long.toString(record.targetId()), record.reason(), record.requestId(), record.createdAt());
        }
    }
}
