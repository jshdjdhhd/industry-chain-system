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
                new AiAnalysisResultParser(),
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
                根据企业画像分析，宁德时代新能源科技股份有限公司最适合挂接到动力电池节点。

                分析依据：
                1. 企业的主营产品包括动力电池、电芯和储能系统，与动力电池节点高度匹配
                2. 经营范围为锂离子电池制造与销售，直接覆盖动力电池环节

                风险提示：
                - 储能系统业务可能需要单独挂接到储能节点

                操作建议：
                建议优先挂接到动力电池节点，并关注储能业务的归属。
                """));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.5000"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("宁德时代"));
        assertTrue(results.get(0).getReason().contains("动力电池"));
        assertTrue(results.get(0).getEvidence().contains("\"model\":\"LLM_ANALYSIS_V1\""));
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
