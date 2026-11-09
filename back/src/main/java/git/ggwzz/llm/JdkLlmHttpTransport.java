package git.ggwzz.llm;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 基于 JDK HttpClient 的 LLM HTTP 传输实现。
 */
@Component
public class JdkLlmHttpTransport implements LlmHttpTransport {

    @Override
    public LlmHttpResponse postJson(URI uri, Map<String, String> headers, String body, Duration timeout)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new LlmHttpResponse(response.statusCode(), response.body());
    }

    @Override
    public void postJsonStreaming(URI uri, Map<String, String> headers, String body, Duration timeout, Consumer<String> onChunk)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        HttpResponse<java.util.stream.Stream<String>> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        response.body().forEach(line -> {
            if (!line.isBlank()) {
                onChunk.accept(line);
            }
        });
    }
}
