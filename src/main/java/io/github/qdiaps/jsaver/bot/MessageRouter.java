package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;

import io.github.qdiaps.jsaver.contract.ChatType;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MessageRouter {
    private final Sender sender;

    public void route(Message message) {
        var metadata = extractMetadata(message);
        var text = message.text().trim();
        log.debug("Received message from user [{}] in chat [{}], message [{}]: {}",
                  metadata.userId(), metadata.chatId(), metadata.messageId(), text);

        var textPayload = new Payload.Text(text);
        sender.sendContent(metadata, textPayload);
    }

    private MessageMetadata extractMetadata(Message message) {
        Long userId = message.senderChat() == null ? message.from().id() : null;
        long chatId = message.chat().id();
        int messageId = message.messageId();
        Integer threadId = Boolean.TRUE.equals(message.isTopicMessage()) ? message.messageThreadId() : null;
        ChatType chatType = message.chat().type() == Chat.Type.Private ? ChatType.PRIVATE : ChatType.GROUP;

        return new MessageMetadata(userId, chatId, messageId, threadId, chatType);
    }
}
