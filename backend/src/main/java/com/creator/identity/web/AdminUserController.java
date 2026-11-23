package com.creator.identity.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.application.AdminUserService;
import com.creator.identity.domain.AccountSummary;
import com.creator.identity.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService users;
    private final com.creator.identity.application.PasswordService passwords;

    public AdminUserController(AdminUserService users, com.creator.identity.application.PasswordService passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @GetMapping
    public PageResult<AdminUserView> list(@RequestAttribute("currentActor") Actor actor,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = users.list(actor, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(AdminUserView::from).toList(),
                result.total(), result.page(), result.size());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView create(@RequestAttribute("currentActor") Actor actor,
            @Valid @RequestBody CreateUserRequest request) {
        return AdminUserView.from(users.create(actor, request.username(), request.password()));
    }

    @PatchMapping("/{id}")
    public AdminUserView status(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody StatusRequest request) {
        return AdminUserView.from(users.setEnabled(actor, id, request.enabled()));
    }

    @PutMapping("/{id}/admin-scope")
    public AdminUserView adminScope(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody AdminScopeRequest request) {
        return AdminUserView.from(users.setAdminScope(actor, id, request.adminScope()));
    }

    public record AdminScopeRequest(@NotBlank @Size(max = 16) String adminScope) { }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestAttribute("currentActor") Actor actor, @PathVariable long id,
            @Valid @RequestBody PasswordResetRequest request) {
        passwords.reset(actor, id, request.newPassword());
    }

    public record PasswordResetRequest(@NotBlank @Size(max = 64) String newPassword) { }

    public record CreateUserRequest(@NotBlank @Size(max = 32) String username,
            @NotBlank @Size(max = 64) String password) { }

    public record StatusRequest(@NotNull Boolean enabled) { }

    public record AdminUserView(String id, String username, String role, String status, String adminScope) {
        static AdminUserView from(AccountSummary account) {
            return new AdminUserView(account.id().toString(), account.username(), account.role().name(), account.status().name(),
                    account.adminScope() == null ? null : account.adminScope().name());
        }
    }
}
