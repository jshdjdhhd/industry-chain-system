package git.ggwzz.analysis.service;

import git.ggwzz.analysis.enums.ChainHealthLevel;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 健康分析报告模板构建器（第一版用模板 Markdown 代替 LLM）。
 *
 * <p>第一版不接大模型，按实现文档 4.7 的报告结构生成模板 Markdown。
 * 7.4 接入 LLM 时，由 ContextBuilder + PromptBuilder 取代此处的模板拼装。</p>
 *
 * <p>本构建器纯内存计算，不依赖数据访问。输入为诊断树与已展开的范围节点 ID 集合，
 * 遍历诊断树采集范围内的指标摘要，按章节拼装 Markdown。</p>
 */
@Service
public class AnalysisReportTemplateBuilder {

    /** 日期时间格式化口径 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构建模板报告内容。
     *
     * @param root             诊断树虚拟根
     * @param resolvedNodeIds  实际分析范围节点 ID 集合（空表示整链）
     * @param scopeDescription 范围描述文字
     * @param chainName        产业链名称
     * @return 模板 Markdown 文本
     */
    public String buildTemplateMarkdown(DiagnosisTreeNodeVO root,
                                        Set<Long> resolvedNodeIds,
                                        String scopeDescription,
                                        String chainName) {
        List<DiagnosisTreeNodeVO> scopeNodes = collectScopeNodes(root, resolvedNodeIds);
        HealthSummary summary = summarize(scopeNodes);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(chainName).append("健康分析报告\n\n");

        sb.append("## 一、分析范围\n");
        sb.append(scopeDescription).append("\n\n");

        sb.append("## 二、总体健康结论\n");
        sb.append("涉及节点总数：").append(summary.totalNodes).append(" 个；")
                .append("已挂载企业总数：").append(summary.totalEnterprises).append(" 家。\n");
        sb.append("健康等级分布：健康 ")
                .append(summary.countByLevel.getOrDefault("HEALTHY", 0))
                .append(" 个，一般 ")
                .append(summary.countByLevel.getOrDefault("NORMAL", 0))
                .append(" 个，偏弱 ")
                .append(summary.countByLevel.getOrDefault("WEAK", 0))
                .append(" 个，风险 ")
                .append(summary.countByLevel.getOrDefault("RISK", 0))
                .append(" 个，空白 ")
                .append(summary.countByLevel.getOrDefault("EMPTY", 0))
                .append(" 个。\n\n");

        sb.append("## 三、关键风险节点\n");
        if (summary.riskNodes.isEmpty()) {
            sb.append("当前范围内无风险或空白节点。\n\n");
        } else {
            for (NodeSummary node : summary.riskNodes) {
                sb.append("- ").append(node.name)
                        .append("（健康分：").append(node.healthScore)
                        .append("，等级：").append(node.healthLabel).append("）");
                if (!node.weakReasons.isEmpty()) {
                    sb.append("：").append(String.join("；", node.weakReasons));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 四、薄弱节点原因分析\n");
        if (summary.weakNodes.isEmpty()) {
            sb.append("当前范围内无偏弱节点。\n\n");
        } else {
            for (NodeSummary node : summary.weakNodes) {
                sb.append("- ").append(node.name)
                        .append("（健康分：").append(node.healthScore).append("）");
                if (!node.weakReasons.isEmpty()) {
                    sb.append("：").append(String.join("；", node.weakReasons));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 五、已挂载企业支撑情况\n");
        sb.append("范围内子树企业数合计约 ").append(summary.totalEnterprises)
                .append(" 家（按各节点子树口径累加，未跨节点去重）。\n\n");

        sb.append("## 六、管理建议\n");
        sb.append("- 优先补齐空白与风险节点的挂链企业，缓解单点依赖风险。\n");
        sb.append("- 对偏弱节点核查企业画像信息（产值、税收、标签、主营产品、核心技术）是否完整。\n");
        sb.append("- 对已挂载企业定期核验挂链关系是否准确，避免错挂漏挂。\n");
        sb.append("- 不做外部招商候选推荐，建议围绕现有已挂载企业完善数据治理与节点治理。\n\n");

        sb.append("## 七、数据口径说明\n");
        sb.append("- 健康分由后端规则实时计算，口径：企业承载 50% + 单点依赖 25% + 同级均衡 15% + 企业质量 10%。\n");
        sb.append("- 健康等级：健康>=80、一般>=60、偏弱>=30、风险>=1，子树无企业为空白。\n");
        sb.append("- 数据来源：产业链节点结构、企业挂链关系、企业画像完整性指标。\n");
        sb.append("- 生成时间：")
                .append(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .append("（第一版为模板生成，7.4 将接入大模型解释）\n");

        return sb.toString();
    }

    /**
     * 递归收集范围内的诊断树节点（含虚拟根的子节点，不含虚拟根本身）。
     *
     * @param node 当前遍历节点
     * @param ids  范围节点 ID 集合，null 或空表示整链全部
     */
    private void collect(DiagnosisTreeNodeVO node, Set<Long> ids,
                         List<DiagnosisTreeNodeVO> out, Set<Long> visited) {
        if (node == null) {
            return;
        }
        // 虚拟根 nodeLevel=0 不计入分析节点，只递归子节点
        if (node.getNodeLevel() != null && node.getNodeLevel() > 0) {
            if (ids == null || ids.isEmpty() || ids.contains(node.getNodeId())) {
                if (visited.add(node.getNodeId())) {
                    out.add(node);
                }
            }
        }
        for (DiagnosisTreeNodeVO child : node.getChildren()) {
            collect(child, ids, out, visited);
        }
    }

    private List<DiagnosisTreeNodeVO> collectScopeNodes(DiagnosisTreeNodeVO root,
                                                       Set<Long> ids) {
        List<DiagnosisTreeNodeVO> out = new ArrayList<>();
        collect(root, ids, out, new HashSet<>());
        return out;
    }

    private HealthSummary summarize(List<DiagnosisTreeNodeVO> nodes) {
        HealthSummary summary = new HealthSummary();
        summary.totalNodes = nodes.size();
        for (DiagnosisTreeNodeVO node : nodes) {
            String level = node.getHealthLevel();
            summary.countByLevel.merge(level, 1, Integer::sum);
            summary.totalEnterprises += nullSafe(node.getSubtreeEnterpriseCount());
            NodeSummary ns = new NodeSummary(
                    node.getNodeName(),
                    nullSafe(node.getHealthScore()),
                    node.getHealthLevel(),
                    node.getHealthLabel(),
                    node.getWeakReasons() == null
                            ? List.of() : node.getWeakReasons());
            if (ChainHealthLevel.EMPTY.name().equals(level)
                    || ChainHealthLevel.RISK.name().equals(level)) {
                summary.riskNodes.add(ns);
            } else if (ChainHealthLevel.WEAK.name().equals(level)) {
                summary.weakNodes.add(ns);
            }
        }
        return summary;
    }

    private int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    /** 范围内的健康摘要。 */
    private static class HealthSummary {
        int totalNodes;
        int totalEnterprises;
        final Map<String, Integer> countByLevel = new LinkedHashMap<>();
        final List<NodeSummary> riskNodes = new ArrayList<>();
        final List<NodeSummary> weakNodes = new ArrayList<>();
    }

    private record NodeSummary(String name, int healthScore,
                               String healthLevel, String healthLabel,
                               List<String> weakReasons) {
    }
}