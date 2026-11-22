package io.github.qdiaps.jsaver.downloader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.DiskSpaceInfo;
import io.github.qdiaps.jsaver.exception.InsufficientSpaceException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkspaceManager {
    private static final String TASKS_DIR_NAME = "tasks";

    private final AppConfig config;
    private final Path tasksDir;
    private final Object lock = new Object();

    private int activeWorkspaces = 0;

    public WorkspaceManager(AppConfig config) {
        this.config = Objects.requireNonNull(config);
        tasksDir = config.workDir().resolve(TASKS_DIR_NAME);
    }

    public void init() throws IOException {
        throw new UnsupportedOperationException("TODO");
    }

    public Workspace allocate(UUID taskId) throws InsufficientSpaceException, IOException {
        throw new UnsupportedOperationException("TODO");
    }

    void release(Workspace workspace) {
        throw new UnsupportedOperationException("TODO");
    }

    public void cleanupOrphans() {
        throw new UnsupportedOperationException("TODO");
    }

    public DiskSpaceInfo getDiskSpaceInfo() {
        throw new UnsupportedOperationException("TODO");
    }
}
