package io.github.qdiaps.jsaver.bot;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;

import io.github.qdiaps.jsaver.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Bot {
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final Receiver receiver;
    private final TelegramBot bot;
    private final ExecutorService updateExecutor;

    public Bot(TelegramBot bot, Receiver receiver) {
        this.bot = Objects.requireNonNull(bot);
        this.receiver = Objects.requireNonNull(receiver);

        var threadFactory = Thread.ofVirtual()
            .name("tg-update-", 0)
            .factory();
        updateExecutor = Executors.newThreadPerTaskExecutor(threadFactory);
    }

    public static TelegramBot createBot(AppConfig config) {
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

    public void start() {
        if (bot == null) {
            log.error("TelegramBot instance is null. Skipping polling initialization.");
            return;
        }

        log.info("Starting Telegram Bot long polling...");

        bot.setUpdatesListener(
            updates -> {
                var lastConfirmedId = UpdatesListener.CONFIRMED_UPDATES_NONE;
                for (var update : updates) {
                    try {
                        updateExecutor.execute(() -> receiver.handle(update));
                        lastConfirmedId = update.updateId();
                    } catch (RejectedExecutionException e) {
                        log.warn("Rejected update [{}] because bot is shutting down", update.updateId());
                        return lastConfirmedId;
                    }
                }
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            },
            e -> {
                if (e.response() != null) {
                    log.error("Telegram API error response: {} - {}",
                              e.response().errorCode(), e.response().description());
                } else {
                    log.error("Telegram network/connection error: {}", e.getMessage());
                }
            }
        );

        log.info("Telegram Bot listener registered successfully.");
    }

    public void stop() {
        if (bot == null)
            return;

        log.info("Stopping Telegram updates listener...");
        bot.removeGetUpdatesListener();

        log.info("Shutting down update executor (waiting up to {}s for active tasks)...", SHUTDOWN_TIMEOUT_SECONDS);
        updateExecutor.shutdown();

        try {
            if (!updateExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Active tasks did not finish within {}s, forcing interruption...", SHUTDOWN_TIMEOUT_SECONDS);
                List<Runnable> dropped = updateExecutor.shutdownNow();
                log.warn("Forced shutdown initiated; {} queued tasks were not started", dropped.size());

                if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS))
                    log.error("Update executor did not terminate after forced shutdown");
            } else {
                log.info("All active tasks completed gracefully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for update executor termination, forcing shutdownNow");
            updateExecutor.shutdownNow();
        } finally {
            bot.shutdown();
            log.info("Telegram HTTP client shut down");
        }
    }
}
