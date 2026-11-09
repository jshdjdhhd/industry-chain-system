package git.ggwzz.matching.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量查询任务响应。
 */
@Data
public class BatchQueryVO {

    /** 总任务数 */
    private Integer total;

    /** 处理中任务数 */
    private Integer processingCount;

    /** 已完成任务数 */
    private Integer completedCount;

    /** 失败任务数 */
    private Integer failedCount;

    /** 已采纳结果数 */
    private Integer acceptedCount;

    /** 已拒绝结果数 */
    private Integer rejectedCount;

    /** 任务列表 */
    private List<MatchingTaskVO> tasks;
}
