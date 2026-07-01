package dev.example.agent;

public record ChatRequest(String sessionId, String message) {
    public String safeSessionId() { return sessionId == null || sessionId.isBlank() ? "default" : sessionId; }
}
