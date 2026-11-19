package io.github.qdiaps.jsaver.command.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.qdiaps.jsaver.access.AccessGuard;
import io.github.qdiaps.jsaver.bot.Sender;
import io.github.qdiaps.jsaver.command.AdminCommand;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccessCommand implements AdminCommand {
    private final Sender sender;
    private final AccessGuard accessGuard;

    public String name() {
        return "access";
    }

    public String description() {
        return "вывести состояние AccessGuard";
    }

    public void execute(MessageMetadata metadata, List<String> args) {
        var state = accessGuard.getState();
        var builder = new StringBuilder();
        var now = Instant.now();

        builder.append("Состояние AccessGuard\n\n");

        builder.append("Режимы:\n");
        builder.append(" ▸ Белый список: %s\n".formatted(state.whitelistEnabled() ? "ВКЛ" : "ВЫКЛ"));
        builder.append(" ▸ Глобальный мут: %s\n%s\n".formatted(
            state.globalMuteEnabled() ? "ВКЛ" : "ВЫКЛ",
            state.globalMuteEnabled() ? ("   └ Причина: " + state.globalMuteReason()) : ""
        ));

        builder.append("Администрация:\n");
        builder.append(" ▸ Root Admin: %d\n".formatted(state.rootAdminId()));
        builder.append(" ▸ Админы (%d): %s\n\n".formatted(
            state.admins().size(),
            formatIds(state.admins())
        ));

        builder.append("Белые списки:\n");
        builder.append(" ▸ Юзеры (%d): %s\n".formatted(
            state.allowedUsers().size(),
            formatIds(state.allowedUsers())
        ));
        builder.append(" ▸ Чаты (%d): %s\n\n".formatted(
            state.allowedChats().size(),
            formatIds(state.allowedChats())
        ));

        builder.append("Ограничения:\n");
        builder.append(" ▸ Забаненные юзеры (%d): %s\n".formatted(
            state.bannedUsers().size(),
            formatIds(state.bannedUsers())
        ));
        builder.append(" ▸ Забаненные чаты (%d): %s\n".formatted(
            state.bannedChats().size(),
            formatIds(state.bannedChats())
        ));

        var activeMutes = state.mutedUsers().entrySet().stream()
            .filter(entry -> entry.getValue().isAfter(now))
            .toList();

        if (activeMutes.isEmpty()) {
            builder.append(" ▸ Активные муты (0): нет\n");
        } else {
            builder.append(" ▸ Активные муты (%d):\n".formatted(activeMutes.size()));
            for (var entry : activeMutes) {
                builder.append("   └ %d (осталось %s)\n".formatted(
                    entry.getKey(),
                    formatRemaining(now, entry.getValue())
                ));
            }
        }

        sender.sendContent(metadata, new Payload.Text(builder.toString()));
    }

    private String formatIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return "нет";
        }
        return ids.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(", "));
    }

    private String formatRemaining(Instant now, Instant until) {
        long minutes = Duration.between(now, until).toMinutes();
        return minutes < 1 ? "< 1 мин." : minutes + " мин.";
    }
}
