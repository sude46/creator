package com.creator.video.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.domain.SearchSuggestions;
import com.creator.video.domain.TagRepository;
import com.creator.video.infrastructure.ProfileMapper;
import com.creator.video.infrastructure.VideoQueryMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchSuggestionService {
    private final VideoQueryMapper videos;
    private final TagRepository tags;
    private final ProfileMapper profiles;

    public SearchSuggestionService(VideoQueryMapper videos, TagRepository tags, ProfileMapper profiles) {
        this.videos = videos; this.tags = tags; this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public SearchSuggestions suggest(String query) {
        String clean = query == null ? "" : query.strip();
        if (clean.isEmpty()) return new SearchSuggestions(List.of(), List.of(), List.of());
        if (clean.length() > 50 || !StandardCharsets.UTF_8.newEncoder().canEncode(clean)
                || clean.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String pattern = "%" + clean.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new SearchSuggestions(videos.suggestVideos(pattern, 5), tags.suggest(pattern, 5),
                profiles.suggestAuthors(pattern, 5));
    }
}
