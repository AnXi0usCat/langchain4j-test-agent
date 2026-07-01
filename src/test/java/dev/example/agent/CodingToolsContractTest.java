package dev.example.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CodingToolsContractTest {

    @Test
    void toolSpecificationsExposeExpectedToolsAndParameters() throws Exception {
        CodingTools tools = new CodingTools(
                TestConfigs.appConfig(Files.createTempDirectory("agent-tools"), 1000)
        );

        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(tools);

        Map<String, ToolSpecification> byName = specs.stream()
                .collect(Collectors.toMap(ToolSpecification::name, spec -> spec));

        assertThat(byName.keySet())
                .containsExactlyInAnyOrder(
                        "execute_bash",
                        "web_search",
                        "fetch_url"
                );

        assertThat(byName.get("execute_bash").description())
                .containsIgnoringCase("bash");

        assertThat(byName.get("web_search").description())
                .containsIgnoringCase("web");

        assertThat(byName.get("fetch_url").description())
                .containsIgnoringCase("url");

        String executeBashParams = String.valueOf(byName.get("execute_bash").parameters());
        String webSearchParams = String.valueOf(byName.get("web_search").parameters());
        String fetchUrlParams = String.valueOf(byName.get("fetch_url").parameters());

        assertThat(executeBashParams).contains("command");
        assertThat(webSearchParams).contains("query");
        assertThat(webSearchParams).contains("maxResults");
        assertThat(fetchUrlParams).contains("url");
    }
}