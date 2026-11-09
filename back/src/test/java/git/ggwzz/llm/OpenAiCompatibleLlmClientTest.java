package git.ggwzz.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void complete_shouldPostOpenAiCompatibleChatCompletionRequest() throws Exception {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("test-key");
        properties.setTemperature(0.2D);
        properties.setMaxTokens(800);
        CapturingTransport transport = new CapturingTransport(
                "{\"model\":\"mimo-v2.5-pro-ultraspeed\",\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, objectMapper, transport);

        LlmChatCompletion completion = client.complete(List.of(
                new LlmMessage("system", "只输出 JSON"),
                new LlmMessage("user", "给候选节点排序")
        ));

        assertEquals("mimo-v2.5-pro-ultraspeed", completion.model());
        assertEquals("{}", completion.content());
        assertEquals(URI.create("https://api.xiaomimimo.com/v1/chat/completions"), transport.uri);
        assertEquals("test-key", transport.headers.get("api-key"));
        assertEquals("application/json", transport.headers.get("Content-Type"));
        assertEquals(Duration.ofSeconds(20), transport.timeout);

        JsonNode body = objectMapper.readTree(transport.body);
        assertEquals("mimo-v2.5-pro-ultraspeed", body.path("model").asText());
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("只输出 JSON", body.path("messages").path(0).path("content").asText());
        assertEquals("user", body.path("messages").path(1).path("role").asText());
        assertEquals(0.2D, body.path("temperature").asDouble());
        assertEquals(800, body.path("max_completion_tokens").asInt());
        assertTrue(body.path("max_tokens").isMissingNode());
        assertEquals("disabled", body.path("thinking").path("type").asText());
        assertTrue(body.path("response_format").isMissingNode());
        assertEquals(false, body.path("stream").asBoolean());
    }

    @Test
    void complete_shouldUseReasoningContentWhenContentIsBlank() {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("test-key");
        CapturingTransport transport = new CapturingTransport(
                "{\"model\":\"mimo-v2.5-pro-ultraspeed\",\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"{\\\"ranked_results\\\":[]}\"}}]}");
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, objectMapper, transport);

        LlmChatCompletion completion = client.complete(List.of(
                new LlmMessage("user", "给候选节点排序")
        ));

        assertEquals("mimo-v2.5-pro-ultraspeed", completion.model());
        assertEquals("{\"ranked_results\":[]}", completion.content());
    }

    private static class CapturingTransport implements LlmHttpTransport {
        private final String responseBody;
        private URI uri;
        private Map<String, String> headers;
        private String body;
        private Duration timeout;

        private CapturingTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public LlmHttpResponse postJson(URI uri, Map<String, String> headers, String body, Duration timeout) {
            this.uri = uri;
            this.headers = headers;
            this.body = body;
            this.timeout = timeout;
            assertTrue(body.contains("给候选节点排序"));
            return new LlmHttpResponse(200, responseBody);
        }

        @Override
        public void postJsonStreaming(URI uri, Map<String, String> headers, String body, Duration timeout, Consumer<String> onChunk) {
            this.uri = uri;
            this.headers = headers;
            this.body = body;
            this.timeout = timeout;
            // 模拟流式响应
            onChunk.accept("data: {\"choices\":[{\"delta\":{\"content\":\"测试\"}}]}");
            onChunk.accept("data: [DONE]");
        }
    }
}
