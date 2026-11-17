package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.DraftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator/drafts")
public class DraftController {
    private final DraftService drafts;

    public DraftController(DraftService drafts) {
        this.drafts = drafts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView create(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody CreateDraftRequest request) {
        return DraftView.from(drafts.create(actor, request.requestKey(), request.metadata()));
    }

    @GetMapping("/{id}")
    public DraftView get(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return DraftView.from(drafts.get(actor, id));
    }

    @GetMapping
    public PageResult<DraftView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = drafts.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(DraftView::from).toList(), result.total(), result.page(), result.size());
    }

    @PutMapping("/{id}")
    public DraftView update(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody UpdateVideoRequest request) {
        return DraftView.from(drafts.update(actor, id, request.version(), request.metadata()));
    }
}
