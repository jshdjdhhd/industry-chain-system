package git.ggwzz.matching.service;

import git.ggwzz.matching.dto.AiChatRequest;
import git.ggwzz.matching.vo.AiChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {
    AiChatResponse chat(AiChatRequest request);

    void chatStreaming(AiChatRequest request, SseEmitter emitter);
}
