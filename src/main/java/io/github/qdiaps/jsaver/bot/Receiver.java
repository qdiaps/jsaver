package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Receiver {
    private final MessageRouter router;

    public void handle(Update update) {
        try {
            if (update.message() == null)
                return;

            Message message = update.message();
            if (!isValid(message))
                return;

            router.route(message);
        } catch (Exception e) {
            log.error("Unexpected error handling update {}: {}", update.updateId(), e.getMessage(), e);
        }
    }

    private boolean isValid(Message message) {
        if (message.chat().type() == Chat.Type.channel
            || Boolean.TRUE.equals(message.isAutomaticForward())
            || message.from() == null) {
            log.debug("Ignoring system/channel message in chat [{}], message [{}]",
                     message.chat().id(), message.messageId());
            return false;
        }

        if (Boolean.TRUE.equals(message.from().isBot())
            && message.senderChat() == null) {
            log.debug("Ignoring bot message from [{}] in chat [{}]",
                     message.from().id(), message.chat().id());
            return false;
        }

        if (message.text() == null
            || message.text().isBlank()) {
            log.debug("Ignoring non-text or blank message in chat [{}], message [{}]",
                     message.chat().id(), message.messageId());
            return false;
        }

        return true;
    }
}
