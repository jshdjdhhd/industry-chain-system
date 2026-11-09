package git.ggwzz.auth.service.impl;

import git.ggwzz.auth.dto.LoginRequest;
import git.ggwzz.auth.entity.SysPermission;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysUser;
import git.ggwzz.auth.enums.UserStatusEnum;
import git.ggwzz.auth.mapper.SysPermissionMapper;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysUserMapper;
import git.ggwzz.auth.service.AuthService;
import git.ggwzz.auth.vo.CurrentUserVO;
import git.ggwzz.auth.vo.LoginVO;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.security.service.SecurityUser;
import git.ggwzz.security.token.JwtTokenProvider;
import git.ggwzz.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 鉴权服务实现。
 *
 * <p>登录校验顺序：用户存在 → 未删除 → 账号状态 → 密码匹配。
 * 账号不存在/已删除/密码错误统一提示"用户名或密码错误"，不泄露账号是否存在；
 * 账号停用/锁定返回对应业务提示。成功后按数据库当前权限生成 JWT。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String BAD_CREDENTIALS = "用户名或密码错误";
    private static final String BEARER = "Bearer";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginVO login(LoginRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        // 按“用户存在 → 账号状态 → 密码”顺序校验，并收敛不存在与密码错误提示，避免泄露账号状态。
        SysUser user = sysUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            log.warn("登录失败：用户不存在 username={}", request.getUsername());
            throw new BusinessException(ResultCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        checkAccountStatus(user);
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码不匹配 username={}", request.getUsername());
            throw new BusinessException(ResultCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        // 登录成功后读取数据库权限并写入 JWT，后续请求可直接从令牌恢复认证主体和权限集合。
        List<String> permissions = loadPermissionCodes(user.getId());
        String token = jwtTokenProvider.generate(user.getId(), user.getUsername(), permissions);
        log.info("登录成功 userId={} username={}", user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .tokenType(BEARER)
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .permissions(permissions)
                .build();
    }

    @Override
    public void logout() {
        Long userId = SecurityUtils.currentUserId();
        if (userId != null) {
            log.info("用户登出 userId={}", userId);
        }
    }

    @Override
    public CurrentUserVO currentUser() {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String username = SecurityUtils.currentUsername();
        List<SysRole> roles = sysRoleMapper.selectByUserId(userId);
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        List<String> permissions = loadPermissionCodes(userId);
        SysUser user = sysUserMapper.selectById(userId);
        String realName = user == null ? null : user.getRealName();
        return CurrentUserVO.builder()
                .userId(userId)
                .username(username)
                .realName(realName)
                .roles(roleCodes)
                .permissions(permissions)
                .build();
    }

    private void checkAccountStatus(SysUser user) {
        UserStatusEnum status = UserStatusEnum.of(user.getStatus());
        if (status == null) {
            log.warn("登录失败：账号状态未知 username={}", user.getUsername());
            throw new BusinessException(ResultCode.FORBIDDEN, "账号状态异常，请联系管理员");
        }
        if (status == UserStatusEnum.DISABLED) {
            log.warn("登录失败：账号已停用 username={}", user.getUsername());
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已停用，请联系管理员");
        }
        if (status == UserStatusEnum.LOCKED) {
            log.warn("登录失败：账号已锁定 username={}", user.getUsername());
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已锁定，请联系管理员");
        }
    }

    private List<String> loadPermissionCodes(Long userId) {
        List<SysPermission> permissions = sysPermissionMapper.selectByUserId(userId);
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return permissions.stream().map(SysPermission::getPermissionCode).toList();
    }

    /** 占位引用以保留 SecurityUser 类的语义关联 */
    @SuppressWarnings("unused")
    private static final Class<SecurityUser> PRINCIPAL_TYPE = SecurityUser.class;
}
