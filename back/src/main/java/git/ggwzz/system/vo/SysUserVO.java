package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SysUserVO {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private List<SysRoleVO> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
