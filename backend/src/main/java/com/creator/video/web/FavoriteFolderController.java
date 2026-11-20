package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.FavoriteFolderService;
import com.creator.video.domain.FavoriteFolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorite-folders")
public class FavoriteFolderController {
    private final FavoriteFolderService folders;
    public FavoriteFolderController(FavoriteFolderService folders) { this.folders = folders; }

    @GetMapping
    public FolderList list(@RequestAttribute("currentActor") Actor actor) {
        return new FolderList(folders.list(actor).stream().map(FolderView::from).toList());
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderView create(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody NameRequest request) {
        return FolderView.from(folders.create(actor, request.name()));
    }
    @PutMapping("/{id}")
    public FolderView rename(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody RenameRequest request) {
        return FolderView.from(folders.rename(actor, id, request.version(), request.name()));
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) { folders.delete(actor, id); }
    @GetMapping("/{id}/videos")
    public PageResult<PublicVideoView> videos(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        var result = folders.videos(actor, id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(PublicVideoView::from).toList(),
                result.total(), result.page(), result.size());
    }
    @PutMapping("/{id}/videos/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestAttribute("currentActor") Actor actor, @PathVariable long id, @PathVariable long videoId) {
        folders.addVideo(actor, id, videoId);
    }
    @DeleteMapping("/{id}/videos/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute("currentActor") Actor actor, @PathVariable long id, @PathVariable long videoId) {
        folders.removeVideo(actor, id, videoId);
    }

    public record NameRequest(@NotBlank @Size(max = 30) String name) { }
    public record RenameRequest(@NotBlank @Size(max = 30) String name, @NotNull @PositiveOrZero Long version) { }
    public record FolderList(List<FolderView> items) { }
    public record FolderView(String id, String name, long version, long itemCount, LocalDateTime createdAt) {
        static FolderView from(FavoriteFolder folder) {
            return new FolderView(Long.toString(folder.id()), folder.name(), folder.version(), folder.itemCount(),
                    folder.createdAt());
        }
    }
}
