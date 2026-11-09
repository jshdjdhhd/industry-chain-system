package git.ggwzz.operationlog.service;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.dto.ChainHttpOperationLogCleanupRequest;

/**
 * 操作日志服务。
 *
 * <p>提供给操作日志 AOP 切面调用，统一异步落库。</p>
 */
public interface OperationLogService {

    /**
     * 异步记录一条操作日志。
     *
     * @param log 操作日志实体
     */
    void record(SysOperationLog log);

    /**
     * 分页查询操作日志（按时间倒序）。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<SysOperationLog> page(int pageNum, int pageSize);

    /**
     * 清理 chain.http 本次联调测试产生的操作日志。
     *
     * @param request 清理范围
     * @return 删除行数
     */
    int cleanupChainHttpTestLogs(ChainHttpOperationLogCleanupRequest request);
}
