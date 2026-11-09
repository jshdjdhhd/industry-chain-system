package git.ggwzz.matching.service.impl;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.dto.AiChatRequest;
import git.ggwzz.matching.service.AiChatService;
import git.ggwzz.matching.vo.AiChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * AI 聊天服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_HISTORY_SIZE = 50;

    private static final String SYSTEM_PROMPT = """
            你是产业链企业挂链分析专家。用户会询问你关于企业与产业链节点匹配的问题。
            请基于企业画像和候选节点信息，给出专业、详细的分析建议。
            用自然语言回答，不要使用 JSON 格式。
            """;

    private static final String GENERAL_SYSTEM_PROMPT = """
            你是一个产业链知识助手。你可以回答关于产业链、企业挂链、产业发展等方面的问题。
            注意事项：
            1. 你不能虚构平台内部的具体企业数据。
            2. 如果用户询问特定企业或产业链的分析，请引导用户在面板中选择对应企业或产业链后再提问。
            3. 回答使用自然语言，不要返回 JSON 格式。
            """;

    private final LlmClient llmClient;
    private final EnterpriseQueryService enterpriseQueryService;
    private final ChainNodeQueryService chainNodeQueryService;

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        validateRequest(request);
        List<LlmMessage> messages = buildMessages(request);
        LlmChatCompletion completion = llmClient.complete(messages);

        AiChatResponse response = new AiChatResponse();
        response.setReply(completion.content());
        response.setModel(completion.model());
        return response;
    }

    @Override
    public void chatStreaming(AiChatRequest request, SseEmitter emitter) {
        // 校验同步执行，确保非法请求返回 400 而非进入 SSE 流
        validateRequest(request);
        // 流式生成放入异步线程，Controller 立即返回 emitter，
        // Spring 才能正确建立 text/event-stream 响应并逐块推送
        CompletableFuture.runAsync(() -> {
            try {
                List<LlmMessage> messages = buildMessages(request);
                llmClient.completeStreaming(messages, chunk -> {
                    try {
                        emitter.send(chunk);
                    } catch (Exception exception) {
                        log.error("SSE 发送失败", exception);
                    }
                });
                emitter.send("[DONE]");
                emitter.complete();
            } catch (Exception exception) {
                log.error("AI 流式回复失败", exception);
                try {
                    emitter.send(exception.getMessage() != null
                            ? exception.getMessage()
                            : "AI 分析失败，请稍后重试");
                    emitter.send("[DONE]");
                } catch (Exception sendException) {
                    log.debug("SSE 错误提示发送失败", sendException);
                }
                emitter.complete();
            }
        });
    }

    /**
     * Service 层防御性校验，防止非 Controller 调用绕过 Jakarta Validation。
     *
     * @param request AI 聊天请求
     */
    private void validateRequest(AiChatRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数格式错误");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能为空");
        }
        if (request.getMessage().length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能超过 4000 字符");
        }

        validateIds(effectiveEnterpriseIds(request), "单次最多选择 20 家企业");
        validateIds(effectiveChainIds(request), "单次最多选择 20 条产业链");

        validateHistory(request.getHistory());
    }

    private void validateIds(List<Long> ids, String tooManyMessage) {
        if (ids.size() > 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, tooManyMessage);
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "企业和产业链 ID 必须为正整数");
            }
        }
    }

    private void validateHistory(List<AiChatRequest.ChatMessage> history) {
        if (history == null) {
            return;
        }
        if (history.size() > MAX_HISTORY_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息不能超过 50 条");
        }
        for (AiChatRequest.ChatMessage message : history) {
            if (message == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息不能为空");
            }
            if (message.getRole() == null || message.getRole().isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息角色不能为空");
            }
            if (!"user".equals(message.getRole()) && !"assistant".equals(message.getRole())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息角色不合法");
            }
            if (message.getContent() == null || message.getContent().isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息内容不能为空");
            }
            if (message.getContent().length() > MAX_MESSAGE_LENGTH) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息内容不能超过 4000 字符");
            }
        }
    }

    private List<LlmMessage> buildMessages(AiChatRequest request) {
        List<LlmMessage> messages = new ArrayList<>();
        List<Long> enterpriseIds = effectiveEnterpriseIds(request);
        List<Long> chainIds = effectiveChainIds(request);
        if (enterpriseIds.isEmpty() && chainIds.isEmpty()) {
            messages.add(new LlmMessage("system", GENERAL_SYSTEM_PROMPT));
        } else {
            messages.add(new LlmMessage("system", buildContextPrompt(enterpriseIds, chainIds)));
        }

        if (request.getHistory() != null) {
            for (AiChatRequest.ChatMessage historyMessage : request.getHistory()) {
                messages.add(new LlmMessage(historyMessage.getRole(), historyMessage.getContent()));
            }
        }
        messages.add(new LlmMessage("user", request.getMessage()));
        return messages;
    }

    private String buildContextPrompt(List<Long> enterpriseIds, List<Long> chainIds) {
        StringBuilder context = new StringBuilder();
        if (!enterpriseIds.isEmpty()) {
            appendEnterpriseContext(context, enterpriseIds);
        }
        if (!chainIds.isEmpty()) {
            appendChainContext(context, chainIds);
        }
        return SYSTEM_PROMPT + "\n\n" + context;
    }

    private void appendEnterpriseContext(StringBuilder context, List<Long> enterpriseIds) {
        Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterprises = enterpriseQueryService.getByIds(enterpriseIds);
        context.append("当前分析的企业（共").append(enterpriseIds.size()).append("家）：\n");
        for (Long enterpriseId : enterpriseIds) {
            RuleBasedMatchingProvider.EnterpriseInfo enterprise =
                    enterprises == null ? null : enterprises.get(enterpriseId);
            if (enterprise == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在: " + enterpriseId);
            }
            context.append("- 企业 ID：").append(enterpriseId).append("\n");
            context.append("  名称：").append(nullSafe(enterprise.getName())).append("\n");
            context.append("  主营产品：").append(nullSafe(enterprise.getMainProducts())).append("\n");
            context.append("  经营范围：").append(nullSafe(enterprise.getBusinessScope())).append("\n");
            context.append("  标签：").append(nullSafe(enterprise.getTags())).append("\n");
        }
        context.append("\n");
    }

    private void appendChainContext(StringBuilder context, List<Long> chainIds) {
        for (Long chainId : chainIds) {
            List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(chainId);
            context.append("产业链 ID ").append(chainId).append(" 的候选节点");
            if (nodes == null || nodes.isEmpty()) {
                context.append("：暂无节点数据。\n\n");
                continue;
            }
            context.append("（共").append(nodes.size()).append("个）：\n");
            for (int i = 0; i < nodes.size(); i++) {
                ChainNodeCandidateDTO node = nodes.get(i);
                context.append(i + 1).append(". ").append(nullSafe(node.getNodePath()));
                if (node.getKeywords() != null && !node.getKeywords().isBlank()) {
                    context.append(" [关键词：").append(node.getKeywords()).append("]");
                }
                context.append("\n");
            }
            context.append("\n");
        }
    }

    private List<Long> effectiveEnterpriseIds(AiChatRequest request) {
        return effectiveIds(request.getEnterpriseId(), request.getEnterpriseIds());
    }

    private List<Long> effectiveChainIds(AiChatRequest request) {
        return effectiveIds(request.getChainId(), request.getChainIds());
    }

    private List<Long> effectiveIds(Long singleId, List<Long> ids) {
        Set<Long> normalized = new LinkedHashSet<>();
        if (singleId != null) {
            normalized.add(singleId);
        }
        if (ids != null) {
            normalized.addAll(ids);
        }
        return new ArrayList<>(normalized);
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
