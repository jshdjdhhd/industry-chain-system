package git.ggwzz.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户 VO。
 */
@Data
@Builder
public class CurrentUserVO {

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 角色编码集合 */
    private List<String> roles;

    /** 权限编码集合 */
    private List<String> permissions;
}