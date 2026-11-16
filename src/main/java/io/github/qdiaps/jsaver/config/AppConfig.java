package io.github.qdiaps.jsaver.config;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public record AppConfig(
        String botToken,
        String botApiUrl,
        Path workDir,
        long commandTimeoutSeconds,
        String environment,
        Set<Long> allowedChatIds,
        int maxRetryAttempts,
        long retryBaseDelayMs,
        int fallbackRetryAfterSeconds,
        int maxRetryAfterSeconds,
        String botUsername
) {
    public AppConfig {
        if (commandTimeoutSeconds <= 0)
            throw new IllegalArgumentException("COMMAND_TIMEOUT_SECONDS must be > 0");
        if (maxRetryAttempts < 1)
            throw new IllegalArgumentException("SENDER_RETRY_MAX_ATTEMPTS must be >= 1");
        if (retryBaseDelayMs <= 0)
            throw new IllegalArgumentException("SENDER_RETRY_BASE_DELAY_MS must be > 0");
        if (fallbackRetryAfterSeconds <= 0)
            throw new IllegalArgumentException("SENDER_FALLBACK_RETRY_AFTER_SEC must be > 0");
        if (maxRetryAfterSeconds < fallbackRetryAfterSeconds)
            throw new IllegalArgumentException("SENDER_MAX_RETRY_AFTER_SEC must be >= SENDER_FALLBACK_RETRY_AFTER_SEC");
        if (!botUsername.endsWith("_bot"))
            throw new IllegalArgumentException("TELEGRAM_BOT_USERNAME must be end with 'bot'");
    }

    public static AppConfig load() {
        Map<String, String> env = new HashMap<>(System.getenv());
        loadDotEnvIfExists(env);

        String botUsername = getString(env, "TELEGRAM_BOT_USERNAME", "");
        if (!botUsername.isBlank()) {
            botUsername = botUsername.startsWith("@")
                ? botUsername.substring(1).toLowerCase()
                : botUsername.toLowerCase();
        }

        return new AppConfig(
            getString(env, "TELEGRAM_BOT_TOKEN", ""),
            getString(env, "TELEGRAM_BOT_API_URL", ""),
            Paths.get(getString(env, "WORK_DIR", "./data")).toAbsolutePath().normalize(),
            getLong(env, "COMMAND_TIMEOUT_SECONDS", 300L),
            getString(env, "APP_ENV", "dev").toLowerCase(),
            getLongSet(env, "ALLOWED_TELEGRAM_USERS"),
            getInt(env, "SENDER_RETRY_MAX_ATTEMPTS", 3),
            getLong(env, "SENDER_RETRY_BASE_DELAY_MS", 1000L),
            getInt(env, "SENDER_FALLBACK_RETRY_AFTER_SEC", 3),
            getInt(env, "SENDER_MAX_RETRY_AFTER_SEC", 60),
            botUsername
        );
    }

    private static String getString(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static int getInt(Map<String, String> env, String key, int defaultValue) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank())
            return defaultValue;

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    private static long getLong(Map<String, String> env, String key, long defaultValue) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank())
            return defaultValue;

        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    private static Set<Long> getLongSet(Map<String, String> env, String key) {
        String raw = env.get(key);
        if (raw == null || raw.isBlank())
            return Collections.emptySet();

        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static void loadDotEnvIfExists(Map<String, String> env) {
        Path dotEnvPath = Paths.get(".env");
        if (!Files.exists(dotEnvPath)) {
            return;
        }

        log.info("Loading environment properties from .env file: {}", dotEnvPath.toAbsolutePath());
        try (BufferedReader reader = Files.newBufferedReader(dotEnvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    // System environment takes precedence over .env
                    env.putIfAbsent(key, value);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file: {}", e.getMessage());
        }
    }

    public boolean isUserAllowed(long userId) {
        return allowedChatIds.isEmpty() || allowedChatIds.contains(userId);
    }

    public boolean hasCustomBotApiUrl() {
        return botApiUrl != null && !botApiUrl.isBlank();
    }
}
