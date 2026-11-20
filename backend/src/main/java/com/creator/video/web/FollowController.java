package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.FollowService;
import com.creator.video.domain.AuthorCard;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class FollowController {
    private final FollowService follows;
    public FollowController(FollowService follows) { this.follows = follows; }

    @GetMapping("/api/authors/{id}/profile")
    public FollowService.Profile profile(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        return follows.profile(actor, id);
    }
    @GetMapping("/api/authors/{id}/followers")
    public PageResult<AuthorCardView> followers(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = follows.followers(actor, id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(AuthorCardView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/api/authors/{id}/following")
    public PageResult<AuthorCardView> following(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = follows.following(actor, id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(AuthorCardView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @PutMapping("/api/authors/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        follows.follow(actor, id);
    }
    @DeleteMapping("/api/authors/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) {
        follows.unfollow(actor, id);
    }

    public record AuthorCardView(String id, String username, String displayName, String bio, boolean hasAvatar,
            long profileVersion, long followerCount, boolean following) {
        static AuthorCardView from(AuthorCard card) {
            return new AuthorCardView(Long.toString(card.id()), card.username(), card.displayName(), card.bio(),
                    card.hasAvatar(), card.profileVersion(), card.followerCount(), card.following());
        }
    }
}
