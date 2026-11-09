package git.ggwzz.agent.tools;

import git.ggwzz.analysis.dto.CreateAnalysisReportRequest;
import git.ggwzz.analysis.service.AnalysisReportService;
import git.ggwzz.analysis.service.ChainDiagnosisTreeService;
import git.ggwzz.analysis.vo.AnalysisReportDetailVO;
import git.ggwzz.analysis.vo.AnalysisReportTaskVO;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 产业链诊断与健康分析报告相关工具集，供 AI Agent 调用。
 */
@Component
@RequiredArgsConstructor
public class AnalysisTools {

    private final ChainDiagnosisTreeService chainDiagnosisTreeService;
    private final AnalysisReportService analysisReportService;

    @Tool(description = "获取产业链诊断树，包含每个节点的健康分、健康等级和弱项原因。用于了解产业链整体健康状况。")
    public String getDiagnosis(
            @ToolParam(description = "产业链ID") Long chainId) {
        try {
            DiagnosisTreeNodeVO tree = chainDiagnosisTreeService.getDiagnosisTree(chainId);
            return formatDiagnosisNode(tree, 0);
        } catch (Exception e) {
            return "获取诊断树失败: " + e.getMessage();
        }
    }

    @Tool(description = "创建产业链健康分析报告任务。支持全产业链范围或指定节点范围。报告将异步生成。")
    public String generateReport(
            @ToolParam(description = "产业链ID") Long chainId,
            @ToolParam(description = "范围类型：ALL_CHAIN 表示全产业链，SELECTED_NODES 表示指定节点") String scopeType,
            @ToolParam(description = "节点ID列表（SELECTED_NODES 时必填，多个用逗号分隔）", required = false) String nodeIds) {
        try {
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest();
            request.setChainId(chainId);
            request.setReportType("HEALTH_ANALYSIS");
            request.setScopeType(scopeType);
            if ("SELECTED_NODES".equals(scopeType) && nodeIds != null && !nodeIds.isEmpty()) {
                List<Long> ids = java.util.Arrays.stream(nodeIds.split(","))
                        .map(String::trim)
                        .map(Long::parseLong)
                        .toList();
                request.setNodeIds(ids);
            }
            AnalysisReportTaskVO task = analysisReportService.createReport(request);
            return String.format("报告任务已创建: 任务ID=%d, 产业链ID=%d, 范围=%s, 状态=%s",
                    task.getId(), task.getChainId(), task.getScopeType(), task.getStatus());
        } catch (Exception e) {
            return "创建报告失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询健康分析报告详情，包含报告状态和已完成报告的内容摘要。")
    public String getReport(
            @ToolParam(description = "报告任务ID") Long reportId) {
        try {
            AnalysisReportDetailVO detail = analysisReportService.getReport(reportId);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("报告ID=%d, 产业链=%s, 类型=%s, 范围=%s, 状态=%s",
                    detail.getId(), detail.getChainName(), detail.getReportType(),
                    detail.getScopeType(), detail.getStatus()));
            if (detail.getErrorMessage() != null) {
                sb.append(", 错误=").append(detail.getErrorMessage());
            }
            if (detail.getTitle() != null) {
                sb.append(", 标题=").append(detail.getTitle());
            }
            if (detail.getContentMarkdown() != null) {
                String preview = detail.getContentMarkdown();
                if (preview.length() > 500) {
                    preview = preview.substring(0, 500) + "...";
                }
                sb.append("\n报告内容预览:\n").append(preview);
            }
            return sb.toString();
        } catch (Exception e) {
            return "获取报告失败: " + e.getMessage();
        }
    }

    /**
     * 递归格式化诊断树节点为缩进文本。
     */
    private String formatDiagnosisNode(DiagnosisTreeNodeVO node, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        sb.append(String.format("%s- %s (健康分=%d, 等级=%s, 直接企业=%d, 子树企业=%d)",
                indent,
                node.getNodeName(),
                node.getHealthScore() != null ? node.getHealthScore() : 0,
                node.getHealthLabel() != null ? node.getHealthLabel() : "未知",
                node.getDirectEnterpriseCount() != null ? node.getDirectEnterpriseCount() : 0,
                node.getSubtreeEnterpriseCount() != null ? node.getSubtreeEnterpriseCount() : 0));
        if (node.getWeakReasons() != null && !node.getWeakReasons().isEmpty()) {
            sb.append("\n").append(indent).append("  弱项: ").append(String.join("; ", node.getWeakReasons()));
        }
        if (node.getChildren() != null) {
            for (DiagnosisTreeNodeVO child : node.getChildren()) {
                sb.append("\n").append(formatDiagnosisNode(child, depth + 1));
            }
        }
        return sb.toString();
    }
}
