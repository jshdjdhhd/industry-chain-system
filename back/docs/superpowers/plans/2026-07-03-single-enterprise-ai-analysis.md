# 单企业人工智能分析实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将单企业智能推荐改为纯 LLM 人工智能分析，只调用 `LlmClient` 对企业与产业链节点进行分析，输出建议回答与分析依据。

**架构：** 新增单企业 AI 分析 prompt、parser 和默认 `MatchingProvider`，让单企业同步/异步任务使用 `LLM_ANALYSIS_V1`。批量任务继续显式使用 `HybridEmbeddingMatchingProvider` 与 `MatchingBatchLocalEngine`。前端保留现有 API 和流程，只把单企业入口与结果区调整为「人工智能分析」语义。

**技术栈：** Java 21、Spring Boot 3.5.15、JUnit 5、Mockito、Vue 3、TypeScript、Vitest、Element Plus。

---

## 文件结构

### 后端仓库：`C:\Users\18443\Desktop\大项目开发\groupproject-back\back`

- 创建：`src/main/java/git/ggwzz/matching/llm/AiAnalysisDecision.java`
  - 承载解析后的 LLM 分析结果：节点 ID、置信度、建议、分析、风险。
- 创建：`src/main/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilder.java`
  - 基于企业画像和产业链节点列表构建纯 LLM 分析 prompt。
- 创建：`src/main/java/git/ggwzz/matching/llm/AiAnalysisResultParser.java`
  - 解析 LLM JSON 输出，过滤未知节点、重复节点，并限制 Top K。
- 创建：`src/main/java/git/ggwzz/matching/service/impl/SingleEnterpriseAiAnalysisMatchingProvider.java`
  - 默认单企业 `MatchingProvider`，只调用 `LlmClient.complete(...)`。
- 修改：`src/main/java/git/ggwzz/matching/service/impl/LlmRerankMatchingProvider.java`
  - 移除 `@Primary`，保留历史 V3 逻辑但不再作为默认 Provider。
- 修改：`src/main/java/git/ggwzz/matching/llm/MatchingLlmProperties.java`
  - 添加分析结果数量配置 `analysisTopK`，默认 3。
- 测试：`src/test/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilderTest.java`
- 测试：`src/test/java/git/ggwzz/matching/llm/AiAnalysisResultParserTest.java`
- 测试：`src/test/java/git/ggwzz/matching/service/SingleEnterpriseAiAnalysisMatchingProviderTest.java`
- 修改测试：`src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java`

### 前端仓库：`C:\Users\18443\Desktop\大项目开发\groupproject\front`

- 修改：`src/views/matching/MatchingTask.vue`
  - 单企业 Tab、按钮、状态提示和结果区文案改为人工智能分析。
- 修改：`src/views/matching/matchingViewModel.ts`
  - 单企业操作日志文案从「智能推荐」调整为「人工智能分析」。
- 修改测试：`src/views/matching/matchingViewModel.spec.ts`
- 创建测试：`src/views/matching/MatchingTask.spec.ts`

---

### 任务 1：后端 AI 分析 Prompt 和 Parser

**文件：**
- 创建：`src/main/java/git/ggwzz/matching/llm/AiAnalysisDecision.java`
- 创建：`src/main/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilder.java`
- 创建：`src/main/java/git/ggwzz/matching/llm/AiAnalysisResultParser.java`
- 测试：`src/test/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilderTest.java`
- 测试：`src/test/java/git/ggwzz/matching/llm/AiAnalysisResultParserTest.java`

- [ ] **步骤 1：编写失败的 Prompt Builder 测试**

创建 `src/test/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilderTest.java`：

```java
package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAnalysisPromptBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAnalysisPromptBuilder builder = new AiAnalysisPromptBuilder(objectMapper);

    @Test
    void buildShouldIncludeEnterpriseNodesAndStrictJsonContract() throws Exception {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(100L);
        enterprise.setName("宁德时代新能源科技股份有限公司");
        enterprise.setMainProducts("动力电池，电芯，储能系统");
        enterprise.setBusinessScope("锂离子电池制造与销售");
        enterprise.setTags("新能源，电池");

        ChainNodeCandidateDTO battery = node(101L, "新能源汽车产业链 / 中游 / 动力电池", "动力电池");
        ChainNodeCandidateDTO motor = node(102L, "新能源汽车产业链 / 中游 / 驱动电机", "驱动电机");

        List<LlmMessage> messages = builder.build(enterprise, List.of(battery, motor), 3);

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertTrue(messages.get(0).content().contains("人工智能分析"));
        assertTrue(messages.get(0).content().contains("只输出 JSON"));

        JsonNode payload = objectMapper.readTree(messages.get(1).content());
        assertEquals(AiAnalysisPromptBuilder.PROMPT_VERSION, payload.path("prompt_version").asText());
        assertEquals("企业与产业链挂链人工智能分析", payload.path("task").asText());
        assertEquals(3, payload.path("result_top_k").asInt());
        assertEquals("宁德时代新能源科技股份有限公司",
                payload.path("enterprise_profile").path("name").asText());
        assertEquals(2, payload.path("candidate_nodes").size());
        assertEquals(101L, payload.path("candidate_nodes").get(0).path("node_id").asLong());
        assertTrue(payload.path("output_contract").toString().contains("suggested_answer"));
        assertTrue(payload.path("analysis_rules").toString().contains("不得输出候选列表之外的节点"));
    }

    private ChainNodeCandidateDTO node(Long id, String path, String keywords) {
        ChainNodeCandidateDTO node = new ChainNodeCandidateDTO();
        node.setNodeId(id);
        node.setChainId(1L);
        node.setNodeName(path.substring(path.lastIndexOf('/') + 1).trim());
        node.setNodePath(path);
        node.setKeywords(keywords);
        node.setLevel(3);
        return node;
    }
}
```

