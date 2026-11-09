package git.ggwzz.matching.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能挂链评估样本，对应表 {@code matching_evaluation_sample}。
 */
@Data
@TableName("matching_evaluation_sample")
public class MatchingEvaluationSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sampleName;

    private Long enterpriseId;

    private Long chainId;

    private Long expectedNodeId;

    private String reason;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
