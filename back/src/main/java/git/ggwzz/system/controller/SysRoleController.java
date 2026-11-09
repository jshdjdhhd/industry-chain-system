package git.ggwzz.system.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.service.SysRoleService;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping
    public Result<List<SysRoleVO>> list() {
        return Result.success(sysRoleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysRoleDetailVO> detail(@PathVariable Long id) {
        return Result.success(sysRoleService.getDetail(id));
    }

    @OperationLog(action = "create_role", targetType = "role")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateRoleRequest request) {
        sysRoleService.create(request);
        return Result.success();
    }

    @OperationLog(action = "update_role", targetType = "role", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        sysRoleService.update(id, request);
        return Result.success();
    }

    @OperationLog(action = "delete_role", targetType = "role", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return Result.success();
    }

    @OperationLog(action = "assign_role_permissions", targetType = "role", targetId = "#id")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                           @Valid @RequestBody AssignPermissionsRequest request) {
        sysRoleService.assignPermissions(id, request);
        return Result.success();
    }
}
