package git.ggwzz.matching.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能挂链推荐任务实体，对应表 {@code matching_task}。
 */
@Data
@TableName("matching_task")
public class MatchingTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    private Long chainId;

    private String inputSnapshot;

    private String modelName;

    private String status;

    @TableField("retry_from_task_id")
    private Long retryFromTaskId;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("cancelled_by")
    private String cancelledBy;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField("error_message")
    private String errorMessage;

    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
