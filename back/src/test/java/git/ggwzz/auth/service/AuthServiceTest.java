package git.ggwzz.auth.service;

import git.ggwzz.auth.dto.LoginRequest;
import git.ggwzz.auth.entity.SysPermission;
import git.ggwzz.auth.entity.SysUser;
import git.ggwzz.auth.enums.UserStatusEnum;
import git.ggwzz.auth.mapper.SysPermissionMapper;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysUserMapper;
import git.ggwzz.auth.service.impl.AuthServiceImpl;
import git.ggwzz.auth.vo.LoginVO;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.security.token.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl} 单元测试，Mockito 隔离 Mapper 与密码编码器。
 *
 * <p>覆盖正确流程、账号不存在、停用、锁定、密码错误，且失败统一提示"用户名或密码错误"。</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysPermissionMapper sysPermissionMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private SysUser buildUser(Integer status) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("{bcrypt}$2a$10$encoded");
        u.setRealName("管理员");
        u.setStatus(status);
        return u;
    }

    private SysPermission buildPerm(String code) {
        SysPermission p = new SysPermission();
        p.setPermissionCode(code);
        return p;
    }

    @BeforeEach
    void stubCommons() {
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(7200L);
    }

    @Test
    void loginShouldRejectNullRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(null));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("请求参数不能为空", ex.getMessage());
        verify(sysUserMapper, never()).selectByUsername(anyString());
    }

    @Test
    void loginShouldSucceedWithValidCredentials() {
        when(sysUserMapper.selectByUsername("admin")).thenReturn(buildUser(UserStatusEnum.NORMAL.getCode()));
        when(passwordEncoder.matches("admin123", "{bcrypt}$2a$10$encoded")).thenReturn(true);
        when(sysPermissionMapper.selectByUserId(1L)).thenReturn(List.of(buildPerm("enterprise:enterprise:query")));
        when(jwtTokenProvider.generate(1L, "admin", List.of("enterprise:enterprise:query"))).thenReturn("TOKEN");

        LoginVO vo = authService.login(new LoginRequest("admin", "admin123"));

        assertEquals("TOKEN", vo.getToken());
        assertEquals("Bearer", vo.getTokenType());
        assertEquals(1L, vo.getUserId());
        assertEquals(List.of("enterprise:enterprise:query"), vo.getPermissions());
    }

    @Test
    void loginShouldRejectUnknownUser() {
        when(sysUserMapper.selectByUsername(anyString())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("nobody", "x")));
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(jwtTokenProvider, never()).generate(anyLong(), anyString(), any());
    }

    @Test
    void loginShouldRejectDisabledAccount() {
        when(sysUserMapper.selectByUsername("admin")).thenReturn(buildUser(UserStatusEnum.DISABLED.getCode()));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "admin123")));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void loginShouldRejectLockedAccount() {
        when(sysUserMapper.selectByUsername("admin")).thenReturn(buildUser(UserStatusEnum.LOCKED.getCode()));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "admin123")));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void loginShouldRejectWrongPassword() {
        when(sysUserMapper.selectByUsername("admin")).thenReturn(buildUser(UserStatusEnum.NORMAL.getCode()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "wrong")));
        assertEquals("用户名或密码错误", ex.getMessage());
    }
}
