package io.github.qdiaps.jsaver.contract;

public record MessageMetadata(
    Long userId, long chatId, int messageId, Integer threadId, ChatType chatType
) {}
