package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

/**
 * 匹配决策汇总投影。
 */
@Data
public class MatchingDecisionRow {

    /** 匹配决策 */
    private String decision;

    /** 决策数量 */
    private long cnt;

    /** 平均置信度 */
    private double avgConfidence;
}
