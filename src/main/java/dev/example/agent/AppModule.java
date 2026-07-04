package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Duration;

public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AppConfig.class).toInstance(AppConfig.fromEnv());
        bind(CodingTools.class).in(Singleton.class);
        bind(AgentService.class).in(Singleton.class);
        bind(AgentResource.class).in(Singleton.class);
        bind(AgentRepository.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Provides
    @Singleton
    DataSource dataSource(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.databaseUrl());
        hikari.setUsername(config.databaseUser());
        hikari.setPassword(config.databasePassword());
        hikari.setMaximumPoolSize(10);
        hikari.setMinimumIdle(2);
        hikari.setPoolName("agent-postgres");
        return new HikariDataSource(hikari);
    }

    @Provides
    @Singleton
    Jdbi jdbi(DataSource dataSource) {
        return Jdbi.create(dataSource).installPlugin(new PostgresPlugin());
    }

    @Provides
    @Singleton
    ChatMemoryStore chatMemoryStore(Jdbi jdbi) {
        return new PostgresChatMemoryStore(jdbi);
    }

    @Provides
    @Singleton
    PromptBundle promptBundle(ObjectMapper objectMapper, AppConfig config) {
        PromptBundle bundle = new PromptBundleLoader(objectMapper).load(
                config.promptBundleResource(),
                config.requireApprovedPrompt(),
                config.promptBundleSha256()
        );

        ToolContractValidator.validatePromptBundleMatchesTools(
                bundle,
                CodingTools.class,
                CodingTools.TOOL_SET_ID
        );

        return bundle;
    }

    @Provides
    @Singleton
    StreamingChatModel streamingChatModel(AppConfig config) {
        // 1. Create a native Java HttpClient builder forced strictly to HTTP/1.1
        HttpClient.Builder nativeClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);

        // 2. Clear out the restricted headers, but keep the simple streaming flag
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("fallback-to-simple-streaming", "true");

        return OpenAiStreamingChatModel.builder()
                .baseUrl(config.modelBaseUrl())
                .apiKey(config.modelApiKey())
                .modelName(config.modelName())
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .customHeaders(headers)
                .httpClientBuilder(dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                        .httpClientBuilder(nativeClientBuilder))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Provides
    @Singleton
    ChatModel chatModel(AppConfig config) {
        HttpClient.Builder nativeClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);

        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("fallback-to-simple-streaming", "true");

        return OpenAiChatModel.builder()
                .baseUrl(config.modelBaseUrl())
                .apiKey(config.modelApiKey())
                .modelName(config.modelName())
                .temperature(0.2)
                .timeout(Duration.ofSeconds(120))
                .customHeaders(headers)
                .httpClientBuilder(dev.langchain4j.http.client.jdk.JdkHttpClient.builder()
                        .httpClientBuilder(nativeClientBuilder))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Provides
    @Singleton
    CodingAssistant codingAssistant(StreamingChatModel streamingModel,
                                    ChatModel chatModel,
                                    CodingTools tools,
                                    AppConfig config,
                                    ChatMemoryStore chatMemoryStore,
                                    PromptBundle promptBundle) {
        return AiServices.builder(CodingAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> promptBundle.systemMessageWithToolAppendix())
                .tools(ToolExecutorFactory.build(promptBundle, tools))
                .toolArgumentsErrorHandler((error, errorContext) -> ToolErrorHandlerResult.text(error.getMessage()))
                .toolExecutionErrorHandler((error, errorContext) -> ToolErrorHandlerResult.text("Tool execution failed."))
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(config.chatMemoryMaxMessages())
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .maxToolCallingRoundTrips(config.maxToolCallingRoundTrips())
                .build();
    }
}
