package git.ggwzz.matching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.entity.MatchingTask;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import git.ggwzz.matching.service.MatchingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * 异步执行智能挂链推荐任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingTaskWorker {

    private final MatchingProvider matchingProvider;
    private final MatchingTaskMapper matchingTaskMapper;
    private final MatchingResultMapper matchingResultMapper;
    private final MatchingResultPersistenceSupport persistenceSupport;

    @Value("${matching.task.async-timeout-ms:120000}")
    private long asyncTimeoutMs = 120000L;

    @Async("matchingTaskExecutor")
    public void execute(Long taskId, Long enterpriseId, Long chainId) {
        executeInternal(taskId, enterpriseId, chainId, matchingProvider, "ASYNC");
    }

    @Async("matchingTaskExecutor")
    public void executeWithProvider(Long taskId,
                                    Long enterpriseId,
                                    Long chainId,
                                    MatchingProvider provider,
                                    String executionMode) {
        executeInternal(taskId, enterpriseId, chainId,
                provider == null ? matchingProvider : provider,
                executionMode == null || executionMode.isBlank() ? "ASYNC" : executionMode);
    }

    private void executeInternal(Long taskId,
                                 Long enterpriseId,
                                 Long chainId,
                                 MatchingProvider provider,
                                 String executionMode) {
        long startedAt = System.nanoTime();
        MatchingTask currentTask = matchingTaskMapper.selectById(taskId);
        MatchingTaskStatus initialStatus = currentTask == null ? null : safeStatus(currentTask.getStatus());
        if (currentTask == null || initialStatus == null) {
            log.warn("async task not found or invalid status: taskId={}", taskId);
            return;
        }
        if (initialStatus == MatchingTaskStatus.CANCELLED) {
            log.info("async task already cancelled before execute: taskId={}", taskId);
            return;
        }
        if (!initialStatus.isCancellable()) {
            log.warn("async task is not runnable: taskId={}, status={}", taskId, currentTask.getStatus());
            return;
        }

        try {
            deleteTaskResults(taskId);
            if (isCancelled(taskId)) {
                log.info("async task cancelled before provider call: taskId={}", taskId);
                return;
            }

            List<MatchingResult> recommendations = provider.match(enterpriseId, chainId);
            if (recommendations == null) {
                recommendations = List.of();
            }
            long elapsedMs = elapsedMillis(startedAt);

            if (isCancelled(taskId)) {
                log.info("async task cancelled after provider call: taskId={}", taskId);
                return;
            }
            if (elapsedMs > asyncTimeoutMs) {
                markTimeout(taskId, currentTask, elapsedMs,
                        "任务执行超时，耗时 " + elapsedMs + " ms，阈值 " + asyncTimeoutMs + " ms",
                        executionMode);
                return;
            }
            if (!saveRecommendations(taskId, recommendations)) {
                log.info("async task cancelled during result persistence: taskId={}", taskId);
                return;
            }

            updateTask(taskId,
                    MatchingTaskStatus.COMPLETED,
                    MatchingTaskSnapshotSupport.completionSnapshot(
                            currentTask.getInputSnapshot(), executionMode, elapsedMs, recommendations.size()),
                    null);
            log.info("async task completed: taskId={}, enterpriseId={}, chainId={}, resultCount={}, elapsedMs={}",
                    taskId, enterpriseId, chainId, recommendations.size(), elapsedMs);
        } catch (Exception ex) {
            if (isCancelled(taskId)) {
                log.info("async task cancelled after exception: taskId={}", taskId);
                return;
            }
            long elapsedMs = elapsedMillis(startedAt);
            MatchingTaskStatus failureStatus = isTimeoutException(ex)
                    ? MatchingTaskStatus.TIMEOUT : MatchingTaskStatus.FAILED;
            String errorMessage = buildErrorMessage(ex, elapsedMs);
            String snapshot = failureStatus == MatchingTaskStatus.TIMEOUT
                    ? MatchingTaskSnapshotSupport.timeoutSnapshot(
                    currentTask.getInputSnapshot(), executionMode, elapsedMs, errorMessage)
                    : MatchingTaskSnapshotSupport.failureSnapshot(
                    currentTask.getInputSnapshot(), executionMode, elapsedMs, ex, false);
            deleteTaskResults(taskId);
            updateTask(taskId, failureStatus, snapshot, errorMessage);
            log.warn("async task failed: taskId={}, enterpriseId={}, chainId={}, status={}, reason={}",
                    taskId, enterpriseId, chainId, failureStatus.name(), ex.getClass().getSimpleName());
            log.debug("async task failure detail", ex);
        }
    }

    private boolean saveRecommendations(Long taskId, List<MatchingResult> recommendations) {
        int rankNo = 1;
        for (MatchingResult result : recommendations) {
            if (isCancelled(taskId)) {
                deleteTaskResults(taskId);
                return false;
            }
            result.setTaskId(taskId);
            result.setRankNo(rankNo++);
            result.setDecision(MatchingDecision.PENDING.name());
            persistenceSupport.prepareForRemotePersistence(result);
            matchingResultMapper.insert(result);
        }
        return true;
    }

    private void markTimeout(Long taskId,
                             MatchingTask task,
                             long elapsedMs,
                             String errorMessage,
                             String executionMode) {
        deleteTaskResults(taskId);
        updateTask(taskId,
                MatchingTaskStatus.TIMEOUT,
                MatchingTaskSnapshotSupport.timeoutSnapshot(task.getInputSnapshot(), executionMode, elapsedMs,
                        errorMessage),
                errorMessage);
        log.warn("async task timed out: taskId={}, elapsedMs={}, timeoutMs={}", taskId, elapsedMs, asyncTimeoutMs);
    }

    private void updateTask(Long taskId, MatchingTaskStatus status, String inputSnapshot, String errorMessage) {
        if (status != MatchingTaskStatus.CANCELLED && isCancelled(taskId)) {
            return;
        }
        MatchingTask task = new MatchingTask();
        task.setId(taskId);
        task.setStatus(status.name());
        task.setInputSnapshot(inputSnapshot);
        task.setErrorMessage(MatchingTaskSnapshotSupport.limit(errorMessage, 500));
        matchingTaskMapper.updateById(task);
    }

    private void deleteTaskResults(Long taskId) {
        matchingResultMapper.delete(new LambdaQueryWrapper<MatchingResult>()
                .eq(MatchingResult::getTaskId, taskId));
    }

    private boolean isCancelled(Long taskId) {
        MatchingTask task = matchingTaskMapper.selectById(taskId);
        return task != null && MatchingTaskStatus.CANCELLED == safeStatus(task.getStatus());
    }

    private MatchingTaskStatus safeStatus(String status) {
        try {
            return MatchingTaskStatus.from(status);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isTimeoutException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof TimeoutException) {
            return true;
        }
        String message = throwable.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) {
            return true;
        }
        return isTimeoutException(throwable.getCause());
    }

    private String buildErrorMessage(Exception ex, long elapsedMs) {
        String message = ex == null ? null : ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex == null ? "unknown error" : ex.getClass().getSimpleName();
        }
        return MatchingTaskSnapshotSupport.limit(message + " (elapsed=" + elapsedMs + "ms)", 500);
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
