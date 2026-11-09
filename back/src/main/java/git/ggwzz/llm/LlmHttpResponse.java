package git.ggwzz.llm;

/**
 * LLM HTTP 响应。
 */
public record LlmHttpResponse(int statusCode, String body) {
}
