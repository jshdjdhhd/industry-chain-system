package git.ggwzz.auth.controller;

import git.ggwzz.auth.dto.LoginRequest;
import git.ggwzz.auth.service.AuthService;
import git.ggwzz.auth.vo.CurrentUserVO;
import git.ggwzz.auth.vo.LoginVO;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口。
 *
 * <p>提供登录、登出、获取当前用户信息接口。登录与登出记录操作日志。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录，校验成功后返回 JWT 令牌与权限集合。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @OperationLog(action = "login", targetType = "user", detail = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 登出，无状态模式下由前端清除令牌，后端返回成功并记录日志。
     *
     * @return 成功响应
     */
    @OperationLog(action = "logout", targetType = "user", detail = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户及其角色与权限。
     *
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Result<CurrentUserVO> me() {
        return Result.success(authService.currentUser());
    }
}