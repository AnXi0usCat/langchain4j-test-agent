package dev.example.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptBundleLoaderTest {

    private final PromptBundleLoader loader = new PromptBundleLoader(new ObjectMapper());

    @Test
    void loadsApprovedPromptBundle() {
        PromptBundle bundle = loader.load(
                "prompt-bundles/test-approved.json",
                true,
                ""
        );

        assertEquals("coding-agent", bundle.id());
        assertEquals(1, bundle.version());
        assertTrue(bundle.approved());
        assertEquals("coding-tools-v1", bundle.toolSetId());
        assertTrue(bundle.systemMessageWithToolAppendix().contains("Available tools:"));
    }

    @Test
    void rejectsUnapprovedPromptBundleWhenApprovalRequired() {
        assertThrows(IllegalStateException.class, () ->
                loader.load(
                        "prompt-bundles/test-unapproved.json",
                        true,
                        ""
                )
        );
    }

    @Test
    void allowsUnapprovedPromptBundleWhenApprovalNotRequired() {
        PromptBundle bundle = loader.load(
                "prompt-bundles/test-unapproved.json",
                false,
                ""
        );

        assertFalse(bundle.approved());
    }

    @Test
    void rejectsWrongHash() {
        assertThrows(IllegalStateException.class, () ->
                loader.load(
                        "prompt-bundles/test-approved.json",
                        true,
                        "not-the-real-sha"
                )
        );
    }
}