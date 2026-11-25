package io.github.qdiaps.jsaver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.pengrad.telegrambot.TelegramBot;

import org.slf4j.bridge.SLF4JBridgeHandler;

import io.github.qdiaps.jsaver.access.AccessGuard;
import io.github.qdiaps.jsaver.access.AccessState;
import io.github.qdiaps.jsaver.bot.Bot;
import io.github.qdiaps.jsaver.bot.MessageRouter;
import io.github.qdiaps.jsaver.bot.Receiver;
import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.command.Command;
import io.github.qdiaps.jsaver.command.CommandRouter;
import io.github.qdiaps.jsaver.command.admin.AccessCommand;
import io.github.qdiaps.jsaver.command.admin.StatsCommand;
import io.github.qdiaps.jsaver.command.user.HelpCommand;
import io.github.qdiaps.jsaver.command.user.StartCommand;
import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.downloader.WorkspaceManager;
import io.github.qdiaps.jsaver.telemetry.BotTelemetry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
            log.error("Uncaught exception in thread [{}]", thread.getName(), throwable)
        );

        log.info("=================================================");
        log.info("Starting jsaver Bot Application");
        log.info("Java Version: {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"));
        log.info("OS: {} {} ({})", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        log.info("=================================================");

        try {
            AppConfig config = AppConfig.load();
            prepareWorkDir(config.workDir());
            log.info("Environment: [{}], working directory: [{}]", config.environment(), config.workDir());

            var workspaceManager = new WorkspaceManager(config);
            var telemetry = new BotTelemetry(config, workspaceManager);

            var startupAccessState = AccessState.load(config);
            log.info("AccessState loaded: rootAdmin=[{}], whitelist=[{}], globalMute=[{}], admins=[{}]",
                     startupAccessState.rootAdminId(),
                     startupAccessState.whitelistEnabled(),
                     startupAccessState.globalMuteEnabled(),
                     startupAccessState.admins().size());

            TelegramBot tgBot = Bot.createBot(config);
            var accessGuard = new AccessGuard(startupAccessState);
            var sender = new Sender(tgBot, config);
            var commands = buildCommands(sender, accessGuard, telemetry);
            var commandRouter = new CommandRouter(commands, config, accessGuard, telemetry);
            var messageRouter = new MessageRouter(commandRouter, accessGuard, sender, telemetry);
            var receiver = new Receiver(messageRouter, telemetry);
            var bot = new Bot(tgBot, receiver, config);

            workspaceManager.init();
            workspaceManager.cleanupOrphans();

            bot.start();

            var shutdownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received. Performing graceful shutdown...");
                try {
                    bot.stop();
                } catch (Exception e) {
                    log.error("Error during bot shutdown", e);
                } finally {
                    shutdownLatch.countDown();
                    log.info("Shutdown complete.");
                }
            }, "shutdown-hook"));

            log.info("jsaver application is running. Press Ctrl+C or send SIGTERM to stop.");
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Main thread interrupted. Exiting.");
        } catch (Exception e) {
            log.error("Fatal error during application startup", e);
            System.exit(1);
        }
    }

    private static List<Command> buildCommands(Sender sender, AccessGuard accessGuard, BotTelemetry telemetry) {
        List<Command> commands = new ArrayList<>();

        // User commands
        commands.add(new StartCommand(sender, telemetry));

        // Admin commands
        commands.add(new AccessCommand(sender, accessGuard, telemetry));
        commands.add(new StatsCommand(sender, telemetry));

        commands.add(new HelpCommand(sender, Collections.unmodifiableList(commands), accessGuard, telemetry));

        return List.copyOf(commands);
    }

    private static void prepareWorkDir(Path workDir) throws IOException {
        Files.createDirectories(workDir);
        if (!Files.isWritable(workDir))
            throw new IOException("Work directory is not writable: " + workDir.toAbsolutePath());
    }
}
