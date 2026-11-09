package git.ggwzz.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名查询未删除的用户。
     *
     * @param username 用户名
     * @return 用户实体，不存在返回 null
     */
    SysUser selectByUsername(String username);
}