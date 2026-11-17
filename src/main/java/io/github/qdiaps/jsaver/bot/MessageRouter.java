package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;

import io.github.qdiaps.jsaver.access.AccessGuard;
import io.github.qdiaps.jsaver.command.CommandRouter;
import io.github.qdiaps.jsaver.contract.AccessDecision;
import io.github.qdiaps.jsaver.contract.ChatType;
import io.github.qdiaps.jsaver.contract.DenialReason;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MessageRouter {
    private final CommandRouter commandRouter;
    private final AccessGuard accessGuard;
    private final Sender sender;

    public void route(Message message) {
        var metadata = extractMetadata(message);

        if (!checkAccess(metadata))
            return;

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

    private boolean checkAccess(MessageMetadata metadata) {
        return switch (accessGuard.checkAccess(metadata)) {
            case AccessDecision.Allowed() -> true;
            case AccessDecision.Denied(var reason, var shouldNotify) -> {
                if (shouldNotify) {
                    var text = reason.text();
                    if (reason == DenialReason.GLOBAL_MUTE)
                        text = accessGuard.getGlobalMuteReason().orElse(text);

                    sender.sendContent(metadata, new Payload.Text(text));
                    log.info("Access denied because [{}] for chat [{}], user [{}]",
                              reason.name(), metadata.chatId(), metadata.userId());
                } else {
                    log.debug("Access denied because [{}] for chat [{}], user [{}]",
                              reason.name(), metadata.chatId(), metadata.userId());
                }
                yield false;
            }
        };
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
