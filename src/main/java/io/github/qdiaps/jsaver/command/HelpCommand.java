package io.github.qdiaps.jsaver.command;

import java.util.List;
import java.util.stream.Collectors;

import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HelpCommand implements Command {
    private final Sender sender;
    private final List<Command> commands;

    public String name() {
        return "help";
    }

    public String description() {
        return "список команд и правила использования";
    }

    public void execute(MessageMetadata metadata, List<String> args) {
        String commandList = commands.stream()
            .map(cmd -> "/" + cmd.name() + " – " + cmd.description())
            .collect(Collectors.joining("\n"));

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
            """.formatted(commandList);
        sender.sendContent(metadata, new Payload.Text(helpText));
    }
}
