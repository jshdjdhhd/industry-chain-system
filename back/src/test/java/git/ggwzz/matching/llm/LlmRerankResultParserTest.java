package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmRerankResultParserTest {

    private final LlmRerankResultParser parser = new LlmRerankResultParser(new ObjectMapper());

    @Test
    void parse_shouldAcceptJsonObjectAndFilterUnknownOrDuplicateNodeIds() {
        String content = """
                {
                  "ranked_results": [
                    {"node_id": 102, "confidence": 0.91, "reason": "更贴近光模块主营产品", "risk": "部分业务描述较泛"},
                    {"node_id": 999, "confidence": 0.88, "reason": "未知节点", "risk": ""},
                    {"node_id": 102, "confidence": 0.77, "reason": "重复节点", "risk": ""},
                    {"node_id": 101, "confidence": 0.66, "reason": "可作为备选", "risk": "证据较弱"}
                  ]
                }
                """;

        List<LlmRerankDecision> decisions = parser.parse(content, Set.of(101L, 102L), 5);

        assertEquals(2, decisions.size());
        assertEquals(102L, decisions.get(0).nodeId());
        assertEquals(0.91D, decisions.get(0).confidence());
        assertEquals("更贴近光模块主营产品", decisions.get(0).reason());
        assertEquals("部分业务描述较泛", decisions.get(0).risk());
        assertEquals(101L, decisions.get(1).nodeId());
    }

    @Test
    void parse_shouldAcceptMarkdownJsonFence() {
        String content = """
                ```json
                {"ranked_results":[{"node_id":101,"confidence":0.83,"reason":"匹配主营产品","risk":"无明显风险"}]}
                ```
                """;

        List<LlmRerankDecision> decisions = parser.parse(content, Set.of(101L), 5);

        assertEquals(1, decisions.size());
        assertEquals(101L, decisions.get(0).nodeId());
        assertEquals("匹配主营产品", decisions.get(0).reason());
    }

    @Test
    void parse_shouldExtractRankedResultsJsonWhenReasoningTextContainsBraces() {
        String content = """
                我会先比较 {企业画像} 和候选节点，避免只按名称相似度排序。
                最终 JSON 如下：
                {"ranked_results":[{"node_id":101,"confidence":0.92,"reason":"主营产品直接匹配光模块节点","risk":"无明显风险"}]}
                以上只使用候选集内节点。
                """;

        List<LlmRerankDecision> decisions = parser.parse(content, Set.of(101L), 5);

        assertEquals(1, decisions.size());
        assertEquals(101L, decisions.get(0).nodeId());
        assertEquals(0.92D, decisions.get(0).confidence());
        assertEquals("主营产品直接匹配光模块节点", decisions.get(0).reason());
    }

    @Test
    void parse_shouldThrowWhenContentIsNotJson() {
        assertThrows(BusinessException.class,
                () -> parser.parse("我认为第一个节点最好", Set.of(101L), 5));
    }
}
