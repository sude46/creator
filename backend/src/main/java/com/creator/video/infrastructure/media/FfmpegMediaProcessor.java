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
    public ProcessedMedia processWithHls(Path source, Path cover, Path hlsDirectory, long deadlineNanos) {
        var metadata = process(source, cover, deadlineNanos);
        return new ProcessedMedia(metadata, hls(source, hlsDirectory, metadata, deadlineNanos));
    }

    private List<ProcessedMedia.HlsVariant> hls(Path source, Path hlsDirectory, MediaMetadata metadata, long deadlineNanos) {
        try {
            Files.createDirectories(hlsDirectory);
            var variants = new ArrayList<ProcessedMedia.HlsVariant>();
            for (var rendition : ladder(metadata.width(), metadata.height())) {
                var directory = hlsDirectory.resolve(rendition.name);
                Files.createDirectories(directory);
                var command = List.of(ffmpeg.toString(), "-hide_banner", "-v", "error", "-nostdin", "-y",
                        "-protocol_whitelist", "file", "-format_whitelist", "mov", "-enable_drefs", "0",
                        "-use_absolute_path", "0", "-threads", "1", "-i", source.toAbsolutePath().toString(),
                        "-map", "0:v:0", "-map", "0:a:0?", "-vf",
                        "scale=" + rendition.width + ":" + rendition.height + ":flags=lanczos",
                        "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p",
                        "-crf", "23", "-maxrate", rendition.bitrate + "k", "-bufsize", (rendition.bitrate * 2) + "k",
                        "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                        "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "48000",
                        "-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod",
                        "-hls_flags", "independent_segments",
                        "-hls_segment_filename", directory.resolve("seg-%05d.ts").toString(),
                        directory.resolve("index.m3u8").toString());
                var result = processes.run(command, source.getParent(), deadlineNanos);
                if (result.exitCode() != 0) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
                var playlist = directory.resolve("index.m3u8");
                if (!Files.isRegularFile(playlist) || Files.size(playlist) < 32) {
                    throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
                }
                variants.add(new ProcessedMedia.HlsVariant(rendition.name, rendition.bitrate,
                        rendition.width, rendition.height, Files.readString(playlist, java.nio.charset.StandardCharsets.UTF_8)));
            }
            return variants;
        } catch (ProcessingException exception) { throw exception; }
        catch (Exception exception) { throw new ProcessingException(ProcessingFailure.PROCESS_FAILED); }
    }

    private List<Rendition> ladder(int width, int height) {
        var renditions = new ArrayList<Rendition>();
        int[] heights = {1080, 720, 480, 360};
        int[] bitrates = {4500, 2500, 1200, 700};
        for (int index = 0; index < heights.length && renditions.size() < 3; index++) {
            if (height >= heights[index]) renditions.add(rendition(width, height, heights[index], bitrates[index]));
        }
        if (renditions.isEmpty()) renditions.add(rendition(width, height, Math.max(2, (height / 2) * 2), 700));
        return renditions;
    }

    private Rendition rendition(int sourceWidth, int sourceHeight, int targetHeight, int bitrate) {
        int height = Math.max(2, (targetHeight / 2) * 2);
        int width = Math.max(2, ((int) Math.round((double) sourceWidth * height / sourceHeight) / 2) * 2);
        return new Rendition(height + "p", width, height, bitrate);
    }

    private record Rendition(String name, int width, int height, int bitrate) { }

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
