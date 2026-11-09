package git.ggwzz.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.analysis.dto.AnalysisReportQuery;
import git.ggwzz.analysis.dto.CreateAnalysisReportRequest;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import git.ggwzz.analysis.mapper.AnalysisReportContentMapper;
import git.ggwzz.analysis.mapper.AnalysisReportTaskMapper;
import git.ggwzz.analysis.service.impl.AnalysisReportServiceImpl;
import git.ggwzz.analysis.vo.AnalysisReportDetailVO;
import git.ggwzz.analysis.vo.AnalysisReportTaskVO;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnalysisReportServiceImpl} 单元测试。
 *
 * <p>纯 Mockito，mock 全部 Mapper 与 Worker。覆盖参数校验、范围展开、
 * 创建任务状态、重试状态守卫与列表分页。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisReportServiceTest {

    @Mock
    private AnalysisReportTaskMapper analysisReportTaskMapper;
    @Mock
    private AnalysisReportContentMapper analysisReportContentMapper;
    @Mock
    private AnalysisReportWorker analysisReportWorker;
    @Mock
    private ChainMapper chainMapper;
    @Mock
    private ChainNodeMapper chainNodeMapper;

    private AnalysisReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnalysisReportServiceImpl(
                analysisReportTaskMapper,
                analysisReportContentMapper,
                analysisReportWorker,
                chainMapper,
                chainNodeMapper,
                new ObjectMapper());
        // insert 时回填自增 id，便于后续 VO 断言
        doAnswer(invocation -> {
            AnalysisReportTask task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 1001L);
            return 1;
        }).when(analysisReportTaskMapper).insert(any(AnalysisReportTask.class));
    }

    // ==================== 参数校验 ====================

    @Test
    void shouldThrowBadRequestWhenRequestIsNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(null));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(analysisReportTaskMapper, never()).insert(any(AnalysisReportTask.class));
    }

    @Test
    void shouldThrowNotFoundWhenChainNotExists() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS", "ALL_CHAIN", null);
        when(chainMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(req));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("产业链不存在", ex.getMessage());
    }

    @Test
    void shouldThrowBadRequestWhenReportTypeInvalid() {
        CreateAnalysisReportRequest req = request(1L, "UNKNOWN", "ALL_CHAIN", null);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(req));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("HEALTH_ANALYSIS"));
    }

    @Test
    void shouldThrowBadRequestWhenScopeTypeInvalid() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS", "WRONG", null);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(req));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("SELECTED_NODES"));
    }

    @Test
    void shouldThrowBadRequestWhenSelectedNodesWithoutNodeIds() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS", "SELECTED_NODES", null);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(req));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("nodeIds"));
    }

    @Test
    void shouldThrowBadRequestWhenNodeIdsNotBelongToChain() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS",
                "SELECTED_NODES", List.of(999L));
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createReport(req));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("999"));
    }

    // ==================== 创建与范围展开 ====================

    @Test
    void shouldCreateTaskWithProcessingStatusForAllChain() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS", "ALL_CHAIN", null);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(nodes());

        AnalysisReportTaskVO vo = service.createReport(req);

        ArgumentCaptor<AnalysisReportTask> captor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).insert(captor.capture());
        AnalysisReportTask task = captor.getValue();
        assertEquals("PROCESSING", task.getStatus());
        assertEquals("HEALTH_ANALYSIS", task.getReportType());
        assertEquals("ALL_CHAIN", task.getScopeType());
        assertNull(task.getRetryFromTaskId());
        assertEquals(0, task.getRetryCount());
        // ALL_CHAIN 展开为全部节点
        assertEquals(List.of(2L, 3L, 4L), task.getResolvedNodeIds());
        // 触发异步执行
        verify(analysisReportWorker).execute(1001L);
        assertEquals("测试链", vo.getChainName());
    }

    @Test
    void shouldExpandSubtreeByNodePathForSelectedNodes() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS",
                "SELECTED_NODES", List.of(2L));
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(nodes());

        AnalysisReportTaskVO vo = service.createReport(req);

        ArgumentCaptor<AnalysisReportTask> captor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).insert(captor.capture());
        // 选中节点 2（路径 /2/）应展开出 2、3、4 全部子树
        assertEquals(List.of(2L, 3L, 4L), captor.getValue().getResolvedNodeIds());
        verify(analysisReportWorker).execute(1001L);
    }

    @Test
    void shouldExpandSubtreeForDeepSelectedNodeOnly() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS",
                "SELECTED_NODES", List.of(3L));
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(nodes());

        service.createReport(req);

        ArgumentCaptor<AnalysisReportTask> captor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).insert(captor.capture());
        // 选中节点 3（路径 /2/3/）只应展开 3、4
        assertEquals(List.of(3L, 4L), captor.getValue().getResolvedNodeIds());
    }

    @Test
    void shouldDispatchWorkerOnlyAfterCommitWhenSynchronizationActive() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS", "ALL_CHAIN", null);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(nodes());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createReport(req);

            // 事务提交前不应触发 Worker
            verify(analysisReportWorker, never()).execute(any());

            // 注册了 1 个同步
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            // 模拟事务提交：afterCommit 后才触发 Worker
            synchronizations.get(0).afterCommit();
            verify(analysisReportWorker).execute(1001L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldDispatchWorkerImmediatelyWhenSynchronizationNotActive() {
        CreateAnalysisReportRequest req = request(1L, "HEALTH_ANALYSIS", "ALL_CHAIN", null);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(chainNodeMapper.selectByChainId(1L)).thenReturn(nodes());

        // 无活动事务同步（单测环境）时回退到同步投递
        service.createReport(req);

        verify(analysisReportWorker).execute(1001L);
    }

    @Test
    void shouldDispatchWorkerOnlyAfterCommitOnRetryWhenSynchronizationActive() {
        AnalysisReportTask failed = task(1001L, 1L, "FAILED");
        failed.setRetryCount(1);
        when(analysisReportTaskMapper.selectById(1001L)).thenReturn(failed);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        doAnswer(invocation -> {
            AnalysisReportTask task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 1002L);
            return 1;
        }).when(analysisReportTaskMapper).insert(any(AnalysisReportTask.class));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.retryReport(1001L);

            verify(analysisReportWorker, never()).execute(any());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            synchronizations.get(0).afterCommit();
            verify(analysisReportWorker).execute(1002L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== 列表 ====================

    @Test
    void shouldReturnPagedListWithChainName() {
        AnalysisReportTask task = task(1001L, 1L, "COMPLETED");
        when(analysisReportTaskMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<AnalysisReportTask> page =
                    invocation.getArgument(0);
            page.setRecords(List.of(task));
            page.setTotal(1);
            return page;
        });
        when(chainMapper.selectBatchIds(any())).thenReturn(List.of(chain(1L, "测试链")));

        AnalysisReportQuery query = new AnalysisReportQuery();
        query.setChainId(1L);
        query.setPageNum(1);
        query.setPageSize(10);
        PageResult<AnalysisReportTaskVO> result = service.listReports(query);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("测试链", result.getRecords().get(0).getChainName());
    }

    // ==================== 详情 ====================

    @Test
    void shouldThrowNotFoundWhenReportNotExists() {
        when(analysisReportTaskMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getReport(999L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void shouldReturnDetailWithContent() {
        AnalysisReportTask task = task(1001L, 1L, "COMPLETED");
        when(analysisReportTaskMapper.selectById(1001L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        when(analysisReportContentMapper.selectOne(any())).thenReturn(null);

        AnalysisReportDetailVO vo = service.getReport(1001L);
        assertEquals(1001L, vo.getId());
        assertEquals("测试链", vo.getChainName());
        assertEquals("COMPLETED", vo.getStatus());
        assertNull(vo.getContentMarkdown());
    }

    // ==================== 重试 ====================

    @Test
    void shouldThrowNotFoundWhenRetryOriginNotExists() {
        when(analysisReportTaskMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retryReport(999L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void shouldThrowConflictWhenRetryCompletedTask() {
        AnalysisReportTask completed = task(1001L, 1L, "COMPLETED");
        when(analysisReportTaskMapper.selectById(1001L)).thenReturn(completed);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retryReport(1001L));
        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    void shouldCreateRetryTaskAndTriggerWorkerWhenFailed() {
        AnalysisReportTask failed = task(1001L, 1L, "FAILED");
        failed.setRetryCount(1);
        when(analysisReportTaskMapper.selectById(1001L)).thenReturn(failed);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));
        // 重试任务回填新的自增 id，区别于原任务 id，确保断言的是"新任务 id"而非"来源 id"
        doAnswer(invocation -> {
            AnalysisReportTask task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "id", 1002L);
            return 1;
        }).when(analysisReportTaskMapper).insert(any(AnalysisReportTask.class));

        AnalysisReportTaskVO vo = service.retryReport(1001L);

        ArgumentCaptor<AnalysisReportTask> captor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).insert(captor.capture());
        AnalysisReportTask retry = captor.getValue();
        assertEquals("PROCESSING", retry.getStatus());
        assertEquals(1001L, retry.getRetryFromTaskId());
        assertEquals(2, retry.getRetryCount());
        // Worker 应以新任务 id 触发，而非原任务 id
        verify(analysisReportWorker).execute(1002L);
        assertEquals("测试链", vo.getChainName());
    }

    @Test
    void shouldAllowRetryWhenCancelled() {
        AnalysisReportTask cancelled = task(1001L, 1L, "CANCELLED");
        when(analysisReportTaskMapper.selectById(1001L)).thenReturn(cancelled);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "测试链"));

        service.retryReport(1001L);

        ArgumentCaptor<AnalysisReportTask> captor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).insert(captor.capture());
        assertEquals("PROCESSING", captor.getValue().getStatus());
    }

    // ==================== fixture ====================

    private CreateAnalysisReportRequest request(Long chainId, String type,
                                                String scope, List<Long> nodeIds) {
        CreateAnalysisReportRequest req = new CreateAnalysisReportRequest();
        req.setChainId(chainId);
        req.setReportType(type);
        req.setScopeType(scope);
        req.setNodeIds(nodeIds);
        return req;
    }

    private Chain chain(Long id, String name) {
        Chain c = new Chain();
        c.setId(id);
        c.setChainName(name);
        return c;
    }

    private List<ChainNode> nodes() {
        ChainNode up = node(2L, 1L, "/2/");
        ChainNode mid = node(3L, 2L, "/2/3/");
        ChainNode leaf = node(4L, 3L, "/2/3/4/");
        return List.of(up, mid, leaf);
    }

    private ChainNode node(Long id, Long parentId, String path) {
        ChainNode n = new ChainNode();
        n.setId(id);
        n.setChainId(1L);
        n.setParentId(parentId);
        n.setNodePath(path);
        n.setNodeName("节点" + id);
        return n;
    }

    private AnalysisReportTask task(Long id, Long chainId, String status) {
        AnalysisReportTask t = new AnalysisReportTask();
        t.setId(id);
        t.setChainId(chainId);
        t.setStatus(status);
        t.setReportType("HEALTH_ANALYSIS");
        t.setScopeType("ALL_CHAIN");
        t.setRetryCount(0);
        return t;
    }
}
