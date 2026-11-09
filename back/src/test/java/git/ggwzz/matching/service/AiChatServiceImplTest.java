package git.ggwzz.matching.service;

import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.dto.AiChatRequest;
import git.ggwzz.matching.service.impl.AiChatServiceImpl;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider.EnterpriseInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private EnterpriseQueryService enterpriseQueryService;

    @Mock
    private ChainNodeQueryService chainNodeQueryService;

    @InjectMocks
    private AiChatServiceImpl service;

    @Test
    @DisplayName("通用模式不查询企业和产业链节点")
    void chatShouldNotQueryContextInGeneralMode() {
        AiChatRequest request = request(null, null, "什么是产业链？");
        when(llmClient.complete(anyList()))
                .thenReturn(new LlmChatCompletion("test-model", "产业链是……"));

        service.chat(request);

        verifyNoInteractions(enterpriseQueryService, chainNodeQueryService);
        verify(llmClient).complete(argThat(messages -> {
            LlmMessage systemMessage = messages.get(0);
            return "system".equals(systemMessage.role())
                    && systemMessage.content().contains("产业链知识助手");
        }));
    }

    @Test
    @DisplayName("上下文模式查询企业和产业链节点")
    void chatShouldQueryEnterpriseAndChainInContextMode() {
        AiChatRequest request = request(1L, 2L, "分析挂链风险");
        EnterpriseInfo enterprise = new EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("测试企业");
        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of());
        when(llmClient.complete(anyList()))
                .thenReturn(new LlmChatCompletion("test-model", "风险分析……"));

        service.chat(request);

        verify(enterpriseQueryService).getById(1L);
        verify(chainNodeQueryService).listByChainId(2L);
    }

    @Test
    @DisplayName("上下文模式企业不存在时抛出 404")
    void chatShouldThrowNotFoundWhenEnterpriseDoesNotExist() {
        AiChatRequest request = request(999L, 2L, "分析");
        when(enterpriseQueryService.getById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.chat(request)
        );

        assertEquals(ResultCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Nested
    @DisplayName("Service 层防御性校验")
    class DefensiveValidation {

        @Test
        @DisplayName("null 请求抛出 400")
        void shouldRejectNullRequest() {
            assertBadRequest(() -> service.chat(null));
        }

        @Test
        @DisplayName("空白消息抛出 400")
        void shouldRejectBlankMessage() {
            assertBadRequest(() -> service.chat(request(null, null, "   ")));
        }

        @Test
        @DisplayName("仅选择企业抛出 400")
        void shouldRejectHalfSelectedContext() {
            assertBadRequest(() -> service.chat(request(1L, null, "测试")));
        }

        @Test
        @DisplayName("非正数 ID 抛出 400")
        void shouldRejectNonPositiveContextId() {
            assertBadRequest(() -> service.chat(request(0L, 1L, "测试")));
        }

        @Test
        @DisplayName("历史消息含 null 项抛出 400")
        void shouldRejectNullHistoryItem() {
            AiChatRequest request = request(null, null, "测试");
            List<AiChatRequest.ChatMessage> history = new ArrayList<>();
            history.add(null);
            request.setHistory(history);

            assertBadRequest(() -> service.chat(request));
        }

        @Test
        @DisplayName("历史消息超过 50 条抛出 400")
        void shouldRejectTooManyHistoryItems() {
            AiChatRequest request = request(null, null, "测试");
            List<AiChatRequest.ChatMessage> history = new ArrayList<>();
            for (int i = 0; i < 51; i++) {
                AiChatRequest.ChatMessage message = new AiChatRequest.ChatMessage();
                message.setRole("user");
                message.setContent("历史" + i);
                history.add(message);
            }
            request.setHistory(history);

            assertBadRequest(() -> service.chat(request));
        }

        @Test
        @DisplayName("流式调用同样执行防御性校验")
        void streamingShouldRejectHalfSelectedContext() {
            AiChatRequest request = request(1L, null, "测试");

            assertBadRequest(() -> service.chatStreaming(request, new SseEmitter()));
        }

        private void assertBadRequest(Runnable action) {
            BusinessException exception = assertThrows(BusinessException.class, action::run);
            assertEquals(ResultCode.BAD_REQUEST.getCode(), exception.getCode());
        }
    }

    private AiChatRequest request(Long enterpriseId, Long chainId, String message) {
        AiChatRequest request = new AiChatRequest();
        request.setEnterpriseId(enterpriseId);
        request.setChainId(chainId);
        request.setMessage(message);
        return request;
    }
}
