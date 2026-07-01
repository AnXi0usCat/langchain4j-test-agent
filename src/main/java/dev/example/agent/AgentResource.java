package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import java.util.Map;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource {
    private final AgentService agentService;
    private final AgentRepository repository;
    private final ObjectMapper mapper;
    private final AppConfig config;

    @Inject
    public AgentResource(AgentService agentService, AgentRepository repository, ObjectMapper mapper, AppConfig config) {
        this.agentService = agentService; this.repository = repository; this.mapper = mapper; this.config = config;
    }

    @GET @Path("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "agent", "langchain4j-coding-agent", "model", config.modelName(), "baseUrl", config.modelBaseUrl());
    }


    @POST @Path("/chat") @Consumes(MediaType.APPLICATION_JSON)
    public Object chat(ChatRequest request) {
        String sessionId = request == null ? "default" : request.safeSessionId();
        String message = request == null ? "" : request.message();
        return agentService.chat(sessionId, message);
    }

    @POST @Path("/chat/stream") @Consumes(MediaType.APPLICATION_JSON) @Produces("application/x-ndjson")
    public StreamingOutput stream(ChatRequest request) {
        return outputStream -> {
            Ndjson ndjson = new Ndjson(mapper, outputStream);
            String sessionId = request == null ? "default" : request.safeSessionId();
            String message = request == null ? "" : request.message();
            agentService.streamChat(sessionId, message, ndjson::event);
        };
    }

    @GET @Path("/runs")
    public Object runs(@QueryParam("limit") Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return repository.listRuns(safeLimit);
    }

    @GET @Path("/runs/{runId}/events")
    public Object runEvents(@PathParam("runId") String runId) { return repository.listEvents(runId); }
}
