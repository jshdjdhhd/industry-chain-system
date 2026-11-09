package git.ggwzz.dashboard.service;

import git.ggwzz.dashboard.vo.DashboardOverviewVO;

/**
 * 数据看板服务接口。
 */
public interface DashboardService {

    /**
     * 获取数据看板概览数据。
     */
    DashboardOverviewVO getOverview();
}
