package git.ggwzz.auth.enums;

import lombok.Getter;

/**
 * 用户状态枚举，避免魔法值。
 */
@Getter
public enum UserStatusEnum {

    /** 正常 */
    NORMAL(1, "正常"),
    /** 停用 */
    DISABLED(2, "停用"),
    /** 锁定 */
    LOCKED(3, "锁定");

    private final int code;
    private final String desc;

    UserStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 由 code 转枚举。
     *
     * @param code 状态码
     * @return 枚举，未匹配返回 null
     */
    public static UserStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}