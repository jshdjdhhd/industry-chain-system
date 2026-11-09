package git.ggwzz.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应封装。
 *
 * <p>所有 JSON 接口统一返回 {@code { code, message, data }} 结构，成功码固定 200。
 * 禁止手工拼装响应结构，统一通过 {@link #success()} / {@link #failure(ResultCode)} 生成。</p>
 *
 * @param <T> data 载荷类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码，成功为 200 */
    private Integer code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;

    /**
     * 构造成功响应（无数据）。
     *
     * @param <T> 载荷类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  载荷类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造失败响应（按状态码枚举）。
     *
     * @param resultCode 状态码枚举
     * @param <T>        载荷类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 构造失败响应（自定义状态码与提示）。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     载荷类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 构造失败响应（按状态码枚举，覆盖提示信息）。
     *
     * @param resultCode 状态码枚举
     * @param message    自定义提示信息
     * @param <T>        载荷类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}