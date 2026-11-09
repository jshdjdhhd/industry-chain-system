package git.ggwzz.common.api;

import lombok.Getter;

/**
 * 统一响应状态码。
 *
 * <p>成功码固定为 {@link #SUCCESS}（200），禁止同时存在 0 与 200 两种成功码。
 * 各业务码与 HTTP 状态码保持一致。</p>
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "success"),
    /** 请求参数错误或一般业务规则不允许 */
    BAD_REQUEST(400, "请求参数错误或业务规则不允许"),
    /** 未登录、Token 缺失/无效/过期 */
    UNAUTHORIZED(401, "未登录或登录已失效"),
    /** 已认证但没有访问权限 */
    FORBIDDEN(403, "无访问权限"),
    /** 业务资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 唯一数据冲突或重复绑定 */
    CONFLICT(409, "数据冲突或重复操作"),
    /** 未预期的系统内部错误 */
    INTERNAL_ERROR(500, "服务器内部错误，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}