- [ ] **步骤 2：运行 Prompt Builder 测试验证失败**

运行：

```bash
mvn -Dtest=AiAnalysisPromptBuilderTest test
```

预期：编译失败，提示找不到 `AiAnalysisPromptBuilder`。

- [ ] **步骤 3：编写失败的 Parser 测试**

创建 `src/test/java/git/ggwzz/matching/llm/AiAnalysisResultParserTest.java`：

```java
package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAnalysisResultParserTest {

    private final AiAnalysisResultParser parser = new AiAnalysisResultParser(new ObjectMapper());

    @Test
    void parseShouldReturnAllowedUniqueResultsAndClampConfidence() {
        String content = """
                {
                  "analysis_summary": "企业主营业务与动力电池环节高度相关",
                  "suggested_answer": "建议优先挂接到动力电池节点",
                  "results": [
                    {"node_id": 101, "confidence": 1.2, "suggestion": "建议挂接", "analysis": "主营产品直接匹配", "risk": "无"},
                    {"node_id": 999, "confidence": 0.91, "suggestion": "未知节点", "analysis": "不应采纳", "risk": "未知"},
                    {"node_id": 101, "confidence": 0.80, "suggestion": "重复", "analysis": "重复节点", "risk": "重复"},
                    {"node_id": 102, "confidence": -0.3, "suggestion": "备选", "analysis": "证据较弱", "risk": "需复核"}
                  ]
                }
                """;

        List<AiAnalysisDecision> decisions = parser.parse(content, Set.of(101L, 102L), 3);

        assertEquals(2, decisions.size());
        assertEquals(101L, decisions.get(0).nodeId());
        assertEquals(0.99D, decisions.get(0).confidence());
        assertEquals("企业主营业务与动力电池环节高度相关", decisions.get(0).analysisSummary());
        assertEquals("建议优先挂接到动力电池节点", decisions.get(0).suggestedAnswer());
        assertEquals("主营产品直接匹配", decisions.get(0).analysis());
        assertEquals(102L, decisions.get(1).nodeId());
        assertEquals(0.0D, decisions.get(1).confidence());
    }

    @Test
    void parseShouldAcceptMarkdownFence() {
        String content = """
                ```json
                {"analysis_summary":"分析完成","suggested_answer":"建议人工复核","results":[
                  {"node_id":101,"confidence":0.72,"suggestion":"建议复核","analysis":"存在业务相关性","risk":"证据不足"}
                ]}
                ```
                """;

        List<AiAnalysisDecision> decisions = parser.parse(content, Set.of(101L), 1);

        assertEquals(1, decisions.size());
        assertEquals("建议复核", decisions.get(0).suggestion());
    }

    @Test
    void parseShouldThrowWhenNoUsableResults() {
        assertThrows(BusinessException.class,
                () -> parser.parse("{\"results\":[{\"node_id\":999}]}", Set.of(101L), 3));
    }
}
```

- [ ] **步骤 4：运行 Parser 测试验证失败**

运行：

```bash
mvn -Dtest=AiAnalysisResultParserTest test
```

预期：编译失败，提示找不到 `AiAnalysisResultParser` 和 `AiAnalysisDecision`。

- [ ] **步骤 5：实现 Prompt Builder、Parser 和 Decision**

创建 `src/main/java/git/ggwzz/matching/llm/AiAnalysisDecision.java`：

```java
package git.ggwzz.matching.llm;

public record AiAnalysisDecision(
        Long nodeId,
        double confidence,
        String suggestion,
        String analysis,
        String risk,
        String analysisSummary,
        String suggestedAnswer) {
}
```

创建 `src/main/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilder.java`：

