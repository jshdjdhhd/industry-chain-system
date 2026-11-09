package git.ggwzz.analysis.service.impl;

import git.ggwzz.analysis.mapper.AnalysisQueryMapper;
import git.ggwzz.analysis.mapper.projection.EnterpriseProfileRow;
import git.ggwzz.analysis.service.ChainDiagnosisTreeService;
import git.ggwzz.analysis.service.ChainHealthMetricService;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.mapper.projection.NodeEnterpriseCountRow;
import git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 产业链诊断树服务实现。
 *
 * <p>性能约束：一次取该链全部 chain_node 平面列表，直接数与子树数均由
 * 批量聚合 SQL 返回，企业质量一次查全链挂链企业画像后在内存按 node_path
 * 前缀归并，避免 N+1。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainDiagnosisTreeServiceImpl implements ChainDiagnosisTreeService {

    private final ChainMapper chainMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    private final AnalysisQueryMapper analysisQueryMapper;
    private final ChainHealthMetricService chainHealthMetricService;

    @Override
    public DiagnosisTreeNodeVO getDiagnosisTree(Long chainId) {
        Chain chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        List<ChainNode> nodes = chainNodeMapper.selectByChainId(chainId);
        Map<Long, Integer> directCountMap = loadDirectCountMap(chainId);
        Map<Long, Integer> subtreeCountMap = loadSubtreeCountMap(chainId);
        // 一次查全链挂链企业画像，用于子树质量归并与虚拟根全链去重计数
        List<EnterpriseProfileRow> profiles =
                analysisQueryMapper.selectEnterpriseProfilesByChainId(chainId);
        // 全链去重企业画像：按 enterprise_id 去重，与 chain 模块 getChainTree 根口径
        // （全链 COUNT(DISTINCT enterprise_id)）一致；过滤口径同样为
        // node/rel/enterprise 均 is_deleted=0，不额外过滤 status。
        RootEnterpriseSummary rootSummary = dedupRootSummary(profiles);
        Map<Long, Integer> subtreeQualityMap =
                loadSubtreeQualityMap(chainId, nodes, profiles);

        Map<Long, List<ChainNode>> childrenMap = new HashMap<>();
        List<ChainNode> roots = new ArrayList<>();
        for (ChainNode node : nodes) {
            if (node.getParentId() == null) {
                roots.add(node);
            } else {
                childrenMap.computeIfAbsent(
                        node.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }

        List<DiagnosisTreeNodeVO> children = buildChildren(
                roots, childrenMap, directCountMap, subtreeCountMap, subtreeQualityMap);

        DiagnosisTreeNodeVO root = new DiagnosisTreeNodeVO();
        root.setNodeId(chain.getId());
        root.setNodeName(chain.getChainName());
        root.setNodeLevel(0);
        root.setDirectEnterpriseCount(0);
        // 虚拟根子树企业数 = 全链去重有效企业数，与 chain 模块 getChainTree 根口径一致，
        // 避免一级子树数之和重复计入跨一级多挂载企业。
        int rootSubtree = rootSummary.distinctEnterpriseCount();
        root.setSubtreeEnterpriseCount(rootSubtree);
        // 虚拟根质量 = 全链去重企业画像完整性项数总和（每家最多 5 项）
        ChainHealthMetricService.NodeHealthInput rootInput =
                new ChainHealthMetricService.NodeHealthInput(
                        rootSubtree, 0.0, true, rootSummary.qualityItemSum());
        ChainHealthMetricService.NodeHealthResult rootHealth =
                chainHealthMetricService.compute(rootInput);
        applyHealth(root, rootHealth);
        root.setChildren(children);
        return root;
    }

    private Map<Long, Integer> loadDirectCountMap(Long chainId) {
        Map<Long, Integer> map = new HashMap<>();
        for (NodeEnterpriseCountRow row
                : enterpriseNodeRelMapper.selectDirectCountsByChainId(chainId)) {
            map.put(row.getNodeId(),
                    Math.toIntExact(row.getDirectEnterpriseCount()));
        }
        return map;
    }

    private Map<Long, Integer> loadSubtreeCountMap(Long chainId) {
        Map<Long, Integer> map = new HashMap<>();
        for (NodeSubtreeEnterpriseCountRow row
                : enterpriseNodeRelMapper.selectSubtreeCountsByChainId(chainId)) {
            map.put(row.getNodeId(),
                    Math.toIntExact(row.getSubtreeEnterpriseCount()));
        }
        return map;
    }

    /**
     * 一次查全链挂链企业画像，按 node_path 前缀在内存归并子树企业质量。
     *
     * <p>返回每个节点的子树企业画像完整性累计项数（每家最多 5 项）。</p>
     */
    private Map<Long, Integer> loadSubtreeQualityMap(
            Long chainId, List<ChainNode> nodes,
            List<EnterpriseProfileRow> profiles) {
        // 按 node_path 索引挂链企业画像，便于前缀归并
        List<ProfileEntry> entries = new ArrayList<>(profiles.size());
        for (EnterpriseProfileRow row : profiles) {
            int itemSum = countTrueItems(row);
            entries.add(new ProfileEntry(
                    row.getEnterpriseId(), row.getNodePath(), itemSum));
        }
        Map<Long, Integer> qualityMap = new HashMap<>();
        for (ChainNode node : nodes) {
            String path = node.getNodePath();
            if (path == null || path.isEmpty()) {
                qualityMap.put(node.getId(), 0);
                continue;
            }
            Set<Long> seen = new HashSet<>();
            int itemSum = 0;
            for (ProfileEntry entry : entries) {
                if (entry.nodePath != null
                        && entry.nodePath.startsWith(path)
                        && seen.add(entry.enterpriseId)) {
                    itemSum += entry.itemSum;
                }
            }
            qualityMap.put(node.getId(), itemSum);
        }
        return qualityMap;
    }

    private int countTrueItems(EnterpriseProfileRow row) {
        int count = 0;
        if (Boolean.TRUE.equals(row.getHasOutputValue())) {
            count++;
        }
        if (Boolean.TRUE.equals(row.getHasTaxAmount())) {
            count++;
        }
        if (Boolean.TRUE.equals(row.getHasTag())) {
            count++;
        }
        if (Boolean.TRUE.equals(row.getHasMainProducts())) {
            count++;
        }
        if (Boolean.TRUE.equals(row.getHasCoreTech())) {
            count++;
        }
        return count;
    }

    private List<DiagnosisTreeNodeVO> buildChildren(
            List<ChainNode> nodes,
            Map<Long, List<ChainNode>> childrenMap,
            Map<Long, Integer> directCountMap,
            Map<Long, Integer> subtreeCountMap,
            Map<Long, Integer> subtreeQualityMap) {
        List<DiagnosisTreeNodeVO> result = new ArrayList<>();
        // 同级均衡需要父节点下所有同级子树数，先按 parentId 计算同级均值。
        // siblingContext 对每个节点遍历全量节点列表，整体为 O(n²)；
        // 诊断树面向单链展示，节点量小（通常数十级），O(n²) 可接受，不强行重构。
        for (ChainNode node : nodes) {
            DiagnosisTreeNodeVO vo = toVO(node, directCountMap, subtreeCountMap);
            List<ChainNode> children = childrenMap.getOrDefault(
                    node.getId(), Collections.emptyList());
            List<DiagnosisTreeNodeVO> childVOs = buildChildren(
                    children, childrenMap, directCountMap,
                    subtreeCountMap, subtreeQualityMap);
            vo.setChildren(childVOs);

            int subtreeCount = subtreeCountMap.getOrDefault(node.getId(), 0);
            SiblingContext ctx = siblingContext(
                    node, nodes, subtreeCountMap);
            int qualityItemSum = subtreeQualityMap.getOrDefault(node.getId(), 0);
            ChainHealthMetricService.NodeHealthInput input =
                    new ChainHealthMetricService.NodeHealthInput(
                            subtreeCount,
                            ctx.siblingAvgSubtreeCount,
                            ctx.allSiblingsEmpty,
                            qualityItemSum);
            ChainHealthMetricService.NodeHealthResult health =
                    chainHealthMetricService.compute(input);
            applyHealth(vo, health);
            result.add(vo);
        }
        return result;
    }

    private SiblingContext siblingContext(ChainNode node, List<ChainNode> allNodes,
                                          Map<Long, Integer> subtreeCountMap) {
        List<ChainNode> siblings = new ArrayList<>();
        for (ChainNode candidate : allNodes) {
            Long aParent = candidate.getParentId();
            Long bParent = node.getParentId();
            if (Objects.equals(aParent, bParent)
                    && !Objects.equals(candidate.getId(), node.getId())) {
                siblings.add(candidate);
            }
        }
        if (siblings.isEmpty()) {
            // 无同级节点时按全空处理，均衡得分给 50
            return new SiblingContext(0.0, true);
        }
        long sum = 0;
        boolean anyNonEmpty = false;
        for (ChainNode sib : siblings) {
            int cnt = subtreeCountMap.getOrDefault(sib.getId(), 0);
            sum += cnt;
            if (cnt > 0) {
                anyNonEmpty = true;
            }
        }
        double avg = (double) sum / siblings.size();
        return new SiblingContext(avg, !anyNonEmpty);
    }

    /**
     * 按 enterprise_id 去重计算全链挂链企业画像摘要，供虚拟根口径使用。
     *
     * <p>全链去重企业数与 chain 模块 getChainTree 根（全链
     * COUNT(DISTINCT enterprise_id)）口径一致；qualityItemSum 为
     * 每家去重企业 5 项画像指标非空项数总和。</p>
     */
    private RootEnterpriseSummary dedupRootSummary(
            List<EnterpriseProfileRow> profiles) {
        Map<Long, Integer> bestItemByEnterprise = new HashMap<>();
        for (EnterpriseProfileRow row : profiles) {
            int itemSum = countTrueItems(row);
            // 同一企业跨多节点挂载时，画像相同，取任一即可；这里保留最大值兜底
            bestItemByEnterprise.merge(row.getEnterpriseId(), itemSum, Math::max);
        }
        int distinctCount = bestItemByEnterprise.size();
        int qualityItemSum = bestItemByEnterprise.values().stream()
                .mapToInt(Integer::intValue).sum();
        return new RootEnterpriseSummary(distinctCount, qualityItemSum);
    }

    private DiagnosisTreeNodeVO toVO(ChainNode node,
                                     Map<Long, Integer> directCountMap,
                                     Map<Long, Integer> subtreeCountMap) {
        DiagnosisTreeNodeVO vo = new DiagnosisTreeNodeVO();
        vo.setNodeId(node.getId());
        vo.setNodeName(node.getNodeName());
        vo.setNodePath(node.getNodePath());
        vo.setNodeLevel(node.getNodeLevel());
        vo.setKeywords(node.getKeywords());
        vo.setDescription(node.getDescription());
        vo.setDirectEnterpriseCount(
                directCountMap.getOrDefault(node.getId(), 0));
        vo.setSubtreeEnterpriseCount(
                subtreeCountMap.getOrDefault(node.getId(), 0));
        return vo;
    }

    private void applyHealth(DiagnosisTreeNodeVO vo,
                             ChainHealthMetricService.NodeHealthResult health) {
        vo.setHealthScore(health.getHealthScore());
        vo.setHealthLevel(health.getHealthLevel().name());
        vo.setHealthLabel(health.getHealthLabel());
        vo.setWeakReasons(health.getWeakReasons());
    }

    /** 挂链企业画像内存归并条目。 */
    private record ProfileEntry(Long enterpriseId, String nodePath, int itemSum) {
    }

    /** 虚拟根全链去重企业画像摘要。 */
    private record RootEnterpriseSummary(
            int distinctEnterpriseCount, int qualityItemSum) {
    }

    /** 同级均衡上下文。 */
    private record SiblingContext(double siblingAvgSubtreeCount,
                                  boolean allSiblingsEmpty) {
    }
}
