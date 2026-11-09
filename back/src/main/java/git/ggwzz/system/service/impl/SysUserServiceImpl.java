package git.ggwzz.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysUser;
import git.ggwzz.auth.entity.SysUserRole;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysUserMapper;
import git.ggwzz.auth.mapper.SysUserRoleMapper;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.service.SysUserService;
import git.ggwzz.system.vo.SysRoleVO;
import git.ggwzz.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<SysUserVO> page(UserQuery query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        List<SysUserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public SysUserVO getDetail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    @Transactional
    public void create(CreateUserRequest request) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        if (request.getRoleIds() != null) {
            for (Long roleId : request.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public void update(Long id, UpdateUserRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (StringUtils.hasText(request.getRealName())) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (isLastAdmin(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少保留一个管理员账号");
        }
        user.setDeleted(1);
        userMapper.updateById(user);
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (status == 2 && isLastAdmin(id)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能停用最后一个管理员");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long id, AssignRolesRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        if (request.getRoleIds() != null) {
            for (Long roleId : request.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(id);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long id, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    private boolean isLastAdmin(Long userId) {
        SysRole adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, "admin")
                        .eq(SysRole::getDeleted, 0));
        if (adminRole == null) {
            return false;
        }
        Long adminCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, adminRole.getId()));
        boolean isCurrentUserAdmin = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, adminRole.getId())) > 0;
        return isCurrentUserAdmin && adminCount <= 1;
    }

    private SysUserVO toVO(SysUser user) {
        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        List<SysRoleVO> roleVOs = roles.stream().map(r -> SysRoleVO.builder()
                .id(r.getId())
                .roleCode(r.getRoleCode())
                .roleName(r.getRoleName())
                .build()).toList();
        return SysUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roleVOs)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
