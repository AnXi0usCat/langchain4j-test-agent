package dev.example.agent;

import java.nio.file.Path;
import java.time.Duration;

final class TestConfigs {
    private TestConfigs() {}

    static AppConfig appConfig(Path workspaceDir, int tokenPersistBatchChars) {
        return new AppConfig(
                8080,
                "http://localhost:8000/v1",
                "not-needed",
                "test-model",
                "jdbc:postgresql://localhost:5432/agent",
                "postgres",
                "postgres",
                workspaceDir.toAbsolutePath().normalize(),
                Duration.ofSeconds(5),
                10_000,
                20,
                30,
                tokenPersistBatchChars,
                "prompt-bundles/coding-agent-v1.json",
                false,
                ""
        );
    }
}