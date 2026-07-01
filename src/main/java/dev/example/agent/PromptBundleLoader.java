package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

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

        PromptBundle bundle;
        try {
            bundle = objectMapper.readValue(bytes, PromptBundle.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse prompt bundle: " + resourcePath, e);
        }

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