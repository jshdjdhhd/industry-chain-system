package git.ggwzz.llm;

/**
 * OpenAI-compatible chat message。
 */
public record LlmMessage(String role, String content) {
}