```java
package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiAnalysisPromptBuilder {

    public static final String PROMPT_VERSION = "single-enterprise-ai-analysis-v1-2026-07-03";

    private static final String SYSTEM_PROMPT = """
            你是产业链企业挂链人工智能分析专家。请只根据用户提供的企业画像和候选产业链节点列表分析。
            你必须给出业务人员可直接阅读的建议回答、分析依据和风险提示。不得输出候选列表之外的节点。
            只输出 JSON，不要输出 Markdown、解释性前缀或多余文本。
            """;

    private final ObjectMapper objectMapper;

    public List<LlmMessage> build(RuleBasedMatchingProvider.EnterpriseInfo enterprise,
                                  List<ChainNodeCandidateDTO> nodes,
                                  int resultTopK) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt_version", PROMPT_VERSION);
        payload.put("task", "企业与产业链挂链人工智能分析");
        payload.put("result_top_k", Math.max(1, resultTopK));
        payload.set("enterprise_profile", enterpriseProfile(enterprise));
        payload.set("candidate_nodes", candidateNodes(nodes));
        payload.set("analysis_rules", analysisRules());
        payload.set("output_contract", outputContract());
        return List.of(
                new LlmMessage("system", SYSTEM_PROMPT),
                new LlmMessage("user", payload.toString()));
    }

    private ObjectNode enterpriseProfile(RuleBasedMatchingProvider.EnterpriseInfo enterprise) {
        ObjectNode profile = objectMapper.createObjectNode();
        if (enterprise == null) {
            return profile;
        }
        profile.put("id", enterprise.getId());
        profile.put("name", safe(enterprise.getName()));
        profile.put("business_scope", safe(enterprise.getBusinessScope()));
        profile.put("main_products", safe(enterprise.getMainProducts()));
        profile.put("tags", safe(enterprise.getTags()));
        return profile;
    }

    private ArrayNode candidateNodes(List<ChainNodeCandidateDTO> nodes) {
        ArrayNode array = objectMapper.createArrayNode();
        if (nodes == null) {
            return array;
        }
        for (ChainNodeCandidateDTO node : nodes) {
            ObjectNode item = array.addObject();
            item.put("node_id", node.getNodeId());
            item.put("chain_id", node.getChainId());
            item.put("node_name", safe(node.getNodeName()));
            item.put("node_path", safe(node.getNodePath()));
            item.put("keywords", safe(node.getKeywords()));
            if (node.getLevel() != null) {
                item.put("level", node.getLevel());
            }
        }
        return array;
    }

    private ArrayNode analysisRules() {
        ArrayNode rules = objectMapper.createArrayNode();
        rules.add("优先判断企业主营产品、核心业务和经营范围是否直接覆盖节点定义。");
        rules.add("必须结合节点路径判断上下游环节，不得仅按企业名称或泛化关键词判断。");
        rules.add("不得输出候选列表之外的节点。");
        rules.add("低置信或证据不足时明确提示人工复核风险。");
        return rules;
    }

    private ObjectNode outputContract() {
        ObjectNode contract = objectMapper.createObjectNode();
        contract.put("format", "json_object");
        contract.put("schema", """
                {"analysis_summary":"总体判断","suggested_answer":"建议回答","results":[{"node_id":101,"confidence":0.86,"suggestion":"建议挂接","analysis":"分析依据","risk":"风险提示"}]}
                """);
        return contract;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
```

创建 `src/main/java/git/ggwzz/matching/llm/AiAnalysisResultParser.java`：

```java
package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisResultParser {

    private final ObjectMapper objectMapper;

    public List<AiAnalysisDecision> parse(String content, Set<Long> allowedNodeIds, int topK) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 未返回人工智能分析结果");
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonPayload(content));
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 人工智能分析结果格式不正确");
            }
            List<AiAnalysisDecision> decisions = parseResults(
                    results,
                    allowedNodeIds == null ? Set.of() : allowedNodeIds,
                    topK,
                    root.path("analysis_summary").asText(""),
                    root.path("suggested_answer").asText(""));
            if (decisions.isEmpty()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 未返回可用候选节点");
            }
            return decisions;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("LLM 人工智能分析结果解析失败", ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 人工智能分析结果不是合法 JSON");
        }
    }

    private List<AiAnalysisDecision> parseResults(JsonNode results,
                                                  Set<Long> allowedNodeIds,
                                                  int topK,
                                                  String analysisSummary,
                                                  String suggestedAnswer) {
        int limit = topK <= 0 ? Integer.MAX_VALUE : topK;
        Set<Long> seen = new LinkedHashSet<>();
        List<AiAnalysisDecision> decisions = new ArrayList<>();
        for (JsonNode item : results) {
            Long nodeId = readNodeId(item);
            if (nodeId == null || !allowedNodeIds.contains(nodeId) || !seen.add(nodeId)) {
                continue;
            }
            decisions.add(new AiAnalysisDecision(
                    nodeId,
                    clampConfidence(item.path("confidence").asDouble(0.0D)),
                    item.path("suggestion").asText(""),
                    item.path("analysis").asText(""),
                    item.path("risk").asText(""),
                    analysisSummary,
                    suggestedAnswer));
            if (decisions.size() >= limit) {
                break;
            }
        }
        return decisions;
    }

    private Long readNodeId(JsonNode item) {
        JsonNode snake = item.get("node_id");
        if (snake != null && snake.canConvertToLong()) {
            return snake.asLong();
        }
        JsonNode camel = item.get("nodeId");
        if (camel != null && camel.canConvertToLong()) {
            return camel.asLong();
        }
        return null;
    }

    private double clampConfidence(double confidence) {
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            return 0.0D;
        }
        return Math.min(0.99D, Math.max(0.0D, confidence));
    }

    private String extractJsonPayload(String content) {
        String trimmed = stripOuterMarkdownFence(content.trim());
        int objectStart = trimmed.indexOf('{');
        if (objectStart < 0) {
            return trimmed;
        }
        int objectEnd = findMatchingJsonEnd(trimmed, objectStart, '{', '}');
        return objectEnd > objectStart ? trimmed.substring(objectStart, objectEnd + 1) : trimmed;
    }

    private String stripOuterMarkdownFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineBreak = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
            return content.substring(firstLineBreak + 1, lastFence).trim();
        }
        return content;
    }

    private int findMatchingJsonEnd(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
```

