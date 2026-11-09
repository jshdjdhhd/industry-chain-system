package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SysRoleDetailVO {
    private Long id;
    private String roleCode;
    private String roleName;
    private List<SysPermissionTreeVO> permissions;
}
