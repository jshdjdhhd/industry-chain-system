package git.ggwzz.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型调用抽象，便于切换不同 OpenAI-compatible 厂商。
 */
public interface LlmClient {

    /**
     * 执行聊天补全。
     *
     * @param messages 消息列表
     * @return 补全结果
     */
    LlmChatCompletion complete(List<LlmMessage> messages);

    /**
     * 执行流式聊天补全。
     *
     * @param messages 消息列表
     * @param onChunk  收到每个文本片段时的回调
     */
    void completeStreaming(List<LlmMessage> messages, Consumer<String> onChunk);
}
