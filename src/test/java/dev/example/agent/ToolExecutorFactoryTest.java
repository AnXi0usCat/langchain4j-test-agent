package dev.example.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolExecutorFactoryTest {

    private static ToolSpecification tool(String name) {
        return ToolSpecification.builder().name(name).description(name).build();
    }

    @Test
    void buildsExecutorsForDeclaredToolsAndInvokesRealMethod() throws Exception {
        CodingTools tools = new CodingTools(TestConfigs.appConfig(Files.createTempDirectory("tool-executor-factory"), 1000));

        PromptBundle bundle = new PromptBundle(
                "coding-agent", 1, CodingTools.TOOL_SET_ID, true, "test", "system prompt",
                List.of(tool("execute_bash"), tool("web_search"), tool("fetch_url"))
        );

        Map<ToolSpecification, ToolExecutor> executors = ToolExecutorFactory.build(bundle, tools);

        assertThat(executors).hasSize(3);

        ToolExecutor bashExecutor = executors.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("execute_bash"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();

        String result = bashExecutor.execute(
                ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("execute_bash")
                        .arguments("{\"command\": \"echo hello-from-factory\"}")
                        .build(),
                "memory-1"
        );

        assertThat(result).contains("hello-from-factory");
    }

    @Test
    void rejectsBundleToolWithNoMatchingMethod() throws Exception {
        CodingTools tools = new CodingTools(TestConfigs.appConfig(Files.createTempDirectory("tool-executor-factory"), 1000));

        PromptBundle bundle = new PromptBundle(
                "coding-agent", 1, CodingTools.TOOL_SET_ID, true, "test", "system prompt",
                List.of(tool("does_not_exist"))
        );

        assertThrows(IllegalStateException.class, () -> ToolExecutorFactory.build(bundle, tools));
    }
}
