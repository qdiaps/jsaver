package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import io.github.qdiaps.jsaver.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public class Bot {
    private final AppConfig config;
    private final Receiver receiver;
    private final MessageRouter router;

    private TelegramBot bot;

    public Bot(AppConfig config) {
        this.config = Objects.requireNonNull(config);

        bot = createBot();

        router = new MessageRouter(bot);
        receiver = new Receiver(router);
    }

    public void start() {
        if (bot == null) {
            log.error("TelegramBot instance is null. Skipping polling initialization.");
            return;
        }

        log.info("Starting Telegram Bot long polling...");

        bot.setUpdatesListener(
            updates -> {
                for (var update : updates)
                    Thread.ofVirtual().name("tg-update-", update.updateId()).start(() -> receiver.handle(update));
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            },
            e -> {
                if (e.response() != null) {
                    log.error("Telegram API error response: {} - {}", e.response().errorCode(), e.response().description());
                } else {
                    log.error("Telegram network/connection error: {}", e.getMessage());
                }
            }
        );

        log.info("Telegram Bot listener registered successfully.");
    }

    public void stop() {
        if (bot != null) {
            log.info("Shutting down Telegram Bot...");
            bot.shutdown();
        }
    }

    private TelegramBot createBot() {
        if (config.botToken() == null || config.botToken().isBlank()) {
            log.error("TELEGRAM_BOT_TOKEN is not configured!");
            return null;
        }

        var builder = new TelegramBot.Builder(config.botToken());
        if (config.hasCustomBotApiUrl()) {
            log.info("Using custom Telegram Bot API Server URL: {}", config.botApiUrl());
            builder.apiUrl(config.botApiUrl());
            String fileUrl = config.botApiUrl().replaceAll("/bot/?$", "") + "/file/bot";
            builder.fileApiUrl(fileUrl);
        }

        return builder.build();
    }
}
