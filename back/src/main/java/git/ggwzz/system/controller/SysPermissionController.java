package git.ggwzz.system.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.system.service.SysRoleService;
import git.ggwzz.system.vo.SysPermissionTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysRoleService sysRoleService;

    @GetMapping("/tree")
    public Result<List<SysPermissionTreeVO>> tree() {
        return Result.success(sysRoleService.getPermissionTree());
    }
}
