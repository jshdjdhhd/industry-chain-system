package git.ggwzz.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.llm.LlmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 健康分析报告提示词构建器。
 *
 * <p>Prompt = systemPrompt（约束）+ userPrompt（结构化上下文 JSON + 报告结构要求）。
 * 由于通用 LLM 客户端使用 {@code response_format=json_object}，提示词要求模型只输出
 * 一个 {@code {"markdown":"..."}} JSON 对象。</p>
 *
 * <p>本构建器纯内存计算，不依赖数据访问。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 构建聊天补全消息列表（system + user）。
     *
     * @param context 结构化上下文（由 {@link AnalysisReportContextBuilder} 组装的 Map）
     * @return [systemMessage, userMessage]
     */
    public List<LlmMessage> buildPrompt(Map<String, Object> context) {
        return List.of(
                new LlmMessage("system", systemPrompt()),
                new LlmMessage("user", userPrompt(context)));
    }

    /**
     * 系统提示词：硬性约束 + 输出格式要求。
     */
    private String systemPrompt() {
        return """
                你是一名产业链健康分析报告撰写助手。
                你只能基于输入材料生成产业链健康分析报告。

                硬性约束：
                1. 不得编造企业、节点、政策、市场事实或外部候选企业。
                2. 健康分由系统规则计算，你只能解释分数，不得修改分数。
                3. 管理建议只围绕已挂载企业、节点治理、挂链核验、数据完善展开，不写外部招商。
                4. 报告正文使用 Markdown，语言为中文。

                输出格式：
                由于当前通用 LLM 客户端使用 json_object 响应格式，请只输出一个 JSON 对象：
                {"markdown":"...Markdown 报告正文..."}
                不要在 markdown 字段以外输出任何内容，不要使用代码块包裹 JSON。
                """;
    }

    /**
     * 用户提示词：结构化上下文 JSON + 报告建议结构。
     */
    private String userPrompt(Map<String, Object> context) {
        String contextJson = serializeContext(context);
        return """
                请基于以下结构化上下文生成产业链健康分析报告。

                【结构化上下文 JSON】
                """ + contextJson + """

                【报告建议结构（Markdown）】
                # {chainName}健康分析报告
                一、分析范围
                二、总体健康结论
                三、关键风险节点
                四、薄弱节点原因分析
                五、已挂载企业支撑情况
                六、管理建议（只围绕已挂载企业/节点治理/挂链核验/数据完善，不写外部招商）
                七、数据口径说明

                要求：
                - 只使用上述上下文中出现的企业、节点、健康分、弱项原因与推荐证据。
                - 解释健康分的含义与成因，但不得修改健康分。
                - 不引入外部政策、新闻、招商候选企业。
                - 只输出一个 {"markdown":"..."} JSON 对象。
                """;
    }

    private String serializeContext(Map<String, Object> context) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            log.warn("serialize report context failed: reason={}", ex.getMessage());
            return "{}";
        }
    }
}