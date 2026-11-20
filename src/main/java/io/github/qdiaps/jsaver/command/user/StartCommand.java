package io.github.qdiaps.jsaver.command.user;

import java.util.List;

import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.command.Command;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import io.github.qdiaps.jsaver.telemetry.BotTelemetry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StartCommand implements Command {
    private static final String WELCOME_TEXT = """
        Привет! Я помогу быстро скачать видео, фото или аудио по ссылке прямо в Telegram.

        Просто отправь ссылку на ролик или пост – я сам разберусь с форматом и пришлю файл в чат.

        Поддерживаю: TikTok, YouTube, Pinterest.
        Подробности и команды — /help
        """;
    
    private final Sender sender;
    private final BotTelemetry telemetry;

    public String name() {
        return "start";
    }

    public String description() {
        return "запустить бота и получить краткую инструкцию";
    }

    public void execute(MessageMetadata metadata, List<String> args) {
        sender.sendContent(metadata, new Payload.Text(WELCOME_TEXT));
        telemetry.recordSuccess();
    }
}
