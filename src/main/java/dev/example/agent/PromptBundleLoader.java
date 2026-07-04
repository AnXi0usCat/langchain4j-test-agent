package dev.example.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class PromptBundleLoader {
    private final ObjectMapper objectMapper;

    public PromptBundleLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PromptBundle load(String resourcePath,
                             boolean requireApproved,
                             String expectedSha256) {
        byte[] bytes = readResource(resourcePath);

        String actualSha256 = sha256(bytes);
        if (expectedSha256 != null && !expectedSha256.isBlank()
                && !actualSha256.equalsIgnoreCase(expectedSha256.trim())) {
            throw new IllegalStateException(
                    "Prompt bundle SHA-256 mismatch for " + resourcePath
                            + ". expected=" + expectedSha256
                            + ", actual=" + actualSha256
            );
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse prompt bundle: " + resourcePath, e);
        }

        PromptBundle bundle = new PromptBundle(
                root.path("id").asText(null),
                root.path("version").asInt(0),
                root.path("toolSetId").asText(null),
                root.path("approved").asBoolean(false),
                root.path("approvedBy").asText(null),
                root.path("systemPrompt").asText(null),
                toolsFrom(root.path("tools"), resourcePath)
        );

        if (requireApproved && !bundle.approved()) {
            throw new IllegalStateException(
                    "Prompt bundle is not approved: " + bundle.versionedId()
            );
        }

        if (bundle.systemPrompt() == null || bundle.systemPrompt().isBlank()) {
            throw new IllegalStateException("Prompt bundle has an empty systemPrompt");
        }

        if (bundle.tools() == null || bundle.tools().isEmpty()) {
            throw new IllegalStateException("Prompt bundle has no tools");
        }

        return bundle;
    }

    /**
     * Each entry in the bundle's "tools" array is a standard tool-calling JSON Schema
     * document (name/description/parameters), so it is parsed directly into the same
     * {@link ToolSpecification} that is sent to the model - the bundle is the schema,
     * not a separate description of it.
     */
    private List<ToolSpecification> toolsFrom(JsonNode toolsNode, String resourcePath) {
        List<ToolSpecification> tools = new ArrayList<>();
        for (JsonNode toolNode : toolsNode) {
            try {
                tools.add(ToolSpecification.fromJson(toolNode.toString()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Invalid tool definition in prompt bundle: " + resourcePath, e
                );
            }
        }
        return tools;
    }

    private static byte[] readResource(String resourcePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256", e);
        }
    }
}
