package git.ggwzz.auth.service;

import git.ggwzz.auth.dto.LoginRequest;
import git.ggwzz.auth.vo.CurrentUserVO;
import git.ggwzz.auth.vo.LoginVO;

/**
 * 鉴权服务。
 *
 * <p>负责登录校验、生成令牌、获取当前用户、登出等业务规则。不返回 {@code Result}。</p>
 */
public interface AuthService {

    /**
     * 用户登录，校验账号状态与密码，成功返回令牌与权限。
     *
     * @param request 登录请求
     * @return 登录响应 VO
     */
    LoginVO login(LoginRequest request);

    /**
     * 登出。无状态模式下后端仅返回成功，由前端清除令牌。
     */
    void logout();

    /**
     * 获取当前登录用户及其角色与权限。
     *
     * @return 当前用户 VO
     */
    CurrentUserVO currentUser();
}