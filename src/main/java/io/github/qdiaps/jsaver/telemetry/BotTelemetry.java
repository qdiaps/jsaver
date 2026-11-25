package io.github.qdiaps.jsaver.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.downloader.WorkspaceManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotTelemetry {
    private final Instant startedAt;
    private final long startNanoTime;
    private final WorkspaceManager workspace;

    private final LongAdder succeededRequests = new LongAdder();
    private final LongAdder rejectedRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder ignoredUpdates = new LongAdder();
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);

    public BotTelemetry(AppConfig config, WorkspaceManager workspace) {
        Objects.requireNonNull(config);
        this.workspace = Objects.requireNonNull(workspace);

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

        var uptime = Duration.ofNanos(System.nanoTime() - startNanoTime);

        return new TelemetrySnapshot(
            startedAt, uptime,
            total, succeeded, rejected, failed,
            ignoredUpdates.sum(), inFlightRequests.get(),
            usedMemoryBytes, maxMemoryBytes,
            workspace.getDiskSpaceInfo()
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
