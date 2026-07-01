package dev.example.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

final class ChatResponses {
    private ChatResponses() {}

    static ChatResponse text(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}