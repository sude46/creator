package com.creator.identity.web;

import com.creator.identity.application.PasswordService;
import com.creator.identity.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class PasswordController {
    private final PasswordService passwords;
    public PasswordController(PasswordService passwords) { this.passwords = passwords; }

    @PutMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@RequestAttribute("currentActor") Actor actor, @Valid @RequestBody ChangeRequest request) {
        passwords.change(actor, request.currentPassword(), request.newPassword());
    }

    public record ChangeRequest(@NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(max = 64) String newPassword) { }
}
