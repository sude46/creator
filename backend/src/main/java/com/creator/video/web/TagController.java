package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.TagService;
import com.creator.video.domain.TagCount;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class TagController {
    private final TagService tags;
    public TagController(TagService tags) { this.tags = tags; }

    @GetMapping("/api/tags")
    public TagList popular(@RequestParam(defaultValue = "20") int limit) {
        return new TagList(tags.popular(limit));
    }

    @GetMapping("/api/videos/{id}/tags")
    public TagsView video(@PathVariable long id) { return new TagsView(tags.publicTags(id)); }

    @GetMapping("/api/creator/videos/{id}/tags")
    public TagsView own(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return new TagsView(tags.ownerTags(actor, id));
    }

    public record TagList(List<TagCount> items) { }
    public record TagsView(List<String> tags) { }
}