- [ ] **步骤 6：运行 Prompt 和 Parser 测试验证通过**

运行：

```bash
mvn -Dtest=AiAnalysisPromptBuilderTest,AiAnalysisResultParserTest test
```

预期：`BUILD SUCCESS`，两个测试类通过。

- [ ] **步骤 7：Commit 后端基础分析组件**

运行：

```bash
git add src/main/java/git/ggwzz/matching/llm/AiAnalysisDecision.java src/main/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilder.java src/main/java/git/ggwzz/matching/llm/AiAnalysisResultParser.java src/test/java/git/ggwzz/matching/llm/AiAnalysisPromptBuilderTest.java src/test/java/git/ggwzz/matching/llm/AiAnalysisResultParserTest.java
git commit -m "feat(智能挂链): 添加单企业 AI 分析解析组件"
```

---

### 任务 2：后端单企业纯 LLM Provider

**文件：**
- 创建：`src/main/java/git/ggwzz/matching/service/impl/SingleEnterpriseAiAnalysisMatchingProvider.java`
- 修改：`src/main/java/git/ggwzz/matching/llm/MatchingLlmProperties.java`
- 测试：`src/test/java/git/ggwzz/matching/service/SingleEnterpriseAiAnalysisMatchingProviderTest.java`

- [ ] **步骤 1：编写失败的 Provider 测试**

创建 `src/test/java/git/ggwzz/matching/service/SingleEnterpriseAiAnalysisMatchingProviderTest.java`：

```java
package git.ggwzz.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.llm.AiAnalysisPromptBuilder;
import git.ggwzz.matching.llm.AiAnalysisResultParser;
import git.ggwzz.matching.llm.MatchingLlmProperties;
import git.ggwzz.matching.service.impl.SingleEnterpriseAiAnalysisMatchingProvider;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SingleEnterpriseAiAnalysisMatchingProviderTest {

    @Mock
    private EnterpriseQueryService enterpriseQueryService;

    @Mock
    private ChainNodeQueryService chainNodeQueryService;

    @Mock
    private LlmClient llmClient;

    private MatchingLlmProperties properties;
    private SingleEnterpriseAiAnalysisMatchingProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MatchingLlmProperties();
        properties.setProvider("mimo");
        properties.setModel("mimo-v2.5-pro-ultraspeed");
        provider = new SingleEnterpriseAiAnalysisMatchingProvider(
                enterpriseQueryService,
                chainNodeQueryService,
                llmClient,
                new AiAnalysisPromptBuilder(new ObjectMapper()),
                new AiAnalysisResultParser(new ObjectMapper()),
                properties,
                new ObjectMapper());
    }

    @Test
    void getModelNameShouldUseLlmAnalysisV1() {
        assertEquals("LLM_ANALYSIS_V1", provider.getModelName());
    }

    @Test
    void matchShouldCallOnlyLlmAndReturnAnalysisResults() {
        when(enterpriseQueryService.getById(100L)).thenReturn(enterprise());
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(List.of(node(101L), node(102L)));
        when(llmClient.complete(anyList())).thenReturn(new LlmChatCompletion("mimo-v2.5-pro-ultraspeed", """
                {"analysis_summary":"企业主营业务与动力电池高度相关","suggested_answer":"建议优先挂接到动力电池节点","results":[
                  {"node_id":101,"confidence":0.88,"suggestion":"建议挂接","analysis":"主营产品直接覆盖动力电池和电芯","risk":"需核验储能业务是否另挂节点"}
                ]}
                """));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.8800"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("建议优先挂接到动力电池节点"));
        assertTrue(results.get(0).getReason().contains("主营产品直接覆盖动力电池和电芯"));
        assertTrue(results.get(0).getReason().contains("需核验储能业务是否另挂节点"));
        assertTrue(results.get(0).getEvidence().contains("\"model\":\"LLM_ANALYSIS_V1\""));
        assertTrue(results.get(0).getEvidence().contains("\"analysis_summary\":\"企业主营业务与动力电池高度相关\""));
        assertTrue(results.get(0).getEvidence().contains("\"llm_model\":\"mimo-v2.5-pro-ultraspeed\""));
        verify(llmClient).complete(anyList());
    }

    @Test
    void matchShouldReturnEmptyWhenEnterpriseMissing() {
        when(enterpriseQueryService.getById(100L)).thenReturn(null);

        List<MatchingResult> results = provider.match(100L, 1L);

        assertTrue(results.isEmpty());
    }

    @Test
    void matchShouldReturnEmptyWhenChainHasNoNodes() {
        when(enterpriseQueryService.getById(100L)).thenReturn(enterprise());
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(List.of());

        List<MatchingResult> results = provider.match(100L, 1L);

        assertTrue(results.isEmpty());
    }

    @Test
    void matchShouldPropagateLlmFailureWithoutFallback() {
        when(enterpriseQueryService.getById(100L)).thenReturn(enterprise());
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(List.of(node(101L)));
        when(llmClient.complete(anyList()))
                .thenThrow(new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 服务不可用"));

        BusinessException ex = assertThrows(BusinessException.class, () -> provider.match(100L, 1L));

        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("LLM 服务不可用"));
    }

    private RuleBasedMatchingProvider.EnterpriseInfo enterprise() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(100L);
        enterprise.setName("宁德时代新能源科技股份有限公司");
        enterprise.setMainProducts("动力电池，电芯，储能系统");
        enterprise.setBusinessScope("锂离子电池制造与销售");
        enterprise.setTags("新能源，电池");
        return enterprise;
    }

    private ChainNodeCandidateDTO node(Long id) {
        ChainNodeCandidateDTO node = new ChainNodeCandidateDTO();
        node.setNodeId(id);
        node.setChainId(1L);
        node.setNodeName(id.equals(101L) ? "动力电池" : "驱动电机");
        node.setNodePath(id.equals(101L)
                ? "新能源汽车产业链 / 中游 / 动力电池"
                : "新能源汽车产业链 / 中游 / 驱动电机");
        node.setKeywords(id.equals(101L) ? "动力电池，电芯" : "驱动电机，电控");
        node.setLevel(3);
        return node;
    }
}
```

