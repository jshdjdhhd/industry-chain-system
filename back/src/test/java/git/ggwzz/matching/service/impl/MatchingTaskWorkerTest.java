package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.entity.MatchingTask;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import git.ggwzz.matching.service.MatchingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingTaskWorkerTest {

    @Mock
    private MatchingProvider matchingProvider;

    @Mock
    private MatchingProvider localMatchingProvider;

    @Mock
    private MatchingTaskMapper matchingTaskMapper;

    @Mock
    private MatchingResultMapper matchingResultMapper;

    private MatchingTaskWorker worker;

    @BeforeEach
    void setUp() {
        MatchingResultPersistenceSupport persistenceSupport =
                new MatchingResultPersistenceSupport(new ObjectMapper());
        worker = new MatchingTaskWorker(
                matchingProvider,
                matchingTaskMapper,
                matchingResultMapper,
                persistenceSupport);
    }

    @Test
    void executeShouldPersistResultsAndMarkTaskCompleted() {
        MatchingTask processingTask = task(7L, MatchingTaskStatus.PROCESSING);
        MatchingResult result = new MatchingResult();
        result.setNodeId(101L);
        result.setConfidence(new BigDecimal("0.9500"));
        result.setReason("主营产品与节点高度匹配");
        result.setEvidence("{\"model\":\"LLM_RERANK_V3\"}");

        when(matchingTaskMapper.selectById(7L)).thenReturn(processingTask);
        when(matchingProvider.match(100L, 1L)).thenReturn(List.of(result));

        worker.execute(7L, 100L, 1L);

        ArgumentCaptor<MatchingResult> resultCaptor = ArgumentCaptor.forClass(MatchingResult.class);
        verify(matchingResultMapper).insert(resultCaptor.capture());
        MatchingResult savedResult = resultCaptor.getValue();
        assertEquals(7L, savedResult.getTaskId());
        assertEquals(1, savedResult.getRankNo());
        assertEquals(MatchingDecision.PENDING.name(), savedResult.getDecision());

        ArgumentCaptor<MatchingTask> taskCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).updateById(taskCaptor.capture());
        MatchingTask completedTask = taskCaptor.getValue();
        assertEquals(MatchingTaskStatus.COMPLETED.name(), completedTask.getStatus());
        assertTrue(completedTask.getInputSnapshot().contains("\"execution_mode\":\"ASYNC\""));
        assertTrue(completedTask.getInputSnapshot().contains("\"elapsed_ms\""));
    }

    @Test
    void executeWithProviderShouldUseSuppliedProviderAndSnapshotMode() {
        MatchingTask processingTask = task(7L, MatchingTaskStatus.PROCESSING);
        MatchingResult result = new MatchingResult();
        result.setNodeId(101L);
        result.setConfidence(new BigDecimal("0.8800"));
        result.setReason("本地混合匹配命中");
        result.setEvidence("{\"model\":\"HYBRID_EMBEDDING_V2\"}");

        when(matchingTaskMapper.selectById(7L)).thenReturn(processingTask);
        when(localMatchingProvider.match(100L, 1L)).thenReturn(List.of(result));

        worker.executeWithProvider(7L, 100L, 1L, localMatchingProvider, "BATCH_LOCAL");

        verify(localMatchingProvider).match(100L, 1L);
        verify(matchingProvider, never()).match(100L, 1L);

        ArgumentCaptor<MatchingTask> taskCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).updateById(taskCaptor.capture());
        MatchingTask completedTask = taskCaptor.getValue();
        assertEquals(MatchingTaskStatus.COMPLETED.name(), completedTask.getStatus());
        assertTrue(completedTask.getInputSnapshot().contains("\"execution_mode\":\"BATCH_LOCAL\""));
    }

    @Test
    void executeShouldMarkTaskFailedWhenProviderThrows() {
        MatchingTask processingTask = task(7L, MatchingTaskStatus.PROCESSING);
        when(matchingTaskMapper.selectById(7L)).thenReturn(processingTask);
        when(matchingProvider.match(100L, 1L)).thenThrow(new IllegalStateException("llm timeout"));

        worker.execute(7L, 100L, 1L);

        verify(matchingResultMapper, never()).insert(any(MatchingResult.class));
        ArgumentCaptor<MatchingTask> taskCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).updateById(taskCaptor.capture());
        MatchingTask failedTask = taskCaptor.getValue();
        assertEquals(MatchingTaskStatus.TIMEOUT.name(), failedTask.getStatus());
        assertTrue(failedTask.getInputSnapshot().contains("\"error_type\":\"Timeout\""));
        assertTrue(failedTask.getErrorMessage().contains("llm timeout"));
    }

    @Test
    void executeShouldMarkTaskTimeoutWhenElapsedExceedsThreshold() throws Exception {
        MatchingTask processingTask = task(7L, MatchingTaskStatus.PROCESSING);
        ReflectionTestUtils.setField(worker, "asyncTimeoutMs", 1L);
        when(matchingTaskMapper.selectById(7L)).thenReturn(processingTask);
        when(matchingProvider.match(100L, 1L)).thenAnswer(invocation -> {
            Thread.sleep(10L);
            return List.of(new MatchingResult());
        });

        worker.execute(7L, 100L, 1L);

        verify(matchingResultMapper, never()).insert(any(MatchingResult.class));
        ArgumentCaptor<MatchingTask> taskCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).updateById(taskCaptor.capture());
        MatchingTask timeoutTask = taskCaptor.getValue();
        assertEquals(MatchingTaskStatus.TIMEOUT.name(), timeoutTask.getStatus());
        assertTrue(timeoutTask.getInputSnapshot().contains("\"timed_out\":true"));
    }

    @Test
    void executeShouldStopWhenTaskCancelledBeforeProviderCall() {
        MatchingTask processingTask = task(7L, MatchingTaskStatus.PROCESSING);
        MatchingTask cancelledTask = task(7L, MatchingTaskStatus.CANCELLED);
        when(matchingTaskMapper.selectById(7L)).thenReturn(processingTask, cancelledTask);

        worker.execute(7L, 100L, 1L);

        verify(matchingProvider, never()).match(eq(100L), eq(1L));
        verify(matchingTaskMapper, never()).updateById(any(MatchingTask.class));
    }

    @Test
    void executeShouldNotWrapWholeAsyncFlowInSingleTransaction() throws Exception {
        Method execute = MatchingTaskWorker.class.getMethod("execute", Long.class, Long.class, Long.class);
        assertFalse(execute.isAnnotationPresent(Transactional.class));
    }

    private MatchingTask task(Long taskId, MatchingTaskStatus status) {
        MatchingTask task = new MatchingTask();
        task.setId(taskId);
        task.setStatus(status.name());
        task.setInputSnapshot("{\"execution_mode\":\"ASYNC\"}");
        return task;
    }
}
