package dev.example.agent;

import dev.langchain4j.agent.tool.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class ToolContractValidator {
    private ToolContractValidator() {}

    public static void validatePromptBundleMatchesTools(PromptBundle bundle,
                                                        Class<?> toolClass,
                                                        String expectedToolSetId) {
        if (!expectedToolSetId.equals(bundle.toolSetId())) {
            throw new IllegalStateException(
                    "Prompt bundle toolSetId mismatch. expected="
                            + expectedToolSetId
                            + ", actual="
                            + bundle.toolSetId()
            );
        }

        Set<String> actualToolNames = Arrays.stream(toolClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(ToolContractValidator::toolName)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> expectedToolNames = bundle.toolNames();

        if (!actualToolNames.equals(expectedToolNames)) {
            throw new IllegalStateException(
                    "Prompt bundle tool names do not match Java tools. expected="
                            + expectedToolNames
                            + ", actual="
                            + actualToolNames
            );
        }
    }

    private static String toolName(Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        if (tool.name() != null && !tool.name().isBlank()) {
            return tool.name();
        }
        return method.getName();
    }
}