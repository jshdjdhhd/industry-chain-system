package git.ggwzz.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.service.EnterpriseNodeRelService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.matching.dto.BatchQueryRequest;
import git.ggwzz.matching.dto.CreateBatchMatchingTaskRequest;
import git.ggwzz.matching.dto.CreateMatchingTaskRequest;
import git.ggwzz.matching.dto.ManualConfirmRequest;
import git.ggwzz.matching.dto.RejectMatchingResultRequest;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.entity.MatchingTask;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import git.ggwzz.matching.service.impl.HybridEmbeddingMatchingProvider;
import git.ggwzz.matching.service.impl.MatchingResultPersistenceSupport;
import git.ggwzz.matching.service.impl.MatchingBatchLocalEngine;
import git.ggwzz.matching.service.impl.MatchingServiceImpl;
import git.ggwzz.matching.service.impl.MatchingTaskWorker;
import git.ggwzz.matching.vo.ManualConfirmVO;
import git.ggwzz.matching.vo.BatchMatchingTaskVO;
import git.ggwzz.matching.vo.MatchingDecisionVO;
import git.ggwzz.matching.vo.MatchingTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private MatchingTaskMapper matchingTaskMapper;

    @Mock
    private MatchingResultMapper matchingResultMapper;

    @Mock
    private MatchingProvider matchingProvider;

    @Mock
    private HybridEmbeddingMatchingProvider hybridEmbeddingMatchingProvider;

    @Mock
    private EnterpriseNodeRelService enterpriseNodeRelService;

    @Mock
    private ChainNodeQueryService chainNodeQueryService;

    @Mock
    private MatchingTaskWorker matchingTaskWorker;

    @Mock
    private EnterpriseMapper enterpriseMapper;

    @Mock
    private ChainMapper chainMapper;

    @Mock
    private MatchingBatchLocalEngine batchLocalEngine;

    @Mock
    private Executor matchingTaskExecutor;

    private MatchingServiceImpl matchingService;

    private MatchingTask completedTask;
    private MatchingTask processingTask;
    private MatchingTask failedTask;
    private MatchingResult result1;
    private MatchingResult result2;

    @BeforeEach
    void setUp() {
        completedTask = task(1L, MatchingTaskStatus.COMPLETED, 100L, 1L);
        processingTask = task(1L, MatchingTaskStatus.PROCESSING, 100L, 1L);
        failedTask = task(1L, MatchingTaskStatus.FAILED, 100L, 1L);
        failedTask.setErrorMessage("provider timeout");

        result1 = result(1L, 1L, 101L, "0.9500", "高度匹配");
        result2 = result(2L, 1L, 102L, "0.3200", "弱匹配");

        matchingService = new MatchingServiceImpl(
                matchingTaskMapper,
                matchingResultMapper,
                matchingProvider,
                hybridEmbeddingMatchingProvider,
                enterpriseNodeRelService,
                chainNodeQueryService,
                matchingTaskWorker,
                enterpriseMapper,
                chainMapper,
                new MatchingResultPersistenceSupport(new ObjectMapper()),
                batchLocalEngine,
                matchingTaskExecutor);
        lenient().when(batchLocalEngine.completeCachedTasks(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createTaskShouldRejectNullRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchingService.createTask(null, 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(matchingTaskMapper, matchingResultMapper, matchingProvider, matchingTaskWorker);
    }

    @Test
    void createTaskShouldCreateTaskAndSaveResults() {
        CreateMatchingTaskRequest request = new CreateMatchingTaskRequest();
        request.setEnterpriseId(100L);
        request.setChainId(1L);

        when(matchingProvider.getModelName()).thenReturn("LLM_ANALYSIS_V1");
        when(matchingProvider.match(100L, 1L)).thenReturn(Arrays.asList(result1, result2));
        doAnswer(invocation -> {
            MatchingTask arg = invocation.getArgument(0);
            arg.setId(1L);
            return 1;
        }).when(matchingTaskMapper).insert(any(MatchingTask.class));
        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);
        when(matchingResultMapper.selectList(any())).thenReturn(Arrays.asList(result1, result2));

        MatchingTaskVO vo = matchingService.createTask(request, 1L);

        assertEquals(MatchingTaskStatus.COMPLETED.name(), vo.getStatus());
        assertEquals(2, vo.getResults().size());

        ArgumentCaptor<MatchingTask> taskCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).insert(taskCaptor.capture());
        MatchingTask savedTask = taskCaptor.getValue();
        assertEquals("LLM_ANALYSIS_V1", savedTask.getModelName());
        assertEquals(0, savedTask.getRetryCount());
        assertTrue(savedTask.getInputSnapshot().contains("\"execution_mode\":\"SYNC\""));
    }

    @Test
    void createTaskAsyncShouldReturnProcessingTaskAndDispatchWorker() {
        CreateMatchingTaskRequest request = new CreateMatchingTaskRequest();
        request.setEnterpriseId(100L);
        request.setChainId(1L);

        when(matchingProvider.getModelName()).thenReturn("LLM_ANALYSIS_V1");
        doAnswer(invocation -> {
            MatchingTask arg = invocation.getArgument(0);
            arg.setId(1L);
            return 1;
        }).when(matchingTaskMapper).insert(any(MatchingTask.class));
        when(matchingTaskMapper.selectById(1L)).thenReturn(processingTask);
        when(matchingResultMapper.selectList(any())).thenReturn(Collections.emptyList());

        MatchingTaskVO vo = matchingService.createTaskAsync(request, 1L);

        assertEquals(MatchingTaskStatus.PROCESSING.name(), vo.getStatus());
        verify(matchingTaskWorker).execute(1L, 100L, 1L);
        verify(matchingProvider, never()).match(anyLong(), anyLong());
    }

    @Test
    void createBatchTasksAsyncShouldExpandAllEnterprisesAndSelectedChains() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(true);
        request.setAllChains(false);
        request.setChainIds(List.of(10L, 11L));

        AtomicLong nextId = new AtomicLong(1L);
        List<MatchingTask> insertedTasks = new java.util.ArrayList<>();
        when(enterpriseMapper.selectList(any())).thenReturn(List.of(enterprise(100L), enterprise(101L)));
        when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        stubBatchTaskInsert(insertedTasks, nextId);

        BatchMatchingTaskVO vo = matchingService.createBatchTasksAsync(request, 1L);

        assertEquals(2, vo.getEnterpriseCount());
        assertEquals(2, vo.getChainCount());
        assertEquals(4, vo.getTotalPairs());
        assertEquals(4, vo.getSubmittedCount());
        assertEquals(4, vo.getTasks().size());
        verify(matchingTaskMapper).insertBatch(insertedTasks);
        verify(matchingTaskMapper, never()).insert(any(MatchingTask.class));
        assertTrue(insertedTasks.stream()
                .allMatch(task -> "HYBRID_EMBEDDING_V2".equals(task.getModelName())
                        && task.getInputSnapshot().contains("\"execution_mode\":\"BATCH_LOCAL\"")));
        ArgumentCaptor<Runnable> commandCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(matchingTaskExecutor).execute(commandCaptor.capture());
        verify(matchingTaskWorker, never()).executeWithProvider(anyLong(), anyLong(), anyLong(), any(), any());

        commandCaptor.getValue().run();
        verify(batchLocalEngine).executeBatch(insertedTasks, List.of(100L, 101L), List.of(10L, 11L));
        verify(matchingProvider, never()).getModelName();
    }

    @Test
    void createBatchTasksAsyncShouldResolveAllChainsForOneEnterprise() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(false);
        request.setEnterpriseIds(List.of(100L));
        request.setAllChains(true);

        AtomicLong nextId = new AtomicLong(1L);
        when(chainMapper.selectList(any())).thenReturn(List.of(chain(10L), chain(11L)));
        when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        List<MatchingTask> insertedTasks = new java.util.ArrayList<>();
        stubBatchTaskInsert(insertedTasks, nextId);

        BatchMatchingTaskVO vo = matchingService.createBatchTasksAsync(request, 1L);

        assertEquals(1, vo.getEnterpriseCount());
        assertEquals(2, vo.getChainCount());
        assertEquals(2, vo.getSubmittedCount());
        verify(matchingTaskMapper).insertBatch(insertedTasks);
        ArgumentCaptor<Runnable> commandCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(matchingTaskExecutor).execute(commandCaptor.capture());

        commandCaptor.getValue().run();
        verify(batchLocalEngine).executeBatch(any(), eq(List.of(100L)), eq(List.of(10L, 11L)));
    }

    @Test
    void createBatchTasksAsyncShouldDispatchOnlyAfterTransactionCommitWhenSynchronizationActive() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(false);
        request.setEnterpriseIds(List.of(100L));
        request.setAllChains(false);
        request.setChainIds(List.of(10L));

        when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        stubBatchTaskInsert(new java.util.ArrayList<>(), new AtomicLong(1L));

        TransactionSynchronizationManager.initSynchronization();
        try {
            BatchMatchingTaskVO vo = matchingService.createBatchTasksAsync(request, 1L);

            assertEquals(1, vo.getSubmittedCount());
            verify(matchingTaskExecutor, never()).execute(any(Runnable.class));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            synchronizations.get(0).afterCommit();
            verify(matchingTaskExecutor).execute(any(Runnable.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createBatchTasksAsyncShouldMarkTasksFailedWhenExecutorRejects() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(false);
        request.setEnterpriseIds(List.of(100L, 101L));
        request.setAllChains(false);
        request.setChainIds(List.of(10L));

        AtomicLong nextId = new AtomicLong(1L);
        when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        stubBatchTaskInsert(new java.util.ArrayList<>(), nextId);
        doThrow(new RejectedExecutionException("queue full"))
                .when(matchingTaskExecutor).execute(any(Runnable.class));

        BatchMatchingTaskVO vo = matchingService.createBatchTasksAsync(request, 1L);

        assertEquals(2, vo.getSubmittedCount());
        ArgumentCaptor<MatchingTask> updateCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper, times(2)).updateById(updateCaptor.capture());
        assertTrue(updateCaptor.getAllValues().stream()
                .allMatch(task -> MatchingTaskStatus.FAILED.name().equals(task.getStatus())
                        && task.getErrorMessage().contains("系统繁忙")));
    }

    @Test
    void batchExecutionCommandShouldMarkTasksFailedWhenEngineThrows() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(false);
        request.setEnterpriseIds(List.of(100L, 101L));
        request.setAllChains(false);
        request.setChainIds(List.of(10L));

        AtomicLong nextId = new AtomicLong(1L);
        List<MatchingTask> insertedTasks = new java.util.ArrayList<>();
        when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        stubBatchTaskInsert(insertedTasks, nextId);
        doThrow(new IllegalStateException("batch bootstrap failed"))
                .when(batchLocalEngine).executeBatch(any(), any(), any());

        matchingService.createBatchTasksAsync(request, 1L);

        ArgumentCaptor<Runnable> commandCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(matchingTaskExecutor).execute(commandCaptor.capture());
        assertDoesNotThrow(() -> commandCaptor.getValue().run());

        ArgumentCaptor<MatchingTask> updateCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper, times(2)).updateById(updateCaptor.capture());
        assertTrue(updateCaptor.getAllValues().stream()
                .allMatch(task -> MatchingTaskStatus.FAILED.name().equals(task.getStatus())
                        && task.getErrorMessage().contains("batch bootstrap failed")));
    }

    @Test
    void createBatchTasksAsyncShouldRejectTooManyPairs() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(false);
        request.setEnterpriseIds(java.util.stream.LongStream.rangeClosed(1, 501)
                .boxed()
                .toList());
        request.setAllChains(false);
        request.setChainIds(List.of(1L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchingService.createBatchTasksAsync(request, 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("最多"));
        verify(matchingTaskMapper, never()).insert(any(MatchingTask.class));
        verify(matchingTaskMapper, never()).insertBatch(any());
    }

    @Test
    void getTaskShouldReturnLifecycleFields() {
        completedTask.setRetryFromTaskId(8L);
        completedTask.setRetryCount(2);
        completedTask.setErrorMessage("历史错误");
        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);
        when(matchingResultMapper.selectList(any())).thenReturn(Arrays.asList(result1, result2));
        when(chainNodeQueryService.buildNodePath(101L)).thenReturn("新能源产业链 / 中游 / 动力电池制造");
        when(chainNodeQueryService.buildNodePath(102L)).thenReturn("新能源产业链 / 上游 / 锂矿");

        MatchingTaskVO vo = matchingService.getTask(1L);

        assertEquals(8L, vo.getRetryFromTaskId());
        assertEquals(2, vo.getRetryCount());
        assertEquals("历史错误", vo.getErrorMessage());
        assertEquals("新能源产业链 / 中游 / 动力电池制造", vo.getResults().get(0).getNodePath());
    }

    @Test
    void getTaskShouldReturnExistingLinkWarningsForEnterpriseAndChain() {
        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);
        when(matchingResultMapper.selectList(any())).thenReturn(Arrays.asList(result1, result2));
        when(chainNodeQueryService.buildNodePath(101L)).thenReturn("新能源产业链 / 中游 / 动力电池制造");
        when(chainNodeQueryService.buildNodePath(102L)).thenReturn("新能源产业链 / 上游 / 锂矿");
        when(enterpriseNodeRelService.listLinkedNodeIdsByEnterpriseAndChain(100L, 1L))
                .thenReturn(Set.of(101L, 103L));

        MatchingTaskVO vo = matchingService.getTask(1L);

        assertTrue(vo.getAlreadyLinkedInChain());
        assertEquals(Set.of(101L, 103L), Set.copyOf(vo.getExistingLinkedNodeIds()));
        assertTrue(vo.getLinkWarning().contains("已在当前产业链存在挂链关系"));
        assertTrue(vo.getResults().get(0).getAlreadyLinked());
        assertTrue(vo.getResults().get(0).getLinkWarning().contains("已挂接到此候选节点"));
        assertEquals(Boolean.FALSE, vo.getResults().get(1).getAlreadyLinked());
    }

    @Test
    void batchQueryTasksShouldCacheNodePathsAcrossResults() {
        BatchQueryRequest request = new BatchQueryRequest();
        request.setTaskIds(List.of(1L, 2L));

        MatchingTask task1 = task(1L, MatchingTaskStatus.COMPLETED, 100L, 10L);
        MatchingTask task2 = task(2L, MatchingTaskStatus.COMPLETED, 101L, 10L);
        MatchingResult task1Result = result(10L, 1L, 101L, "0.9500", "高度匹配");
        MatchingResult task2Result = result(11L, 2L, 101L, "0.9100", "高度匹配");

        when(matchingTaskMapper.selectBatchByIds(List.of(1L, 2L))).thenReturn(List.of(task1, task2));
        when(matchingResultMapper.selectByTaskIds(List.of(1L, 2L)))
                .thenReturn(List.of(task1Result, task2Result));
        when(enterpriseMapper.selectBatchIds(List.of(100L, 101L)))
                .thenReturn(List.of(enterprise(100L), enterprise(101L)));
        when(chainMapper.selectBatchIds(List.of(10L)))
                .thenReturn(List.of(chain(10L)));
        when(chainNodeQueryService.buildNodePath(101L))
                .thenReturn("新能源产业链 / 中游 / 动力电池制造");

        var vo = matchingService.batchQueryTasks(request);

        assertEquals(2, vo.getTotal());
        assertEquals("新能源产业链 / 中游 / 动力电池制造",
                vo.getTasks().get(0).getResults().get(0).getNodePath());
        assertEquals("新能源产业链 / 中游 / 动力电池制造",
                vo.getTasks().get(1).getResults().get(0).getNodePath());
        assertEquals("企业100", vo.getTasks().get(0).getEnterpriseName());
        assertEquals("企业101", vo.getTasks().get(1).getEnterpriseName());
        assertEquals("产业链10", vo.getTasks().get(0).getChainName());
        verify(chainNodeQueryService, times(1)).buildNodePath(101L);
    }

    @Test
    void cancelTaskShouldMarkTaskCancelled() {
        MatchingTask cancelledTask = task(1L, MatchingTaskStatus.CANCELLED, 100L, 1L);
        cancelledTask.setCancelledBy("1");
        cancelledTask.setCancelledAt(LocalDateTime.now());
        cancelledTask.setErrorMessage("任务已取消");

        when(matchingTaskMapper.selectById(1L)).thenReturn(processingTask).thenReturn(cancelledTask);
        when(matchingResultMapper.selectList(any())).thenReturn(Collections.emptyList());

        MatchingTaskVO vo = matchingService.cancelTask(1L, 1L);

        assertEquals(MatchingTaskStatus.CANCELLED.name(), vo.getStatus());
        ArgumentCaptor<MatchingTask> updateCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).updateById(updateCaptor.capture());
        MatchingTask updated = updateCaptor.getValue();
        assertEquals(MatchingTaskStatus.CANCELLED.name(), updated.getStatus());
        assertEquals("1", updated.getCancelledBy());
        assertEquals("任务已取消", updated.getErrorMessage());
    }

    @Test
    void retryTaskShouldCreateRetryingTaskAndDispatchWorker() {
        AtomicLong nextId = new AtomicLong(2L);
        MatchingTask retryTask = task(2L, MatchingTaskStatus.RETRYING, 100L, 1L);
        retryTask.setRetryFromTaskId(1L);
        retryTask.setRetryCount(1);

        when(matchingProvider.getModelName()).thenReturn("LLM_ANALYSIS_V1");
        when(matchingTaskMapper.selectById(1L)).thenReturn(failedTask);
        when(matchingTaskMapper.selectById(2L)).thenReturn(retryTask);
        when(matchingResultMapper.selectList(any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            MatchingTask arg = invocation.getArgument(0);
            arg.setId(nextId.getAndIncrement());
            return 1;
        }).when(matchingTaskMapper).insert(any(MatchingTask.class));

        MatchingTaskVO vo = matchingService.retryTask(1L, 2L);

        assertEquals(2L, vo.getTaskId());
        assertEquals(MatchingTaskStatus.RETRYING.name(), vo.getStatus());
        assertEquals(1L, vo.getRetryFromTaskId());
        assertEquals(1, vo.getRetryCount());
        verify(matchingTaskWorker).execute(2L, 100L, 1L);
    }

    @Test
    void acceptResultShouldAcceptPendingResult() {
        when(matchingResultMapper.selectById(1L)).thenReturn(result1);
        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);

        MatchingDecisionVO vo = matchingService.acceptResult(1L, 1L);

        assertEquals(MatchingDecision.ACCEPTED.name(), vo.getDecision());
        verify(enterpriseNodeRelService).linkByAi(
                eq(100L), eq(101L), eq(new BigDecimal("0.9500")), eq(result1.getReason()), eq(1L));
    }

    @Test
    void rejectResultShouldRejectPendingResult() {
        RejectMatchingResultRequest request = new RejectMatchingResultRequest();
        request.setRejectReason("不符合业务需求");
        when(matchingResultMapper.selectById(1L)).thenReturn(result1);

        MatchingDecisionVO vo = matchingService.rejectResult(1L, request, 1L);

        assertEquals(MatchingDecision.REJECTED.name(), vo.getDecision());
        ArgumentCaptor<MatchingResult> resultCaptor = ArgumentCaptor.forClass(MatchingResult.class);
        verify(matchingResultMapper).updateById(resultCaptor.capture());
        assertEquals("不符合业务需求", resultCaptor.getValue().getRejectReason());
    }

    @Test
    void manualConfirmShouldCreateRelation() {
        ManualConfirmRequest request = new ManualConfirmRequest();
        request.setEnterpriseId(100L);
        request.setNodeId(101L);
        request.setReason("人工确认挂链");

        ManualConfirmVO vo = matchingService.manualConfirm(request, 1L);

        assertNotNull(vo);
        assertTrue(vo.getLinked());
        verify(enterpriseNodeRelService).linkManually(eq(100L), eq(101L), eq("人工确认挂链"), eq(1L));
    }

    @Test
    void manualConfirmShouldRecordManualOverrideOnMatchingTask() {
        ManualConfirmRequest request = new ManualConfirmRequest();
        request.setEnterpriseId(100L);
        request.setNodeId(103L);
        request.setTaskId(1L);
        request.setSourceResultId(1L);
        request.setReason("人工改选到电驱系统节点");

        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);
        when(matchingResultMapper.selectById(1L)).thenReturn(result1);
        when(matchingResultMapper.selectList(any())).thenReturn(Arrays.asList(result1, result2));
        when(chainNodeQueryService.listByChainId(1L))
                .thenReturn(List.of(chainNodeCandidate(101L), chainNodeCandidate(102L), chainNodeCandidate(103L)));
        when(chainNodeQueryService.buildNodePath(103L)).thenReturn("新能源产业链 / 中游 / 电驱系统");

        matchingService.manualConfirm(request, 1L);

        verify(enterpriseNodeRelService).linkManually(eq(100L), eq(103L), eq("人工改选到电驱系统节点"), eq(1L));
        verify(matchingResultMapper).insert(any(MatchingResult.class));
    }

    @Test
    void manualConfirmShouldRejectNodeOutsideTaskChain() {
        ManualConfirmRequest request = new ManualConfirmRequest();
        request.setEnterpriseId(100L);
        request.setNodeId(999L);
        request.setTaskId(1L);
        request.setReason("跨链节点");

        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);
        when(chainNodeQueryService.listByChainId(1L))
                .thenReturn(List.of(chainNodeCandidate(101L), chainNodeCandidate(102L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchingService.manualConfirm(request, 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不属于推荐任务产业链"));
        verify(enterpriseNodeRelService, never()).linkManually(anyLong(), anyLong(), any(), any());
    }

    @Test
    void manualConfirmShouldWorkWithoutReason() {
        ManualConfirmRequest request = new ManualConfirmRequest();
        request.setEnterpriseId(100L);
        request.setNodeId(101L);

        matchingService.manualConfirm(request, 1L);

        verify(enterpriseNodeRelService).linkManually(eq(100L), eq(101L), isNull(), eq(1L));
    }

    @Test
    void cancelTaskShouldRejectCompletedTask() {
        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> matchingService.cancelTask(1L, 1L));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    private MatchingTask task(Long id, MatchingTaskStatus status, Long enterpriseId, Long chainId) {
        MatchingTask task = new MatchingTask();
        task.setId(id);
        task.setEnterpriseId(enterpriseId);
        task.setChainId(chainId);
        task.setModelName("RULE_BASED");
        task.setStatus(status.name());
        task.setCreatedBy("1");
        task.setRetryCount(0);
        task.setInputSnapshot("{\"execution_mode\":\"ASYNC\"}");
        return task;
    }

    private MatchingResult result(Long id, Long taskId, Long nodeId, String confidence, String reason) {
        MatchingResult result = new MatchingResult();
        result.setId(id);
        result.setTaskId(taskId);
        result.setNodeId(nodeId);
        result.setConfidence(new BigDecimal(confidence));
        result.setReason(reason);
        result.setEvidence("{\"keyword_match\":[\"动力电池\"]}");
        result.setRankNo(1);
        result.setDecision(MatchingDecision.PENDING.name());
        return result;
    }

    private ChainNodeCandidateDTO chainNodeCandidate(Long nodeId) {
        ChainNodeCandidateDTO node = new ChainNodeCandidateDTO();
        node.setNodeId(nodeId);
        return node;
    }

    @Test
    void revokeTaskShouldUndoAcceptedAndManualLinksThenClearResults() {
        result1.setDecision(MatchingDecision.ACCEPTED.name());
        result2.setDecision(MatchingDecision.MANUAL.name());
        MatchingTask revokedTask = task(1L, MatchingTaskStatus.CANCELLED, 100L, 1L);
        revokedTask.setErrorMessage("任务已撤回，推荐结果已清空");

        when(matchingTaskMapper.selectById(1L)).thenReturn(completedTask).thenReturn(revokedTask);
        when(matchingResultMapper.selectList(any()))
                .thenReturn(Arrays.asList(result1, result2))
                .thenReturn(Collections.emptyList());

        MatchingTaskVO vo = matchingService.revokeTask(1L, 9L);

        assertEquals(MatchingTaskStatus.CANCELLED.name(), vo.getStatus());
        assertEquals(0, vo.getResults().size());
        verify(enterpriseNodeRelService).unlink(101L, 100L, 9L);
        verify(enterpriseNodeRelService).unlink(102L, 100L, 9L);
        verify(matchingResultMapper).delete(any());

        ArgumentCaptor<MatchingTask> taskCaptor = ArgumentCaptor.forClass(MatchingTask.class);
        verify(matchingTaskMapper).updateById(taskCaptor.capture());
        MatchingTask updated = taskCaptor.getValue();
        assertEquals(MatchingTaskStatus.CANCELLED.name(), updated.getStatus());
        assertTrue(updated.getErrorMessage().contains("撤回"));
    }

    private Enterprise enterprise(Long id) {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(id);
        enterprise.setEnterpriseName("企业" + id);
        enterprise.setStatus("ACTIVE");
        return enterprise;
    }

    private Chain chain(Long id) {
        Chain chain = new Chain();
        chain.setId(id);
        chain.setChainName("产业链" + id);
        return chain;
    }

    @Test
    void createBatchTasksAsyncShouldKeepHybridProviderInsteadOfDefaultAiAnalysisProvider() {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(false);
        request.setEnterpriseIds(List.of(100L));
        request.setAllChains(false);
        request.setChainIds(List.of(10L));

        List<MatchingTask> insertedTasks = new java.util.ArrayList<>();
        when(hybridEmbeddingMatchingProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        stubBatchTaskInsert(insertedTasks, new AtomicLong(1L));

        BatchMatchingTaskVO vo = matchingService.createBatchTasksAsync(request, 1L);

        assertEquals(1, vo.getSubmittedCount());
        assertEquals("HYBRID_EMBEDDING_V2", insertedTasks.get(0).getModelName());
        verify(matchingProvider, never()).getModelName();
    }

    private void stubBatchTaskInsert(List<MatchingTask> insertedTasks, AtomicLong nextId) {
        doAnswer(invocation -> {
            List<MatchingTask> batch = invocation.getArgument(0);
            for (MatchingTask task : batch) {
                task.setId(nextId.getAndIncrement());
            }
            insertedTasks.addAll(batch);
            return batch.size();
        }).when(matchingTaskMapper).insertBatch(any());
    }
}
