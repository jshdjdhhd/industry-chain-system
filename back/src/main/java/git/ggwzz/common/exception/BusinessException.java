package git.ggwzz.common.exception;

import git.ggwzz.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于表达可预期的业务规则不允许情形，对外返回用户可理解的提示信息。
 * 禁止把 SQL、堆栈、服务器路径等信息通过本异常返回前端。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final int code;

    /**
     * 按状态码枚举构造，使用枚举默认提示信息。
     *
     * @param resultCode 状态码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 按状态码枚举构造，覆盖提示信息。
     *
     * @param resultCode 状态码枚举
     * @param message    自定义提示信息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 按自定义状态码与提示信息构造。
     *
     * @param code    状态码
     * @param message 提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}