package dev.example.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record PromptBundle(
        String id,
        int version,
        String toolSetId,
        boolean approved,
        String approvedBy,
        String systemPrompt,
        List<ToolSpecification> tools
) {
    public String versionedId() {
        return id + ":v" + version;
    }

    public Set<String> toolNames() {
        return tools.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String systemMessageWithToolAppendix() {
        StringBuilder out = new StringBuilder();

        out.append(systemPrompt.strip()).append("\n\n");
        out.append("Approved prompt bundle: ")
                .append(versionedId())
                .append("\n");
        out.append("Approved tool set: ")
                .append(toolSetId)
                .append("\n\n");

        out.append("Available tools:\n");

        for (ToolSpecification tool : tools) {
            out.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append("\n");

            JsonObjectSchema parameters = tool.parameters();
            if (parameters != null && parameters.properties() != null && !parameters.properties().isEmpty()) {
                Set<String> required = parameters.required() == null
                        ? Set.of()
                        : new HashSet<>(parameters.required());

                out.append("  Parameters:\n");
                for (Map.Entry<String, JsonSchemaElement> property : parameters.properties().entrySet()) {
                    out.append("  - ")
                            .append(property.getKey())
                            .append(required.contains(property.getKey()) ? " required" : " optional")
                            .append(": ")
                            .append(property.getValue().description())
                            .append("\n");
                }
            }
        }

        return out.toString();
    }
}
