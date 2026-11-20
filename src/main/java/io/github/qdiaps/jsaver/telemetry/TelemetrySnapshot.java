package io.github.qdiaps.jsaver.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TelemetrySnapshot(
    Instant startedAt, Duration uptime,

    long totalRequests, long succeededRequests, long rejectedRequests, long failedRequests,
    long ignoredUpdates, long inFlightRequests,

    long usedMemoryBytes, long maxMemoryBytes,
    long usableDiskBytes, long totalDiskBytes
) {
    public TelemetrySnapshot {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(uptime, "uptime must not be null");

        if (uptime.isNegative()) {
            throw new IllegalArgumentException("uptime cannot be negative: " + uptime);
        }

        if (succeededRequests < 0 || rejectedRequests < 0 || failedRequests < 0
                || ignoredUpdates < 0 || inFlightRequests < 0) {
            throw new IllegalArgumentException(
                    "Request counters cannot be negative: succeeded=%d, rejected=%d, failed=%d, ignored=%d, inFlight=%d"
                            .formatted(succeededRequests, rejectedRequests, failedRequests, ignoredUpdates, inFlightRequests)
            );
        }

        long expectedTotal = Math.addExact(Math.addExact(succeededRequests, rejectedRequests), failedRequests);
        if (totalRequests != expectedTotal) {
            throw new IllegalArgumentException(
                    "totalRequests (%d) must equal succeeded + rejected + failed (%d)"
                            .formatted(totalRequests, expectedTotal)
            );
        }

        if (usedMemoryBytes < 0 || maxMemoryBytes <= 0 || usedMemoryBytes > maxMemoryBytes) {
            throw new IllegalArgumentException(
                    "Invalid memory metrics: used=%d, max=%d".formatted(usedMemoryBytes, maxMemoryBytes)
            );
        }

        if (usableDiskBytes < 0 || totalDiskBytes < 0 || usableDiskBytes > totalDiskBytes) {
            throw new IllegalArgumentException(
                    "Invalid disk metrics: usable=%d, total=%d".formatted(usableDiskBytes, totalDiskBytes)
            );
        }
    }
}
