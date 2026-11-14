package io.github.qdiaps.jsaver.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import io.github.qdiaps.jsaver.cli.CliExecutor;
import io.github.qdiaps.jsaver.cli.CliTool;
import io.github.qdiaps.jsaver.cli.ExecutionResult;
import io.github.qdiaps.jsaver.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class TelegramBotService {
    private final AppConfig config;
    private final CliExecutor cliExecutor;
    private final TelegramBot bot;

    public TelegramBotService(AppConfig config, CliExecutor cliExecutor) {
        this.config = config;
        this.cliExecutor = cliExecutor;
        this.bot = createBot(config);
    }

    private static TelegramBot createBot(AppConfig config) {
        if (config.botToken() == null || config.botToken().isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN is not configured! Bot service will not start polling.");
            return null;
        }

        TelegramBot.Builder builder = new TelegramBot.Builder(config.botToken());
        if (config.hasCustomBotApiUrl()) {
            log.info("Using custom Telegram Bot API Server URL: {}", config.botApiUrl());
            builder.apiUrl(config.botApiUrl());
            String fileUrl = config.botApiUrl().replaceAll("/bot/?$", "") + "/file/bot";
            builder.fileApiUrl(fileUrl);
        }

        if ("dev".equalsIgnoreCase(config.environment())) {
            builder.debug();
        }

        return builder.build();
    }

    public void start() {
        if (bot == null) {
            log.warn("TelegramBot instance is null. Skipping polling initialization.");
            return;
        }

        log.info("Starting Telegram Bot long polling...");
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                Thread.ofVirtual().name("tg-update-", update.updateId()).start(() -> processUpdate(update));
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, e -> {
            if (e.response() != null) {
                log.error("Telegram API error response: {} - {}", e.response().errorCode(), e.response().description());
            } else {
                log.error("Telegram network/connection error: {}", e.getMessage());
            }
        });

        log.info("Telegram Bot listener registered successfully.");
    }

    private void processUpdate(Update update) {
        try {
            Message message = update.message();
            if (message == null || message.text() == null) {
                return;
            }

            long chatId = message.chat().id();
            long userId = message.from() != null ? message.from().id() : chatId;
            String text = message.text().trim();

            log.info("Received message from user [{}] in chat [{}]: {}", userId, chatId, text);

            if (!config.isUserAllowed(userId)) {
                log.warn("Unauthorized access attempt from user ID: {}", userId);
                sendMessage(chatId, "⛔ Access denied. Your user ID is not in the whitelist.");
                return;
            }

            handleCommand(chatId, text);
        } catch (Exception e) {
            log.error("Unexpected error handling update {}: {}", update.updateId(), e.getMessage(), e);
        }
    }

    private void handleCommand(long chatId, String text) {
        if (text.startsWith("/start")) {
            sendWelcome(chatId);
        } else if (text.startsWith("/status") || text.startsWith("/tools")) {
            sendToolsStatus(chatId);
        } else if (text.startsWith("/help")) {
            sendHelp(chatId);
        } else if (text.startsWith("/exec ")) {
            handleCliExec(chatId, text.substring("/exec ".length()).trim());
        } else {
            sendMessage(chatId, "Unknown command or unsupported input. Send /help for available options.");
        }
    }

    private void sendWelcome(long chatId) {
        String msg = "👋 *Welcome to jsaver Bot!*\n\n" +
                "Commands:\n" +
                "• /status - Check CLI utilities availability\n" +
                "• /help - Display help message\n" +
                "• /exec `<tool>` `<args>` - Execute authorized CLI command";
        sendMarkdown(chatId, msg);
    }

    private void sendHelp(long chatId) {
        String msg = "ℹ️ *jsaver Bot Help*\n\n" +
                "Configured environment: `" + config.environment() + "`\n" +
                "Work directory: `" + config.workDir().toString() + "`\n" +
                "Local API: `" + (config.hasCustomBotApiUrl() ? "enabled" : "disabled") + "`\n\n" +
                "Supported tools: yt-dlp, gallery-dl, ffmpeg, ffprobe, quickjs, python3.";
        sendMarkdown(chatId, msg);
    }

    private void sendToolsStatus(long chatId) {
        Map<CliTool, Boolean> statuses = CliTool.checkAll();
        StringBuilder sb = new StringBuilder("🛠 *Installed CLI Tools Status:*\n\n");
        for (Map.Entry<CliTool, Boolean> entry : statuses.entrySet()) {
            sb.append(entry.getValue() ? "✅ " : "❌ ")
              .append("`").append(entry.getKey().binaryName()).append("`")
              .append(": ")
              .append(entry.getValue() ? "Available" : "Not found in PATH")
              .append("\n");
        }
        sendMarkdown(chatId, sb.toString());
    }

    private void handleCliExec(long chatId, String commandLine) {
        if (commandLine.isBlank()) {
            sendMessage(chatId, "Usage: /exec <tool> [arguments...]");
            return;
        }

        List<String> tokens = Arrays.asList(commandLine.split("\\s+"));
        String tool = tokens.getFirst();

        sendMessage(chatId, "⏳ Running `" + tool + "`...");
        ExecutionResult result = cliExecutor.execute(tokens, Duration.ofSeconds(config.defaultTimeoutSeconds()));

        StringBuilder response = new StringBuilder();
        response.append(result.isSuccess() ? "✅ Command completed" : "❌ Command failed")
                .append(" (exit: ").append(result.exitCode())
                .append(", duration: ").append(result.durationMs()).append("ms)\n\n");

        if (result.timedOut()) {
            response.append("⚠️ Process timed out!\n\n");
        }

        if (!result.stdout().isBlank()) {
            response.append("Output:\n```\n").append(truncate(result.stdout(), 3000)).append("\n```\n");
        }
        if (!result.stderr().isBlank()) {
            response.append("Errors:\n```\n").append(truncate(result.stderr(), 1000)).append("\n```");
        }

        sendMarkdown(chatId, response.toString());
    }

    public void sendMessage(long chatId, String text) {
        if (bot == null) return;
        SendMessage req = new SendMessage(chatId, text);
        SendResponse res = bot.execute(req);
        if (!res.isOk()) {
            log.error("Failed to send message: {}", res.description());
        }
    }

    public void sendMarkdown(long chatId, String markdown) {
        if (bot == null) return;
        SendMessage req = new SendMessage(chatId, markdown).parseMode(ParseMode.Markdown);
        SendResponse res = bot.execute(req);
        if (!res.isOk()) {
            log.warn("Markdown failed, falling back to plain text: {}", res.description());
            sendMessage(chatId, markdown.replace("*", "").replace("`", ""));
        }
    }

    public void shutdown() {
        if (bot != null) {
            log.info("Shutting down Telegram Bot...");
            bot.shutdown();
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "\n...[truncated]";
    }
}
