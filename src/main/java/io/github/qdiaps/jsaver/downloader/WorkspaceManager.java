package io.github.qdiaps.jsaver.downloader;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.DiskSpaceInfo;
import io.github.qdiaps.jsaver.exception.InsufficientSpaceException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkspaceManager {
    private final Path tasksDir;
    private final Object lock = new Object();
    private final long reservePerTaskBytes;
    private final long safetyMarginBytes;

    private int activeWorkspaces = 0;

    public WorkspaceManager(AppConfig config) {
        Objects.requireNonNull(config);

        tasksDir = config.workDir().resolve(config.tasksDirName());
        reservePerTaskBytes = Math.multiplyExact(config.taskDiskReserveMb(), 1024L * 1024L);
        safetyMarginBytes = Math.multiplyExact(config.diskSafetyMarginMb(), 1024L * 1024L);
    }

    public void init() throws IOException {
        Files.createDirectories(tasksDir);
        if (!Files.isWritable(tasksDir))
            throw new IOException("Tasks directory is not writable: " + tasksDir.toAbsolutePath());
    }

    public Workspace allocate(UUID taskId) throws InsufficientSpaceException, IOException {
        synchronized (lock) {
            Objects.requireNonNull(taskId);

            var availableBytes = Files.getFileStore(tasksDir).getUsableSpace();
            var requiredBytes = safetyMarginBytes + Math.multiplyExact((long)activeWorkspaces + 1, reservePerTaskBytes);

            if (availableBytes < requiredBytes)
                throw new InsufficientSpaceException(requiredBytes, availableBytes, activeWorkspaces, reservePerTaskBytes);

            var taskDir = tasksDir.resolve(taskId.toString());
            Files.createDirectories(taskDir);
            activeWorkspaces++;

            log.debug("Allocated workspace: taskId={}, path={}, activeWorkspaces={}", taskId, taskDir, activeWorkspaces);
            return new Workspace(taskId, taskDir, this);
        }
    }

    void release(Workspace workspace) {
        deleteRecursively(workspace.path());

        synchronized (lock) {
            if (activeWorkspaces > 0)
                activeWorkspaces--;
        }

        log.debug("Released workspace: taskId={}, activeWorkspaces={}", workspace.taskId(), activeWorkspaces);
    }

    public void cleanupOrphans() {
        if (Files.notExists(tasksDir))
            return;

        List<Path> entries;
        try (var stream = Files.list(tasksDir)) {
            entries = stream.toList();
        } catch (IOException e) {
            log.error("Failed to scan tasks directory for orphans: path={}", tasksDir, e);
            return;
        }

        if (entries.isEmpty()) {
            return;
        }

        int removedDirs = 0;
        int removedFiles = 0;

        for (var entry : entries) {
            if (Files.isDirectory(entry)) {
                long fileCount = 0;
                long sizeBytes = 0;

                try (var walk = Files.walk(entry)) {
                    for (var p : walk.toList()) {
                        if (Files.isRegularFile(p)) {
                            fileCount++;
                            sizeBytes += safeFileSize(p);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to inspect orphaned directory before cleanup: path={}", entry, e);
                }

                log.warn("Found orphaned workspace directory on startup: name={}, files={}, sizeMb={}",
                    entry.getFileName(), fileCount, sizeBytes / (1024L * 1024L));
                deleteRecursively(entry);
                removedDirs++;
            } else {
                long sizeBytes = safeFileSize(entry);
                log.warn("Found unexpected file in tasks directory on startup: name={}, sizeBytes={}",
                    entry.getFileName(), sizeBytes);
                try {
                    Files.deleteIfExists(entry);
                    removedFiles++;
                } catch (IOException e) {
                    log.warn("Failed to delete unexpected file during orphan cleanup: path={}", entry, e);
                }
            }
        }

        log.info("Orphan cleanup finished: removedDirs={}, removedFiles={}", removedDirs, removedFiles);
    }

    public DiskSpaceInfo getDiskSpaceInfo() {
        int active;
        synchronized (lock) {
            active = activeWorkspaces;
        }

        var reservedBytes = Math.multiplyExact((long)active, reservePerTaskBytes);
        try {
            var store = Files.getFileStore(tasksDir);
            var totalBytes = store.getTotalSpace();
            var usableBytes = store.getUsableSpace();

            var effectiveFreeBytes = Math.max(0L, usableBytes - reservedBytes - safetyMarginBytes);
            var availableTaskSlots = (int)(effectiveFreeBytes / reservePerTaskBytes);

            return new DiskSpaceInfo(totalBytes, usableBytes, effectiveFreeBytes, reservedBytes,
                reservePerTaskBytes, active, availableTaskSlots
            );
        } catch (IOException e) {
            log.error("Failed to read disk space info: path={}", tasksDir, e);
            return new DiskSpaceInfo(0, 0, 0, reservedBytes, reservePerTaskBytes, active, 0);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root))
            return;

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Objects.requireNonNull(file);

                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        log.warn("Failed to delete file during workspace cleanup: {}", file, e);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    Objects.requireNonNull(file);
                    log.warn("Failed to access file during workspace cleanup: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    Objects.requireNonNull(dir);

                    if (exc != null)
                        log.warn("Error occurred while traversing directory: {}", dir, exc);

                    try {
                        Files.deleteIfExists(dir);
                    } catch (IOException e) {
                        log.warn("Failed to delete directory during workspace cleanup: {}", dir, e);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk workspace directory for cleanup: {}", root, e);
        }
    }

    private long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.debug("Failed to read file size: path={}", path, e);
            return 0;
        }
    }
}
