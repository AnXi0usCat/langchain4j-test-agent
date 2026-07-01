package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBundleContractTest {

    @Test
    void approvedPromptMentionsOperationalRules() {
        PromptBundle bundle = new PromptBundleLoader(new ObjectMapper()).load(
                "prompt-bundles/coding-agent-v1.json",
                true,
                ""
        );

        String system = bundle.systemMessageWithToolAppendix();

        assertTrue(system.contains("Only modify files under /workspace"));
        assertTrue(system.contains("execute_bash"));
        assertTrue(system.contains("web_search"));
        assertTrue(system.contains("fetch_url"));
        assertTrue(system.contains("Available tools:"));
    }
}
