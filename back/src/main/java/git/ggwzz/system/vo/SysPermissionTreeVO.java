package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SysPermissionTreeVO {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private Long parentId;
    private Integer sort;
    private List<SysPermissionTreeVO> children;
}
