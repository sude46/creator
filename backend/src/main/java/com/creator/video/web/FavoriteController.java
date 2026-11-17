package com.creator.video.web;

import com.creator.common.*;
import com.creator.identity.domain.Actor;
import com.creator.video.application.FavoriteService;
import com.creator.video.domain.FavoriteItem;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites) { this.favorites = favorites; }
    @PutMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestAttribute("currentActor") Actor actor, @PathVariable long videoId) { favorites.add(actor, videoId); }
    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute("currentActor") Actor actor, @PathVariable long videoId) { favorites.remove(actor, videoId); }
    @GetMapping("/{videoId}")
    public FavoriteState state(@RequestAttribute("currentActor") Actor actor, @PathVariable long videoId) {
        return new FavoriteState(favorites.contains(actor, videoId));
    }
    @GetMapping
    public PageResult<FavoriteView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = favorites.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(FavoriteView::from).toList(), result.total(), result.page(), result.size());
    }
    public record FavoriteState(boolean favorited) { }
    public record FavoriteView(String videoId, LocalDateTime createdAt, boolean available, PublicVideoView video) {
        static FavoriteView from(FavoriteItem item) {
            return new FavoriteView(Long.toString(item.videoId()), item.createdAt(), item.video() != null,
                    item.video() == null ? null : PublicVideoView.from(item.video()));
        }
    }
}
