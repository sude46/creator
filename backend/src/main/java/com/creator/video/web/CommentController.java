package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.identity.domain.Actor;
import com.creator.video.application.CommentService;
import com.creator.video.domain.Comment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommentController {
    private final CommentService comments;
    public CommentController(CommentService comments) { this.comments = comments; }

    @GetMapping("/api/videos/{videoId}/comments")
    public PageResult<CommentView> list(@RequestAttribute("currentActor") Actor actor, @PathVariable long videoId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        var result = comments.list(actor, videoId, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(CommentView::from).toList(), result.total(), result.page(), result.size());
    }

    @PostMapping("/api/videos/{videoId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView add(@RequestAttribute("currentActor") Actor actor, @PathVariable long videoId,
            @Valid @RequestBody AddRequest request) {
        return CommentView.from(comments.add(actor, videoId, request.content()));
    }

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("currentActor") Actor actor, @PathVariable long id) { comments.delete(actor, id); }

    public record AddRequest(@NotBlank @Size(max = 500) String content) { }
    public record CommentView(String id, String videoId, String userId, String username, String content, LocalDateTime createdAt) {
        static CommentView from(Comment c) {
            return new CommentView(Long.toString(c.id()), Long.toString(c.videoId()), Long.toString(c.userId()), c.username(), c.content(), c.createdAt());
        }
    }
}
