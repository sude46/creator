package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.FollowService;
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
}
