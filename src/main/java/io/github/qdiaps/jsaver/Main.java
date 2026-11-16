package io.github.qdiaps.jsaver;

import java.util.concurrent.CountDownLatch;

import com.pengrad.telegrambot.TelegramBot;

import org.slf4j.bridge.SLF4JBridgeHandler;

import io.github.qdiaps.jsaver.bot.Bot;
import io.github.qdiaps.jsaver.bot.MessageRouter;
import io.github.qdiaps.jsaver.bot.Receiver;
import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        log.info("=================================================");
        log.info("Starting jsaver Bot Application");
        log.info("Java Version: {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"));
        log.info("OS: {} {} ({})", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        log.info("=================================================");

        AppConfig config = AppConfig.load();
        log.info("Environment: {}", config.environment());
        log.info("Working directory: {}", config.workDir());
        log.info("Default command timeout: {}s", config.commandTimeoutSeconds());
        log.info("Allowed users whitelist: {}", config.allowedChatIds().isEmpty() ? "ALL (Open)" : config.allowedChatIds());

        //CliTool.checkAll();

        //CliExecutor cliExecutor = new CliExecutor(config.workDir().toFile());
        TelegramBot tgBot = Bot.createBot(config);
        var sender = new Sender(tgBot, config);
        var messageRouter = new MessageRouter(sender);
        var receiver = new Receiver(messageRouter);
        var bot = new Bot(tgBot, receiver);

        var shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Performing graceful shutdown...");
            bot.stop();
            shutdownLatch.countDown();
            log.info("Shutdown complete.");
        }, "shutdown-hook"));

        bot.start();
        log.info("jsaver application is running. Press Ctrl+C or send SIGTERM to stop.");

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Main thread interrupted. Exiting.");
        }
    }
}
