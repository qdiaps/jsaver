package io.github.qdiaps.jsaver;

import io.github.qdiaps.jsaver.bot.TelegramBotService;
import io.github.qdiaps.jsaver.cli.CliExecutor;
import io.github.qdiaps.jsaver.cli.CliTool;
import io.github.qdiaps.jsaver.config.AppConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;

@Slf4j
public class Main {
    public static void main(String[] args) {
        log.info("=================================================");
        log.info("Starting jsaver Bot Application");
        log.info("Java Version: {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"));
        log.info("OS: {} {} ({})", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        log.info("=================================================");

        AppConfig config = AppConfig.load();
        log.info("Environment: {}", config.environment());
        log.info("Working directory: {}", config.workDir());
        log.info("Default command timeout: {}s", config.defaultTimeoutSeconds());
        log.info("Allowed users whitelist: {}", config.allowedChatIds().isEmpty() ? "ALL (Open)" : config.allowedChatIds());

        CliTool.checkAll();

        CliExecutor cliExecutor = new CliExecutor(config.workDir().toFile());
        TelegramBotService botService = new TelegramBotService(config, cliExecutor);

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Performing graceful shutdown...");
            botService.shutdown();
            shutdownLatch.countDown();
            log.info("Shutdown complete.");
        }, "shutdown-hook"));

        botService.start();
        log.info("jsaver application is running. Press Ctrl+C or send SIGTERM to stop.");

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Main thread interrupted. Exiting.");
        }
    }
}
