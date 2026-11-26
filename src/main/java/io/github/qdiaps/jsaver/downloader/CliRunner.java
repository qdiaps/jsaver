package io.github.qdiaps.jsaver.downloader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.CliResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CliRunner {
    private record StreamOutput(String content, boolean truncated) {}
    private record ProcessScope(Process process) implements AutoCloseable {
        @Override
        public void close() {
            if (process != null && process.isAlive()) {
                log.debug("Destroying process tree still running on scope exit: pid={}", process.pid());
                killProcessTree(process);
            }
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }

        private void closeQuietly(AutoCloseable closeable) {
            try {
                if (closeable != null)
                    closeable.close();
            } catch (Exception ignored) {}
        }
    }

    private static final int BUFFER_SIZE = 4096;
    private static final long TERMINATION_WAIT_SECONDS = 5L;

    private final AppConfig config;
    private final int maxOutputChars;

    public CliRunner(AppConfig config) {
        this.config = Objects.requireNonNull(config);
        maxOutputChars = Math.multiplyExact(config.cliOutputLimitKb(), 1024);
    }

    public CliResult execute(CliTool tool, List<String> args, Path workDir) throws InterruptedException {
        return execute(tool, args, workDir, Duration.ofSeconds(config.commandTimeoutSeconds()));
    }

    public CliResult execute(CliTool tool, List<String> args, Path workDir, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(tool);
        Objects.requireNonNull(args);
        Objects.requireNonNull(workDir);
        Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("timeout must be > 0");

        var command = new ArrayList<String>(args.size() + 1);
        command.add(tool.binaryName());
        command.addAll(args);

        log.debug("Executing CLI command: tool={}, workDir={}, cmd={}",
            tool.binaryName(), workDir.toAbsolutePath(), String.join(" ", command));
        var start = System.nanoTime();

        var pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());

        Process process;
        try {
            process = pb.start();
        } catch (Exception e) {
            var durationMs = elapsedMillis(start);
            log.error("Failed to start CLI process: tool={}, workDir={}",
                tool.binaryName(), workDir.toAbsolutePath(), e);
            return new CliResult(-1, "", "Failed to start process: " + e.getMessage(), durationMs, false, false);
        }

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
             ProcessScope ignored = new ProcessScope(process)) {

            CompletableFuture<StreamOutput> stdoutFuture = CompletableFuture.supplyAsync(
                    () -> readHead(process.getInputStream(), maxOutputChars),
                    virtualExecutor
            );

            CompletableFuture<StreamOutput> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readTail(process.getInputStream(), maxOutputChars),
                    virtualExecutor
            );

            var timedOut = false;
            var exitCode = -1;

            try {
                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    timedOut = true;
                    log.warn("CLI process timed out, terminating process tree: tool={}, timeoutSec={}",
                        tool.binaryName(), timeout.toSeconds());
                    killProcessTree(process);
                }
                exitCode = timedOut ? -1 : process.exitValue();
            } catch (InterruptedException e) {
                log.warn("CLI execution interrupted, terminating process tree: tool={}", tool.binaryName());
                killProcessTree(process);
                Thread.currentThread().interrupt();
                throw e;
            }

            var stdoutOut = stdoutFuture.join();
            var stderrOut = stderrFuture.join();
            var durationMs = elapsedMillis(start);
            var truncated = stdoutOut.truncated() || stderrOut.truncated();

            log.debug("CLI command finished: tool={}, exitCode={}, durationMs={}, timedOut={}, truncated={}",
                tool.binaryName(), exitCode, durationMs, timedOut, truncated);

            return new CliResult(exitCode, stdoutOut.content(), stderrOut.content(), durationMs, timedOut, truncated);
        }
    }

    private StreamOutput readHead(InputStream inputStream, int maxChars) {
        var output = new StringBuilder(Math.min(maxChars, BUFFER_SIZE));
        var truncated = false;

        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            var buffer = new char[BUFFER_SIZE];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                var remaining = maxChars - output.length();
                if (remaining > 0) {
                    var toAppend = Math.min(read, remaining);
                    output.append(buffer, 0, toAppend);
                    if (toAppend < read)
                        truncated = true;
                } else {
                    truncated = true;
                }
            }
        } catch (IOException e) {
            log.debug("Stopped reading stdout stream: {}", e.getMessage());
        }

        return new StreamOutput(output.toString(), truncated);
    }

    private StreamOutput readTail(InputStream inputStream, int maxChars) {
        var output = new StringBuilder(Math.min(maxChars, BUFFER_SIZE));
        var softLimit = Math.multiplyExact(maxChars, 2);
        var truncated = false;

        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            var buffer = new char[BUFFER_SIZE];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
                if (output.length() > softLimit) {
                    output.delete(0, output.length() - maxChars);
                    truncated = true;
                }
            }
        } catch (IOException e) {
            log.debug("Stopped reading stderr stream: {}", e.getMessage());
        }

        if (output.length() > maxChars) {
            output.delete(0, output.length() - maxChars);
            truncated = true;
        }

        return new StreamOutput(output.toString(), truncated);
    }

    private static void killProcessTree(Process process) {
        if (process == null)
            return;

        var descendants = process.descendants().toList();
        for (var child : descendants) {
            if (child.isAlive()) {
                log.debug("Forcibly terminating child process: pid={}, command={}",
                    child.pid(), child.info().command().orElse("unknown"));
                child.destroyForcibly();
            }
        }

        if (process.isAlive())
            process.destroyForcibly();

        try {
            process.waitFor(TERMINATION_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
