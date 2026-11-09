package git.ggwzz.matching.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.matching.dto.AiChatRequest;
import git.ggwzz.matching.dto.CreateBatchMatchingTaskRequest;
import git.ggwzz.matching.dto.CreateMatchingTaskRequest;
import git.ggwzz.matching.dto.ManualConfirmRequest;
import git.ggwzz.matching.dto.RejectMatchingResultRequest;
import git.ggwzz.matching.service.AiChatService;
import git.ggwzz.matching.service.MatchingService;
import git.ggwzz.matching.service.impl.MatchingEvaluationService;
import git.ggwzz.matching.vo.AiChatResponse;
import git.ggwzz.matching.vo.ManualConfirmVO;
import git.ggwzz.matching.vo.BatchMatchingTaskVO;
import git.ggwzz.matching.vo.MatchingDecisionVO;
import git.ggwzz.matching.vo.MatchingEvaluationReport;
import git.ggwzz.matching.vo.MatchingTaskVO;
import git.ggwzz.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能挂链推荐控制器。
 */
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;
    private final MatchingEvaluationService matchingEvaluationService;
    private final AiChatService aiChatService;

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('matching:task:create')")
    @OperationLog(action = "创建智能挂链推荐任务", targetType = "MatchingTask")
    public Result<MatchingTaskVO> createTask(@Valid @RequestBody CreateMatchingTaskRequest request) {
        return Result.success(matchingService.createTask(request, currentUserId()));
    }

    @PostMapping("/tasks/async")
    @PreAuthorize("hasAuthority('matching:task:execute')")
    @OperationLog(action = "异步创建智能挂链推荐任务", targetType = "MatchingTask")
    public Result<MatchingTaskVO> createTaskAsync(@Valid @RequestBody CreateMatchingTaskRequest request) {
        return Result.success(matchingService.createTaskAsync(request, currentUserId()));
    }

    @PostMapping("/tasks/batch-async")
    @PreAuthorize("hasAuthority('matching:task:execute')")
    @OperationLog(action = "批量创建智能挂链推荐任务", targetType = "MatchingTask")
    public Result<BatchMatchingTaskVO> createBatchTasksAsync(
            @RequestBody CreateBatchMatchingTaskRequest request) {
        return Result.success(matchingService.createBatchTasksAsync(request, currentUserId()));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('matching:task:query')")
    public Result<MatchingTaskVO> getTask(@PathVariable Long taskId) {
        return Result.success(matchingService.getTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @PreAuthorize("hasAuthority('matching:task:cancel')")
    @OperationLog(action = "取消智能挂链推荐任务", targetType = "MatchingTask", targetId = "#taskId")
    public Result<MatchingTaskVO> cancelTask(@PathVariable Long taskId) {
        return Result.success(matchingService.cancelTask(taskId, currentUserId()));
    }

    @PostMapping("/tasks/{taskId}/revoke")
    @PreAuthorize("hasAuthority('matching:task:cancel')")
    @OperationLog(action = "撤回智能挂链推荐任务", targetType = "MatchingTask", targetId = "#taskId")
    public Result<MatchingTaskVO> revokeTask(@PathVariable Long taskId) {
        return Result.success(matchingService.revokeTask(taskId, currentUserId()));
    }

    @PostMapping("/tasks/{taskId}/retry")
    @PreAuthorize("hasAuthority('matching:task:retry')")
    @OperationLog(action = "重试智能挂链推荐任务", targetType = "MatchingTask", targetId = "#taskId")
    public Result<MatchingTaskVO> retryTask(@PathVariable Long taskId) {
        return Result.success(matchingService.retryTask(taskId, currentUserId()));
    }

    @PostMapping("/results/{resultId}/accept")
    @PreAuthorize("hasAuthority('matching:result:accept')")
    @OperationLog(action = "采纳智能挂链推荐", targetType = "MatchingResult", targetId = "#resultId")
    public Result<MatchingDecisionVO> acceptResult(@PathVariable Long resultId) {
        return Result.success(matchingService.acceptResult(resultId, currentUserId()));
    }

    @PostMapping("/results/{resultId}/reject")
    @PreAuthorize("hasAuthority('matching:result:reject')")
    @OperationLog(action = "拒绝智能挂链推荐", targetType = "MatchingResult", targetId = "#resultId")
    public Result<MatchingDecisionVO> rejectResult(@PathVariable Long resultId,
                                                   @Valid @RequestBody RejectMatchingResultRequest request) {
        return Result.success(matchingService.rejectResult(resultId, request, currentUserId()));
    }

    @PostMapping("/manual-confirm")
    @PreAuthorize("hasAuthority('matching:manual-confirm:create')")
    @OperationLog(action = "人工确认挂链", targetType = "EnterpriseNodeRel")
    public Result<ManualConfirmVO> manualConfirm(@Valid @RequestBody ManualConfirmRequest request) {
        return Result.success(matchingService.manualConfirm(request, currentUserId()));
    }

    @PostMapping("/ai-chat")
    @PreAuthorize("hasAuthority('matching:task:create')")
    @OperationLog(action = "AI 挂链分析对话", targetType = "MatchingTask")
    public Result<AiChatResponse> aiChat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(request));
    }

    @PostMapping(value = "/ai-chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('matching:task:create')")
    public SseEmitter aiChatStream(@Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        aiChatService.chatStreaming(request, emitter);
        return emitter;
    }

    @PostMapping("/evaluations/run")
    @PreAuthorize("hasAuthority('matching:evaluation:run')")
    @OperationLog(action = "运行智能挂链评测", targetType = "MatchingEvaluation")
    public Result<List<MatchingEvaluationReport>> runEvaluation(@RequestParam Long chainId) {
        return Result.success(matchingEvaluationService.evaluateAll(chainId));
    }

    private Long currentUserId() {
        try {
            return SecurityUtils.currentUserId();
        } catch (Exception ex) {
            return null;
        }
    }
}
