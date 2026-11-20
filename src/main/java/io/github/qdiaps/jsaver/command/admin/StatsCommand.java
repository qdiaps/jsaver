package io.github.qdiaps.jsaver.command.admin;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.command.AdminCommand;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import io.github.qdiaps.jsaver.telemetry.BotTelemetry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StatsCommand implements AdminCommand {
    private static final DateTimeFormatter START_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final Sender sender;
    private final BotTelemetry telemetry;

    public String name() {
        return "stats";
    }

    public String description() {
        return "вывести всю телеметрию";
    }

    public void execute(MessageMetadata metadata, List<String> args) {
        var builder = new StringBuilder();
        var s = telemetry.snapshot();

        builder.append("Телеметрия\n\n");

        builder.append("Время работы:\n");
        builder.append(" ▸ Запущен: %s\n".formatted(START_TIME_FORMATTER.format(s.startedAt())));
        builder.append(" ▸ Аптайм: %s\n\n".formatted(formatDuration(s.uptime())));

        builder.append("Запросы:\n");
        builder.append(" ▸ В работе: %d\n".formatted(s.inFlightRequests()));
        builder.append(" ▸ Обработано всего: %d\n".formatted(s.totalRequests()));
        builder.append("  └ Успешно: %d\n".formatted(s.succeededRequests()));
        builder.append("  └ Отклонено: %d\n".formatted(s.rejectedRequests()));
        builder.append("  └ С ошибками: %d\n".formatted(s.failedRequests()));
        builder.append(" ▸ Игнорировано: %d\n\n".formatted(s.ignoredUpdates()));

        var memoryPercent = (s.usedMemoryBytes() * 100) / s.maxMemoryBytes();
        var diskInfo = s.totalDiskBytes() > 0
            ? "свободно %s из %s".formatted(formatBytes(s.usableDiskBytes()), formatBytes(s.totalDiskBytes()))
            : "недоступно";

        builder.append("Ресурсы:\n");
        builder.append(" ▸ Память JVM: %s / %s (%d%%)\n".formatted(
            formatBytes(s.usedMemoryBytes()),
            formatBytes(s.maxMemoryBytes()),
            memoryPercent
        ));
        builder.append(" ▸ Диск: %s".formatted(diskInfo));

        sender.sendContent(metadata, new Payload.Text(builder.toString()));
        telemetry.recordSuccess();
    }

    private String formatDuration(Duration duration) {
        var totalSeconds = duration.toSeconds();
        var days = totalSeconds / 86_400;
        var hours = (totalSeconds % 86_400) / 3_600;
        var minutes = (totalSeconds % 3_600) / 60;
        var seconds = totalSeconds % 60;

        if (days > 0)
            return "%dд %02dч %02dм %02dс".formatted(days, hours, minutes, seconds);
        if (hours > 0)
            return "%dч %02dм %02dс".formatted(hours, minutes, seconds);

        return "%dм %02dс".formatted(minutes, seconds);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " Б";

        double kb = bytes / 1024.0;
        if (kb < 1024)
            return "%.1f КБ".formatted(kb);

        double mb = kb / 1024.0;
        if (mb < 1024)
            return "%.1f МБ".formatted(mb);

        double gb = mb / 1024.0;

        return "%.2f ГБ".formatted(gb);
    }
}
