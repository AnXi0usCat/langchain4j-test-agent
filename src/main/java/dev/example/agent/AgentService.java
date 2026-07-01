package dev.example.agent;

import com.google.inject.Inject;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final CodingAssistant assistant;
    private final AgentRepository repository;
    private final AppConfig config;

    @Inject
    public AgentService(CodingAssistant assistant, AgentRepository repository, AppConfig config) {
        this.assistant = assistant;
        this.repository = repository;
        this.config = config;
    }

    public Map<String, Object> chat(String sessionId, String message) {
        UUID runId = repository.createRun(sessionId, message);
        repository.addEvent(runId, "start", Map.of("sessionId", sessionId, "mode", "blocking"));

        try {
            Result<String> result = assistant.chatBlocking(sessionId, message == null ? "" : message);
            List<ToolExecution> toolExecutions = result.toolExecutions();

            if (!toolExecutions.isEmpty()) {
                for(ToolExecution toolExecution: toolExecutions) {
                    String text = String.valueOf(toolExecution);
                    repository.addEvent(runId, "tool_executed", Map.of("value", text));
                }
            }

            String answer = result.content();
            repository.addEvent(runId, "complete", Map.of("text", answer == null ? "" : answer));
            repository.completeRun(runId, "completed");

            return Map.of(
                    "runId", runId.toString(),
                    "sessionId", sessionId,
                    "status", "completed",
                    "answer", answer == null ? "" : answer
            );
        } catch (Exception e) {
            log.warn("Agent request failed", e);
            String errorMessage = e.getMessage() == null ? "" : e.getMessage();
            repository.addEvent(runId, "error", Map.of("type", e.getClass().getSimpleName(), "message", errorMessage));
            repository.completeRun(runId, "failed");

            return Map.of(
                    "runId", runId.toString(),
                    "sessionId", sessionId,
                    "status", "failed",
                    "error", Map.of("type", e.getClass().getSimpleName(), "message", errorMessage)
            );
        }
    }

    public void streamChat(String sessionId, String message, BiConsumer<String, Object> emit) {
        UUID runId = repository.createRun(sessionId, message);
        emit.accept("start", Map.of("runId", runId.toString(), "sessionId", sessionId, "createdAt", Instant.now().toString()));
        repository.addEvent(runId, "start", Map.of("sessionId", sessionId, "mode", "streaming"));

        TokenBatch tokenBatch = new TokenBatch(config.tokenPersistBatchChars(), text ->
                repository.addEvent(runId, "token_batch", Map.of("text", text))
        );

        CompletableFuture<Void> done = new CompletableFuture<>();

        try {
            TokenStream stream = assistant.chat(sessionId, message == null ? "" : message);
            stream.onPartialResponse(token -> {
                        emit.accept("token", Map.of("text", token));
                        tokenBatch.append(token);
                    })
                    .onPartialToolCall(partial -> {
                        tokenBatch.flush();
                        String text = String.valueOf(partial);
                        emit.accept("partial_tool_call", Map.of("value", text));
                        repository.addEvent(runId, "partial_tool_call", Map.of("value", text));
                    })
                    .beforeToolExecution(before -> {
                        tokenBatch.flush();
                        String text = String.valueOf(before);
                        emit.accept("before_tool", Map.of("value", text));
                        repository.addEvent(runId, "before_tool", Map.of("value", text));
                    })
                    .onToolExecuted(toolExecution -> {
                        tokenBatch.flush();
                        String text = String.valueOf(toolExecution);
                        emit.accept("tool_executed", Map.of("value", text));
                        repository.addEvent(runId, "tool_executed", Map.of("value", text));
                    })
                    .onCompleteResponse(response -> {
                        tokenBatch.flush();
                        String text = responseText(response);
                        emit.accept("complete", Map.of("text", text, "raw", String.valueOf(response)));
                        repository.addEvent(runId, "complete", Map.of("text", text, "raw", String.valueOf(response)));
                        repository.completeRun(runId, "completed");
                        done.complete(null);
                    })
                    .onError(error -> {
                        tokenBatch.flush();
                        log.warn("Agent stream failed", error);
                        String errorMessage = error.getMessage() == null ? "" : error.getMessage();
                        emit.accept("error", Map.of("type", error.getClass().getSimpleName(), "message", errorMessage));
                        repository.addEvent(runId, "error", Map.of("type", error.getClass().getSimpleName(), "message", errorMessage));
                        repository.completeRun(runId, "failed");
                        done.complete(null);
                    })
                    .start();

            done.get(30, TimeUnit.MINUTES);
        } catch (Exception e) {
            tokenBatch.flush();
            log.warn("Agent request failed", e);
            String errorMessage = e.getMessage() == null ? "" : e.getMessage();
            emit.accept("error", Map.of("type", e.getClass().getSimpleName(), "message", errorMessage));
            repository.addEvent(runId, "error", Map.of("type", e.getClass().getSimpleName(), "message", errorMessage));
            repository.completeRun(runId, "failed");
        }
    }

    private static String responseText(ChatResponse response) {
        if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
            return response == null ? "" : String.valueOf(response);
        }

        return response.aiMessage().text();
    }

    private static final class TokenBatch {
        private final int batchChars;
        private final java.util.function.Consumer<String> persist;
        private final StringBuilder buffer = new StringBuilder();

        private TokenBatch(int batchChars, java.util.function.Consumer<String> persist) {
            this.batchChars = Math.max(1, batchChars);
            this.persist = persist;
        }

        synchronized void append(String token) {
            if (token == null || token.isEmpty()) {
                return;
            }

            buffer.append(token);
            if (buffer.length() >= batchChars) {
                flush();
            }
        }

        synchronized void flush() {
            if (buffer.isEmpty()) {
                return;
            }

            String text = buffer.toString();
            buffer.setLength(0);
            persist.accept(text);
        }
    }
}
