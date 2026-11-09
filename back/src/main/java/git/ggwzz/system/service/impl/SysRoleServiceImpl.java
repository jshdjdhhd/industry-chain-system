package git.ggwzz.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.auth.entity.SysPermission;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysRolePermission;
import git.ggwzz.auth.entity.SysUserRole;
import git.ggwzz.auth.mapper.SysPermissionMapper;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysRolePermissionMapper;
import git.ggwzz.auth.mapper.SysUserRoleMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.service.SysRoleService;
import git.ggwzz.system.vo.SysPermissionTreeVO;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<SysRoleVO> listAll() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0));
        return roles.stream().map(r -> SysRoleVO.builder()
                .id(r.getId())
                .roleCode(r.getRoleCode())
                .roleName(r.getRoleName())
                .build()).toList();
    }

    @Override
    public SysRoleDetailVO getDetail(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        List<SysPermission> permissions = permissionMapper.selectByRoleId(id);
        return SysRoleDetailVO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .permissions(buildTree(permissions))
                .build();
    }

    @Override
    @Transactional
    public void create(CreateRoleRequest request) {
        Long exists = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, request.getRoleCode())
                        .eq(SysRole::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDeleted(0);
        roleMapper.insert(role);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            for (Long permId : request.getPermissionIds()) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(role.getId());
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
    }

    @Override
    public void update(Long id, UpdateRoleRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        role.setRoleName(request.getRoleName());
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该角色下存在用户，无法删除");
        }
        role.setDeleted(1);
        roleMapper.updateById(role);
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
    }

    @Override
    @Transactional
    public void assignPermissions(Long id, AssignPermissionsRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        if (request.getPermissionIds() != null) {
            for (Long permId : request.getPermissionIds()) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(id);
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
    }

    @Override
    public List<SysPermissionTreeVO> getPermissionTree() {
        List<SysPermission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDeleted, 0)
                        .orderByAsc(SysPermission::getSort));
        return buildTree(all);
    }

    private List<SysPermissionTreeVO> buildTree(List<SysPermission> permissions) {
        Map<Long, List<SysPermission>> grouped = permissions.stream()
                .filter(p -> p.getParentId() != null && p.getParentId() != 0)
                .collect(Collectors.groupingBy(SysPermission::getParentId));
        return permissions.stream()
                .filter(p -> p.getParentId() == null || p.getParentId() == 0)
                .map(root -> toTreeVO(root, grouped))
                .toList();
    }

    private SysPermissionTreeVO toTreeVO(SysPermission p, Map<Long, List<SysPermission>> grouped) {
        List<SysPermissionTreeVO> children = grouped.getOrDefault(p.getId(), new ArrayList<>())
                .stream().map(c -> toTreeVO(c, grouped)).toList();
        return SysPermissionTreeVO.builder()
                .id(p.getId())
                .permissionCode(p.getPermissionCode())
                .permissionName(p.getPermissionName())
                .parentId(p.getParentId())
                .sort(p.getSort())
                .children(children.isEmpty() ? null : children)
                .build();
    }
}
