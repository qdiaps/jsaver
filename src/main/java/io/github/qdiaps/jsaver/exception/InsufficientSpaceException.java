package io.github.qdiaps.jsaver.exception;

public class InsufficientSpaceException extends Exception {
    private final long requiredBytes;
    private final long availableBytes;
    private final int activeWorkspaces;
    private final long resersePerTaskBytes;

    public InsufficientSpaceException(long requiredBytes, long availableBytes,
        int activeWorkspaces, long resersePerTaskBytes
    ) {
        super(String.format(
            "Insufficient disk space: required %d MB, available %d MB (activeWorkspaces=%d, reservePerTask=%d MB)",
            requiredBytes / (1024 * 1024),
            availableBytes / (1024 * 1024),
            activeWorkspaces,
            resersePerTaskBytes / (1024 * 1024)
        ));

        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
        this.activeWorkspaces = activeWorkspaces;
        this.resersePerTaskBytes = resersePerTaskBytes;
    }

    public long requiredBytes() {
        return requiredBytes;
    }

    public long availableBytes() {
        return availableBytes;
    }

    public int activeWorkspaces() {
        return activeWorkspaces;
    }

    public long reservePerTaskBytes() {
        return resersePerTaskBytes;
    }
}
