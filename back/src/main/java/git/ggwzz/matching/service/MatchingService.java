package git.ggwzz.matching.service;

import git.ggwzz.matching.dto.BatchQueryRequest;
import git.ggwzz.matching.dto.CreateMatchingTaskRequest;
import git.ggwzz.matching.dto.CreateBatchMatchingTaskRequest;
import git.ggwzz.matching.dto.ManualConfirmRequest;
import git.ggwzz.matching.dto.RejectMatchingResultRequest;
import git.ggwzz.matching.vo.BatchQueryVO;
import git.ggwzz.matching.vo.ManualConfirmVO;
import git.ggwzz.matching.vo.BatchMatchingTaskVO;
import git.ggwzz.matching.vo.MatchingDecisionVO;
import git.ggwzz.matching.vo.MatchingTaskVO;

/**
 * 智能挂链推荐服务接口。
 */
public interface MatchingService {

    MatchingTaskVO createTask(CreateMatchingTaskRequest request, Long operator);

    MatchingTaskVO createTaskAsync(CreateMatchingTaskRequest request, Long operator);

    BatchMatchingTaskVO createBatchTasksAsync(CreateBatchMatchingTaskRequest request, Long operator);

    MatchingTaskVO getTask(Long taskId);

    MatchingTaskVO cancelTask(Long taskId, Long operator);

    MatchingTaskVO revokeTask(Long taskId, Long operator);

    MatchingTaskVO retryTask(Long taskId, Long operator);

    MatchingDecisionVO acceptResult(Long resultId, Long operator);

    MatchingDecisionVO rejectResult(Long resultId, RejectMatchingResultRequest request, Long operator);

    ManualConfirmVO manualConfirm(ManualConfirmRequest request, Long operator);

    /**
     * 批量查询任务状态。
     */
    BatchQueryVO batchQueryTasks(BatchQueryRequest request);
}
