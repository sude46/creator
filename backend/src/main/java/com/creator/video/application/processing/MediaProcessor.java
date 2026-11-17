package com.creator.video.application.processing;

import com.creator.video.domain.MediaMetadata;
import java.nio.file.Path;

public interface MediaProcessor {
    void verify();
    MediaMetadata process(Path source, Path cover, long deadlineNanos);
}
