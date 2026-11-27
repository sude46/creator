package com.creator.video.application;

import java.nio.file.Path;

public interface ObjectStorage {
    void verifyPrivateBucket();
    void put(String objectKey, Path file, long size);
    void delete(String objectKey);
    byte[] read(String objectKey);
}
