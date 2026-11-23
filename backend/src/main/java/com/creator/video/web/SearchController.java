package com.creator.video.web;

import com.creator.video.application.SearchSuggestionService;
import com.creator.video.domain.SearchSuggestions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchSuggestionService suggestions;
    public SearchController(SearchSuggestionService suggestions) { this.suggestions = suggestions; }
    @GetMapping("/suggestions")
    public SearchSuggestions suggestions(@RequestParam(defaultValue = "") String q) {
        return suggestions.suggest(q);
    }
}
