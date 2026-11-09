package git.ggwzz.operationlog.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.operationlog.dto.ChainHttpOperationLogCleanupRequest;
import git.ggwzz.operationlog.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志维护接口。
 *
 * <p>仅在本地和测试环境启用，用于清理 IDEA HTTP Client 联调脚本产生的操作日志。</p>
 */
@Profile({"local", "test", "unittest"})
@RestController
@RequestMapping("/api/operation-logs/maintenance")
@RequiredArgsConstructor
public class OperationLogMaintenanceController {

    private final OperationLogService operationLogService;

    /**
     * 清理 chain.http 本次测试产生的操作日志。
     *
     * @param request 清理范围
     * @return 删除行数
     */
    @DeleteMapping("/chain-http")
    public Result<Integer> cleanupChainHttpLogs(
            @Valid @RequestBody ChainHttpOperationLogCleanupRequest request) {
        return Result.success(operationLogService.cleanupChainHttpTestLogs(request));
    }
}
