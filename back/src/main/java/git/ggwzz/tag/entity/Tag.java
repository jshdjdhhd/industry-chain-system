package git.ggwzz.tag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签实体，对应表 {@code tag}。
 */
@Data
@TableName("tag")
public class Tag {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称 */
    private String tagName;

    /** 标签类型：INDUSTRY/SCALE/RISK/CUSTOM */
    private String tagType;

    /** 颜色（十六进制） */
    private String color;

    /** 标签说明 */
    private String description;

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
