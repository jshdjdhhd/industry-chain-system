package git.ggwzz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.auth.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}