package git.ggwzz.matching.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 Qwen embedding 客户端。
 *
 * <p>调用本机 Ollama 部署的 Qwen3 Embedding 推理服务，不接云端 embedding API。
 * 服务使用 OpenAI-compatible 的 {@code /v1/embeddings} 接口。</p>
 */
@Component
public class LocalQwenEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LocalQwenEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    @Override
    public String modelName() {
        return "LOCAL_QWEN:" + properties.getModel();
    }

    @Override
    public int dimension() {
        return properties.getDimension();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedBatch(List.of(text == null ? "" : text));
        if (vectors.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "本地 Qwen embedding 响应格式不正确");
        }
        return vectors.getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        validateConfig();
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            HttpRequest request = buildRequest(texts);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "本地 Qwen embedding 服务返回异常");
            }
            List<float[]> vectors = parseEmbeddings(response.body());
            if (vectors.size() != texts.size()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "本地 Qwen embedding 响应数量不正确");
            }
            return vectors;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "本地 Qwen embedding 服务不可用");
        }
    }

    private HttpRequest buildRequest(List<String> texts) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        if (texts.size() == 1) {
            body.put("input", texts.getFirst() == null ? "" : texts.getFirst());
        } else {
            body.set("input", objectMapper.valueToTree(texts.stream()
                    .map(text -> text == null ? "" : text)
                    .toList()));
        }
        if (properties.getDimension() > 0) {
            body.put("dimensions", properties.getDimension());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getEndpoint()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey().trim());
        }
        return builder.build();
    }

    private List<float[]> parseEmbeddings(String responseBody) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "本地 Qwen embedding 响应格式不正确");
        }
        List<float[]> vectors = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            vectors.add(parseEmbedding(item.path("embedding")));
        }
        return vectors;
    }

    private float[] parseEmbedding(JsonNode embedding) {
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "本地 Qwen embedding 响应格式不正确");
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }

    private void validateConfig() {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()
                || properties.getModel() == null || properties.getModel().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "本地 Qwen embedding endpoint 或 model 未配置");
        }
    }
}
