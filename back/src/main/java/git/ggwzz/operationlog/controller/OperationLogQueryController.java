package git.ggwzz.operationlog.controller;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.util.PageQuery;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询接口。
 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogQueryController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志。
     */
    @GetMapping
    public Result<PageResult<SysOperationLog>> page(@Valid PageQuery query) {
        return Result.success(operationLogService.page(query.getPageNum(), query.getPageSize()));
    }
}
