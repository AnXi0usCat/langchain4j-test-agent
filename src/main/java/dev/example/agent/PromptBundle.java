package dev.example.agent;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record PromptBundle(
        String id,
        int version,
        String toolSetId,
        boolean approved,
        String approvedBy,
        String systemPrompt,
        List<ToolPrompt> tools
) {
    public String versionedId() {
        return id + ":v" + version;
    }

    public Set<String> toolNames() {
        return tools.stream()
                .map(ToolPrompt::name)
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

        for (ToolPrompt tool : tools) {
            out.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append("\n");

            if (tool.parameters() != null && !tool.parameters().isEmpty()) {
                out.append("  Parameters:\n");
                for (ToolParameter parameter : tool.parameters()) {
                    out.append("  - ")
                            .append(parameter.name())
                            .append(parameter.required() ? " required" : " optional")
                            .append(": ")
                            .append(parameter.description())
                            .append("\n");
                }
            }
        }

        return out.toString();
    }

    public record ToolPrompt(
            String name,
            String description,
            List<ToolParameter> parameters
    ) {}

    public record ToolParameter(
            String name,
            String description,
            boolean required
    ) {}
}