- [ ] **步骤 2：运行 Provider 测试验证失败**

运行：

```bash
mvn -Dtest=SingleEnterpriseAiAnalysisMatchingProviderTest test
```

预期：编译失败，提示找不到 `SingleEnterpriseAiAnalysisMatchingProvider`，或 `MatchingLlmProperties#setAnalysisTopK` 尚不存在。

- [ ] **步骤 3：添加分析 Top K 配置**

修改 `src/main/java/git/ggwzz/matching/llm/MatchingLlmProperties.java`，在现有字段末尾添加：

```java
    private int analysisTopK = 3;
```

- [ ] **步骤 4：实现单企业 AI 分析 Provider**

创建 `src/main/java/git/ggwzz/matching/service/impl/SingleEnterpriseAiAnalysisMatchingProvider.java`：

```java
package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.llm.AiAnalysisDecision;
import git.ggwzz.matching.llm.AiAnalysisPromptBuilder;
import git.ggwzz.matching.llm.AiAnalysisResultParser;
import git.ggwzz.matching.llm.MatchingLlmProperties;
import git.ggwzz.matching.service.MatchingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class SingleEnterpriseAiAnalysisMatchingProvider implements MatchingProvider {

    private static final String MODEL_NAME = "LLM_ANALYSIS_V1";

    private final EnterpriseQueryService enterpriseQueryService;
    private final ChainNodeQueryService chainNodeQueryService;
    private final LlmClient llmClient;
    private final AiAnalysisPromptBuilder promptBuilder;
    private final AiAnalysisResultParser resultParser;
    private final MatchingLlmProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public List<MatchingResult> match(Long enterpriseId, Long chainId) {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseQueryService.getById(enterpriseId);
        if (enterprise == null) {
            log.warn("enterprise not found for AI analysis: enterpriseId={}", enterpriseId);
            return List.of();
        }
        List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(chainId);
        if (nodes == null || nodes.isEmpty()) {
            log.warn("chain nodes not found for AI analysis: chainId={}", chainId);
            return List.of();
        }
        LlmChatCompletion completion = llmClient.complete(
                promptBuilder.build(enterprise, nodes, properties.getAnalysisTopK()));
        Set<Long> allowedNodeIds = nodes.stream()
                .map(ChainNodeCandidateDTO::getNodeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return resultParser.parse(completion.content(), allowedNodeIds, properties.getAnalysisTopK()).stream()
                .map(decision -> toMatchingResult(decision, completion))
                .toList();
    }

    private MatchingResult toMatchingResult(AiAnalysisDecision decision, LlmChatCompletion completion) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(decision.nodeId());
        result.setConfidence(BigDecimal.valueOf(decision.confidence()).setScale(4, RoundingMode.HALF_UP));
        result.setReason(buildReason(decision));
        result.setEvidence(buildEvidence(decision, completion));
        return result;
    }

    private String buildReason(AiAnalysisDecision decision) {
        StringBuilder reason = new StringBuilder();
        appendSentence(reason, "建议回答", decision.suggestedAnswer());
        appendSentence(reason, "节点建议", decision.suggestion());
        appendSentence(reason, "分析依据", decision.analysis());
        appendSentence(reason, "风险提示", decision.risk());
        return reason.toString();
    }

    private void appendSentence(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" ");
        }
        builder.append(label).append("：").append(stripTrailingPunctuation(value)).append("。");
    }

    private String buildEvidence(AiAnalysisDecision decision, LlmChatCompletion completion) {
        try {
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("model", MODEL_NAME);
            evidence.put("prompt_version", AiAnalysisPromptBuilder.PROMPT_VERSION);
            evidence.put("analysis_summary", safe(decision.analysisSummary()));
            evidence.put("suggested_answer", safe(decision.suggestedAnswer()));
            evidence.put("suggestion", safe(decision.suggestion()));
            evidence.put("analysis", safe(decision.analysis()));
            evidence.put("risk", safe(decision.risk()));
            evidence.put("llm_provider", properties.getProvider());
            evidence.put("llm_model", completion.model());
            evidence.put("llm_fallback", false);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception ex) {
            log.warn("build AI analysis evidence failed", ex);
            return "{\"model\":\"LLM_ANALYSIS_V1\",\"llm_fallback\":false}";
        }
    }

    private String stripTrailingPunctuation(String value) {
        return value.replaceAll("[。；;,.，\\s]+$", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
```

