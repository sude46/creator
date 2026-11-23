package com.creator.video.application.processing;

import com.creator.video.domain.HlsVariant;
import com.creator.video.domain.ProcessingFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class HlsUploader {
    private HlsUploader() { }

    public static List<HlsVariant> upload(long videoId, long generation,
            List<ProcessedMedia.HlsVariant> processed, Path root, ProcessingStorage storage,
            long deadline, List<String> uploaded) throws IOException {
        var variants = new ArrayList<HlsVariant>();
        for (var variant : processed) {
            var directory = root.resolve(variant.name());
            var baseKey = "videos/" + videoId + "/" + generation + "/hls/" + variant.name();
            var playlist = directory.resolve("index.m3u8");
            var playlistKey = baseKey + "/index.m3u8";
            storage.uploadHls(playlistKey, playlist, Files.size(playlist), "application/vnd.apple.mpegurl", deadline);
            uploaded.add(playlistKey);
            var segmentKeys = new ArrayList<String>();
            try (var files = Files.list(directory)) {
                for (var segment : files.filter(path -> path.getFileName().toString().endsWith(".ts")).sorted().toList()) {
                    ProcessingWorker.requireTime(deadline);
                    String key = baseKey + "/" + segment.getFileName();
                    storage.uploadHls(key, segment, Files.size(segment), "video/mp2t", deadline);
                    uploaded.add(key);
                    segmentKeys.add(key);
                }
            }
            if (segmentKeys.isEmpty()) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
            variants.add(new HlsVariant(variant.name(), variant.bandwidth(), variant.width(), variant.height(),
                    baseKey, playlistKey, variant.playlist(), segmentKeys));
        }
        return variants;
    }

    public static void cleanup(ProcessingStorage storage, List<String> keys, org.slf4j.Logger logger) {
        for (String key : keys) {
            try { storage.delete(key); }
            catch (RuntimeException exception) {
                logger.warn("HLS cleanup deferred [key={}, failure={}]", key, exception.getClass().getSimpleName());
            }
        }
    }
}
