package com.creator.video.web;

import com.creator.common.PageRequest;
import com.creator.common.PageResult;
import com.creator.video.application.PublicVideoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/authors")
public class PublicAuthorController {
    private final PublicVideoService videos;

    public PublicAuthorController(PublicVideoService videos) {
        this.videos = videos;
    }

    @GetMapping("/{id}/videos")
    public PageResult<PublicVideoView> videos(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size) {
        var result = videos.listByAuthor(id, new PageRequest(page, size));
        return new PageResult<>(result.items().stream().map(PublicVideoView::from).toList(),
                result.total(), result.page(), result.size());
    }
}
