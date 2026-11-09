package git.ggwzz.common.exception;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>统一将异常转换为 {@link Result}，并保证 HTTP 状态码与响应体 {@code code} 一致。
 * 至少覆盖：参数校验、约束违反、请求体不可读、业务异常、AccessDenied、404、未知异常。
 * 未知异常记录完整堆栈，对外返回 500 通用提示，禁止回吐 SQL、堆栈、服务器路径。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 请求体参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ResultCode.BAD_REQUEST, msg));
    }

    /** 方法参数 / 路径变量约束违反 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ":" + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ResultCode.BAD_REQUEST, msg));
    }

    /** 请求体不可读 / JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体不可读: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ResultCode.BAD_REQUEST, "请求体格式错误或缺失"));
    }

    /** 业务异常，按异常自带 code 返回 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 path={} code={} msg={}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status)
                .body(Result.failure(ex.getCode(), ex.getMessage()));
    }

    /** 已认证但无权限：403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("无权限访问 path={}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.failure(ResultCode.FORBIDDEN));
    }

    /** 资源不存在：404 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException ex) {
        log.warn("资源不存在: {}", ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.failure(ResultCode.NOT_FOUND));
    }

    /** 未知异常：记录完整堆栈，对外返回 500 通用提示 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("未知异常 path={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(ResultCode.INTERNAL_ERROR));
    }

    private String formatFieldError(FieldError fe) {
        String field = fe.getField();
        return field + ":" + fe.getDefaultMessage();
    }
}