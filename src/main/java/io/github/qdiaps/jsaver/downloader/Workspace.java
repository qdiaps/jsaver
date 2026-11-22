package io.github.qdiaps.jsaver.downloader;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Workspace implements AutoCloseable {
    private final UUID taskId;
    private final Path path;
    private final WorkspaceManager manager;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    Workspace(UUID taskId, Path path, WorkspaceManager manager) {
        this.taskId = Objects.requireNonNull(taskId);
        this.path = Objects.requireNonNull(path);
        this.manager = Objects.requireNonNull(manager);
    }

    public UUID taskId() {
        return taskId;
    }

    public Path path() {
        return path;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true))
            manager.release(this);
    }
}
