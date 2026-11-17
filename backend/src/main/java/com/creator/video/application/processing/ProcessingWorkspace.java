package com.creator.video.application.processing;

import com.creator.video.domain.ProcessingFailure;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessingWorkspace {
    private final Path root;
    private final WorkerProperties properties;
    private final Set<Path> active = ConcurrentHashMap.newKeySet();

    public ProcessingWorkspace(WorkerProperties properties) throws IOException {
        this.properties = properties;
        root = Path.of(properties.tempDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Worker directory cannot be a symlink");
    }

    public Path create() throws IOException {
        if (Files.getFileStore(root).getUsableSpace() < properties.minFreeBytes() + properties.maxSourceBytes() + 1048576) {
            throw new ProcessingException(ProcessingFailure.TEMP_UNAVAILABLE);
        }
        var directory = Files.createTempDirectory(root, "processing-");
        active.add(directory);
        return directory;
    }

    public void discard(Path directory) throws IOException {
        try { remove(directory); }
        finally { active.remove(directory); }
    }

    public void cleanAbandoned() throws IOException {
        var cutoff = Instant.now().minusSeconds(properties.leaseSeconds() + 600L);
        try (var paths = Files.newDirectoryStream(root, "processing-*")) {
            int scanned = 0;
            for (Path path : paths) {
                if (++scanned > 100) break;
                if (!active.contains(path) && !Files.isSymbolicLink(path)
                        && Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) remove(path);
            }
        }
    }

    private void remove(Path directory) throws IOException {
        if (!directory.normalize().getParent().equals(root)) throw new IOException("Invalid workspace path");
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
