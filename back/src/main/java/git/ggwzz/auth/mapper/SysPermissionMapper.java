package git.ggwzz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统权限 Mapper。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 按用户 ID 查询其拥有的权限编码集合（经用户-角色、角色-权限关联）。
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    List<SysPermission> selectByUserId(Long userId);

    /**
     * 按角色 ID 查询其拥有的权限。
     *
     * @param roleId 角色 ID
     * @return 权限列表
     */
    List<SysPermission> selectByRoleId(Long roleId);
}