package io.github.qdiaps.jsaver.bot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ReplyParameters;
import com.pengrad.telegrambot.request.AbstractSendRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.ChatType;
import io.github.qdiaps.jsaver.contract.DeliveryReport;
import io.github.qdiaps.jsaver.contract.FailureCategory;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Sender {
    private static final Duration THROTTLE_COOLDOWN = Duration.ofSeconds(15);

    private record ThrottleKey(long chatId, String text) {}

    private final TelegramBot bot;
    private final AppConfig config;
    private final ConcurrentHashMap<ThrottleKey, Instant> lastFailureSent = new ConcurrentHashMap<>();

    public Sender(TelegramBot bot, AppConfig config) {
        this.bot = Objects.requireNonNull(bot);
        this.config = Objects.requireNonNull(config);
    }

    public DeliveryReport sendContent(MessageMetadata metadata, Payload payload) {
        return sendContent(metadata, List.of(payload));
    }

    public DeliveryReport sendContent(MessageMetadata metadata, List<Payload> payloads) {
        if (payloads == null || payloads.isEmpty())
            return new DeliveryReport(metadata, List.of());

        List<DeliveryReport.Item> items = new ArrayList<>(payloads.size());
        boolean isReply = true;

        for (var payload : payloads) {
            DeliveryReport.Item item = sendSinglePayload(metadata, payload, true, isReply);
            items.add(item);
            if (item.delivered())
                isReply = false;
        }

        long deliveredCount = items.stream().filter(DeliveryReport.Item::delivered).count();
        if (deliveredCount < items.size()) {
            log.warn("Incomplete delivery to chat [{}]: delivered {}/{} items",
                     metadata.chatId(), deliveredCount, items.size());
        } else {
            log.debug("Delivered {}/{} items to chat [{}]",
                      deliveredCount, items.size(), metadata.chatId());
        }

        return new DeliveryReport(metadata, List.copyOf(items));
    }

    public void sendFailure(MessageMetadata metadata, String text, FailureCategory category) {
        var payload = new Payload.Text(text);

        switch (category) {
            case PRIVATE_ONLY -> {
                if (metadata.chatType() == ChatType.PRIVATE) {
                    deliverFailure(metadata, payload, category, text);
                } else {
                    log.debug("Suppressed PRIVATE_ONLY failure in group chat [{}]: {}",
                             metadata.chatId(), text);
                }
            }
            case THROTTLED -> {
                var now = Instant.now();

                if (lastFailureSent.size() > 500) {
                    lastFailureSent.entrySet().removeIf(
                        entry -> Duration.between(entry.getValue(), now).compareTo(THROTTLE_COOLDOWN) >= 0
                    );
                }

                var shouldSend = new AtomicBoolean(false);
                var key = new ThrottleKey(metadata.chatId(), text);

                lastFailureSent.compute(key, (k, lastSent) -> {
                    if (lastSent == null || Duration.between(lastSent, now).compareTo(THROTTLE_COOLDOWN) >= 0) {
                        shouldSend.set(true);
                        return now;
                    }
                    return lastSent;
                });

                if (shouldSend.get())
                    deliverFailure(metadata, payload, category, text);
                else
                    log.debug("Throttled failure notification for chat [{}]: {}", metadata.chatId(), text);
            }
            case CRITICAL -> deliverFailure(metadata, payload, category, text);
        }
    }

    private DeliveryReport.Item sendSinglePayload(
        MessageMetadata metadata, Payload payload, boolean isImportant, boolean isReply
    ) {
        var response = executeWithRetry(useReply -> bot.execute(buildRequest(metadata, payload, useReply)), isReply, isImportant);
        if (response.isEmpty()) {
            log.warn("Failed to deliver [{}] to chat [{}]", payload.getClass().getSimpleName(), metadata.chatId());
            return new DeliveryReport.Item(payload, List.of(), false);
        }
        return new DeliveryReport.Item(payload, extractFileIds(payload, response.get()), true);
    }

    private AbstractSendRequest<?> buildRequest(
        MessageMetadata metadata, Payload payload, boolean useReply
    ) {
        AbstractSendRequest<?> request = switch (payload) {
            case Payload.Text(var text) -> new SendMessage(metadata.chatId(), text);
        };

        if (metadata.threadId() != null)
            request.messageThreadId(metadata.threadId());
        if (useReply)
            request.replyParameters(new ReplyParameters(metadata.messageId()));

        return request;
    }

    private <R extends BaseResponse> Optional<R> executeWithRetry(
        Function<Boolean, R> action, boolean initialReply, boolean isImportant
    ) {
        boolean useReply = initialReply;
        int maxAttempts = isImportant ? config.maxRetryAttempts() : 1;
        int attempt = 0;

        while (attempt < maxAttempts) {
            R response;
            attempt++;

            try {
                response = action.apply(useReply);
            } catch (RuntimeException e) {
                log.warn("Network error on attempt {}/{}: {}", attempt, maxAttempts, e.getMessage());
                if (attempt >= maxAttempts || !sleepBackoff(attempt))
                    return Optional.empty();
                continue;
            }

            if (response.isOk())
                return Optional.of(response);

            int code = response.errorCode();

            if (code == 400 && useReply) {
                log.debug("Got 400 with reply enabled, retrying without reply: {}", response.description());
                useReply = false;
                attempt--;
                continue;
            } else if (code == 429) {
                var retryAfter = (response.parameters() != null && response.parameters().retryAfter() != null)
                    ? response.parameters().retryAfter() : config.fallbackRetryAfterSeconds();
                var sleepSec = Math.min(retryAfter, config.maxRetryAfterSeconds());
                log.warn("Rate limited (429), sleeping for {}s (requested {}s) on attempt {}/{}",
                         sleepSec, retryAfter, attempt, maxAttempts);
                if (attempt >= maxAttempts || !sleepSeconds(sleepSec))
                    return Optional.empty();
                continue;
            } else if (code >= 500 && code < 600) {
                log.warn("Telegram server error {} on attempt {}/{}: {}", code, attempt, maxAttempts, response.description());
                if (attempt >= maxAttempts || !sleepBackoff(attempt))
                    return Optional.empty();
                continue;
            }

            log.error("Fatal Telegram API error [{}]: {}", code, response.description());
            return Optional.empty();
        }

        return Optional.empty();
    }

    private List<String> extractFileIds(Payload payload, SendResponse response) {
        return switch (payload) {
            case Payload.Text ignored -> List.of();
        };
    }

    private boolean sleepSeconds(int seconds) {
        if (seconds <= 0)
            return true;
        return sleep(Duration.ofSeconds(seconds));
    }

    private boolean sleepBackoff(int attempt) {
        long delayMs = config.retryBaseDelayMs() * (1L << (attempt - 1));
        return sleep(Duration.ofMillis(delayMs));
    }

    private boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sender thread interrupted during sleep ({}ms)", duration.toMillis());
            return false;
        }
    }

    private void deliverFailure(MessageMetadata metadata, Payload payload, FailureCategory category, String text) {
        if (!sendSinglePayload(metadata, payload, true, true).delivered())
            return;

        if (category == FailureCategory.CRITICAL)
            log.warn("Sent [{}] failure notification to chat [{}]: {}", category, metadata.chatId(), text);
        else
            log.info("Sent [{}] failure notification to chat [{}]: {}", category, metadata.chatId(), text);
    }
}
