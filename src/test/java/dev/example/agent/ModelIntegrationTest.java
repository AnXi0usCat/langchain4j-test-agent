package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ModelIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("agent")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Test
    void createsHelloPyRunsToolAndCompletes() throws Exception {
        Path workspace = Files.createTempDirectory("model-it-workspace");

        AppConfig config = new AppConfig(
                8080,
                env("MODEL_BASE_URL", "http://localhost:8000/v1"),
                env("MODEL_API_KEY", "not-needed"),
                env("MODEL_NAME", "nemotron-3-super"),
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword(),
                workspace,
                Duration.ofSeconds(20),
                12_000,
                30,
                30,
                1000,
                "prompt-bundles/coding-agent-v1.json",
                false,
                ""
        );

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.databaseUrl());
        hikari.setUsername(config.databaseUser());
        hikari.setPassword(config.databasePassword());

        try (HikariDataSource ds = new HikariDataSource(hikari)) {
            Jdbi jdbi = Jdbi.create(ds).installPlugin(new PostgresPlugin());
            AgentRepository repository = new AgentRepository(jdbi, mapper);
            repository.migrate();

            ChatMemoryStore memoryStore = new PostgresChatMemoryStore(jdbi);
            CodingTools tools = new CodingTools(config);

            CodingAssistant assistant = buildAssistant(config, tools, memoryStore);

            AgentService service = new AgentService(assistant, repository, config);

            Map<String, Object> response = service.chat(
                    "model-it-hello",
                    "Use bash to inspect the workspace. Create hello.py that prints hello from Java LangChain4j, run it, and summarize what happened."
            );

            assertThat(response.get("status")).isEqualTo("completed");
            assertThat(response.get("answer").toString()).containsIgnoringCase("hello");

            assertThat(workspace.resolve("hello.py")).exists();

            List<Map<String, Object>> runs = repository.listRuns(10);
            assertThat(runs).isNotEmpty();

            String runId = runs.getFirst().get("id").toString();
            List<Map<String, Object>> events = repository.listEvents(runId);
            System.out.println("____________________________________");
            System.out.println(events);

            assertThat(events.stream().map(e -> e.get("event_type").toString()).toList())
                    .contains("start", "complete");

            assertThat(events.stream()
                    .anyMatch(e -> "tool_executed".equals(e.get("event_type").toString())
                            && e.get("payload").toString().contains("execute_bash")))
                    .isTrue();
        }
    }

    private static CodingAssistant buildAssistant(AppConfig config,
                                                  CodingTools tools,
                                                  ChatMemoryStore memoryStore) {
        HttpClient.Builder nativeClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);

        Map<String, String> headers = new HashMap<>();
        headers.put("fallback-to-simple-streaming", "true");

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(config.modelBaseUrl())
                .apiKey(config.modelApiKey())
                .modelName(config.modelName())
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .customHeaders(headers)
                .httpClientBuilder(dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                        .httpClientBuilder(nativeClientBuilder))
                .logRequests(true)
                .logResponses(true)
                .build();

        StreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(config.modelBaseUrl())
                .apiKey(config.modelApiKey())
                .modelName(config.modelName())
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .customHeaders(headers)
                .httpClientBuilder(dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                        .httpClientBuilder(nativeClientBuilder))
                .logRequests(true)
                .logResponses(true)
                .build();

        return AiServices.builder(CodingAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(config.chatMemoryMaxMessages())
                        .chatMemoryStore(memoryStore)
                        .build())
                .maxToolCallingRoundTrips(config.maxToolCallingRoundTrips())
                .build();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}