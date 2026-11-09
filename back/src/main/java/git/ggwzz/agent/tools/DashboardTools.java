package git.ggwzz.agent.tools;

import git.ggwzz.dashboard.service.DashboardService;
import git.ggwzz.dashboard.vo.DashboardOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 数据看板相关工具集，供 AI Agent 调用。
 */
@Component
@RequiredArgsConstructor
public class DashboardTools {

    private final DashboardService dashboardService;

    @Tool(description = "获取数据看板概览，包含核心指标、产业链分布、节点统计、推荐汇总和近期操作。")
    public String getOverview() {
        try {
            DashboardOverviewVO overview = dashboardService.getOverview();
            StringBuilder sb = new StringBuilder();

            // 核心指标
            if (overview.getMetrics() != null && !overview.getMetrics().isEmpty()) {
                sb.append("核心指标:\n");
                for (DashboardOverviewVO.Metric m : overview.getMetrics()) {
                    sb.append(String.format("  %s: %s%s\n", m.getLabel(), m.getValue(),
                            m.getUnit() != null ? m.getUnit() : ""));
                }
            }

            // 产业链分布
            if (overview.getChainDistribution() != null && !overview.getChainDistribution().isEmpty()) {
                sb.append("产业链分布:\n");
                for (DashboardOverviewVO.ChainDistribution c : overview.getChainDistribution()) {
                    sb.append(String.format("  %s: %d (%s)\n", c.getName(), c.getValue(),
                            c.getPercent() != null ? c.getPercent() : ""));
                }
            }

            // 推荐汇总
            if (overview.getMatchingSummary() != null) {
                DashboardOverviewVO.MatchingSummary ms = overview.getMatchingSummary();
                sb.append(String.format("推荐汇总: 待处理=%d, 已接受=%d, 已拒绝=%d, 人工确认=%d, 平均置信度=%.2f\n",
                        ms.getPending(), ms.getAccepted(), ms.getRejected(), ms.getManual(),
                        ms.getAverageConfidence()));
            }

            // 近期操作
            if (overview.getRecentOperations() != null && !overview.getRecentOperations().isEmpty()) {
                sb.append("近期操作:\n");
                for (DashboardOverviewVO.RecentOperation op : overview.getRecentOperations()) {
                    sb.append(String.format("  [%s] %s %s %s (ID=%d) %s\n",
                            op.getCreatedAt(), op.getOperator(), op.getAction(),
                            op.getTargetType(), op.getTargetId(),
                            op.getResult() != null ? op.getResult() : ""));
                }
            }

            return sb.length() > 0 ? sb.toString().trim() : "暂无看板数据";
        } catch (Exception e) {
            return "获取看板概览失败: " + e.getMessage();
        }
    }
}
