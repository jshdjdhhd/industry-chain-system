package git.ggwzz.matching.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.matching.dto.BatchQueryRequest;
import git.ggwzz.matching.service.MatchingService;
import git.ggwzz.matching.vo.BatchQueryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量匹配任务控制器。
 */
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class BatchMatchingController {

    private final MatchingService matchingService;

    /**
     * 批量查询任务状态。
     */
    @PostMapping("/tasks/batch-query")
    @PreAuthorize("hasAuthority('matching:task:query')")
    public Result<BatchQueryVO> batchQueryTasks(@RequestBody BatchQueryRequest request) {
        return Result.success(matchingService.batchQueryTasks(request));
    }
}
