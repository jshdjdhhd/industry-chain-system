package git.ggwzz.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import git.ggwzz.analysis.enums.AnalysisScopeType;
import git.ggwzz.analysis.enums.ChainHealthLevel;
import git.ggwzz.analysis.mapper.AnalysisQueryMapper;
import git.ggwzz.analysis.mapper.projection.EnterpriseProfileDetailRow;
import git.ggwzz.analysis.mapper.projection.MatchingEvidenceRow;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import git.ggwzz.chain.entity.Chain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 健康分析报告结构化上下文构建器。
 *
 * <p>只从现有结构化业务表读取数据，组装喂给大模型的上下文：
 * chain / scope / metrics / enterpriseSamples / matchingEvidence。
 * 不引入外部政策、新闻、招商候选企业。</p>
 *
 * <p>上下文控制：
 * <ul>
 *   <li>风险、偏弱、空白节点优先进入报告。</li>
 *   <li>企业样本总数上限 {@value #TOTAL_ENTERPRISE_SAMPLE_LIMIT} 家。</li>
 *   <li>单节点企业样本上限：非健康节点（风险/空白/偏弱/一般）最多
 *       {@value #NON_HEALTHY_NODE_SAMPLE_LIMIT} 家，健康节点最多
 *       {@value #HEALTHY_NODE_SAMPLE_LIMIT} 家。</li>
 *   <li>推荐证据 reason/evidence 单条截断，防止长文本撑爆模型上下文窗口。</li>
 *   <li>上下文 JSON 序列化结果软上限 {@value #CONTEXT_JSON_MAX_LENGTH} 字符，超限按优先级裁剪。</li>
 *   <li>不把未挂载企业放入上下文。</li>
 * </ul>
 * </p>
 *
 * <p>纯计算无写库，不加事务。同时产出 metricsSnapshot JSON 字符串供任务保存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportContextBuilder {

    /** 企业样本总数上限 */
    static final int TOTAL_ENTERPRISE_SAMPLE_LIMIT = 50;
    /** 非健康节点（风险/空白/偏弱/一般）单节点企业样本上限 */
    static final int NON_HEALTHY_NODE_SAMPLE_LIMIT = 15;
    /** 健康节点单节点企业样本上限 */
    static final int HEALTHY_NODE_SAMPLE_LIMIT = 5;
    /** matching 证据最大返回行数 */
    static final int MATCHING_EVIDENCE_LIMIT = 100;
    /** 单条推荐理由截断长度 */
    static final int EVIDENCE_REASON_MAX_LENGTH = 200;
    /** 单条推荐证据截断长度 */
    static final int EVIDENCE_EVIDENCE_MAX_LENGTH = 500;
    /** 上下文 JSON 序列化结果软上限（字符），超限按优先级裁剪 */
    static final int CONTEXT_JSON_MAX_LENGTH = 60000;

    private final ChainDiagnosisTreeService chainDiagnosisTreeService;
    private final AnalysisQueryMapper analysisQueryMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构建结构化上下文与指标快照。
     *
     * @param task  报告任务（含 chainId / scopeType / selectedNodeIds / resolvedNodeIds）
     * @param chain 产业链
     * @return 上下文 + 指标快照 JSON 的包装
     */
    public ReportContext buildContext(AnalysisReportTask task, Chain chain) {
        DiagnosisTreeNodeVO root = chainDiagnosisTreeService.getDiagnosisTree(task.getChainId());
        Set<Long> resolved = task.getResolvedNodeIds() == null
                ? new HashSet<>() : new HashSet<>(task.getResolvedNodeIds());

        // 范围内的诊断节点（已按 resolved 过滤）
        List<DiagnosisTreeNodeVO> scopeNodes = collectScopeNodes(root, resolved);
        MetricsResult metrics = buildMetrics(scopeNodes);

        // 企业样本：取全链挂链企业画像明细，内存按 nodeId 过滤 + 样本上限裁剪
        List<EnterpriseProfileDetailRow> allProfiles = analysisQueryMapper
                .selectEnterpriseProfilesByChainIdForReport(task.getChainId());
        List<Map<String, Object>> enterpriseSamples = buildEnterpriseSamples(allProfiles, resolved, metrics);

        // 推荐证据：取全链证据，内存按 resolved nodeId 过滤（单条截断）
        List<MatchingEvidenceRow> allEvidence = analysisQueryMapper
                .selectMatchingEvidenceByChainId(task.getChainId(), MATCHING_EVIDENCE_LIMIT);
        List<Map<String, Object>> matchingEvidence = buildMatchingEvidence(allEvidence, resolved);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("chain", buildChainSection(chain));
        context.put("scope", buildScopeSection(task));
        context.put("metrics", metrics.asContextMap());
        context.put("enterpriseSamples", enterpriseSamples);
        context.put("matchingEvidence", matchingEvidence);

        // 上下文长度软上限：超限按优先级丢弃尾部企业样本/证据
        trimContextByLength(context);

        String metricsSnapshot = serialize(metrics.asContextMap());
        return new ReportContext(context, metricsSnapshot);
    }

    // ==================== 上下文章节 ====================

    private Map<String, Object> buildChainSection(Chain chain) {
        Map<String, Object> chainMap = new LinkedHashMap<>();
        chainMap.put("chainId", chain.getId());
        chainMap.put("chainName", chain.getChainName());
        return chainMap;
    }

    private Map<String, Object> buildScopeSection(AnalysisReportTask task) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("selectedNodeIds",
                task.getSelectedNodeIds() == null ? List.of() : task.getSelectedNodeIds());
        scope.put("resolvedNodeIds",
                task.getResolvedNodeIds() == null ? List.of() : task.getResolvedNodeIds());
        scope.put("scopeDescription", buildScopeDescription(task));
        return scope;
    }

    private String buildScopeDescription(AnalysisReportTask task) {
        AnalysisScopeType scopeType = AnalysisScopeType.from(task.getScopeType());
        if (scopeType == null) {
            return "未指定范围";
        }
        if (scopeType == AnalysisScopeType.ALL_CHAIN) {
            return "整条产业链";
        }
        List<Long> resolved = task.getResolvedNodeIds();
        int count = resolved == null ? 0 : resolved.size();
        return String.format(Locale.ROOT, "选中节点及其子树，共 %d 个节点", count);
    }

    // ==================== metrics ====================

    /**
     * 遍历范围内的诊断节点，统计 summary + 各节点健康指标。
     */
    MetricsResult buildMetrics(List<DiagnosisTreeNodeVO> scopeNodes) {
        int nodeCount = scopeNodes.size();
        int enterpriseCount = 0;
        int healthyCount = 0;
        int normalCount = 0;
        int weakCount = 0;
        int riskCount = 0;
        int emptyCount = 0;
        List<Map<String, Object>> nodes = new ArrayList<>(scopeNodes.size());
        for (DiagnosisTreeNodeVO node : scopeNodes) {
            ChainHealthLevel level = parseLevel(node.getHealthLevel());
            int subtreeEnt = nullSafe(node.getSubtreeEnterpriseCount());
            enterpriseCount += subtreeEnt;
            switch (level) {
                case HEALTHY -> healthyCount++;
                case NORMAL -> normalCount++;
                case WEAK -> weakCount++;
                case RISK -> riskCount++;
                case EMPTY -> emptyCount++;
            }
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("nodeId", node.getNodeId());
            nodeMap.put("nodeName", node.getNodeName());
            nodeMap.put("nodeLevel", node.getNodeLevel());
            nodeMap.put("nodePath", node.getNodePath());
            nodeMap.put("healthScore", nullSafe(node.getHealthScore()));
            nodeMap.put("healthLevel", node.getHealthLevel());
            nodeMap.put("healthLabel", node.getHealthLabel());
            nodeMap.put("subtreeEnterpriseCount", subtreeEnt);
            nodeMap.put("directEnterpriseCount", nullSafe(node.getDirectEnterpriseCount()));
            nodeMap.put("weakReasons",
                    node.getWeakReasons() == null ? List.of() : node.getWeakReasons());
            nodes.add(nodeMap);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeCount", nodeCount);
        // 口径：按各 resolved 节点子树企业数累加，未跨节点去重（与 TemplateBuilder 口径一致）。
        // 嵌套父子节点的 subtreeEnterpriseCount 会重复计数，故可能大于全链去重企业数，仅作概览参考。
        summary.put("enterpriseCount", enterpriseCount);
        summary.put("healthyCount", healthyCount);
        summary.put("normalCount", normalCount);
        summary.put("weakCount", weakCount);
        summary.put("riskCount", riskCount);
        summary.put("emptyCount", emptyCount);
        return new MetricsResult(summary, nodes);
    }

    // ==================== enterpriseSamples ====================

    /**
     * 按 nodeId 过滤 + 样本上限裁剪企业画像。
     *
     * <p>裁剪策略：按节点健康等级排序（风险/空白/偏弱 > 一般 > 健康），
     * 单节点按上限取，总量不超过 {@value #TOTAL_ENTERPRISE_SAMPLE_LIMIT}。
     * 同一企业在多个节点出现时按节点分别计入样本（不做跨节点去重），符合
     * "围绕节点支撑情况"的报告视角。</p>
     */
    List<Map<String, Object>> buildEnterpriseSamples(List<EnterpriseProfileDetailRow> allProfiles,
                                                     Set<Long> resolved,
                                                     MetricsResult metrics) {
        if (resolved.isEmpty()) {
            return List.of();
        }
        Map<Long, ChainHealthLevel> levelByNodeId = new LinkedHashMap<>();
        for (Map<String, Object> node : metrics.nodes()) {
            Object id = node.get("nodeId");
            if (id instanceof Long nodeId) {
                levelByNodeId.put(nodeId, parseLevel((String) node.get("healthLevel")));
            }
        }
        // 按 nodeId 分组
        Map<Long, List<EnterpriseProfileDetailRow>> byNode = new LinkedHashMap<>();
        for (EnterpriseProfileDetailRow row : allProfiles) {
            if (row.getNodeId() != null && resolved.contains(row.getNodeId())) {
                byNode.computeIfAbsent(row.getNodeId(), k -> new ArrayList<>()).add(row);
            }
        }
        // 节点按健康等级优先级排序：RISK/EMPTY/WEAK > NORMAL > HEALTHY
        List<Long> orderedNodeIds = new ArrayList<>(byNode.keySet());
        orderedNodeIds.sort((a, b) -> Integer.compare(
                nodePriority(levelByNodeId.getOrDefault(a, ChainHealthLevel.EMPTY)),
                nodePriority(levelByNodeId.getOrDefault(b, ChainHealthLevel.EMPTY))));

        List<Map<String, Object>> samples = new ArrayList<>();
        int total = 0;
        for (Long nodeId : orderedNodeIds) {
            if (total >= TOTAL_ENTERPRISE_SAMPLE_LIMIT) {
                break;
            }
            ChainHealthLevel level = levelByNodeId.getOrDefault(nodeId, ChainHealthLevel.EMPTY);
            int perNodeLimit = perNodeSampleLimit(level);
            int remaining = TOTAL_ENTERPRISE_SAMPLE_LIMIT - total;
            int take = Math.min(perNodeLimit, remaining);
            List<EnterpriseProfileDetailRow> rows = byNode.get(nodeId);
            for (int i = 0; i < Math.min(take, rows.size()); i++) {
                samples.add(toSampleMap(nodeId, rows.get(i)));
                total++;
            }
        }
        return samples;
    }

    private Map<String, Object> toSampleMap(Long nodeId, EnterpriseProfileDetailRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nodeId", nodeId);
        map.put("enterpriseId", row.getEnterpriseId());
        map.put("enterpriseName", row.getEnterpriseName());
        map.put("status", row.getStatus());
        map.put("region", row.getRegion());
        map.put("industry", row.getIndustry());
        map.put("mainProducts", row.getMainProducts());
        map.put("coreTech", row.getCoreTech());
        map.put("outputValue", row.getOutputValue());
        map.put("taxAmount", row.getTaxAmount());
        map.put("tagNames", row.getTagNames());
        return map;
    }

    private int perNodeSampleLimit(ChainHealthLevel level) {
        return switch (level) {
            // RISK/EMPTY/WEAK/NORMAL 同属非健康档，单节点样本上限一致
            case RISK, EMPTY, WEAK, NORMAL -> NON_HEALTHY_NODE_SAMPLE_LIMIT;
            case HEALTHY -> HEALTHY_NODE_SAMPLE_LIMIT;
        };
    }

    /** 节点优先级值越小越优先 */
    private int nodePriority(ChainHealthLevel level) {
        return switch (level) {
            case RISK, EMPTY -> 0;
            case WEAK -> 1;
            case NORMAL -> 2;
            case HEALTHY -> 3;
        };
    }

    // ==================== matchingEvidence ====================

    List<Map<String, Object>> buildMatchingEvidence(List<MatchingEvidenceRow> allEvidence,
                                                    Set<Long> resolved) {
        if (resolved.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingEvidenceRow row : allEvidence) {
            if (row.getNodeId() != null && resolved.contains(row.getNodeId())) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("resultId", row.getResultId());
                map.put("taskId", row.getTaskId());
                map.put("enterpriseId", row.getEnterpriseId());
                map.put("enterpriseName", row.getEnterpriseName());
                map.put("nodeId", row.getNodeId());
                map.put("confidence", row.getConfidence());
                // 单条截断，防止长文本撑爆模型上下文窗口
                map.put("reason", truncate(row.getReason(), EVIDENCE_REASON_MAX_LENGTH));
                map.put("evidence", truncate(row.getEvidence(), EVIDENCE_EVIDENCE_MAX_LENGTH));
                map.put("decision", row.getDecision());
                map.put("createdAt", row.getCreatedAt());
                out.add(map);
            }
        }
        return out;
    }

    /**
     * 单条文本截断，超过maxLength按字符截断并加省略号；null原样返回。
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }

    /**
     * 上下文长度软上限裁剪：序列化 context，超限按优先级从尾部丢弃
     * enterpriseSamples → matchingEvidence，直至不超过上限。
     *
     * <p>metrics/chain/scope 为报告核心结构不裁剪；企业样本与证据从尾部丢
     * （尾部多为健康节点样本与较旧证据）。</p>
     */
    @SuppressWarnings("unchecked")
    void trimContextByLength(Map<String, Object> context) {
        int limit = contextJsonMaxLength();
        String json = serialize(context);
        if (json == null || json.length() <= limit) {
            return;
        }
        List<Object> samples = (List<Object>) context.get("enterpriseSamples");
        List<Object> evidence = (List<Object>) context.get("matchingEvidence");
        while (json != null && json.length() > limit) {
            // 优先丢企业样本尾部（健康节点样本），再丢证据尾部（较旧证据）
            if (samples != null && !samples.isEmpty()) {
                samples.remove(samples.size() - 1);
            } else if (evidence != null && !evidence.isEmpty()) {
                evidence.remove(evidence.size() - 1);
            } else {
                break;
            }
            json = serialize(context);
        }
        log.warn("报告上下文超限，已裁剪至 {} 字符以内",
                json == null ? 0 : Math.min(json.length(), limit));
    }

    /**
     * 上下文 JSON 序列化结果软上限（字符）。包内可见且可被测试子类覆盖，
     * 以便构造超小上限场景验证裁剪逻辑。
     */
    int contextJsonMaxLength() {
        return CONTEXT_JSON_MAX_LENGTH;
    }

    // ==================== 诊断树遍历 ====================

    /**
     * 递归收集范围内的诊断树节点（含虚拟根的子节点，不含虚拟根本身）。
     */
    private List<DiagnosisTreeNodeVO> collectScopeNodes(DiagnosisTreeNodeVO root, Set<Long> resolved) {
        List<DiagnosisTreeNodeVO> out = new ArrayList<>();
        collect(root, resolved, out, new HashSet<>());
        return out;
    }

    /**
     * 包内可见的收集辅助方法，仅用于单元测试独立验证 metrics 构造。
     */
    List<DiagnosisTreeNodeVO> collectScopeNodesPublic(DiagnosisTreeNodeVO root, Set<Long> resolved) {
        return collectScopeNodes(root, resolved);
    }

    private void collect(DiagnosisTreeNodeVO node, Set<Long> ids,
                         List<DiagnosisTreeNodeVO> out, Set<Long> visited) {
        if (node == null) {
            return;
        }
        if (node.getNodeLevel() != null && node.getNodeLevel() > 0) {
            if (ids.isEmpty() || ids.contains(node.getNodeId())) {
                if (visited.add(node.getNodeId())) {
                    out.add(node);
                }
            }
        }
        for (DiagnosisTreeNodeVO child : node.getChildren()) {
            collect(child, ids, out, visited);
        }
    }

    // ==================== 工具 ====================

    private ChainHealthLevel parseLevel(String level) {
        if (level == null) {
            return ChainHealthLevel.EMPTY;
        }
        try {
            return ChainHealthLevel.valueOf(level);
        } catch (IllegalArgumentException ex) {
            return ChainHealthLevel.EMPTY;
        }
    }

    private int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    private String serialize(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            log.warn("serialize metrics snapshot failed: reason={}", ex.getMessage());
            return null;
        }
    }

    // ==================== 结果包装 ====================

    /**
     * 上下文构建结果：结构化上下文 Map + 指标快照 JSON 字符串。
     */
    public record ReportContext(Map<String, Object> context, String metricsSnapshot) {
    }

    /**
     * 指标构建中间结果：summary + 各节点明细。
     */
    record MetricsResult(Map<String, Object> summary, List<Map<String, Object>> nodes) {
        Map<String, Object> asContextMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("summary", summary);
            map.put("nodes", nodes);
            return map;
        }
    }
}
