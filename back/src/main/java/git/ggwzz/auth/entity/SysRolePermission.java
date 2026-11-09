package git.ggwzz.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联实体，对应表 {@code sys_role_permission}。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID */
    private Long permissionId;
}