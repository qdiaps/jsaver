package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.model.Update;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Receiver {
    private final MessageRouter router;

    public void handle(Update update) {
        try {
            var message = update.message();
            if (message != null && message.text() != null) {
                router.route(message);
            }
        } catch (Exception e) {
            log.error("Unexpected error handling update {}: {}", update.updateId(), e.getMessage(), e);
        }
    }
}
