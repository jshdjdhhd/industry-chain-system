package git.ggwzz.system.service;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.vo.SysUserVO;

public interface SysUserService {

    PageResult<SysUserVO> page(UserQuery query);

    SysUserVO getDetail(Long id);

    void create(CreateUserRequest request);

    void update(Long id, UpdateUserRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    void assignRoles(Long id, AssignRolesRequest request);

    void resetPassword(Long id, ResetPasswordRequest request);

    void changePassword(Long id, ChangePasswordRequest request);
}
