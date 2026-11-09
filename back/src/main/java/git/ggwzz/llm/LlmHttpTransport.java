package git.ggwzz.llm;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM HTTP 传输层抽象，隔离真实网络调用以便测试。
 */
public interface LlmHttpTransport {

    /**
     * 发送 JSON POST 请求。
     */
    LlmHttpResponse postJson(URI uri, Map<String, String> headers, String body, Duration timeout) throws Exception;

    /**
     * 发送流式 JSON POST 请求（SSE）。
     *
     * @param uri      请求地址
     * @param headers  请求头
     * @param body     请求体
     * @param timeout  超时时间
     * @param onChunk  收到每个 chunk 时的回调
     */
    void postJsonStreaming(URI uri, Map<String, String> headers, String body, Duration timeout, Consumer<String> onChunk) throws Exception;
}
