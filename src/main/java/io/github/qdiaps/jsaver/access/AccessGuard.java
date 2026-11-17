package io.github.qdiaps.jsaver.access;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.github.qdiaps.jsaver.contract.AccessDecision;
import io.github.qdiaps.jsaver.contract.ChatType;
import io.github.qdiaps.jsaver.contract.DenialReason;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccessGuard {
    private final AtomicReference<AccessState> stateRef;

    public AccessGuard(AccessState startupState) {
        stateRef = new AtomicReference<>(Objects.requireNonNull(startupState));
    }

    public AccessDecision checkAccess(MessageMetadata metadata) {
        Objects.requireNonNull(metadata);

        var state = stateRef.get();
        var userId = metadata.userId();
        if (userId != null &&
            (state.rootAdminId() == userId || state.admins().contains(userId))) {
            return new AccessDecision.Allowed();
        } else if (state.globalMuteEnabled()) {
            return new AccessDecision.Denied(DenialReason.GLOBAL_MUTE, true);
        }

        if (metadata.chatType() == ChatType.GROUP) {
            var chatId = metadata.chatId();
            if (state.bannedChats().contains(chatId)) {
                return new AccessDecision.Denied(DenialReason.CHAT_BANNED, false);
            } else if (state.whitelistEnabled() && !state.allowedChats().contains(chatId)) {
                return new AccessDecision.Denied(DenialReason.NOT_IN_WHITELIST, false);
            }

            if (userId != null)
                return checkUserAccess(state, userId, false);
        } else if (metadata.chatType() == ChatType.PRIVATE) {
            var decision = checkUserAccess(state, userId, true);
            if (decision instanceof AccessDecision.Allowed
                && state.whitelistEnabled() && !state.allowedUsers().contains(userId))
                return new AccessDecision.Denied(DenialReason.NOT_IN_WHITELIST, true);
            else
                return decision;
        }

        return new AccessDecision.Allowed();
    }

    public boolean isAdmin(long userId) {
        var state = stateRef.get();
        return state.rootAdminId() == userId || state.admins().contains(userId);
    }

    public void setWhitelist(boolean isEnabled) {
        stateRef.updateAndGet(current -> {
            if (current.whitelistEnabled() == isEnabled) {
                return current;
            } else if (isEnabled && current.allowedChats().isEmpty() && current.allowedUsers().isEmpty()) {
                throw new IllegalStateException(
                    "Whitelist cannot enable when both allowedUsers and allowedChats are empty"
                );
            }

            return current.withWhitelistEnabled(isEnabled);
        });
    }

    public void setGlobalMute(boolean isEnabled, String reason) {
        stateRef.updateAndGet(current -> current.withGlobalMute(isEnabled, reason));
    }

    public Optional<String> getGlobalMuteReason() {
        var state = stateRef.get();
        if (state.globalMuteEnabled())
            return Optional.of(state.globalMuteReason());

        return Optional.empty();
    }

    private AccessDecision checkUserAccess(AccessState state, long userId, boolean shouldNotify) {
        if (state.bannedUsers().contains(userId))
            return new AccessDecision.Denied(DenialReason.USER_BANNED, shouldNotify);

        Instant until = state.mutedUsers().get(userId);
        if (until != null && Instant.now().isBefore(until))
            return new AccessDecision.Denied(DenialReason.USER_MUTED, shouldNotify);

        return new AccessDecision.Allowed();
    }
}
