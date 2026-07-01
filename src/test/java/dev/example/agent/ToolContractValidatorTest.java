package dev.example.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolContractValidatorTest {

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
                        new PromptBundle.ToolPrompt("execute_bash", "Execute bash", List.of()),
                        new PromptBundle.ToolPrompt("web_search", "Search web", List.of()),
                        new PromptBundle.ToolPrompt("fetch_url", "Fetch URL", List.of())
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
                        new PromptBundle.ToolPrompt("execute_bash", "Execute bash", List.of())
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
                        new PromptBundle.ToolPrompt("execute_bash", "Execute bash", List.of()),
                        new PromptBundle.ToolPrompt("web_search", "Search web", List.of()),
                        new PromptBundle.ToolPrompt("fetch_url", "Fetch URL", List.of())
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