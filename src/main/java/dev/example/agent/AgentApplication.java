package dev.example.agent;

import jakarta.ws.rs.core.Application;
import java.util.Set;

public class AgentApplication extends Application {
    @Override
    public Set<Object> getSingletons() {
        return Set.of(Main.injector.getInstance(AgentResource.class));
    }
}
