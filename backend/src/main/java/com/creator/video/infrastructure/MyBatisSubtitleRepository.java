package com.creator.video.infrastructure;

import com.creator.video.domain.SubtitleRepository;
import com.creator.video.domain.VideoSubtitle;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSubtitleRepository implements SubtitleRepository {
    private final SubtitleMapper mapper;
    public MyBatisSubtitleRepository(SubtitleMapper mapper) { this.mapper = mapper; }
    @Override public Optional<VideoSubtitle> find(long videoId) { return Optional.ofNullable(mapper.find(videoId)); }
    @Override public Optional<Metadata> metadata(long videoId) { return Optional.ofNullable(mapper.metadata(videoId)); }
    @Override public void save(long videoId, byte[] content, int sizeBytes) {
        if (mapper.update(videoId, content, sizeBytes) == 0) mapper.insert(videoId, content, sizeBytes);
    }
    @Override public void delete(long videoId) { mapper.delete(videoId); }
}
