package git.ggwzz.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录成功响应 VO。
 */
@Data
@Builder
public class LoginVO {

    /** 访问令牌 */
    private String token;

    /** 令牌类型，固定 Bearer */
    private String tokenType;

    /** 有效期（秒） */
    private long expiresIn;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 权限编码集合 */
    private List<String> permissions;
}