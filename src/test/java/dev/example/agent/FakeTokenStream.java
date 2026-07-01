package dev.example.agent;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FakeTokenStream implements TokenStream {
    private final List<String> tokens = new ArrayList<>();
    private ChatResponse completeResponse;
    private Throwable error;

    private Consumer<String> partialResponseHandler;
    private Consumer<List<Content>> retrievedHandler;
    private Consumer<ToolExecution> toolExecutedHandler;
    private Consumer<ChatResponse> completeResponseHandler;
    private Consumer<Throwable> errorHandler;

    public static FakeTokenStream success(String... tokens) {
        FakeTokenStream stream = new FakeTokenStream();
        stream.tokens.addAll(List.of(tokens));
        stream.completeResponse = ChatResponses.text(String.join("", tokens));
        return stream;
    }

    public static FakeTokenStream failure(Throwable error) {
        FakeTokenStream stream = new FakeTokenStream();
        stream.error = error;
        return stream;
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> retrievedHandler) {
        this.retrievedHandler = retrievedHandler;
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecutedHandler) {
        this.toolExecutedHandler = toolExecutedHandler;
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
        this.completeResponseHandler = completeResponseHandler;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        return this;
    }

    @Override
    public void start() {
        if (error != null) {
            if (errorHandler != null) {
                errorHandler.accept(error);
            }
            return;
        }

        if (partialResponseHandler != null) {
            for (String token : tokens) {
                partialResponseHandler.accept(token);
            }
        }

        if (completeResponseHandler != null) {
            completeResponseHandler.accept(completeResponse);
        }
    }
}