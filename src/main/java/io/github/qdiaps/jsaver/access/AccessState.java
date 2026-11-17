package io.github.qdiaps.jsaver.access;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.DenialReason;

public record AccessState(
    long rootAdminId, Set<Long> admins,
    Set<Long> bannedUsers, Set<Long> bannedChats,
    Map<Long, Instant> mutedUsers,
    boolean whitelistEnabled, Set<Long> allowedUsers, Set<Long> allowedChats,
    boolean globalMuteEnabled, String globalMuteReason
) {
    public AccessState {
        admins = admins == null ? Set.of() : Set.copyOf(admins);
        bannedUsers = bannedUsers == null ? Set.of() : Set.copyOf(bannedUsers);
        bannedChats = bannedChats == null ? Set.of() : Set.copyOf(bannedChats);
        mutedUsers = mutedUsers == null ? Map.of() : Map.copyOf(mutedUsers);
        allowedUsers = allowedUsers == null ? Set.of() : Set.copyOf(allowedUsers);
        allowedChats = allowedChats == null ? Set.of() : Set.copyOf(allowedChats);

        if (rootAdminId <= 0)
            throw new IllegalArgumentException("rootAdminId must be positive, got: " + rootAdminId);
        if (admins.contains(rootAdminId) || bannedUsers.contains(rootAdminId) || mutedUsers.containsKey(rootAdminId))
            throw new IllegalArgumentException("rootAdminId (%d) connot be present in admins, bannedUsers or mutedUsers".formatted(rootAdminId));
        if (!Collections.disjoint(bannedUsers, mutedUsers.keySet()))
            throw new IllegalArgumentException("Users cannot be simultaneously banned and muted");
        if (whitelistEnabled && (allowedUsers.isEmpty() && allowedChats.isEmpty()))
            throw new IllegalArgumentException("Whitelist cannot be enabled when both allowedUsers and allowedChats are empty");
        if (globalMuteEnabled && (globalMuteReason == null || globalMuteReason.isBlank()))
            globalMuteReason = DenialReason.GLOBAL_MUTE.text();
        if (!globalMuteEnabled)
            globalMuteReason = null;
        if (!Collections.disjoint(bannedUsers, admins) || !Collections.disjoint(mutedUsers.keySet(), admins))
            throw new IllegalArgumentException("Admins cannot be banned or muted");
    }

    public static AccessState load(AppConfig config) {
        return new AccessState(
            config.rootAdminId(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            (config.allowedUsers().isEmpty() && config.allowedChats().isEmpty()) ? false : true,
            config.allowedUsers(),
            config.allowedChats(),
            false,
            null
        );
    }

    public AccessState withWhitelistEnabled(boolean enabled) {
        return new AccessState(
            this.rootAdminId(), this.admins(), this.bannedUsers(), this.bannedChats(), this.mutedUsers(),
            enabled, this.allowedUsers(), this.allowedChats(), this.globalMuteEnabled(), this.globalMuteReason()
        );
    }

    public AccessState withGlobalMute(boolean enabled, String reason) {
        return new AccessState(
            this.rootAdminId(), this.admins(), this.bannedUsers(), this.bannedChats(), this.mutedUsers(),
            this.whitelistEnabled(), this.allowedUsers(), this.allowedChats(), enabled, reason
        );
    }
}
