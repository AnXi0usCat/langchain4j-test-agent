package dev.example.agent;

import java.nio.file.Path;
import java.time.Duration;

public record AppConfig(
        int port,
        String modelBaseUrl,
        String modelApiKey,
        String modelName,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        Path workspaceDir,
        Duration bashTimeout,
        int maxToolOutputChars,
        int maxToolCallingRoundTrips,
        int chatMemoryMaxMessages,
        int tokenPersistBatchChars,
        String promptBundleResource,
        boolean requireApprovedPrompt,
        String promptBundleSha256
) {
    public static AppConfig fromEnv() {
        return new AppConfig(
                intEnv("PORT", 8080),
                env("MODEL_BASE_URL", "http://host.docker.internal:8000/v1"),
                env("MODEL_API_KEY", "not-needed"),
                env("MODEL_NAME", "nvidia/nemotron-3-super-120b-a12b"),
                env("DATABASE_URL", "jdbc:postgresql://postgres:5432/agent"),
                env("DATABASE_USER", "postgres"),
                env("DATABASE_PASSWORD", "postgres"),
                Path.of(env("WORKSPACE_DIR", "/workspace")).toAbsolutePath().normalize(),
                Duration.ofSeconds(intEnv("BASH_TIMEOUT_SECONDS", 20)),
                intEnv("MAX_TOOL_OUTPUT_CHARS", 6000),
                intEnv("MAX_TOOL_CALLING_ROUND_TRIPS", 20),
                intEnv("CHAT_MEMORY_MAX_MESSAGES", 30),
                intEnv("TOKEN_PERSIST_BATCH_CHARS", 1000),
                env("PROMPT_BUNDLE_RESOURCE", "prompt-bundles/coding-agent-v1.json"),
                boolEnv("REQUIRE_APPROVED_PROMPT", true),
                env("PROMPT_BUNDLE_SHA256", "")
        );
    }
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static boolean boolEnv(String name, boolean fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }
}
