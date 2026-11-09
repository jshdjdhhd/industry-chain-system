package git.ggwzz.matching.vo;

import lombok.Data;

/**
 * 推荐结果决策视图对象。
 */
@Data
public class MatchingDecisionVO {

    private Long resultId;
    private String decision;
}
