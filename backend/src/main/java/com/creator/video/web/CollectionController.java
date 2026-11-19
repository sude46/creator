package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.CollectionService;
import com.creator.video.application.PublicCollectionService;
import com.creator.video.domain.VideoCollection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CollectionController {
    private final CollectionService collections;
    private final PublicCollectionService publicCollections;

    public CollectionController(CollectionService collections, PublicCollectionService publicCollections) {
        this.collections = collections;
        this.publicCollections = publicCollections;
    }

    @GetMapping("/api/creator/collections")
    public PageResult<CollectionView> own(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = collections.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(CollectionView::from).toList(), result.total(), result.page(), result.size());
    }

    @PostMapping("/api/creator/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionView create(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody WriteRequest request) {
        return CollectionView.from(collections.create(actor, request.title(), request.description(), request.visibility()));
    }

    @PutMapping("/api/creator/collections/{id}")
    public CollectionView update(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody UpdateRequest request) {
        return CollectionView.from(collections.update(actor, id, request.version(), request.title(), request.description(), request.visibility()));
    }

    @DeleteMapping("/api/creator/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id, @RequestParam long version) {
        collections.delete(actor, id, version);
    }

    @PostMapping("/api/creator/collections/{id}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestAttribute("currentActor") Actor actor, @PathVariable long id, @Valid @RequestBody ItemRequest request) {
        collections.add(actor, id, request.videoId());
    }

    @DeleteMapping("/api/creator/collections/{id}/items/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute("currentActor") Actor actor, @PathVariable long id, @PathVariable long videoId) {
        collections.remove(actor, id, videoId);
    }

    @GetMapping("/api/authors/{id}/collections")
    public PageResult<CollectionView> byAuthor(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = publicCollections.listByAuthor(id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(CollectionView::from).toList(), result.total(), result.page(), result.size());
    }

    @GetMapping("/api/collections/{id}")
    public DetailView publicDetail(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        var result = publicCollections.get(id, new PageRequest(page, size));
        return new DetailView(CollectionView.from(result.collection()),
                new PageResult<>(result.items().items().stream().map(PublicVideoView::from).toList(),
                        result.items().total(), result.items().page(), result.items().size()));
    }

    public record WriteRequest(@NotBlank @Size(max = 80) String title, @Size(max = 500) String description,
            @NotBlank String visibility) { }
    public record UpdateRequest(@NotNull Long version, @NotBlank @Size(max = 80) String title,
            @Size(max = 500) String description, @NotBlank String visibility) { }
    public record ItemRequest(@NotNull Long videoId) { }
    public record CollectionView(String id, String ownerId, String ownerName, String title, String description,
            String visibility, long version, long itemCount, java.time.LocalDateTime createdAt) {
        static CollectionView from(VideoCollection c) {
            return new CollectionView(Long.toString(c.id()), Long.toString(c.ownerId()), c.ownerName(), c.title(),
                    c.description(), c.visibility(), c.version(), c.itemCount(), c.createdAt());
        }
    }
    public record DetailView(CollectionView collection, PageResult<PublicVideoView> items) { }
}
