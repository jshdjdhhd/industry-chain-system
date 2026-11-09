package git.ggwzz.operationlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.operationlog.dto.ChainHttpOperationLogCleanupRequest;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.mapper.SysOperationLogMapper;
import git.ggwzz.operationlog.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志服务实现。
 *
 * <p>使用 {@link Async} 异步落库，避免阻塞业务请求；落库失败仅记录日志，不影响主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private static final List<String> CHAIN_HTTP_ACTIONS = List.of(
            "login", "create_chain", "update_chain", "delete_chain",
            "create_node", "update_node", "move_node", "sort_nodes", "delete_node",
            "bind", "batch_bind", "unbind"
    );

    private final SysOperationLogMapper sysOperationLogMapper;

    @Async
    @Override
    public void record(SysOperationLog operationLog) {
        try {
            sysOperationLogMapper.insert(operationLog);
        } catch (Exception ex) {
            log.error("操作日志落库失败 action={} targetType={}", operationLog.getAction(), operationLog.getTargetType(), ex);
        }
    }

    @Override
    public PageResult<SysOperationLog> page(int pageNum, int pageSize) {
        Page<SysOperationLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysOperationLog::getCreatedAt);
        Page<SysOperationLog> result = sysOperationLogMapper.selectPage(page, wrapper);
        PageResult<SysOperationLog> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setTotal((int) result.getTotal());
        pageResult.setPageNum(pageNum);
        pageResult.setPageSize(pageSize);
        return pageResult;
    }

    @Override
    public int cleanupChainHttpTestLogs(ChainHttpOperationLogCleanupRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        List<Long> targetIds = new ArrayList<>();
        targetIds.add(request.getChainId());
        targetIds.addAll(request.getNodeIds());
        targetIds.addAll(request.getEnterpriseIds());

        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysOperationLog::getAction, CHAIN_HTTP_ACTIONS)
                .ge(SysOperationLog::getCreatedAt, request.getStartedAt())
                .le(SysOperationLog::getCreatedAt, request.getEndedAt())
                .and(condition -> condition.isNull(SysOperationLog::getTargetId)
                        .or()
                        .in(SysOperationLog::getTargetId, targetIds));
        return sysOperationLogMapper.delete(wrapper);
    }
}
