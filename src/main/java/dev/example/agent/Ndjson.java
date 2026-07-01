package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Ndjson {
    private final ObjectMapper mapper;
    private final OutputStream outputStream;
    public Ndjson(ObjectMapper mapper, OutputStream outputStream) { this.mapper = mapper; this.outputStream = outputStream; }
    public synchronized void event(String event, Object data) {
        try {
            byte[] bytes = mapper.writeValueAsString(Map.of("event", event, "data", data)).getBytes(StandardCharsets.UTF_8);
            outputStream.write(bytes);
            outputStream.write('\n');
            outputStream.flush();
        } catch (IOException e) { throw new RuntimeException(e); }
    }
}
