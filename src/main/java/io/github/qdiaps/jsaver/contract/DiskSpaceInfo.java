package io.github.qdiaps.jsaver.contract;

public record DiskSpaceInfo(
    long totalBytes, long usableBytes, long effectiveFreeBytes,
    long reservedBytes, long reservePerTaskBytes,
    int activeWorkspaces, int availableTaskSlots
) {
    public DiskSpaceInfo {
        if (totalBytes < 0)
            throw new IllegalArgumentException("totalBytes must be >= 0");
        if (usableBytes < 0)
            throw new IllegalArgumentException("usableBytes must be >= 0");
        if (effectiveFreeBytes < 0)
            throw new IllegalArgumentException("effectiveFreeBytes must be >= 0");
        if (reservedBytes < 0)
            throw new IllegalArgumentException("reservedBytes must be >= 0");
        if (reservePerTaskBytes < 0)
            throw new IllegalArgumentException("reservePerTaskBytes must be >= 0");
        if (activeWorkspaces < 0)
            throw new IllegalArgumentException("activeWorkspaces must be >= 0");
        if (availableTaskSlots < 0)
            throw new IllegalArgumentException("availableTaskSlots must be >= 0");
    }
}
