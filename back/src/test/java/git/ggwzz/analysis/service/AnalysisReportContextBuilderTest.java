package git.ggwzz.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import git.ggwzz.analysis.enums.ChainHealthLevel;
import git.ggwzz.analysis.mapper.AnalysisQueryMapper;
import git.ggwzz.analysis.mapper.projection.EnterpriseProfileDetailRow;
import git.ggwzz.analysis.mapper.projection.MatchingEvidenceRow;
import git.ggwzz.analysis.service.AnalysisReportContextBuilder.MetricsResult;
import git.ggwzz.analysis.service.AnalysisReportContextBuilder.ReportContext;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import git.ggwzz.chain.entity.Chain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AnalysisReportContextBuilder} 单元测试。
 *
 * <p>覆盖：metrics summary 统计、企业样本按 resolved 过滤 + 上限裁剪、
 * 推荐证据按 resolved 过滤、上下文章节组装。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisReportContextBuilderTest {

    @Mock
    private AnalysisQueryMapper analysisQueryMapper;
    @Mock
    private ChainDiagnosisTreeService chainDiagnosisTreeService;

    private AnalysisReportContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AnalysisReportContextBuilder(
                chainDiagnosisTreeService, analysisQueryMapper, new ObjectMapper());
    }

    @Test
    void shouldBuildContextWithAllSections() {
        AnalysisReportTask task = task(1L, "ALL_CHAIN", List.of(2L, 3L));
        Chain chain = chain(1L, "新能源汽车");
        when(chainDiagnosisTreeService.getDiagnosisTree(1L)).thenReturn(buildTree());
        when(analysisQueryMapper.selectEnterpriseProfilesByChainIdForReport(1L))
                .thenReturn(List.of());
        when(analysisQueryMapper.selectMatchingEvidenceByChainId(eq(1L), anyInt()))
                .thenReturn(List.of());

        ReportContext result = builder.buildContext(task, chain);

        Map<String, Object> context = result.context();
        assertNotNull(context.get("chain"));
        assertNotNull(context.get("scope"));
        assertNotNull(context.get("metrics"));
        assertNotNull(context.get("enterpriseSamples"));
        assertNotNull(context.get("matchingEvidence"));
        assertNotNull(result.metricsSnapshot());
    }

    @Test
    void metricsSummaryShouldCountLevels() {
        DiagnosisTreeNodeVO root = treeWithLevels();

        // 直接调用 buildMetrics
        MetricsResult metrics = builder.buildMetrics(
                builder.collectScopeNodesPublic(root, Set.of(2L, 3L, 4L, 5L, 6L)));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) metrics.asContextMap().get("summary");
        assertEquals(5, ((Number) summary.get("nodeCount")).intValue());
        assertEquals(1, ((Number) summary.get("healthyCount")).intValue());
        assertEquals(1, ((Number) summary.get("normalCount")).intValue());
        assertEquals(1, ((Number) summary.get("weakCount")).intValue());
        assertEquals(1, ((Number) summary.get("riskCount")).intValue());
        assertEquals(1, ((Number) summary.get("emptyCount")).intValue());
    }

    @Test
    void enterpriseSamplesShouldFilterByResolvedNodes() {
        EnterpriseProfileDetailRow rowOnNode2 = profile(10L, 2L, "A公司");
        EnterpriseProfileDetailRow rowOnNode9 = profile(11L, 9L, "未纳入节点公司");
        Set<Long> resolved = Set.of(2L);

        List<Map<String, Object>> samples = builder.buildEnterpriseSamples(
                List.of(rowOnNode2, rowOnNode9), resolved, metricsForNode(2L, "HEALTHY"));

        assertEquals(1, samples.size());
        assertEquals("A公司", samples.get(0).get("enterpriseName"));
        assertEquals(2L, samples.get(0).get("nodeId"));
    }

    @Test
    void enterpriseSamplesShouldCapAtTotalLimit() {
        // 构造 60 家企业分布在风险节点上，每节点上限 15，总数上限 50
        List<EnterpriseProfileDetailRow> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(profile(100L + i, 2L, "企业" + i));
        }
        for (int i = 0; i < 30; i++) {
            rows.add(profile(200L + i, 3L, "企业B" + i));
        }
        Set<Long> resolved = Set.of(2L, 3L);
        MetricsResult metrics = metricsForNodes(
                Map.of(2L, ChainHealthLevel.RISK, 3L, ChainHealthLevel.RISK));

        List<Map<String, Object>> samples = builder.buildEnterpriseSamples(
                rows, resolved, metrics);

        assertTrue(samples.size() <= AnalysisReportContextBuilder.TOTAL_ENTERPRISE_SAMPLE_LIMIT,
                "样本总数不应超过上限");
    }

    @Test
    void healthyNodeShouldHaveSmallerPerNodeLimitThanRiskNode() {
        // 健康节点放 10 家，应被裁到 5
        List<EnterpriseProfileDetailRow> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(profile(300L + i, 2L, "健康节点企业" + i));
        }
        Set<Long> resolved = Set.of(2L);
        MetricsResult metrics = metricsForNodes(Map.of(2L, ChainHealthLevel.HEALTHY));

        List<Map<String, Object>> samples = builder.buildEnterpriseSamples(
                rows, resolved, metrics);

        assertEquals(AnalysisReportContextBuilder.HEALTHY_NODE_SAMPLE_LIMIT, samples.size());
    }

    @Test
    void riskNodesShouldBeSampledBeforeHealthyNodes() {
        // 风险节点放 15 家，健康节点放 10 家，总数上限 50 不会触发，应都被取入
        // 但验证排序：风险节点样本在前
        List<EnterpriseProfileDetailRow> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(profile(400L + i, 7L, "健康企业" + i));   // 健康节点
        }
        for (int i = 0; i < 5; i++) {
            rows.add(profile(500L + i, 8L, "风险企业" + i));   // 风险节点
        }
        Set<Long> resolved = Set.of(7L, 8L);
        MetricsResult metrics = metricsForNodes(
                Map.of(7L, ChainHealthLevel.HEALTHY, 8L, ChainHealthLevel.RISK));

        List<Map<String, Object>> samples = builder.buildEnterpriseSamples(
                rows, resolved, metrics);

        assertEquals(10, samples.size());
        assertEquals("风险企业0", samples.get(0).get("enterpriseName"),
                "风险节点企业应排在健康节点企业之前");
    }

    @Test
    void matchingEvidenceShouldFilterByResolvedNodes() {
        MatchingEvidenceRow onNode2 = evidence(1L, 2L, "A");
        MatchingEvidenceRow onNode9 = evidence(2L, 9L, "B");
        Set<Long> resolved = Set.of(2L);

        List<Map<String, Object>> out = builder.buildMatchingEvidence(
                List.of(onNode2, onNode9), resolved);

        assertEquals(1, out.size());
        assertEquals(2L, out.get(0).get("nodeId"));
        assertEquals("A", out.get(0).get("enterpriseName"));
    }

    @Test
    void matchingEvidenceShouldTruncateLongReasonAndEvidence() {
        MatchingEvidenceRow row = evidence(1L, 2L, "A");
        row.setReason("理".repeat(AnalysisReportContextBuilder.EVIDENCE_REASON_MAX_LENGTH + 50));
        row.setEvidence("证".repeat(AnalysisReportContextBuilder.EVIDENCE_EVIDENCE_MAX_LENGTH + 50));
        Set<Long> resolved = Set.of(2L);

        List<Map<String, Object>> out = builder.buildMatchingEvidence(List.of(row), resolved);

        String reason = (String) out.get(0).get("reason");
        String evidence = (String) out.get(0).get("evidence");
        assertTrue(reason.length() <= AnalysisReportContextBuilder.EVIDENCE_REASON_MAX_LENGTH + 1,
                "reason 截断后长度不应超 maxLength+1（含省略号）");
        assertTrue(reason.endsWith("…"));
        assertTrue(evidence.length() <= AnalysisReportContextBuilder.EVIDENCE_EVIDENCE_MAX_LENGTH + 1,
                "evidence 截断后长度不应超 maxLength+1（含省略号）");
        assertTrue(evidence.endsWith("…"));
    }

    @Test
    void contextShouldBeTrimmedWhenExceedingMaxLength() {
        AnalysisReportContextBuilder shortLimitBuilder = new AnalysisReportContextBuilder(
                chainDiagnosisTreeService, analysisQueryMapper, new ObjectMapper()) {
            @Override
            int contextJsonMaxLength() {
                return 200;
            }
        };
        AnalysisReportTask task = task(1L, "ALL_CHAIN", List.of(2L));
        Chain chain = chain(1L, "链");
        when(chainDiagnosisTreeService.getDiagnosisTree(1L)).thenReturn(buildTree());
        List<EnterpriseProfileDetailRow> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(profile(100L + i, 2L, "企业" + i));
        }
        when(analysisQueryMapper.selectEnterpriseProfilesByChainIdForReport(1L))
                .thenReturn(rows);
        when(analysisQueryMapper.selectMatchingEvidenceByChainId(eq(1L), anyInt()))
                .thenReturn(List.of());

        ReportContext result = shortLimitBuilder.buildContext(task, chain);

        @SuppressWarnings("unchecked")
        List<Object> samples = (List<Object>) result.context().get("enterpriseSamples");
        assertTrue(samples.size() < 30, "上下文超限应裁剪企业样本");
    }

    @Test
    void emptyResolvedShouldYieldEmptySamples() {
        List<Map<String, Object>> samples = builder.buildEnterpriseSamples(
                List.of(profile(1L, 2L, "x")), Collections.emptySet(), metricsForNode(2L, "HEALTHY"));
        assertTrue(samples.isEmpty());
    }

    // ==================== fixture ====================

    private Chain chain(Long id, String name) {
        Chain c = new Chain();
        c.setId(id);
        c.setChainName(name);
        return c;
    }

    private AnalysisReportTask task(Long chainId, String scopeType, List<Long> resolved) {
        AnalysisReportTask t = new AnalysisReportTask();
        t.setChainId(chainId);
        t.setScopeType(scopeType);
        t.setResolvedNodeIds(resolved);
        return t;
    }

    private EnterpriseProfileDetailRow profile(Long eid, Long nodeId, String name) {
        EnterpriseProfileDetailRow r = new EnterpriseProfileDetailRow();
        r.setEnterpriseId(eid);
        r.setNodeId(nodeId);
        r.setEnterpriseName(name);
        r.setStatus("ACTIVE");
        r.setOutputValue(new BigDecimal("100.00"));
        return r;
    }

    private MatchingEvidenceRow evidence(Long id, Long nodeId, String enterpriseName) {
        MatchingEvidenceRow r = new MatchingEvidenceRow();
        r.setResultId(id);
        r.setNodeId(nodeId);
        r.setEnterpriseName(enterpriseName);
        r.setConfidence(new BigDecimal("0.8200"));
        r.setDecision("ACCEPTED");
        return r;
    }

    private MetricsResult metricsForNode(Long nodeId, String level) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", nodeId);
        node.put("healthLevel", level);
        return new MetricsResult(Map.of("nodeCount", 1), List.of(node));
    }

    private MetricsResult metricsForNodes(Map<Long, ChainHealthLevel> levelByNode) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (var e : levelByNode.entrySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("nodeId", e.getKey());
            node.put("healthLevel", e.getValue().name());
            nodes.add(node);
        }
        return new MetricsResult(Map.of("nodeCount", nodes.size()), nodes);
    }

    private DiagnosisTreeNodeVO buildTree() {
        DiagnosisTreeNodeVO root = node(1L, "根", 0, "EMPTY", 0);
        root.setChildren(new ArrayList<>(Arrays.asList(
                node(2L, "一级A", 1, "HEALTHY", 80),
                node(3L, "一级B", 1, "NORMAL", 60))));
        return root;
    }

    private DiagnosisTreeNodeVO treeWithLevels() {
        DiagnosisTreeNodeVO root = node(1L, "根", 0, "EMPTY", 0);
        root.setChildren(new ArrayList<>(Arrays.asList(
                node(2L, "健康", 1, "HEALTHY", 85),
                node(3L, "一般", 1, "NORMAL", 65),
                node(4L, "偏弱", 1, "WEAK", 45),
                node(5L, "风险", 1, "RISK", 15),
                node(6L, "空白", 1, "EMPTY", 0))));
        return root;
    }

    private DiagnosisTreeNodeVO node(Long id, String name, int level, String healthLevel, int score) {
        DiagnosisTreeNodeVO n = new DiagnosisTreeNodeVO();
        n.setNodeId(id);
        n.setNodeName(name);
        n.setNodeLevel(level);
        n.setHealthLevel(healthLevel);
        n.setHealthLabel(healthLabel(healthLevel));
        n.setHealthScore(score);
        n.setSubtreeEnterpriseCount(1);
        n.setDirectEnterpriseCount(1);
        n.setWeakReasons(new ArrayList<>());
        n.setChildren(new ArrayList<>());
        return n;
    }

    private String healthLabel(String level) {
        return switch (level) {
            case "HEALTHY" -> "健康";
            case "NORMAL" -> "一般";
            case "WEAK" -> "偏弱";
            case "RISK" -> "风险";
            default -> "空白";
        };
    }
}
