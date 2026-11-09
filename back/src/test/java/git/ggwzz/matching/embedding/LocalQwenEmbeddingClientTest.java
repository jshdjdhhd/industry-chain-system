package git.ggwzz.matching.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalQwenEmbeddingClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void modelName_shouldUseLocalQwenOnly() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setModel("qwen3-embedding:0.6b");
        properties.setDimension(1024);

        LocalQwenEmbeddingClient client = new LocalQwenEmbeddingClient(properties, new ObjectMapper());

        assertEquals("LOCAL_QWEN:qwen3-embedding:0.6b", client.modelName());
        assertEquals(1024, client.dimension());
    }

    @Test
    void embed_shouldRequireLocalEndpointAndModel() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEndpoint("");
        properties.setModel("qwen3-embedding:0.6b");

        LocalQwenEmbeddingClient client = new LocalQwenEmbeddingClient(properties, new ObjectMapper());

        assertThrows(BusinessException.class, () -> client.embed("测试文本"));
    }

    @Test
    void embedBatch_shouldSendArrayInputAndParseMultipleVectors() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = startEmbeddingServer(requestBody);

        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEndpoint("http://localhost:" + server.getAddress().getPort() + "/v1/embeddings");
        properties.setModel("qwen3-embedding:0.6b");
        properties.setDimension(2);
        LocalQwenEmbeddingClient client = new LocalQwenEmbeddingClient(properties, new ObjectMapper());

        List<float[]> vectors = client.embedBatch(List.of("enterprise profile", "node profile"));

        assertEquals(2, vectors.size());
        assertEquals(0.1f, vectors.get(0)[0], 0.0001f);
        assertEquals(0.4f, vectors.get(1)[1], 0.0001f);
        assertTrue(requestBody.get().contains("\"input\":[\"enterprise profile\",\"node profile\"]"));
    }

    private HttpServer startEmbeddingServer(AtomicReference<String> requestBody) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/v1/embeddings", exchange -> handleEmbeddingRequest(exchange, requestBody));
        httpServer.start();
        return httpServer;
    }

    private void handleEmbeddingRequest(HttpExchange exchange,
                                        AtomicReference<String> requestBody) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = """
                {"data":[{"embedding":[0.1,0.2]},{"embedding":[0.3,0.4]}]}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
