package dev.example.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires the {@link ToolSpecification}s declared in an approved {@link PromptBundle} - the
 * schema actually sent to the model - to real {@code @Tool}-annotated methods, by name,
 * using LangChain4j's own reflection-based {@link DefaultToolExecutor}.
 * <p>
 * This is the low-level counterpart to {@code AiServices.tools(Object...)}: instead of deriving
 * the tool schema from annotations, the schema comes from the prompt bundle, and annotations are
 * only used to locate the method that implements each declared tool.
 */
public final class ToolExecutorFactory {
    private ToolExecutorFactory() {}

    public static Map<ToolSpecification, ToolExecutor> build(PromptBundle bundle, Object toolsInstance) {
        Map<String, Method> methodsByToolName = new LinkedHashMap<>();
        for (Method method : toolsInstance.getClass().getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) continue;
            String name = tool.name().isBlank() ? method.getName() : tool.name();
            methodsByToolName.put(name, method);
        }

        Map<ToolSpecification, ToolExecutor> executors = new LinkedHashMap<>();
        for (ToolSpecification spec : bundle.tools()) {
            Method method = methodsByToolName.get(spec.name());
            if (method == null) {
                throw new IllegalStateException(
                        "Prompt bundle declares tool '" + spec.name()
                                + "' but no matching @Tool method was found on "
                                + toolsInstance.getClass().getName()
                );
            }
            executors.put(spec, new DefaultToolExecutor(toolsInstance, method));
        }
        return executors;
    }
}