- [ ] **步骤 5：运行 Provider 测试验证通过**

运行：

```bash
mvn -Dtest=SingleEnterpriseAiAnalysisMatchingProviderTest test
```

预期：`BUILD SUCCESS`。

- [ ] **步骤 6：Commit 单企业 AI 分析 Provider**

运行：

```bash
git add src/main/java/git/ggwzz/matching/service/impl/SingleEnterpriseAiAnalysisMatchingProvider.java src/main/java/git/ggwzz/matching/llm/MatchingLlmProperties.java src/test/java/git/ggwzz/matching/service/SingleEnterpriseAiAnalysisMatchingProviderTest.java
git commit -m "feat(智能挂链): 单企业改为纯 LLM AI 分析"
```

---

### 任务 3：后端默认 Provider 接线和任务行为校准

**文件：**
- 修改：`src/main/java/git/ggwzz/matching/service/impl/LlmRerankMatchingProvider.java`
- 修改测试：`src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java`

- [ ] **步骤 1：编写失败的 Service 测试断言**

修改 `src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java`：

```java
// 在 createTaskShouldCreateTaskAndSaveResults 中，把 modelName stub 和断言改为：
when(matchingProvider.getModelName()).thenReturn("LLM_ANALYSIS_V1");
...
assertEquals("LLM_ANALYSIS_V1", savedTask.getModelName());

// 在 createTaskAsyncShouldReturnProcessingTaskAndDispatchWorker 中，把 modelName stub 改为：
when(matchingProvider.getModelName()).thenReturn("LLM_ANALYSIS_V1");

// 在 retryTaskShouldCreateRetryingTaskAndDispatchWorker 中，把 modelName stub 改为：
when(matchingProvider.getModelName()).thenReturn("LLM_ANALYSIS_V1");
```

新增测试方法，确认批量任务不使用默认纯 LLM Provider：

```java
@Test
void createBatchTasksAsyncShouldKeepHybridProviderInsteadOfDefaultAiAnalysisProvider() {
    CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
    request.setAllEnterprises(false);
    request.setEnterpriseIds(List.of(100L));
    request.setAllChains(false);
    request.setChainIds(List.of(10L));

    List<MatchingTask> insertedTasks = new java.util.ArrayList<>();
    when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
    stubBatchTaskInsert(insertedTasks, new AtomicLong(1L));

    BatchMatchingTaskVO vo = matchingService.createBatchTasksAsync(request, 1L);

    assertEquals(1, vo.getSubmittedCount());
    assertEquals("HYBRID_EMBEDDING_V2", insertedTasks.get(0).getModelName());
    verify(matchingProvider, never()).getModelName();
}
```

- [ ] **步骤 2：运行 Service 测试验证失败或现有冲突**

运行：

```bash
mvn -Dtest=MatchingServiceTest test
```

预期：如果还未完成 Provider 接线，测试中 `LLM_ANALYSIS_V1` 相关断言失败，或 Spring 上下文后续运行时会因两个 `@Primary` Provider 冲突。

- [ ] **步骤 3：移除旧 V3 Provider 的 Primary 标记**

修改 `src/main/java/git/ggwzz/matching/service/impl/LlmRerankMatchingProvider.java`：

```java
// 删除 import
import org.springframework.context.annotation.Primary;

// 删除类上的 @Primary，只保留：
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRerankMatchingProvider implements MatchingProvider {
```

- [ ] **步骤 4：运行 Service 测试验证通过**

运行：

```bash
mvn -Dtest=MatchingServiceTest test
```

预期：`BUILD SUCCESS`，批量任务仍记录 `HYBRID_EMBEDDING_V2`。

- [ ] **步骤 5：运行后端匹配相关测试**

运行：

