package com.creator.video;

import com.creator.video.application.processing.*;
import com.creator.video.domain.ProcessingFailure;
import com.creator.video.infrastructure.media.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class MediaProcessTest {
    @TempDir Path directory;

    @Test
    void runnerEnforcesTimeoutAndBoundsOutput() {
        var runner = new BoundedMediaProcess();
        var timeout = assertThrows(ProcessingException.class, () -> runner.run(helper("sleep"), directory,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(1)));
        assertEquals(ProcessingFailure.PROCESS_TIMEOUT, timeout.failure());
        long processId = Long.parseLong(assertDoesNotThrow(() -> Files.readString(directory.resolve("process.pid"))));
        assertFalse(ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false));
        var overflow = assertThrows(ProcessingException.class, () -> runner.run(helper("flood"), directory,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(5)));
        assertEquals(ProcessingFailure.PROCESS_FAILED, overflow.failure());
    }

    @Test
    void childEnvironmentDoesNotInheritCredentialsAndWorkingDirectoryIsControlled() {
        var result = new BoundedMediaProcess().run(helper("environment"), directory,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
        assertEquals(0, result.exitCode());
        String output = new String(result.stdout(), StandardCharsets.UTF_8);
        assertEquals(directory.toAbsolutePath() + "\ntrue", output);
    }

    @Test
    void workspaceRemovesFailedTaskAndDoesNotTraverseSymlinkOrArbitraryPath() throws Exception {
        var workspace = new ProcessingWorkspace(properties("test", "test"));
        var path = workspace.create();
        Files.writeString(path.resolve("file"), "temporary");
        workspace.discard(path);
        assertFalse(Files.exists(path));
        assertThrows(java.io.IOException.class, () -> workspace.discard(directory.getParent()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CREATOR_FFMPEG_IT_DIR", matches = ".+")
    void actualFfmpegProducesBoundedCoverAndRejectsUnsupportedCodecAndCorruptFile() throws Exception {
        var bin = Path.of(System.getenv("CREATOR_FFMPEG_IT_DIR"));
        var ffmpeg = bin.resolve(nativeName("ffmpeg"));
        var probe = bin.resolve(nativeName("ffprobe"));
        var runner = new BoundedMediaProcess();
        var processor = new FfmpegMediaProcessor(properties(probe.toString(), ffmpeg.toString()), new ObjectMapper(), runner);
        processor.verify();
        var source = directory.resolve("source.mp4");
        generate(ffmpeg, source, "libx264");
        var cover = directory.resolve("cover.jpg");
        var metadata = processor.process(source, cover, System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
        assertTrue(metadata.durationMillis() >= 1000 && metadata.durationMillis() <= 1100);
        assertEquals(320, metadata.width());
        assertEquals(240, metadata.height());
        assertTrue(Files.size(cover) > 10 && Files.size(cover) <= 1048576);
        var unsupported = directory.resolve("unsupported.mp4");
        generate(ffmpeg, unsupported, "mpeg4");
        assertEquals(ProcessingFailure.UNSUPPORTED_MEDIA, assertThrows(ProcessingException.class,
                () -> processor.process(unsupported, directory.resolve("bad.jpg"), System.nanoTime() + TimeUnit.SECONDS.toNanos(10))).failure());
        Files.writeString(source, "not a video");
        assertEquals(ProcessingFailure.INVALID_MEDIA, assertThrows(ProcessingException.class,
                () -> processor.process(source, cover, System.nanoTime() + TimeUnit.SECONDS.toNanos(10))).failure());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CREATOR_FFMPEG_IT_DIR", matches = ".+")
    void actualFfmpegProducesHlsPlaylistAndSegments() throws Exception {
        var bin = Path.of(System.getenv("CREATOR_FFMPEG_IT_DIR"));
        var ffmpeg = bin.resolve(nativeName("ffmpeg"));
        var processor = new FfmpegMediaProcessor(properties(bin.resolve(nativeName("ffprobe")).toString(), ffmpeg.toString()),
                new ObjectMapper(), new BoundedMediaProcess());
        var source = directory.resolve("source.mp4");
        generate(ffmpeg, source, "libx264");
        var hls = directory.resolve("hls");
        var processed = processor.processWithHls(source, directory.resolve("cover.jpg"), hls,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
        assertEquals(1, processed.variants().size());
        var variant = processed.variants().getFirst();
        assertEquals("240p", variant.name());
        assertEquals(320, variant.width());
        assertEquals(240, variant.height());
        assertTrue(variant.playlist().contains("#EXTM3U"));
        assertTrue(variant.playlist().lines().anyMatch(line -> line.endsWith(".ts")));
        try (var files = Files.list(hls.resolve(variant.name()))) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".ts")));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CREATOR_FFMPEG_IT_DIR", matches = ".+")
    void actualFfmpegTranscodesHevcToH264Hls() throws Exception {
        var bin = Path.of(System.getenv("CREATOR_FFMPEG_IT_DIR"));
        var ffmpeg = bin.resolve(nativeName("ffmpeg"));
        var processor = new FfmpegMediaProcessor(properties(bin.resolve(nativeName("ffprobe")).toString(), ffmpeg.toString()),
                new ObjectMapper(), new BoundedMediaProcess());
        var source = directory.resolve("hevc-source.mp4");
        generate(ffmpeg, source, "libx265");
        var processed = processor.processWithHls(source, directory.resolve("hevc-cover.jpg"),
                directory.resolve("hevc-hls"), System.nanoTime() + TimeUnit.SECONDS.toNanos(45));
        assertEquals(1, processed.variants().size());
        assertTrue(processed.variants().getFirst().playlist().contains("#EXTM3U"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CREATOR_FFMPEG_IT_DIR", matches = ".+")
    void metadataRejectsExternalStreamTypesInvalidDimensionsAndExcessiveDuration() throws Exception {
        var bin = Path.of(System.getenv("CREATOR_FFMPEG_IT_DIR"));
        var processor = new FfmpegMediaProcessor(properties(bin.resolve(nativeName("ffprobe")).toString(), bin.resolve(nativeName("ffmpeg")).toString()),
                new ObjectMapper(), new BoundedMediaProcess());
        String base = "{\"format\":{\"format_name\":\"mov,mp4\",\"duration\":\"1.000001\"},\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"pix_fmt\":\"yuv420p\",\"width\":640,\"height\":360,\"avg_frame_rate\":\"30/1\"}]}";
        assertEquals(1001, processor.metadata(base.getBytes(StandardCharsets.UTF_8)).durationMillis());
        assertEquals(1001, processor.metadata(base.replace("h264", "hevc").getBytes(StandardCharsets.UTF_8)).durationMillis());
        assertEquals(1001, processor.metadata(base.replace("yuv420p", "yuv420p10le").getBytes(StandardCharsets.UTF_8)).durationMillis());
        for (String invalid : List.of(base.replace("640", "8000"), base.replace("1.000001", "601"),
                base.replace("30/1", "120/1"), base.replace("video", "data"))) {
            assertThrows(ProcessingException.class, () -> processor.metadata(invalid.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void generate(Path ffmpeg, Path target, String codec) {
        var result = new BoundedMediaProcess().run(List.of(ffmpeg.toString(), "-v", "error", "-f", "lavfi", "-i",
                "testsrc=size=320x240:rate=30", "-t", "1", "-c:v", codec, "-threads", "1", "-pix_fmt", "yuv420p", "-y", target.toString()),
                directory, System.nanoTime() + TimeUnit.SECONDS.toNanos(15));
        assertEquals(0, result.exitCode());
    }

    private List<String> helper(String action) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", nativeName("java")).toString(), "-cp",
                Path.of("target/test-classes").toAbsolutePath().toString(), ProcessFixture.class.getName(), action);
    }
    private WorkerProperties properties(String probe, String ffmpeg) {
        return new WorkerProperties(directory.toString(), probe, ffmpeg, 3, 90, 150, 5, 10, 1048576, 1048576);
    }
    static String nativeName(String name) { return System.getProperty("os.name").startsWith("Windows") ? name + ".exe" : name; }
    public static class ProcessFixture {
        public static void main(String[] arguments) throws Exception {
            switch (arguments[0]) {
                case "sleep" -> {
                    Files.writeString(Path.of("process.pid"), Long.toString(ProcessHandle.current().pid()));
                    Thread.sleep(30000);
                }
                case "flood" -> { for (int count = 0; count < 100000; count++) System.out.print("flood"); }
                case "environment" -> System.out.write((Path.of("").toAbsolutePath() + "\n"
                        + System.getenv().keySet().stream().allMatch(key -> Set.of("SYSTEMROOT", "TMP", "TEMP", "LANG").contains(key.toUpperCase(Locale.ROOT))))
                        .getBytes(StandardCharsets.UTF_8));
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
