package git.ggwzz.security.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 自定义 UserDetails，封装当前登录用户主体。
 *
 * <p>携带用户 ID、用户名、密码、启用状态与权限集合。供 {@link UserDetailsServiceImpl}
 * 构造并写入 SecurityContext。</p>
 */
@Getter
public class SecurityUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    @JsonIgnore
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 构造登录用户主体。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param password    密码哈希
     * @param enabled     是否启用
     * @param authorities 权限集合
     */
    public SecurityUser(Long userId, String username, String password,
                        boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}