```bash
mvn -Dtest=AiAnalysisPromptBuilderTest,AiAnalysisResultParserTest,SingleEnterpriseAiAnalysisMatchingProviderTest,MatchingServiceTest,LlmRerankMatchingProviderTest test
```

预期：`BUILD SUCCESS`。`LlmRerankMatchingProviderTest` 仍通过，证明旧 V3 逻辑保留。

- [ ] **步骤 6：Commit 后端接线**

运行：

```bash
git add src/main/java/git/ggwzz/matching/service/impl/LlmRerankMatchingProvider.java src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java
git commit -m "refactor(智能挂链): 切换单企业默认 AI 分析 Provider"
```

---

### 任务 4：前端单企业人工智能分析文案

**文件：**
- 修改：`src/views/matching/MatchingTask.vue`
- 修改：`src/views/matching/matchingViewModel.ts`
- 修改测试：`src/views/matching/matchingViewModel.spec.ts`
- 创建测试：`src/views/matching/MatchingTask.spec.ts`

- [ ] **步骤 1：编写失败的 MatchingTask 组件文案测试**

在前端仓库创建 `src/views/matching/MatchingTask.spec.ts`：

```typescript
import { defineComponent } from "vue";
import { shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MatchingTask from "./MatchingTask.vue";

const enterpriseApiMocks = vi.hoisted(() => ({
  fetchEnterprise: vi.fn(),
  fetchEnterprises: vi.fn(),
}));
const chainApiMocks = vi.hoisted(() => ({
  fetchChains: vi.fn(),
  fetchNodeTree: vi.fn(),
  unlinkEnterpriseFromNode: vi.fn(),
}));
const matchingApiMocks = vi.hoisted(() => ({
  acceptMatchingResult: vi.fn(),
  cancelMatchingTask: vi.fn(),
  createMatchingTask: vi.fn(),
  fetchMatchingTask: vi.fn(),
  manualConfirmMatching: vi.fn(),
  rejectMatchingResult: vi.fn(),
  revokeMatchingTask: vi.fn(),
  retryMatchingTask: vi.fn(),
}));
const matchingStoreMocks = vi.hoisted(() => ({
  setCurrentTask: vi.fn(),
  setRecommending: vi.fn(),
}));

vi.mock("@/api/enterprise", () => enterpriseApiMocks);
vi.mock("@/api/chain", () => chainApiMocks);
vi.mock("@/api/matching", () => matchingApiMocks);
vi.mock("@/api/mode", () => ({ isMockApiEnabled: () => false }));
vi.mock("@/stores/matching", () => ({
  useMatchingStore: () => matchingStoreMocks,
}));
vi.mock("element-plus", () => ({
  ElMessage: {
    error: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(),
  },
}));

const passthrough = defineComponent({
  template: "<div><slot /><slot name=\"actions\" /><slot name=\"footer\" /></div>",
});

function mountPage() {
  return shallowMount(MatchingTask, {
    global: {
      stubs: {
        BatchWorkbench: passthrough,
        SearchBar: passthrough,
        StatusTag: passthrough,
        ElAlert: passthrough,
        ElButton: defineComponent({ template: "<button><slot /></button>" }),
        ElDialog: passthrough,
        ElForm: passthrough,
        ElFormItem: passthrough,
        ElInput: passthrough,
        ElOption: passthrough,
        ElSelect: passthrough,
        ElTable: passthrough,
        ElTableColumn: passthrough,
        ElTabPane: passthrough,
        ElTabs: passthrough,
        ElTag: passthrough,
      },
    },
  });
}

describe("MatchingTask", () => {
  beforeEach(() => {
    enterpriseApiMocks.fetchEnterprises.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 200 });
    enterpriseApiMocks.fetchEnterprise.mockResolvedValue(null);
    chainApiMocks.fetchChains.mockResolvedValue([]);
    chainApiMocks.fetchNodeTree.mockResolvedValue([]);
    vi.clearAllMocks();
  });

  it("renders single enterprise mode as AI analysis instead of recommendation", () => {
    const wrapper = mountPage();

    expect(wrapper.text()).toContain("人工智能分析");
    expect(wrapper.text()).toContain("开始分析");
    expect(wrapper.text()).toContain("分析与建议");
    expect(wrapper.text()).not.toContain("单企业智能推荐");
    expect(wrapper.text()).not.toContain("生成推荐");
    expect(wrapper.text()).not.toContain("推荐理由");
  });
});
```

- [ ] **步骤 2：运行组件文案测试验证失败**

运行：

```bash
npm test -- src/views/matching/MatchingTask.spec.ts
```

预期：测试失败，文本仍包含「单企业智能推荐」「生成推荐」「推荐理由」。

- [ ] **步骤 3：编写失败的 ViewModel 文案测试**

修改 `src/views/matching/matchingViewModel.spec.ts` 中 `builds expandable operation logs with revoke targets` 断言：

```typescript
expect(logs[0].action).toBe("人工智能分析");
expect(logs[0].content).toBe("生成 1 条分析候选节点");
expect(logs[0].details?.[0].description).toContain("分析建议：");
expect(logs[0].details?.[0].description).toContain("分析依据：语义相似度 0.71；规则置信度 0.80");
```

