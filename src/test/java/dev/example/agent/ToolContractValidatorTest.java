package dev.example.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolContractValidatorTest {

    private static ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder().name(name).description(description).build();
    }

    @Test
    void acceptsMatchingToolSet() {
        PromptBundle bundle = new PromptBundle(
                "coding-agent",
                1,
                CodingTools.TOOL_SET_ID,
                true,
                "test",
                "system prompt",
                List.of(
                        tool("execute_bash", "Execute bash"),
                        tool("web_search", "Search web"),
                        tool("fetch_url", "Fetch URL")
                )
        );

        assertDoesNotThrow(() ->
                ToolContractValidator.validatePromptBundleMatchesTools(
                        bundle,
                        CodingTools.class,
                        CodingTools.TOOL_SET_ID
                )
        );
    }

    @Test
    void rejectsMissingTool() {
        PromptBundle bundle = new PromptBundle(
                "coding-agent",
                1,
                CodingTools.TOOL_SET_ID,
                true,
                "test",
                "system prompt",
                List.of(
                        tool("execute_bash", "Execute bash")
                )
        );

        assertThrows(IllegalStateException.class, () ->
                ToolContractValidator.validatePromptBundleMatchesTools(
                        bundle,
                        CodingTools.class,
                        CodingTools.TOOL_SET_ID
                )
        );
    }

    @Test
    void rejectsWrongToolSetId() {
        PromptBundle bundle = new PromptBundle(
                "coding-agent",
                1,
                "wrong-tool-set",
                true,
                "test",
                "system prompt",
                List.of(
                        tool("execute_bash", "Execute bash"),
                        tool("web_search", "Search web"),
                        tool("fetch_url", "Fetch URL")
                )
        );

        assertThrows(IllegalStateException.class, () ->
                ToolContractValidator.validatePromptBundleMatchesTools(
                        bundle,
                        CodingTools.class,
                        CodingTools.TOOL_SET_ID
                )
        );
    }
}