package com.creator.video.infrastructure;

import com.creator.video.domain.SubtitleRepository;
import com.creator.video.domain.VideoSubtitle;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSubtitleRepository implements SubtitleRepository {
    private final SubtitleMapper mapper;
    public MyBatisSubtitleRepository(SubtitleMapper mapper) { this.mapper = mapper; }
    @Override public Optional<VideoSubtitle> find(long videoId, String language) {
        return Optional.ofNullable(mapper.find(videoId, language));
    }
    @Override public List<Metadata> list(long videoId) { return mapper.list(videoId); }
    @Override public Optional<Metadata> metadata(long videoId, String language) {
        return Optional.ofNullable(mapper.metadata(videoId, language));
    }
    @Override public void save(long videoId, String language, String label, byte[] content, int sizeBytes) {
        if (mapper.update(videoId, language, label, content, sizeBytes) == 0) {
            mapper.insert(videoId, language, label, content, sizeBytes);
        }
    }
    @Override public void delete(long videoId, String language) { mapper.delete(videoId, language); }
}