- [ ] **步骤 4：运行 ViewModel 测试验证失败**

运行：

```bash
npm test -- src/views/matching/matchingViewModel.spec.ts
```

预期：测试失败，当前日志仍使用「智能推荐」「推荐理由」等推荐语义。

- [ ] **步骤 5：修改 MatchingTask 单企业文案**

修改 `src/views/matching/MatchingTask.vue`：

```vue
<p class="page__description">
  支持批量挂链工作台，并为单企业提供大模型人工智能分析。
</p>
...
<el-tab-pane label="人工智能分析" name="single">
...
{{ loading ? "分析中" : "开始分析" }}
...
await resolveTaskAfterSubmission(
  submittedTask,
  "分析任务已提交，正在调用大模型生成结果",
);
...
ElMessage.success("人工智能分析已生成");
...
ElMessage.error("人工智能分析失败，请检查 LLM 配置或稍后重试。");
...
AI 分析 {{ task.results.length }} 个节点，共
...
<h3>分析与建议</h3>
...
<el-alert
  :closable="false"
  title="AI 建议"
  :description="activeResult.reason"
  type="success"
/>
<el-alert
  :closable="false"
  title="综合分析"
  :description="`该企业可重点评估 ${activeResult.nodePath} 节点`"
  type="success"
/>
...
<span>分析证据</span>
<strong>{{ activeResult.evidence || "大模型分析结果" }}</strong>
...
<div v-else class="empty-note">选择候选节点查看分析依据。</div>
```

保留函数名 `generateRecommendation`，避免本任务扩大为行为重构；文案改动已满足单企业语义要求。

- [ ] **步骤 6：修改 ViewModel 单企业日志文案**

修改 `src/views/matching/matchingViewModel.ts` 中 `buildMatchingOperationLogs` 的单企业任务日志：

```typescript
const countText = `${input.task.results.length} 条分析候选节点`;
...
action: "人工智能分析",
content: `生成 ${countText}`,
...
`分析等级：${getConfidenceLevel(result.confidence)}`,
`综合置信度：${confidence}`,
`分析依据：${result.reason || "无"}`,
`分析证据：${result.evidence || "大模型分析结果"}`,
`处理状态：${decisionLabel}`,
`风险提示：${riskTip}`,
```

在同一个详情数组中不新增推荐字段；批量日志 `buildBatchOperationLogs` 保持「批量智能推荐」不改。

- [ ] **步骤 7：运行前端文案测试验证通过**

运行：

```bash
npm test -- src/views/matching/MatchingTask.spec.ts src/views/matching/matchingViewModel.spec.ts
```

预期：`2 passed` 或更多通过数，两个测试文件通过。

- [ ] **步骤 8：Commit 前端文案**

在 `C:\Users\18443\Desktop\大项目开发\groupproject\front` 运行：

```bash
git add src/views/matching/MatchingTask.vue src/views/matching/matchingViewModel.ts src/views/matching/matchingViewModel.spec.ts src/views/matching/MatchingTask.spec.ts
git commit -m "feat(智能挂链): 单企业页面改为 AI 分析"
```

---

### 任务 5：全量验证和收尾

**文件：**
- 不创建新文件。
- 只在验证失败时回到对应任务修复。

- [ ] **步骤 1：运行后端匹配测试**

在 `C:\Users\18443\Desktop\大项目开发\groupproject-back\back` 运行：

```bash
mvn -Dtest=AiAnalysisPromptBuilderTest,AiAnalysisResultParserTest,SingleEnterpriseAiAnalysisMatchingProviderTest,MatchingServiceTest,LlmRerankMatchingProviderTest test
```

预期：`BUILD SUCCESS`。

- [ ] **步骤 2：运行后端全量测试**

运行：

```bash
mvn test
```

预期：`BUILD SUCCESS`。若失败，定位到失败测试并回到对应任务补红绿循环。

- [ ] **步骤 3：运行前端相关测试**

在 `C:\Users\18443\Desktop\大项目开发\groupproject\front` 运行：

```bash
npm test -- src/api/matching.spec.ts src/views/matching/matchingViewModel.spec.ts src/views/matching/MatchingTask.spec.ts
```

预期：相关测试全部通过。

- [ ] **步骤 4：运行前端构建或类型检查**

运行：

```bash
npm run build
```

预期：`vue-tsc -b && vite build` 成功完成。

- [ ] **步骤 5：检查工作区只剩预期变更**

分别运行：

```bash
git status --short
```

预期：

- 后端仓库只剩用户既有未提交改动，或当前任务已经全部提交。
- 前端仓库当前任务已经提交，无未提交任务改动。

- [ ] **步骤 6：最终说明**

最终回复包含：

- 后端新增纯 LLM 单企业分析 Provider。
- 单企业 evidence 标识 `LLM_ANALYSIS_V1`。
- 批量挂链仍走本地批量引擎。
- 前端单企业页面已改为「人工智能分析」语义。
- 列出实际运行的验证命令及结果。
