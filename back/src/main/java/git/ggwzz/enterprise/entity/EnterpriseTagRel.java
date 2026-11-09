package git.ggwzz.enterprise.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业-标签关联实体，对应表 {@code enterprise_tag_rel}。
 */
@Data
@TableName("enterprise_tag_rel")
public class EnterpriseTagRel {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业ID */
    private Long enterpriseId;

    /** 标签ID */
    private Long tagId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
