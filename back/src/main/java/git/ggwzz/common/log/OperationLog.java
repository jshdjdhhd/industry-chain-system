package git.ggwzz.common.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 *
 * <p>标注在 Controller 方法或 Service 方法上，由 {@link OperationLogAspect} 统一采集
 * 并异步落库到 {@code operation_log}。用于满足登录、企业入库/出库、节点变更、
 * 挂链/解绑、推荐采纳/拒绝等关键动作的审计要求。</p>
 *
 * <p>落库字段映射：{@code action}→action，{@code targetType}→target_type，
 * {@code targetId}→target_id（支持 SpEL 从方法参数解析），{@code operator} 取当前登录用户名。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 业务动作，如 login、inbound、bind */
    String action();

    /** 目标类型，如 enterprise、chain_node、matching_result */
    String targetType() default "";

    /** 目标 ID 的 SpEL 表达式，从方法参数解析，如 {@code #id}；不填则不记录 */
    String targetId() default "";

    /** 附加详情键，用于在 detail 中追加说明 */
    String detail() default "";
}