package dev.example.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentRepository {
    private final Jdbi jdbi;
    private final ObjectMapper mapper;
    @Inject
    public AgentRepository(Jdbi jdbi, ObjectMapper mapper) { this.jdbi = jdbi; this.mapper = mapper; }

    public void migrate() {
        jdbi.useHandle(handle -> {
            handle.execute("""
                    create table if not exists agent_runs (
                      id uuid primary key,
                      session_id text not null,
                      user_message text not null,
                      status text not null,
                      created_at timestamptz not null default now(),
                      completed_at timestamptz
                    )
                    """);
            handle.execute("""
                    create table if not exists agent_events (
                      id bigserial primary key,
                      run_id uuid not null references agent_runs(id) on delete cascade,
                      event_type text not null,
                      payload jsonb not null,
                      created_at timestamptz not null default now()
                    )
                    """);

            handle.execute("""
                    create table if not exists agent_chat_memory (
                      memory_id text primary key,
                      messages jsonb not null,
                      updated_at timestamptz not null default now()
                    )
                    """);
            handle.execute("create index if not exists idx_agent_chat_memory_updated_at on agent_chat_memory(updated_at desc)");
            handle.execute("create index if not exists idx_agent_runs_created_at on agent_runs(created_at desc)");
            handle.execute("create index if not exists idx_agent_events_run_id on agent_events(run_id, id)");
        });
    }

    public UUID createRun(String sessionId, String userMessage) {
        UUID id = UUID.randomUUID();
        jdbi.useHandle(handle -> handle.createUpdate("""
                        insert into agent_runs (id, session_id, user_message, status)
                        values (:id, :session_id, :user_message, 'running')
                        """)
                .bind("id", id).bind("session_id", sessionId).bind("user_message", userMessage == null ? "" : userMessage).execute());
        return id;
    }

    public void completeRun(UUID runId, String status) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                        update agent_runs set status = :status, completed_at = now() where id = :id
                        """)
                .bind("id", runId).bind("status", status).execute());
    }

    public void addEvent(UUID runId, String eventType, Object payload) {
        jdbi.useHandle(handle -> handle.createUpdate("""
                        insert into agent_events (run_id, event_type, payload)
                        values (:run_id, :event_type, cast(:payload as jsonb))
                        """)
                .bind("run_id", runId).bind("event_type", eventType).bind("payload", toJson(payload)).execute());
    }

    public List<Map<String, Object>> listRuns(int limit) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        select id::text as id, session_id, user_message, status, created_at, completed_at
                        from agent_runs order by created_at desc limit :limit
                        """)
                .bind("limit", limit).mapToMap().list());
    }

    public List<Map<String, Object>> listEvents(String runId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        select id, run_id::text as run_id, event_type, payload::text as payload, created_at
                        from agent_events where run_id = cast(:run_id as uuid) order by id asc
                        """)
                .bind("run_id", runId).mapToMap().list());
    }

    private String toJson(Object payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (JsonProcessingException e) { return "{\"error\":\"failed to serialize payload\"}"; }
    }
}
