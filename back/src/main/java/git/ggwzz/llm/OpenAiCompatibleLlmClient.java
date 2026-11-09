package git.ggwzz.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI-compatible 大模型客户端。
 *
 * <p>默认对接小米 MiMo，也可通过 base-url/model/api-key 切换到其他兼容厂商。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final LlmHttpTransport transport;

    @Override
    public LlmChatCompletion complete(List<LlmMessage> messages) {
        validateConfig();
        try {
            String requestBody = buildRequestBody(messages);
            LlmHttpResponse response = transport.postJson(
                    chatCompletionsUri(),
                    headers(),
                    requestBody,
                    Duration.ofSeconds(properties.getTimeoutSeconds()));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LLM 服务返回异常: status={}, body={}",
                        response.statusCode(), limit(response.body(), 500));
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 服务返回异常");
            }
            return parseCompletion(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("LLM 服务调用失败: type={}, message={}",
                    ex.getClass().getSimpleName(), limit(ex.getMessage(), 240));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 服务不可用");
        }
    }

    @Override
    public void completeStreaming(List<LlmMessage> messages, Consumer<String> onChunk) {
        validateConfig();
        try {
            String requestBody = buildStreamingRequestBody(messages);
            transport.postJsonStreaming(
                    chatCompletionsUri(),
                    headers(),
                    requestBody,
                    Duration.ofSeconds(properties.getTimeoutSeconds()),
                    line -> parseStreamingLine(line, onChunk));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("LLM 流式调用失败: type={}, message={}",
                    ex.getClass().getSimpleName(), limit(ex.getMessage(), 240));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 服务不可用");
        }
    }

    private void parseStreamingLine(String line, Consumer<String> onChunk) {
        // SSE 格式: data: {...}\n\n 或 data: [DONE]\n\n
        if (!line.startsWith("data:")) {
            return;
        }
        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode delta = root.path("choices").path(0).path("delta");
            String content = delta.path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                onChunk.accept(content);
            }
        } catch (Exception ex) {
            log.debug("解析流式响应失败: {}", data);
        }
    }

    private String buildRequestBody(List<LlmMessage> messages) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        ArrayNode messageArray = body.putArray("messages");
        for (LlmMessage message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        body.put("temperature", properties.getTemperature());
        body.put("max_completion_tokens", properties.getMaxTokens());
        body.put("stream", false);
        ObjectNode thinking = body.putObject("thinking");
        thinking.put("type", "disabled");
        // 去掉 response_format，让 LLM 自由输出分析文本
        return objectMapper.writeValueAsString(body);
    }

    private String buildStreamingRequestBody(List<LlmMessage> messages) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        ArrayNode messageArray = body.putArray("messages");
        for (LlmMessage message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        body.put("temperature", properties.getTemperature());
        body.put("max_completion_tokens", properties.getMaxTokens());
        body.put("stream", true);
        ObjectNode thinking = body.putObject("thinking");
        thinking.put("type", "disabled");
        return objectMapper.writeValueAsString(body);
    }

    private LlmChatCompletion parseCompletion(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");
        // 优先取 content，其次取 reasoning_content（思考模型可能把正文放在 reasoning_content）
        String content = message.path("content").asText(null);
        if (content == null || content.isBlank()) {
            content = message.path("reasoning_content").asText(null);
        }
        if (content == null || content.isBlank()) {
            log.warn("LLM 响应无 content 字段: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 响应格式不正确");
        }
        log.debug("LLM 返回内容前200字符: {}", content.length() > 200 ? content.substring(0, 200) : content);
        String model = root.path("model").asText(properties.getModel());
        return new LlmChatCompletion(model, content);
    }

    private URI chatCompletionsUri() {
        String baseUrl = properties.getBaseUrl().trim();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        return URI.create(baseUrl + "chat/completions");
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("api-key", properties.getApiKey().trim());
        return headers;
    }

    private void validateConfig() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getModel() == null || properties.getModel().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "LLM base-url 或 model 未配置");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "LLM API Key 未配置");
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
