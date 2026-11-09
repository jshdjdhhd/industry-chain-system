package git.ggwzz.security.service;

import git.ggwzz.auth.entity.SysPermission;
import git.ggwzz.auth.entity.SysUser;
import git.ggwzz.auth.mapper.SysPermissionMapper;
import git.ggwzz.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户详情加载实现。
 *
 * <p>按用户名查询 {@link SysUser} 及其关联权限，构造 {@link SecurityUser}。
 * 权限编码格式为 {@code 模块:资源:动作}，以数据库当前状态为准，不在代码中写死。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        List<SysPermission> permissions = sysPermissionMapper.selectByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SysPermission::getPermissionCode)
                .map(SimpleGrantedAuthority::new)
                .toList();
        boolean enabled = isEnabled(user);
        return new SecurityUser(user.getId(), user.getUsername(), user.getPassword(), enabled, authorities);
    }

    private boolean isEnabled(SysUser user) {
        final Integer normal = 1;
        return normal.equals(user.getStatus());
    }
}