package git.ggwzz.matching.service;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.embedding.EmbeddingProfileTextBuilder;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.service.impl.HybridEmbeddingMatchingProvider;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridEmbeddingMatchingProviderTest {

    @Mock
    private EnterpriseQueryService enterpriseQueryService;

    @Mock
    private ChainNodeQueryService chainNodeQueryService;

    @Mock
    private EmbeddingVectorService embeddingVectorService;

    @Mock
    private RuleBasedMatchingProvider ruleBasedMatchingProvider;

    private HybridEmbeddingMatchingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HybridEmbeddingMatchingProvider(
                enterpriseQueryService,
                chainNodeQueryService,
                new EmbeddingProfileTextBuilder(),
                embeddingVectorService,
                ruleBasedMatchingProvider);
    }

    @Test
    void matchShouldRankByFullProfileVectorSimilarity() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("中际旭创股份有限公司");
        enterprise.setBusinessScope("光通信器件、光模块及相关设备研发、制造、销售");
        enterprise.setMainProducts("高速光模块,数据中心光模块,硅光模块,光电转换芯片");
        enterprise.setTags("光通信,算力基础设施");

        ChainNodeCandidateDTO opticalModule = node(
                201L, "光模块",
                "高速光模块,光通信模块,光器件,光电转换,数据中心互联",
                "光通信产业链 / 中游 / 光模块");
        ChainNodeCandidateDTO lithiumMining = node(
                202L, "锂矿开采",
                "锂矿,锂辉石,碳酸锂",
                "新能源汽车产业链 / 上游 / 锂矿开采");

        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of(opticalModule, lithiumMining));
        when(ruleBasedMatchingProvider.match(1L, 2L)).thenReturn(Collections.emptyList());
        when(embeddingVectorService.getOrCreateVector(eq("ENTERPRISE"), eq(1L), anyString()))
                .thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        when(embeddingVectorService.getOrCreateVector(eq("CHAIN_NODE"), eq(201L), anyString()))
                .thenReturn(new float[]{0.98f, 0.02f, 0.0f});
        when(embeddingVectorService.getOrCreateVector(eq("CHAIN_NODE"), eq(202L), anyString()))
                .thenReturn(new float[]{0.0f, 1.0f, 0.0f});

        List<MatchingResult> results = provider.match(1L, 2L);

        assertFalse(results.isEmpty());
        assertEquals(201L, results.get(0).getNodeId());
        assertTrue(results.get(0).getConfidence().doubleValue() > 0.65D);
        assertTrue(results.get(0).getReason().contains("综合推荐依据"));
        assertTrue(results.get(0).getEvidence().contains("\"vector_similarity\""));
        assertTrue(results.get(0).getEvidence().contains("中际旭创股份有限公司"));
        assertTrue(results.get(0).getEvidence().contains("光通信产业链 / 中游 / 光模块"));
    }

    @Test
    void matchShouldFallbackToRuleResultsWhenEmbeddingFails() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("中际旭创股份有限公司");
        enterprise.setBusinessScope("光通信器件制造");
        enterprise.setMainProducts("高速光模块");
        enterprise.setTags("光通信");

        ChainNodeCandidateDTO opticalModule = node(
                201L, "光模块",
                "高速光模块,光通信模块",
                "光通信产业链 / 中游 / 光模块");

        MatchingResult ruleResult = new MatchingResult();
        ruleResult.setNodeId(201L);
        ruleResult.setConfidence(new BigDecimal("0.8000"));
        ruleResult.setReason("主营产品命中1个关键词");
        ruleResult.setEvidence("{\"keyword_match\":[\"高速光模块\"]}");

        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of(opticalModule));
        when(ruleBasedMatchingProvider.match(1L, 2L)).thenReturn(List.of(ruleResult));
        when(embeddingVectorService.getOrCreateVector(eq("ENTERPRISE"), eq(1L), anyString()))
                .thenThrow(new IllegalStateException("embedding unavailable"));

        List<MatchingResult> results = provider.match(1L, 2L);

        assertEquals(1, results.size());
        assertEquals(201L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.8000"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("embedding 服务不可用"));
        assertTrue(results.get(0).getEvidence().contains("\"embedding_fallback\":true"));
        assertTrue(results.get(0).getEvidence().contains("\"fallback_model\":\"RULE_BASED\""));
    }

    @Test
    void matchShouldReturnBestLowVectorCandidatesWhenNothingPassesThreshold() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("泛化科技有限公司");
        enterprise.setBusinessScope("技术服务、软件开发、企业咨询");
        enterprise.setMainProducts("数字化平台");

        ChainNodeCandidateDTO battery = node(
                201L, "动力电池", "动力电池,电芯", "新能源汽车产业链 / 中游 / 动力电池");
        ChainNodeCandidateDTO motor = node(
                202L, "驱动电机", "驱动电机,电控", "新能源汽车产业链 / 中游 / 驱动电机");

        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of(battery, motor));
        when(ruleBasedMatchingProvider.match(1L, 2L)).thenReturn(Collections.emptyList());
        when(embeddingVectorService.getOrCreateVector(eq("ENTERPRISE"), eq(1L), anyString()))
                .thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        when(embeddingVectorService.getOrCreateVector(eq("CHAIN_NODE"), eq(201L), anyString()))
                .thenReturn(new float[]{0.10f, 0.90f, 0.0f});
        when(embeddingVectorService.getOrCreateVector(eq("CHAIN_NODE"), eq(202L), anyString()))
                .thenReturn(new float[]{0.20f, 0.80f, 0.0f});

        List<MatchingResult> results = provider.match(1L, 2L);

        assertEquals(2, results.size());
        assertEquals(202L, results.get(0).getNodeId());
        assertTrue(results.get(0).getConfidence().doubleValue() < 0.25D);
        assertTrue(results.get(0).getReason().contains("低置信候选"));
        assertTrue(results.get(0).getEvidence().contains("\"below_vector_threshold\":true"));
    }

    @Test
    void matchShouldSkipEmbeddingWhenRuleResultIsDecisive() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("宁德时代新能源科技股份有限公司");
        enterprise.setBusinessScope("动力电池系统、电芯及储能系统研发制造");
        enterprise.setMainProducts("动力电池,电芯,电池模组");

        ChainNodeCandidateDTO battery = node(
                201L, "动力电池", "动力电池,电芯,电池模组", "新能源汽车产业链 / 中游 / 动力电池");

        MatchingResult ruleResult = new MatchingResult();
        ruleResult.setNodeId(201L);
        ruleResult.setConfidence(new BigDecimal("0.9500"));
        ruleResult.setReason("主营产品命中3个关键词；经营范围命中1个关键词");
        ruleResult.setEvidence("{\"keyword_match\":[\"动力电池\",\"电芯\",\"电池模组\"]}");

        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of(battery));
        when(ruleBasedMatchingProvider.match(1L, 2L)).thenReturn(List.of(ruleResult));

        List<MatchingResult> results = provider.match(1L, 2L);

        assertEquals(1, results.size());
        assertEquals(201L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.9500"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("本地规则已形成高置信判断"));
        assertTrue(results.get(0).getEvidence().contains("\"embedding_skipped\":true"));
        verifyNoInteractions(embeddingVectorService);
    }

    @Test
    void getModelNameShouldUseHybridEmbeddingV2() {
        assertEquals("HYBRID_EMBEDDING_V2", provider.getModelName());
    }

    private ChainNodeCandidateDTO node(Long nodeId, String nodeName, String keywords, String nodePath) {
        ChainNodeCandidateDTO dto = new ChainNodeCandidateDTO();
        dto.setNodeId(nodeId);
        dto.setChainId(2L);
        dto.setNodeName(nodeName);
        dto.setKeywords(keywords);
        dto.setNodePath(nodePath);
        dto.setLevel(3);
        return dto;
    }
}
