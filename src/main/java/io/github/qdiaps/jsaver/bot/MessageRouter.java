package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MessageRouter {
    private final TelegramBot bot;

    public void route(Message message) {
        long chatId = message.chat().id();
        long userId = message.from() != null ? message.from().id() : chatId;
        String text = message.text().trim();

        log.info("Received message from user [{}] in chat [{}]: {}", userId, chatId, text);

        var request = new SendMessage(chatId, text);
        var response = bot.execute(request);
        if (!response.isOk())
            log.error("Failed to send message: {}", response.description());
    }
}
