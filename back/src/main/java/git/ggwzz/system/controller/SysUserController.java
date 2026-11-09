package git.ggwzz.system.controller;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.service.SysUserService;
import git.ggwzz.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping
    public Result<PageResult<SysUserVO>> page(@Valid UserQuery query) {
        return Result.success(sysUserService.page(query));
    }

    @GetMapping("/{id}")
    public Result<SysUserVO> detail(@PathVariable Long id) {
        return Result.success(sysUserService.getDetail(id));
    }

    @OperationLog(action = "create_user", targetType = "user")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateUserRequest request) {
        sysUserService.create(request);
        return Result.success();
    }

    @OperationLog(action = "update_user", targetType = "user", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        sysUserService.update(id, request);
        return Result.success();
    }

    @OperationLog(action = "delete_user", targetType = "user", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success();
    }

    @OperationLog(action = "update_user_status", targetType = "user", targetId = "#id")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysUserService.updateStatus(id, status);
        return Result.success();
    }

    @OperationLog(action = "assign_user_roles", targetType = "user", targetId = "#id")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        sysUserService.assignRoles(id, request);
        return Result.success();
    }

    @OperationLog(action = "reset_password", targetType = "user", targetId = "#id")
    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        sysUserService.resetPassword(id, request);
        return Result.success();
    }
}
