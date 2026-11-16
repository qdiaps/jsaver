package io.github.qdiaps.jsaver.command;

import io.github.qdiaps.jsaver.contract.MessageMetadata;

import java.util.List;

public interface Command {
    String name();
    String description();
    void execute(MessageMetadata metadata, List<String> args);
}
