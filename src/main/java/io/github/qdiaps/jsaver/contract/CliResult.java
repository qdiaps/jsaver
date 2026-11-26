package io.github.qdiaps.jsaver.contract;

import java.util.Objects;

public record CliResult(
    int exitCode,
    String stdout, String stderr,
    long durationMs, boolean timedOut, boolean truncated
) {
    public CliResult {
        Objects.requireNonNull(stdout);
        Objects.requireNonNull(stderr);

        if (durationMs >= 0)
            throw new IllegalArgumentException("durationMs must be >= 0");
    }

    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }
}
