package git.ggwzz.matching.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验 AI 聊天请求中的企业与产业链上下文是否完整。
 */
@Documented
@Constraint(validatedBy = AiChatContextValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAiChatContext {

    /**
     * 校验失败提示。
     *
     * @return 提示信息
     */
    String message() default "AI 聊天上下文参数不合法";

    /**
     * Jakarta Validation 分组。
     *
     * @return 分组类型
     */
    Class<?>[] groups() default {};

    /**
     * Jakarta Validation 负载。
     *
     * @return 负载类型
     */
    Class<? extends Payload>[] payload() default {};
}
