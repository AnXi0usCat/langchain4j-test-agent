package dev.example.agent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    @Test
    void blockingChatPersistsRunAndReturnsAnswer() throws Exception {
        CodingAssistant assistant = mock(CodingAssistant.class);
        AgentRepository repository = mock(AgentRepository.class);

        UUID runId = UUID.randomUUID();
        when(repository.createRun("s1", "hello")).thenReturn(runId);
        //when(assistant.chatBlocking("s1", "hello")).thenReturn("hi there");

        AppConfig config = TestConfigs.appConfig(Files.createTempDirectory("agent-test"), 1000);
        AgentService service = new AgentService(assistant, repository, config);

        Map<String, Object> response = service.chat("s1", "hello");

        assertThat(response)
                .containsEntry("runId", runId.toString())
                .containsEntry("sessionId", "s1")
                .containsEntry("status", "completed")
                .containsEntry("answer", "hi there");

        verify(assistant).chatBlocking("s1", "hello");
        verify(repository).createRun("s1", "hello");
        verify(repository).addEvent(eq(runId), eq("start"), any());
        verify(repository).addEvent(eq(runId), eq("complete"), any());
        verify(repository).completeRun(runId, "completed");
    }

    @Test
    void blockingChatPersistsFailure() throws Exception {
        CodingAssistant assistant = mock(CodingAssistant.class);
        AgentRepository repository = mock(AgentRepository.class);

        UUID runId = UUID.randomUUID();
        when(repository.createRun("s1", "boom")).thenReturn(runId);
        when(assistant.chatBlocking("s1", "boom"))
                .thenThrow(new RuntimeException("model unavailable"));

        AppConfig config = TestConfigs.appConfig(Files.createTempDirectory("agent-test"), 1000);
        AgentService service = new AgentService(assistant, repository, config);

        Map<String, Object> response = service.chat("s1", "boom");

        assertThat(response)
                .containsEntry("runId", runId.toString())
                .containsEntry("sessionId", "s1")
                .containsEntry("status", "failed");

        verify(repository).addEvent(eq(runId), eq("error"), any());
        verify(repository).completeRun(runId, "failed");
    }

    @Test
    void streamingChatEmitsTokensAndBatchesTokenPersistence() throws Exception {
        CodingAssistant assistant = mock(CodingAssistant.class);
        AgentRepository repository = mock(AgentRepository.class);

        UUID runId = UUID.randomUUID();
        when(repository.createRun("s-stream", "stream me")).thenReturn(runId);
        when(assistant.chat("s-stream", "stream me"))
                .thenReturn(FakeTokenStream.success("ab", "cd", "ef"));

        // batch at 4 chars:
        //   "ab" + "cd" => token_batch "abcd"
        //   "ef" flushed on complete => token_batch "ef"
        AppConfig config = TestConfigs.appConfig(Files.createTempDirectory("agent-test"), 4);
        AgentService service = new AgentService(assistant, repository, config);

        List<Map.Entry<String, Object>> emitted = new ArrayList<>();

        service.streamChat(
                "s-stream",
                "stream me",
                (event, data) -> emitted.add(Map.entry(event, data))
        );

        assertThat(emitted.stream().map(Map.Entry::getKey).toList())
                .contains("start", "token", "token", "token", "complete");

        verify(assistant).chat("s-stream", "stream me");
        verify(repository).createRun("s-stream", "stream me");

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeastOnce()).addEvent(eq(runId), eventTypeCaptor.capture(), any());

        assertThat(eventTypeCaptor.getAllValues())
                .contains("start", "token_batch", "complete");

        // We expect 2 token batches from the batching behavior.
        long tokenBatchCount = eventTypeCaptor.getAllValues().stream()
                .filter("token_batch"::equals)
                .count();

        assertThat(tokenBatchCount).isEqualTo(2);

        verify(repository).completeRun(runId, "completed");
    }

    @Test
    void streamingChatPersistsError() throws Exception {
        CodingAssistant assistant = mock(CodingAssistant.class);
        AgentRepository repository = mock(AgentRepository.class);

        UUID runId = UUID.randomUUID();
        when(repository.createRun("s-stream", "fail")).thenReturn(runId);
        when(assistant.chat("s-stream", "fail"))
                .thenReturn(FakeTokenStream.failure(new RuntimeException("stream failed")));

        AppConfig config = TestConfigs.appConfig(Files.createTempDirectory("agent-test"), 1000);
        AgentService service = new AgentService(assistant, repository, config);

        List<Map.Entry<String, Object>> emitted = new ArrayList<>();

        service.streamChat(
                "s-stream",
                "fail",
                (event, data) -> emitted.add(Map.entry(event, data))
        );

        assertThat(emitted.stream().map(Map.Entry::getKey).toList())
                .contains("start", "error");

        verify(repository).addEvent(eq(runId), eq("error"), any());
        verify(repository).completeRun(runId, "failed");
    }
}