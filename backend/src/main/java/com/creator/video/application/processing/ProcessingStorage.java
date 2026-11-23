package com.creator.video.application.processing;

import java.nio.file.Path;

public interface ProcessingStorage {
    void verify();
    void download(String key, Path target, long expectedBytes, long deadlineNanos);
    void uploadCover(String key, Path file, long bytes, long deadlineNanos);
    void uploadHls(String key, Path file, long bytes, String contentType, long deadlineNanos);
    void delete(String key);
}
