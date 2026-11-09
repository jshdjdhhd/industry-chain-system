package git.ggwzz.chain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 企业-节点关系实体，对应表 {@code enterprise_node_rel}。
 */
@Data
@TableName("enterprise_node_rel")
public class EnterpriseNodeRel {

    /** 关系ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业ID */
    private Long enterpriseId;

    /** 节点ID */
    private Long nodeId;

    /** 来源（MANUAL/AI/IMPORT） */
    private String source;

    /** 置信度 */
    private BigDecimal confidence;

    /** 理由 */
    private String reason;

    /** 确认人ID */
    private Long confirmedBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
