package git.ggwzz.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.llm.LlmMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnalysisReportPromptBuilder} 单元测试。
 *
 * <p>验证 systemPrompt 含关键约束关键词、userPrompt 含上下文与报告结构要求、
 * 消息角色正确（system + user）。</p>
 */
class AnalysisReportPromptBuilderTest {

    private AnalysisReportPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new AnalysisReportPromptBuilder(new ObjectMapper());
    }

    @Test
    void shouldBuildSystemAndUserMessagesWithConstraints() {
        Map<String, Object> context = sampleContext();

        List<LlmMessage> messages = promptBuilder.buildPrompt(context);

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
    }

    @Test
    void systemPromptShouldContainKeyConstraints() {
        Map<String, Object> context = sampleContext();

        List<LlmMessage> messages = promptBuilder.buildPrompt(context);

        String system = messages.get(0).content();
        assertNotNull(system);
        assertTrue(system.contains("不得编造"), "systemPrompt 应包含不得编造约束");
        assertTrue(system.contains("健康分"), "systemPrompt 应提及健康分");
        assertTrue(system.contains("不得修改"), "systemPrompt 应禁止修改健康分");
        assertTrue(system.contains("Markdown"), "systemPrompt 应要求 Markdown 正文");
        assertTrue(system.contains("{\"markdown\":\""), "systemPrompt 应要求返回 markdown JSON 对象");
    }

    @Test
    void userPromptShouldContainContextAndStructure() {
        Map<String, Object> context = sampleContext();

        List<LlmMessage> messages = promptBuilder.buildPrompt(context);

        String user = messages.get(1).content();
        assertNotNull(user);
        assertTrue(user.contains("新能源汽车"), "userPrompt 应包含上下文中的产业链名称");
        assertTrue(user.contains("分析范围"), "userPrompt 应包含报告结构要求");
        assertTrue(user.contains("管理建议"), "userPrompt 应包含管理建议章节要求");
        assertTrue(user.contains("数据口径说明"), "userPrompt 应包含数据口径说明章节要求");
        assertTrue(user.contains("不写外部招商"), "userPrompt 应约束不写外部招商");
    }

    private Map<String, Object> sampleContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("chainId", 1);
        chain.put("chainName", "新能源汽车");
        context.put("chain", chain);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("selectedNodeIds", List.of(12));
        scope.put("resolvedNodeIds", List.of(12, 13));
        scope.put("scopeDescription", "选中节点及其子树，共 2 个节点");
        context.put("scope", scope);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("summary", Map.of("nodeCount", 2, "enterpriseCount", 5));
        metrics.put("nodes", List.of());
        context.put("metrics", metrics);
        context.put("enterpriseSamples", List.of());
        context.put("matchingEvidence", List.of());
        return context;
    }
}
