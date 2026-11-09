package git.ggwzz.dashboard.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.dashboard.service.DashboardService;
import git.ggwzz.dashboard.vo.DashboardOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板接口。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取数据看板概览。
     */
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        return Result.success(dashboardService.getOverview());
    }
}
