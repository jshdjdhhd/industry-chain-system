package git.ggwzz.system.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.security.util.SecurityUtils;
import git.ggwzz.system.dto.ChangePasswordRequest;
import git.ggwzz.system.service.SysUserService;
import git.ggwzz.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final SysUserService sysUserService;

    @GetMapping
    public Result<SysUserVO> profile() {
        return Result.success(sysUserService.getDetail(SecurityUtils.currentUserId()));
    }

    @OperationLog(action = "change_password", targetType = "user")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        sysUserService.changePassword(SecurityUtils.currentUserId(), request);
        return Result.success();
    }
}
