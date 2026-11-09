package git.ggwzz.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.service.OperationLogService;
import git.ggwzz.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志切面。
 *
 * <p>环绕拦截 {@link OperationLog} 注解方法，采集操作人、动作、目标类型、目标 ID
 * 与详情，委托 {@link OperationLogService} 异步落库到 {@code operation_log}。
 * 请求参数序列化前剔除密码等敏感字段，不记录完整 Token、JWT 密钥、数据库密码。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 环绕切面：记录操作日志并放行原方法。
     *
     * @param joinPoint     连接点
     * @param operationLog  注解
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常透传
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        // 业务执行前采集注解元数据、当前操作者和请求上下文，形成与具体 Controller 解耦的审计记录。
        SysOperationLog entity = new SysOperationLog();
        entity.setAction(operationLog.action());
        entity.setTargetType(operationLog.targetType());
        entity.setTargetId(resolveTargetId(joinPoint, operationLog.targetId()));
        entity.setOperator(SecurityUtils.currentUsername());

        Map<String, Object> detailMap = new HashMap<>(4);
        detailMap.put("description", operationLog.detail());
        fillRequest(detailMap);

        Object result = null;
        String status = "SUCCESS";
        String error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAIL";
            error = ex.getClass().getSimpleName() + ":" + ex.getMessage();
            throw ex;
        } finally {
            // finally 同时覆盖成功和异常路径；只补充结果、耗时和错误摘要，不吞掉原业务异常。
            detailMap.put("result", status);
            detailMap.put("costTime", System.currentTimeMillis() - start);
            if (error != null) {
                detailMap.put("error", error);
            }
            entity.setDetail(serializeDetail(detailMap));
            try {
                // 日志服务异步落库，提交失败仅记录错误，不能反向影响已经完成的主业务。
                operationLogService.record(entity);
            } catch (Exception ex) {
                log.error("操作日志提交失败 action={} targetType={}", entity.getAction(), entity.getTargetType(), ex);
            }
        }
    }

    private void fillRequest(Map<String, Object> detailMap) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        detailMap.put("method", request.getMethod());
        detailMap.put("url", request.getRequestURI());
        detailMap.put("ip", SecurityUtils.extractIp(request));
    }

    private Long resolveTargetId(ProceedingJoinPoint joinPoint, String spel) {
        if (!StringUtils.hasText(spel)) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(), paramNameDiscoverer);
            Expression expression = parser.parseExpression(spel);
            Object value = expression.getValue(context);
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.longValue();
            }
            return Long.valueOf(value.toString());
        } catch (Exception ex) {
            log.warn("操作日志 targetId 解析失败 spel={}: {}", spel, ex.getMessage());
            return null;
        }
    }

    private String serializeDetail(Map<String, Object> detailMap) {
        try {
            return objectMapper.writeValueAsString(detailMap);
        } catch (Exception ex) {
            log.warn("操作日志 detail 序列化失败: {}", ex.getMessage());
            return "{}";
        }
    }
}
