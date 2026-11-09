package git.ggwzz.matching.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量智能挂链推荐任务提交结果。
 */
@Data
public class BatchMatchingTaskVO {

    private Integer enterpriseCount;
    private Integer chainCount;
    private Integer totalPairs;
    private Integer submittedCount;
    private List<MatchingTaskVO> tasks;
}
