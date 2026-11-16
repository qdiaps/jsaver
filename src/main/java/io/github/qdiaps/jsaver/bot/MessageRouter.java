package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;

import io.github.qdiaps.jsaver.command.CommandRouter;
import io.github.qdiaps.jsaver.contract.ChatType;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MessageRouter {
    private final CommandRouter commandRouter;

    public void route(Message message) {
        var metadata = extractMetadata(message);
        var text = message.text().trim();

        if (isCommand(message)) {
            commandRouter.route(metadata, text);
            return;
        }
    }

    private MessageMetadata extractMetadata(Message message) {
        Long userId = message.senderChat() == null ? message.from().id() : null;
        long chatId = message.chat().id();
        int messageId = message.messageId();
        Integer threadId = Boolean.TRUE.equals(message.isTopicMessage()) ? message.messageThreadId() : null;
        ChatType chatType = message.chat().type() == Chat.Type.Private ? ChatType.PRIVATE : ChatType.GROUP;

        return new MessageMetadata(userId, chatId, messageId, threadId, chatType);
    }

    private boolean isCommand(Message message) {
        MessageEntity[] entities = message.entities();
        if (entities == null)
            return false;

        for (var entity : entities) {
            if (entity.offset() == 0 && entity.type() == MessageEntity.Type.bot_command)
                return true;
        }

        return false;
    }
}
