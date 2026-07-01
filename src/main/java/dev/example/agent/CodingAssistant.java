package dev.example.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface CodingAssistant {
    TokenStream chat(@MemoryId String sessionId, @UserMessage String message);

    Result<String> chatBlocking(@MemoryId String sessionId, @UserMessage String message);
}
