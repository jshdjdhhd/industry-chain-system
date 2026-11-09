package git.ggwzz.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统权限实体，对应表 {@code sys_permission}。
 *
 * <p>权限编码格式 {@code 模块:资源:动作}，例如 {@code enterprise:enterprise:query}。</p>
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码，格式 模块:资源:动作 */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 父权限 ID，0 表示根 */
    private Long parentId;

    /** 排序 */
    private Integer sort;

    /** 逻辑删除标识 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}