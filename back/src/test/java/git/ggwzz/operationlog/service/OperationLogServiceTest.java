package git.ggwzz.operationlog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.operationlog.dto.ChainHttpOperationLogCleanupRequest;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.mapper.SysOperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OperationLogService} 操作日志维护逻辑测试。
 */
@SpringBootTest
@ActiveProfiles("unittest")
class OperationLogServiceTest {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private SysOperationLogMapper sysOperationLogMapper;

    @BeforeEach
    void setUp() {
        sysOperationLogMapper.delete(new LambdaQueryWrapper<>());
    }

    @Test
    void cleanupChainHttpTestLogsShouldRejectNullRequest() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> operationLogService.cleanupChainHttpTestLogs(null));

        assertEquals(400, ex.getCode());
        assertEquals("请求参数不能为空", ex.getMessage());
        assertEquals(0L, sysOperationLogMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    @Test
    void cleanupChainHttpTestLogsShouldDeleteOnlyCurrentRunRecords() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 26, 10, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 6, 26, 10, 30, 0);

        insertLog("create_chain", "chain", null, startedAt.plusMinutes(1));
        insertLog("update_node", "chain_node", 2L, startedAt.plusMinutes(2));
        insertLog("update_node", "chain_node", 999L, startedAt.plusMinutes(3));
        insertLog("update_node", "chain_node", 2L, startedAt.minusMinutes(1));

        ChainHttpOperationLogCleanupRequest request = new ChainHttpOperationLogCleanupRequest();
        request.setStartedAt(startedAt);
        request.setEndedAt(endedAt);
        request.setChainId(1L);
        request.setNodeIds(List.of(2L));
        request.setEnterpriseIds(List.of(3L));

        int deleted = operationLogService.cleanupChainHttpTestLogs(request);

        assertEquals(2, deleted);
        assertEquals(2L, sysOperationLogMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    private void insertLog(String action, String targetType, Long targetId, LocalDateTime createdAt) {
        SysOperationLog log = new SysOperationLog();
        log.setOperator("admin");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail("{}");
        log.setCreatedAt(createdAt);
        sysOperationLogMapper.insert(log);
    }
}
