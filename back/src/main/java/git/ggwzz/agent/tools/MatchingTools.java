package git.ggwzz.agent.tools;

import git.ggwzz.matching.dto.CreateMatchingTaskRequest;
import git.ggwzz.matching.dto.RejectMatchingResultRequest;
import git.ggwzz.matching.service.MatchingService;
import git.ggwzz.matching.vo.MatchingDecisionVO;
import git.ggwzz.matching.vo.MatchingResultVO;
import git.ggwzz.matching.vo.MatchingTaskVO;
import git.ggwzz.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 智能挂链推荐相关工具集，供 AI Agent 调用。
 */
@Component
@RequiredArgsConstructor
public class MatchingTools {

    private final MatchingService matchingService;

    @Tool(description = "运行智能挂链推荐，为企业在指定产业链中寻找合适的节点。返回推荐结果列表，包含置信度和推荐理由。")
    public String runSmartMatching(
            @ToolParam(description = "企业ID") Long enterpriseId,
            @ToolParam(description = "产业链ID") Long chainId) {
        try {
            Long operator = SecurityUtils.currentUserId();
            CreateMatchingTaskRequest request = new CreateMatchingTaskRequest();
            request.setEnterpriseId(enterpriseId);
            request.setChainId(chainId);
            MatchingTaskVO task = matchingService.createTask(request, operator);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("推荐任务已创建: 任务ID=%d, 状态=%s", task.getTaskId(), task.getStatus()));
            if (Boolean.TRUE.equals(task.getAlreadyLinkedInChain())) {
                sb.append("\n注意: 该企业在此产业链中已有挂链节点");
            }
            if (task.getResults() != null && !task.getResults().isEmpty()) {
                sb.append("\n推荐结果:");
                for (MatchingResultVO r : task.getResults()) {
                    sb.append(String.format("\n  结果ID=%d, 节点ID=%d, 置信度=%s, 决策状态=%s",
                            r.getResultId(), r.getNodeId(),
                            r.getConfidence() != null ? r.getConfidence().toPlainString() : "N/A",
                            r.getDecision()));
                    if (r.getReason() != null) {
                        sb.append(", 理由=").append(r.getReason());
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "智能推荐失败: " + e.getMessage();
        }
    }

    @Tool(description = "接受智能挂链推荐结果，确认将企业挂链到推荐的节点。")
    public String acceptMatchingResult(
            @ToolParam(description = "推荐结果ID") Long resultId) {
        try {
            Long operator = SecurityUtils.currentUserId();
            MatchingDecisionVO decision = matchingService.acceptResult(resultId, operator);
            return String.format("已接受推荐结果: 结果ID=%d, 决策=%s", decision.getResultId(), decision.getDecision());
        } catch (Exception e) {
            return "接受推荐结果失败: " + e.getMessage();
        }
    }

    @Tool(description = "拒绝智能挂链推荐结果，并说明拒绝原因。")
    public String rejectMatchingResult(
            @ToolParam(description = "推荐结果ID") Long resultId,
            @ToolParam(description = "拒绝原因") String reason) {
        try {
            Long operator = SecurityUtils.currentUserId();
            RejectMatchingResultRequest request = new RejectMatchingResultRequest();
            request.setRejectReason(reason);
            MatchingDecisionVO decision = matchingService.rejectResult(resultId, request, operator);
            return String.format("已拒绝推荐结果: 结果ID=%d, 决策=%s", decision.getResultId(), decision.getDecision());
        } catch (Exception e) {
            return "拒绝推荐结果失败: " + e.getMessage();
        }
    }
}
