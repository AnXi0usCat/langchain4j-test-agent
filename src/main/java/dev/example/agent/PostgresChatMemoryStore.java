package dev.example.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * Durable LangChain4j chat memory backed by Postgres.
 *
 * LangChain4j's MessageWindowChatMemory owns the memory policy: it decides
 * which messages remain in the window. This class only stores/retrieves the
 * current memory state for a memory ID, usually the API sessionId.
 */
public class PostgresChatMemoryStore implements ChatMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(PostgresChatMemoryStore.class);

    private final Jdbi jdbi;

    public PostgresChatMemoryStore(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String id = normalizeMemoryId(memoryId);

        String json = jdbi.withHandle(handle -> handle.createQuery("""
                        select messages::text
                        from agent_chat_memory
                        where memory_id = :memory_id
                        """)
                .bind("memory_id", id)
                .mapTo(String.class)
                .findOne()
                .orElse("[]"));

        try {
            return messagesFromJson(json);
        } catch (Exception e) {
            log.warn("Failed to deserialize chat memory for memory_id={}; resetting memory", id, e);
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = normalizeMemoryId(memoryId);
        String json = messagesToJson(messages == null ? List.of() : messages);

        jdbi.useHandle(handle -> handle.createUpdate("""
                        insert into agent_chat_memory (memory_id, messages, updated_at)
                        values (:memory_id, cast(:messages as jsonb), now())
                        on conflict (memory_id)
                        do update set messages = excluded.messages, updated_at = now()
                        """)
                .bind("memory_id", id)
                .bind("messages", json)
                .execute());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String id = normalizeMemoryId(memoryId);
        jdbi.useHandle(handle -> handle.createUpdate("""
                        delete from agent_chat_memory
                        where memory_id = :memory_id
                        """)
                .bind("memory_id", id)
                .execute());
    }

    private static String normalizeMemoryId(Object memoryId) {
        if (memoryId == null) {
            return "default";
        }

        String value = String.valueOf(memoryId).trim();
        return value.isEmpty() ? "default" : value;
    }
}
