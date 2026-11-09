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
 * 产业领域词别名实体，对应表 {@code industry_term_alias}。
 */
@Data
@TableName("industry_term_alias")
public class IndustryTermAlias {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceTerm;

    private String expandedTerms;

    private String industry;

    private Integer priority;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
