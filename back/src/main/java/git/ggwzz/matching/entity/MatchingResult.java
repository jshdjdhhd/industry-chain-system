package git.ggwzz.matching.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能挂链推荐结果实体，对应表 {@code matching_result}。
 */
@Data
@TableName("matching_result")
public class MatchingResult {

    /** 结果ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务ID */
    private Long taskId;

    /** 节点ID */
    private Long nodeId;

    /** 置信度 */
    private BigDecimal confidence;

    /** 推荐理由 */
    private String reason;

    /** 证据字段 */
    private String evidence;

    /** 排名 */
    private Integer rankNo;

    /** 决策状态 */
    private String decision;

    /** 拒绝原因 */
    private String rejectReason;

    /** 决策人ID */
    private String decidedBy;

    /** 决策时间 */
    private LocalDateTime decidedAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
