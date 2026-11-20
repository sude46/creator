package com.creator.video.web;

import com.creator.identity.domain.Actor;
import com.creator.video.application.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/api/me/profile")
    public ProfileService.Form mine(@RequestAttribute("currentActor") Actor actor) { return profiles.mine(actor); }

    @PutMapping("/api/me/profile")
    public ProfileService.Form update(@RequestAttribute("currentActor") Actor actor,
            @Valid @RequestBody UpdateRequest request) {
        return profiles.update(actor, request.version(), request.displayName(), request.bio(), request.website());
    }

    @PutMapping(value = "/api/me/profile/avatar", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void avatar(@RequestAttribute("currentActor") Actor actor, @RequestBody byte[] data) {
        profiles.uploadAvatar(actor, data);
    }

    @DeleteMapping("/api/me/profile/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@RequestAttribute("currentActor") Actor actor) { profiles.removeAvatar(actor); }

    @GetMapping("/api/authors/{id}/avatar")
    public ResponseEntity<byte[]> publicAvatar(@PathVariable long id) {
        var avatar = profiles.avatar(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.data());
    }

    public record UpdateRequest(@NotNull @PositiveOrZero Long version,
            @Size(max = 32) String displayName, @Size(max = 200) String bio, @Size(max = 200) String website) { }
}
