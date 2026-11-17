package com.creator.video.infrastructure.media;

import com.creator.video.application.processing.ProcessingException;
import com.creator.video.application.processing.ProcessingWorker;
import com.creator.video.domain.ProcessingFailure;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BoundedMediaProcess {
    public record Result(int exitCode, byte[] stdout) { }

    public Result run(List<String> command, Path directory, long deadline) {
        ProcessingWorker.requireTime(deadline);
        Process process = null;
        Thread stdoutReader = null;
        Thread stderrReader = null;
        var stdout = new ByteArrayOutputStream();
        var overflow = new AtomicBoolean();
        var readFailure = new AtomicBoolean();
        try {
            var builder = new ProcessBuilder(command).directory(directory.toFile());
            String systemRoot = System.getenv("SystemRoot");
            builder.environment().clear();
            if (systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
            builder.environment().put("TMP", directory.toString());
            builder.environment().put("TEMP", directory.toString());
            builder.environment().put("LANG", "C");
            process = builder.start();
            process.getOutputStream().close();
            stdoutReader = drain(process.getInputStream(), stdout, overflow, readFailure);
            stderrReader = drain(process.getErrorStream(), null, overflow, readFailure);
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                ProcessingWorker.requireTime(deadline);
                if (overflow.get()) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
            }
            stdoutReader.join(1000);
            stderrReader.join(1000);
            ProcessingWorker.requireTime(deadline);
            if (stdoutReader.isAlive() || stderrReader.isAlive() || overflow.get() || readFailure.get()) {
                throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
            }
            return new Result(process.exitValue(), stdout.toByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(ProcessingFailure.PROCESS_TIMEOUT);
        } catch (IOException exception) {
            throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
        } finally {
            if (process != null) {
                var descendants = process.descendants().toList();
                descendants.forEach(ProcessHandle::destroyForcibly);
                if (process.isAlive()) process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                try { process.getInputStream().close(); } catch (IOException ignored) { }
                try { process.getErrorStream().close(); } catch (IOException ignored) { }
            }
        }
    }

    private Thread drain(InputStream input, ByteArrayOutputStream output, AtomicBoolean overflow, AtomicBoolean failure) {
        return Thread.ofPlatform().daemon().name("media-output").start(() -> {
            int total = 0;
            try (input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (total <= 65536 && count <= 65536 - total) {
                        if (output != null) output.write(buffer, 0, count);
                        total += count;
                    } else {
                        overflow.set(true);
                    }
                }
            } catch (IOException exception) { failure.set(true); }
        });
    }
}
