package git.ggwzz.system.service;

import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.vo.SysPermissionTreeVO;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;

import java.util.List;

public interface SysRoleService {

    List<SysRoleVO> listAll();

    SysRoleDetailVO getDetail(Long id);

    void create(CreateRoleRequest request);

    void update(Long id, UpdateRoleRequest request);

    void delete(Long id);

    void assignPermissions(Long id, AssignPermissionsRequest request);

    List<SysPermissionTreeVO> getPermissionTree();
}
