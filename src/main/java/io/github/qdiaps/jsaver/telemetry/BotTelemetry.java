package io.github.qdiaps.jsaver.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import io.github.qdiaps.jsaver.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotTelemetry {
    private final Instant startedAt;
    private final long startNanoTime;
    private final Path workDir;

    private final LongAdder succeededRequests = new LongAdder();
    private final LongAdder rejectedRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder ignoredUpdates = new LongAdder();
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);

    public BotTelemetry(AppConfig config) {
        Objects.requireNonNull(config);

        workDir = config.workDir();
        startedAt = Instant.now();
        startNanoTime = System.nanoTime();
    }

    public TelemetrySnapshot snapshot() {
        var succeeded = succeededRequests.sum();
        var rejected = rejectedRequests.sum();
        var failed = failedRequests.sum();
        var total = succeeded + rejected + failed;

        var runtime = Runtime.getRuntime();
        var maxMemoryBytes = runtime.maxMemory();
        var usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory();

        var usableDiskBytes = 0L;
        var totalDiskBytes = 0L;
        try {
            var store = Files.getFileStore(workDir);
            usableDiskBytes = store.getUsableSpace();
            totalDiskBytes = store.getTotalSpace();
        } catch (IOException e) {
            log.warn("Failed to read disk space for {}: {}", workDir, e.getMessage());
        }

        var uptime = Duration.ofNanos(System.nanoTime() - startNanoTime);

        return new TelemetrySnapshot(
            startedAt, uptime,
            total, succeeded, rejected, failed,
            ignoredUpdates.sum(), inFlightRequests.get(),
            usedMemoryBytes, maxMemoryBytes,
            usableDiskBytes, totalDiskBytes
        );
    }

    public void onRequestStarted() {
        inFlightRequests.incrementAndGet();
        log.debug("Telemetry: request started");
    }

    public void onRequestFinished() {
        inFlightRequests.decrementAndGet();
        log.debug("Telemetry: request finished");
    }

    public void recordSuccess() {
        succeededRequests.increment();
        log.debug("Telemetry: record success");
    }

    public void recordRejected() {
        rejectedRequests.increment();
        log.debug("Telemetry: record rejected");
    }

    public void recordFailure() {
        failedRequests.increment();
        log.debug("Telemetry: record failure");
    }

    public void recordIgnored() {
        ignoredUpdates.increment();
        log.debug("Telemetry: record ignored");
    }
}
