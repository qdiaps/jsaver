package io.github.qdiaps.jsaver.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CliExecutorTest {

    private CliExecutor executor;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        executor = new CliExecutor(tempDir.toFile());
    }

    @Test
    @DisplayName("Successfully executes a basic CLI command and captures stdout")
    void testBasicCommandExecution() {
        ExecutionResult result = executor.execute(List.of("echo", "hello-jsaver"), Duration.ofSeconds(5));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().trim()).isEqualTo("hello-jsaver");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("Handles non-zero exit codes correctly")
    void testNonZeroExitCode() {
        ExecutionResult result = executor.execute(List.of("sh", "-c", "exit 42"), Duration.ofSeconds(5));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isEqualTo(42);
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("Enforces timeout and terminates long-running process")
    void testProcessTimeout() {
        ExecutionResult result = executor.execute(
                List.of("sleep", "10"),
                Duration.ofMillis(400)
        );

        assertThat(result.timedOut()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(350);
        assertThat(result.durationMs()).isLessThan(3000);
    }
}
