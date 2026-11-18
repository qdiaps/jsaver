package io.github.qdiaps.jsaver.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.qdiaps.jsaver.access.AccessGuard;
import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommandRouter {
    private final Map<String, Command> commands;
    private final String botUsername;
    private final AccessGuard accessGuard;

    public CommandRouter(List<Command> commandList, AppConfig config, AccessGuard accessGuard) {
        Objects.requireNonNull(commandList);
        Objects.requireNonNull(config);
        this.accessGuard = Objects.requireNonNull(accessGuard);

        botUsername = config.botUsername();
        commands = commandList.stream()
            .collect(Collectors.toUnmodifiableMap(
                    cmd -> cmd.name().toLowerCase(),
                    Function.identity()
            ));
    }

    public void route(MessageMetadata metadata, String text) {
        String[] parts = text.substring(1).trim().split("\\s+", 2);
        String rawCommand = parts[0].toLowerCase();

        if (rawCommand.isEmpty())
            return;

        int atIndex = rawCommand.indexOf('@');
        if (atIndex != -1) {
            String targetBot = rawCommand.substring(atIndex + 1);
            if (!targetBot.equals(botUsername))
                return;
            rawCommand = rawCommand.substring(0, atIndex);
        }

        Command command = commands.get(rawCommand);
        if (command == null) {
            log.debug("Unknown command [/{}] in chat [{}]", rawCommand, metadata.chatId());
            return;
        } else if (command instanceof AdminCommand) {
            if (metadata.userId() == null || !accessGuard.isAdmin(metadata.userId())) {
                log.debug("Unauthorized admin command [/{}] from user [{}] in chat [{}]",
                         rawCommand, metadata.userId(), metadata.chatId());
                return;
            }
        }

        List<String> args = (parts.length > 1 && !parts[1].isBlank())
            ? List.of(parts[1].split("\\s+"))
            : List.of();

        log.info("Received command from user [{}] in chat [{}], message [{}]: {}",
                  metadata.userId(), metadata.chatId(), metadata.messageId(), text);
        command.execute(metadata, args);
    }
}
