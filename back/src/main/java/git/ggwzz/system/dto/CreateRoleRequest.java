package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 64, message = "角色编码长度 2-64")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 64, message = "角色名称长度 2-64")
    private String roleName;

    /** 初始权限 ID 列表 */
    private List<Long> permissionIds;
}
