package io.github.qdiaps.jsaver.cli;

public record ExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        boolean timedOut
) {
    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }
}
