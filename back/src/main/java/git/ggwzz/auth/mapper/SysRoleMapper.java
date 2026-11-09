package git.ggwzz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.auth.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统角色 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 按用户 ID 查询其拥有的角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<SysRole> selectByUserId(Long userId);
}