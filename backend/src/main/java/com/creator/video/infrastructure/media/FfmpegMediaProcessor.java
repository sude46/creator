package com.creator.video.infrastructure.media;

import com.creator.video.application.processing.*;
import com.creator.video.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FfmpegMediaProcessor implements MediaProcessor {
    private final WorkerProperties properties;
    private final ObjectMapper json;
    private final BoundedMediaProcess processes;
    private final Path probe;
    private final Path ffmpeg;

    public FfmpegMediaProcessor(WorkerProperties properties, ObjectMapper json, BoundedMediaProcess processes) {
        this.properties = properties;
        this.json = json;
        this.processes = processes;
        probe = executable(properties.ffprobe());
        ffmpeg = executable(properties.ffmpeg());
    }

    private Path executable(String value) {
        var path = Path.of(value);
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!path.isAbsolute() || !Files.isRegularFile(path) || name.endsWith(".cmd") || name.endsWith(".bat")) {
            throw new IllegalStateException("Configure absolute paths to native FFprobe and FFmpeg executables");
        }
        return path.normalize();
    }

    @Override
    public void verify() {
        for (var executable : List.of(probe, ffmpeg)) {
            var result = processes.run(List.of(executable.toString(), "-version"), executable.getParent(),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
            if (result.exitCode() != 0) throw new IllegalStateException("Cannot execute configured media tools");
        }
    }

    @Override
    public MediaMetadata process(Path source, Path cover, long deadlineNanos) {
        var probeCommand = new ArrayList<>(List.of(probe.toString(), "-v", "error", "-protocol_whitelist", "file",
                "-format_whitelist", "mov", "-enable_drefs", "0", "-use_absolute_path", "0", "-threads", "1",
                "-show_format", "-show_streams", "-of", "json", source.toAbsolutePath().toString()));
        var result = processes.run(probeCommand, source.getParent(), deadline(deadlineNanos, properties.probeSeconds()));
        if (result.exitCode() != 0) throw new ProcessingException(ProcessingFailure.INVALID_MEDIA);
        var metadata = metadata(result.stdout());
        var command = List.of(ffmpeg.toString(), "-hide_banner", "-v", "error", "-nostdin", "-y",
                "-protocol_whitelist", "file", "-format_whitelist", "mov", "-enable_drefs", "0", "-use_absolute_path", "0",
                "-threads", "1", "-i", source.toAbsolutePath().toString(), "-map", "0:v:0", "-an", "-sn", "-dn",
                "-frames:v", "1", "-vf", "scale=640:-2", "-filter_threads", "1", "-threads", "1", "-c:v", "mjpeg",
                "-q:v", "3", "-fs", "1048576", "-f", "image2", "-update", "1", cover.toAbsolutePath().toString());
        var screenshot = processes.run(command, source.getParent(), deadline(deadlineNanos, properties.screenshotSeconds()));
        if (screenshot.exitCode() != 0) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
        try (var input = Files.newInputStream(cover)) {
            if (Files.size(cover) > 1048576 || input.read() != 255 || input.read() != 216 || input.read() != 255) {
                throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
            }
        } catch (java.io.IOException exception) { throw new ProcessingException(ProcessingFailure.PROCESS_FAILED); }
        return metadata;
    }

    public MediaMetadata metadata(byte[] output) {
        try {
            var root = json.readTree(output);
            var format = root.path("format");
            if (!Arrays.asList(format.path("format_name").asText().split(",")).contains("mp4")) unsupported();
            long duration = new BigDecimal(format.path("duration").asText()).multiply(BigDecimal.valueOf(1000))
                    .setScale(0, java.math.RoundingMode.CEILING).longValueExact();
            JsonNode video = null;
            int audioCount = 0;
            var streams = root.path("streams");
            if (!streams.isArray() || streams.size() < 1 || streams.size() > 2) unsupported();
            for (var stream : streams) {
                switch (stream.path("codec_type").asText()) {
                    case "video" -> {
                        if (video != null || !stream.path("codec_name").asText().equals("h264")
                                || !stream.path("pix_fmt").asText().equals("yuv420p")
                                || stream.path("disposition").path("attached_pic").asInt() != 0) unsupported();
                        video = stream;
                        String[] rate = stream.path("avg_frame_rate").asText().split("/");
                        if (rate.length != 2) unsupported();
                        double fps = Double.parseDouble(rate[0]) / Double.parseDouble(rate[1]);
                        if (!Double.isFinite(fps) || fps <= 0 || fps > 60) unsupported();
                    }
                    case "audio" -> {
                        if (++audioCount > 1 || !stream.path("codec_name").asText().equals("aac")
                                || stream.path("channels").asInt() < 1 || stream.path("channels").asInt() > 2
                                || stream.path("sample_rate").asInt() < 8000 || stream.path("sample_rate").asInt() > 48000) unsupported();
                    }
                    default -> unsupported();
                }
            }
            if (video == null) unsupported();
            return new MediaMetadata(duration, video.path("width").asInt(), video.path("height").asInt());
        } catch (ProcessingException exception) { throw exception; }
        catch (Exception exception) { throw new ProcessingException(ProcessingFailure.INVALID_MEDIA); }
    }

    private void unsupported() { throw new ProcessingException(ProcessingFailure.UNSUPPORTED_MEDIA); }
    private long deadline(long overall, int seconds) { return Math.min(overall, System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)); }
}
