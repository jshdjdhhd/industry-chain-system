package git.ggwzz.matching.llm;

import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAnalysisResultParserTest {

    private final AiAnalysisResultParser parser = new AiAnalysisResultParser();

    @Test
    void parseShouldReturnSingleDecisionWithFullAnalysisText() {
        String content = """
                根据企业画像分析，宁德时代新能源科技股份有限公司最适合挂接到动力电池节点。

                分析依据：
                1. 企业的主营产品包括动力电池、电芯和储能系统，与动力电池节点高度匹配
                2. 经营范围为锂离子电池制造与销售，直接覆盖动力电池环节
                3. 标签中的"新能源"和"电池"进一步确认了业务相关性

                风险提示：
                - 储能系统业务可能需要单独挂接到储能节点
                - 建议核验企业是否涉及其他产业链环节

                操作建议：
                建议优先挂接到动力电池节点，并关注储能业务的归属。
                """;

        List<AiAnalysisDecision> decisions = parser.parse(content, Set.of(101L, 102L), 3);

        assertEquals(1, decisions.size());
        assertNotNull(decisions.get(0).nodeId());
        assertEquals(0.5D, decisions.get(0).confidence());
        assertEquals("AI 分析建议", decisions.get(0).suggestion());
        assertTrue(decisions.get(0).analysis().contains("宁德时代"));
        assertTrue(decisions.get(0).analysis().contains("动力电池"));
        assertEquals("请人工复核 AI 分析结果", decisions.get(0).risk());
        assertTrue(decisions.get(0).analysisSummary().contains("宁德时代"));
        assertEquals(decisions.get(0).analysis(), decisions.get(0).suggestedAnswer());
    }

    @Test
    void parseShouldUseFirstAllowedNodeIdWhenNoNodeIdExtracted() {
        String content = "企业适合挂接到动力电池节点。";

        Set<Long> allowedNodeIds = new LinkedHashSet<>();
        allowedNodeIds.add(101L);
        allowedNodeIds.add(102L);
        List<AiAnalysisDecision> decisions = parser.parse(content, allowedNodeIds, 1);

        assertEquals(1, decisions.size());
        assertEquals(101L, decisions.get(0).nodeId());
    }

    @Test
    void parseShouldThrowWhenContentIsBlank() {
        assertThrows(BusinessException.class,
                () -> parser.parse("", Set.of(101L), 3));
    }

    @Test
    void parseShouldThrowWhenContentIsNull() {
        assertThrows(BusinessException.class,
                () -> parser.parse(null, Set.of(101L), 3));
    }
}
