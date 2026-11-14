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
        long defaultTimeoutSeconds,
        String environment,
        Set<Long> allowedChatIds
) {
    public static AppConfig load() {
        Map<String, String> env = new HashMap<>(System.getenv());
        loadDotEnvIfExists(env);

        String botToken = env.getOrDefault("TELEGRAM_BOT_TOKEN", "").trim();
        String botApiUrl = env.getOrDefault("TELEGRAM_BOT_API_URL", "").trim();

        String rawWorkDir = env.getOrDefault("JSAVER_WORK_DIR", "./data").trim();
        Path workDir = Paths.get(rawWorkDir).toAbsolutePath().normalize();

        long defaultTimeout = 300;
        try {
            String rawTimeout = env.get("JSAVER_COMMAND_TIMEOUT_SECONDS");
            if (rawTimeout != null && !rawTimeout.isBlank()) {
                defaultTimeout = Long.parseLong(rawTimeout.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid JSAVER_COMMAND_TIMEOUT_SECONDS, using default: {}", defaultTimeout);
        }

        String environment = env.getOrDefault("APP_ENV", "dev").trim().toLowerCase();

        String rawAllowed = env.getOrDefault("ALLOWED_TELEGRAM_USERS", "").trim();
        Set<Long> allowedUsers = Collections.emptySet();
        if (!rawAllowed.isBlank()) {
            allowedUsers = Arrays.stream(rawAllowed.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toUnmodifiableSet());
        }

        return new AppConfig(botToken, botApiUrl, workDir, defaultTimeout, environment, allowedUsers);
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
