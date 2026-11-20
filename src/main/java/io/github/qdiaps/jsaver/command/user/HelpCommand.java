package io.github.qdiaps.jsaver.command.user;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.github.qdiaps.jsaver.access.AccessGuard;
import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.command.AdminCommand;
import io.github.qdiaps.jsaver.command.Command;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import io.github.qdiaps.jsaver.telemetry.BotTelemetry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HelpCommand implements Command {
    private final Sender sender;
    private final List<Command> commands;
    private final AccessGuard accessGuard;
    private final BotTelemetry telemetry;

    public String name() {
        return "help";
    }

    public String description() {
        return "список команд и правила использования";
    }

    public void execute(MessageMetadata metadata, List<String> args) {
        String userCommandList = formatCommands(cmd -> !(cmd instanceof AdminCommand));

        String helpText = """
            Как пользоваться:

            ▸ Просто скинь ссылку в чат – бот сам её подхватит и в личке, и в группе.
            ▸ Обрабатывается только одна ссылка за раз: если в сообщении их несколько, я возьму первую.

            Поддерживаемые площадки:
            • (!) TikTok – видео без водяных знаков и фото-карусели
            • (!) YouTube – обычные видео и Shorts
            • (!) Pinterest – картинки и видеопины

            Доступные команды:
            %s
            """.formatted(userCommandList);

        if (metadata.userId() != null && accessGuard.isAdmin(metadata.userId())) {
            String adminCommandList = formatCommands(cmd -> cmd instanceof AdminCommand);
            if (!adminCommandList.isEmpty())
                helpText += "\nАдмин команды:\n" + adminCommandList;
        }

        sender.sendContent(metadata, new Payload.Text(helpText));
        telemetry.recordSuccess();
    }

    private String formatCommands(Predicate<Command> filter) {
        return commands.stream()
            .filter(filter)
            .map(cmd -> "/" + cmd.name() + " - " + cmd.description())
            .collect(Collectors.joining("\n"));
    }
}
