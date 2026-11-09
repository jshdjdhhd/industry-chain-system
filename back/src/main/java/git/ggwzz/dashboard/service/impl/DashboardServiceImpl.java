package git.ggwzz.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import git.ggwzz.dashboard.service.DashboardService;
import git.ggwzz.dashboard.vo.DashboardOverviewVO;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据看板服务实现。
 *
 * <p>从企业、产业链、挂链推荐、操作日志等表聚合统计，返回前端 DashboardOverview 所需的全部字段。</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String[] TONES = {"blue", "green", "amber", "purple"};

    private final EnterpriseMapper enterpriseMapper;
    private final ChainMapper chainMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    private final MatchingResultMapper matchingResultMapper;
    private final SysOperationLogMapper operationLogMapper;

    @Override
    public DashboardOverviewVO getOverview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();

        // 1. 企业统计
        long totalEnterprises = enterpriseMapper.selectCount(
                new LambdaQueryWrapper<Enterprise>().eq(Enterprise::getStatus, "ACTIVE"));
        long linkedEnterprises = countLinkedEnterprises();

        // 2. 产业链分布
        List<DashboardOverviewVO.ChainDistribution> chainDistribution = buildChainDistribution(totalEnterprises);

        // 3. 节点企业统计（取第一条产业链的根节点）
        List<DashboardOverviewVO.NodeStatistic> nodeStatistics = buildNodeStatistics();

        // 4. 匹配汇总
        DashboardOverviewVO.MatchingSummary matchingSummary = buildMatchingSummary();

        // 5. 推荐采纳率
        String adoptionRate = calculateAdoptionRate();

        // 6. 待挂链企业数 = 总企业数 - 已挂链企业数
        long pendingEnterprises = totalEnterprises - linkedEnterprises;

        // 7. 指标卡片
        vo.setMetrics(buildMetrics(totalEnterprises, linkedEnterprises, (int) pendingEnterprises, adoptionRate));

        vo.setChainDistribution(chainDistribution);
        vo.setNodeStatistics(nodeStatistics);
        vo.setMatchingSummary(matchingSummary);

        // 7. 本周进度
        vo.setWeeklyProgress(buildWeeklyProgress());

        // 8. 最近操作
        vo.setRecentOperations(buildRecentOperations());

        return vo;
    }

    private List<DashboardOverviewVO.Metric> buildMetrics(long total, long linked, int pending, String adoptionRate) {
        List<DashboardOverviewVO.Metric> metrics = new ArrayList<>();

        DashboardOverviewVO.Metric m1 = new DashboardOverviewVO.Metric();
        m1.setLabel("企业总数");
        m1.setValue(total);
        m1.setUnit("家");
        m1.setTone("blue");
        metrics.add(m1);

        DashboardOverviewVO.Metric m2 = new DashboardOverviewVO.Metric();
        m2.setLabel("已入库企业");
        m2.setValue(linked);
        m2.setUnit("家");
        m2.setTone("green");
        metrics.add(m2);

        DashboardOverviewVO.Metric m3 = new DashboardOverviewVO.Metric();
        m3.setLabel("待智能挂链");
        m3.setValue(pending);
        m3.setUnit("家");
        m3.setTone("amber");
        metrics.add(m3);

        DashboardOverviewVO.Metric m4 = new DashboardOverviewVO.Metric();
        m4.setLabel("推荐采纳率");
        m4.setValue(adoptionRate);
        m4.setTone("purple");
        metrics.add(m4);

        return metrics;
    }

    private long countLinkedEnterprises() {
        // 统计有挂链关系的独立企业数（而非关系记录数）
        List<EnterpriseNodeRel> rels = enterpriseNodeRelMapper.selectList(
                new LambdaQueryWrapper<EnterpriseNodeRel>()
                        .eq(EnterpriseNodeRel::getDeleted, 0));
        return rels.stream()
                .map(EnterpriseNodeRel::getEnterpriseId)
                .distinct()
                .count();
    }

    private List<DashboardOverviewVO.ChainDistribution> buildChainDistribution(long totalEnterprises) {
        List<Chain> chains = chainMapper.selectList(
                new LambdaQueryWrapper<Chain>().eq(Chain::getDeleted, 0));
        if (chains.isEmpty()) return Collections.emptyList();

        // 统计每条产业链下挂接的独立企业数（通过 node → chain 关联）
        List<ChainNode> allNodes = chainNodeMapper.selectList(
                new LambdaQueryWrapper<ChainNode>().eq(ChainNode::getDeleted, 0));
        Map<Long, List<ChainNode>> nodesByChain = allNodes.stream()
                .collect(Collectors.groupingBy(ChainNode::getChainId));

        List<EnterpriseNodeRel> allRels = enterpriseNodeRelMapper.selectList(
                new LambdaQueryWrapper<EnterpriseNodeRel>().eq(EnterpriseNodeRel::getDeleted, 0));
        // nodeId → 企业ID集合（去重）
        Map<Long, Set<Long>> enterprisesByNode = allRels.stream()
                .collect(Collectors.groupingBy(EnterpriseNodeRel::getNodeId,
                        Collectors.mapping(EnterpriseNodeRel::getEnterpriseId, Collectors.toSet())));

        // 记录已挂链的企业ID（用于计算未挂接数）
        Set<Long> allLinkedEnterpriseIds = new HashSet<>();

        List<DashboardOverviewVO.ChainDistribution> result = new ArrayList<>();

        for (Chain chain : chains) {
            List<ChainNode> chainNodes = nodesByChain.getOrDefault(chain.getId(), Collections.emptyList());
            // 收集该产业链下所有节点关联的独立企业ID
            Set<Long> chainEnterpriseIds = new HashSet<>();
            for (ChainNode node : chainNodes) {
                Set<Long> eids = enterprisesByNode.getOrDefault(node.getId(), Collections.emptySet());
                chainEnterpriseIds.addAll(eids);
            }
            if (!chainEnterpriseIds.isEmpty()) {
                allLinkedEnterpriseIds.addAll(chainEnterpriseIds);
                DashboardOverviewVO.ChainDistribution dist = new DashboardOverviewVO.ChainDistribution();
                dist.setName(chain.getChainName());
                dist.setValue(chainEnterpriseIds.size());
                result.add(dist);
            }
        }

        // 添加未挂接（从未挂过任何产业链的企业）
        long unlinked = totalEnterprises - allLinkedEnterpriseIds.size();
        if (unlinked > 0) {
            DashboardOverviewVO.ChainDistribution dist = new DashboardOverviewVO.ChainDistribution();
            dist.setName("未挂接");
            dist.setValue((int) unlinked);
            result.add(dist);
        }

        long total = result.stream().mapToLong(DashboardOverviewVO.ChainDistribution::getValue).sum();
        for (DashboardOverviewVO.ChainDistribution dist : result) {
            double pct = total > 0 ? (dist.getValue() * 100.0 / total) : 0;
            dist.setPercent(String.format("%.0f%%", pct));
        }

        return result;
    }

    private List<DashboardOverviewVO.NodeStatistic> buildNodeStatistics() {
        // 获取所有产业链
        List<Chain> chains = chainMapper.selectList(
                new LambdaQueryWrapper<Chain>().eq(Chain::getDeleted, 0));
        if (chains.isEmpty()) return Collections.emptyList();

        // 获取所有一级节点（所有产业链）
        List<ChainNode> rootNodes = chainNodeMapper.selectList(
                new LambdaQueryWrapper<ChainNode>()
                        .eq(ChainNode::getNodeLevel, 1)
                        .eq(ChainNode::getDeleted, 0)
                        .orderByAsc(ChainNode::getSortOrder));
        if (rootNodes.isEmpty()) return Collections.emptyList();

        // 获取所有节点
        List<ChainNode> allNodes = chainNodeMapper.selectList(
                new LambdaQueryWrapper<ChainNode>()
                        .eq(ChainNode::getDeleted, 0));

        Map<Long, List<ChainNode>> childrenMap = allNodes.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(ChainNode::getParentId));

        // 统计每个节点下的独立企业数
        List<EnterpriseNodeRel> rels = enterpriseNodeRelMapper.selectList(
                new LambdaQueryWrapper<EnterpriseNodeRel>().eq(EnterpriseNodeRel::getDeleted, 0));
        Map<Long, Set<Long>> enterpriseIdsByNode = rels.stream()
                .collect(Collectors.groupingBy(EnterpriseNodeRel::getNodeId,
                        Collectors.mapping(EnterpriseNodeRel::getEnterpriseId, Collectors.toSet())));

        String[] colors = {"#2F6BFF", "#00A896", "#F59E0B", "#7C3AED"};
        List<DashboardOverviewVO.NodeStatistic> result = new ArrayList<>();
        int index = 0;

        for (ChainNode root : rootNodes) {
            Set<Long> subtreeIds = new HashSet<>();
            collectSubtreeIds(root.getId(), childrenMap, subtreeIds);
            // 收集子树下所有独立企业ID（去重）
            Set<Long> subtreeEnterpriseIds = new HashSet<>();
            for (Long nodeId : subtreeIds) {
                Set<Long> eids = enterpriseIdsByNode.getOrDefault(nodeId, Collections.emptySet());
                subtreeEnterpriseIds.addAll(eids);
            }
            long count = subtreeEnterpriseIds.size();

            // 只显示有企业的节点
            if (count > 0) {
                DashboardOverviewVO.NodeStatistic stat = new DashboardOverviewVO.NodeStatistic();
                stat.setName(root.getNodeName());
                stat.setValue((int) count);
                stat.setTone(colors[index % colors.length]);
                result.add(stat);
                index++;
            }
        }

        return result;
    }

    private void collectSubtreeIds(Long nodeId, Map<Long, List<ChainNode>> childrenMap, Set<Long> result) {
        result.add(nodeId);
        List<ChainNode> children = childrenMap.getOrDefault(nodeId, Collections.emptyList());
        for (ChainNode child : children) {
            collectSubtreeIds(child.getId(), childrenMap, result);
        }
    }

    private DashboardOverviewVO.MatchingSummary buildMatchingSummary() {
        // 用聚合查询替代 selectList(null)，避免全量加载 22K+ 行导致超时
        Map<String, MatchingDecisionRow> decisionMap =
                matchingResultMapper.selectDecisionSummary();
        if (decisionMap == null) {
            decisionMap = Collections.emptyMap();
        }

        long pending = countByDecision(decisionMap, MatchingDecision.PENDING);
        long accepted = countByDecision(decisionMap, MatchingDecision.ACCEPTED);
        long rejected = countByDecision(decisionMap, MatchingDecision.REJECTED);

        // 人工挂链：通过 enterprise_node_rel 中 source=MANUAL 的企业数
        long manual = enterpriseNodeRelMapper.selectCount(
                new LambdaQueryWrapper<EnterpriseNodeRel>()
                        .eq(EnterpriseNodeRel::getSource, "MANUAL")
                        .eq(EnterpriseNodeRel::getDeleted, 0));

        // 平均置信度：用聚合查询返回的 AVG 值
        double avg = decisionMap.values().stream()
                .mapToDouble(MatchingDecisionRow::getAvgConfidence)
                .average()
                .orElse(0.0);

        // 本周推荐数：用聚合查询统计
        LocalDate weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weekly = matchingResultMapper.selectWeeklyRecommendations(
                        weekStart.atStartOfDay()).stream()
                .mapToLong(DayRecommendationRow::getRecommendations)
                .sum();

        DashboardOverviewVO.MatchingSummary summary = new DashboardOverviewVO.MatchingSummary();
        summary.setPending((int) pending);
        summary.setAccepted((int) accepted);
        summary.setRejected((int) rejected);
        summary.setManual((int) manual);
        summary.setAverageConfidence(avg);
        summary.setWeeklyRecommendations((int) weekly);
        return summary;
    }

    private long countByDecision(Map<String, MatchingDecisionRow> map,
                                  MatchingDecision decision) {
        MatchingDecisionRow row = map.get(decision.name());
        return row == null ? 0 : row.getCnt();
    }

    private String calculateAdoptionRate() {
        long total = matchingResultMapper.selectCount(null);
        if (total == 0) return "0%";
        long accepted = matchingResultMapper.selectCount(
                new LambdaQueryWrapper<MatchingResult>()
                        .eq(MatchingResult::getDecision, MatchingDecision.ACCEPTED.name()));
        return Math.round(accepted * 100.0 / total) + "%";
    }

    private List<DashboardOverviewVO.WeeklyProgress> buildWeeklyProgress() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");

        List<DashboardOverviewVO.WeeklyProgress> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            DashboardOverviewVO.WeeklyProgress wp = new DashboardOverviewVO.WeeklyProgress();
            wp.setDate(date.format(fmt));
            wp.setInbound(0);
            wp.setNodes(0);
            wp.setRecommendations(0);
            wp.setAccepted(0);
            result.add(wp);
        }

        // 用聚合查询替代 selectList，避免全量加载导致超时
        List<DayRecommendationRow> weekRows = matchingResultMapper.selectWeeklyRecommendations(
                weekStart.atStartOfDay());
        for (DayRecommendationRow row : weekRows) {
            // DAYOFWEEK：1=周日，2=周一 ... 7=周六
            int dayIndex = (row.getDayOfWeek() + 5) % 7; // 2(周一)->0, 3(周二)->1, ..., 1(周日)->6
            if (dayIndex >= 0 && dayIndex < 7) {
                DashboardOverviewVO.WeeklyProgress wp = result.get(dayIndex);
                wp.setRecommendations(wp.getRecommendations() + row.getRecommendations());
                wp.setAccepted(wp.getAccepted() + row.getAccepted());
            }
        }

        return result;
    }

    private List<DashboardOverviewVO.RecentOperation> buildRecentOperations() {
        List<SysOperationLog> logs = operationLogMapper.selectList(
                new LambdaQueryWrapper<SysOperationLog>()
                        .orderByDesc(SysOperationLog::getId)
                        .last("LIMIT 10"));

        return logs.stream().map(log -> {
            DashboardOverviewVO.RecentOperation op = new DashboardOverviewVO.RecentOperation();
            op.setId(log.getId());
            op.setOperator(log.getOperator());
            op.setAction(log.getAction());
            op.setTargetType(log.getTargetType());
            op.setTargetId(log.getTargetId());
            op.setCreatedAt(log.getCreatedAt() != null
                    ? log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            op.setTargetName(log.getTargetType());
            // 从 detail JSON 中提取结果
            String detail = log.getDetail();
            if (detail != null && detail.contains("\"result\":\"FAIL\"")) {
                op.setResult("失败");
            } else {
                op.setResult("成功");
            }
            return op;
        }).collect(Collectors.toList());
    }
}
