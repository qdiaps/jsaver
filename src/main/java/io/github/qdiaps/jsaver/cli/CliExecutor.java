package io.github.qdiaps.jsaver.cli;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CliExecutor {
    private final File defaultWorkDir;

    public CliExecutor(File defaultWorkDir) {
        this.defaultWorkDir = defaultWorkDir;
        if (!defaultWorkDir.exists() && !defaultWorkDir.mkdirs()) {
            log.warn("Could not create working directory: {}", defaultWorkDir.getAbsolutePath());
        }
    }

    public ExecutionResult execute(List<String> command, Duration timeout) {
        return execute(command, defaultWorkDir, Map.of(), timeout);
    }

    public ExecutionResult execute(List<String> command, File workDir, Map<String, String> extraEnv, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command list cannot be empty");
        }

        log.debug("Executing command in [{}]: {}", workDir.getAbsolutePath(), String.join(" ", command));
        Instant start = Instant.now();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        if (extraEnv != null && !extraEnv.isEmpty()) {
            pb.environment().putAll(extraEnv);
        }

        Process process;
        try {
            process = pb.start();
        } catch (Exception e) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            log.error("Failed to start process: {}", e.getMessage(), e);
            return new ExecutionResult(-1, "", "Failed to start process: " + e.getMessage(), duration, false);
        }

        try (ProcessScope ignored = new ProcessScope(process);
             ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getInputStream()),
                    virtualExecutor
            );

            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getErrorStream()),
                    virtualExecutor
            );

            boolean timedOut = false;
            int exitCode = -1;

            try {
                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    timedOut = true;
                    log.warn("Process timed out after {}s. Forcibly terminating: {}", timeout.toSeconds(), command.getFirst());
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
                exitCode = timedOut ? -1 : process.exitValue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                timedOut = true;
                process.destroyForcibly();
                log.warn("Execution interrupted, killed process: {}", command.getFirst());
            }

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();

            log.debug("Finished execution in {}ms, exitCode={}, timedOut={}", durationMs, exitCode, timedOut);
            return new ExecutionResult(exitCode, stdout, stderr, durationMs, timedOut);
        }
    }

    private String readStream(InputStream inputStream) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
            }
        } catch (Exception e) {
            log.debug("Error reading process stream: {}", e.getMessage());
        }
        return output.toString();
    }

    private record ProcessScope(Process process) implements AutoCloseable {
        @Override
        public void close() {
            if (process != null && process.isAlive()) {
                log.debug("Destroying process still running on scope exit");
                process.destroyForcibly();
            }
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (Exception ignored) {
                }
                try {
                    process.getErrorStream().close();
                } catch (Exception ignored) {
                }
                try {
                    process.getOutputStream().close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
