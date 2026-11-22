package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.TagService;
import com.creator.video.domain.TagAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tags")
public class AdminTagController {
    private final TagService tags;
    public AdminTagController(TagService tags) { this.tags = tags; }

    @GetMapping
    public List<TagAdminView> list(@RequestAttribute("currentActor") Actor actor) {
        return tags.adminList(actor).stream().map(TagAdminView::from).toList();
    }
    @PutMapping("/{id}")
    public TagAdminView rename(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody RenameRequest request) {
        return TagAdminView.from(tags.rename(actor, id, request.name()));
    }
    @PostMapping("/{id}/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void merge(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody MergeRequest request) {
        tags.merge(actor, id, request.targetId());
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        tags.delete(actor, id);
    }

    public record RenameRequest(@NotBlank @Size(max = 20) String name) { }
    public record MergeRequest(@NotNull @Positive Long targetId) { }
    public record TagAdminView(String id, String name, long uses) {
        static TagAdminView from(TagAdmin tag) {
            return new TagAdminView(Long.toString(tag.id()), tag.name(), tag.uses());
        }
    }
}
