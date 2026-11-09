package git.ggwzz.analysis.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史挂链推荐证据投影行。
 *
 * <p>用于健康分析报告上下文中"已采纳/待决策推荐证据"的结构化材料。
 * 每行对应一条 matching_result 记录（ACCEPTED 或 PENDING），
 * 通过 matching_task 关联到企业 ID 与产业链 ID。</p>
 */
@Data
public class MatchingEvidenceRow {

    /** 推荐结果 ID */
    private Long resultId;

    /** 推荐任务 ID */
    private Long taskId;

    /** 候选企业 ID（来自 matching_task） */
    private Long enterpriseId;

    /** 候选企业名称 */
    private String enterpriseName;

    /** 目标节点 ID */
    private Long nodeId;

    /** 置信度 */
    private BigDecimal confidence;

    /** 推荐理由 */
    private String reason;

    /** 证据字段 */
    private String evidence;

    /** 决策状态（ACCEPTED / PENDING） */
    private String decision;

    /** 推荐结果创建时间 */
    private LocalDateTime createdAt;
}
