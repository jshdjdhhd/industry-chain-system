package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

/**
 * 每日推荐统计投影。
 */
@Data
public class DayRecommendationRow {

    /** 数据库星期序号：1 为星期日，2 为星期一 */
    private int dayOfWeek;

    /** 推荐数量 */
    private int recommendations;

    /** 已采纳数量 */
    private int accepted;
}
