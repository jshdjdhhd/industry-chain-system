package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

/**
 * 企业总数和已挂链企业数聚合投影。
 */
@Data
public class EnterpriseCountRow {

    /** 活跃企业总数 */
    private long total;

    /** 已挂链企业数 */
    private long linked;
}
