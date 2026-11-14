package io.github.qdiaps.jsaver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @Test
    @DisplayName("AppConfig loads default values when environment is minimal")
    void testDefaultValues() {
        AppConfig config = new AppConfig(
                "test-token",
                "",
                Path.of("./data"),
                300,
                "dev",
                Set.of()
        );

        assertThat(config.botToken()).isEqualTo("test-token");
        assertThat(config.hasCustomBotApiUrl()).isFalse();
        assertThat(config.isUserAllowed(123456L)).isTrue();
    }

    @Test
    @DisplayName("AppConfig correctly checks user whitelist")
    void testUserWhitelist() {
        AppConfig config = new AppConfig(
                "test-token",
                "http://localhost:8081/bot",
                Path.of("./data"),
                60,
                "prod",
                Set.of(100L, 200L)
        );

        assertThat(config.hasCustomBotApiUrl()).isTrue();
        assertThat(config.isUserAllowed(100L)).isTrue();
        assertThat(config.isUserAllowed(200L)).isTrue();
        assertThat(config.isUserAllowed(999L)).isFalse();
    }
}
