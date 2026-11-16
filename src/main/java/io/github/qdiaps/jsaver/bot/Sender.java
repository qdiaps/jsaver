package io.github.qdiaps.jsaver.bot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ReplyParameters;
import com.pengrad.telegrambot.request.AbstractSendRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;

import io.github.qdiaps.jsaver.config.AppConfig;
import io.github.qdiaps.jsaver.contract.DeliveryReport;
import io.github.qdiaps.jsaver.contract.MessageMetadata;
import io.github.qdiaps.jsaver.contract.Payload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Sender {
    private final TelegramBot bot;
    private final AppConfig config;

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

        return new DeliveryReport(metadata, List.copyOf(items));
    }

    private DeliveryReport.Item sendSinglePayload(
        MessageMetadata metadata, Payload payload, boolean isImportant, boolean isReply
    ) {
        return executeWithRetry(useReply -> bot.execute(buildRequest(metadata, payload, useReply)), isReply, isImportant)
            .map(response -> new DeliveryReport.Item(payload, extractFileIds(payload, response), true))
            .orElseGet(() -> new DeliveryReport.Item(payload, List.of(), false));
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
                int retryAfter = (response.parameters() != null && response.parameters().retryAfter() != null)
                    ? response.parameters().retryAfter() : config.fallbackRetryAfterSeconds();
                log.warn("Rate limited (429), sleeping for {}s", retryAfter);
                if (attempt >= maxAttempts || !sleepSeconds(Math.min(retryAfter, config.maxRetryAfterSeconds())))
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
            return false;
        }
    }
}
