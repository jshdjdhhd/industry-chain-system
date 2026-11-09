package git.ggwzz.security.util;

import jakarta.servlet.http.HttpServletRequest;
import git.ggwzz.security.service.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/**
 * 安全上下文工具。
 *
 * <p>提供从 {@link SecurityContextHolder} 获取当前登录用户 ID、用户名的便捷方法，
 * 以及从请求头解析客户端 IP（用于操作日志记录）。</p>
 */
public final class SecurityUtils {

    private static final String UNKNOWN = "unknown";
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户的 UserDetails。
     *
     * @return 当前登录用户，未认证时返回 null
     */
    public static Object currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof SecurityUser)) {
            return null;
        }
        return auth.getPrincipal();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID，未登录返回 null
     */
    public static Long currentUserId() {
        Object principal = currentPrincipal();
        return principal instanceof SecurityUser su ? su.getUserId() : null;
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 用户名，未登录返回 null
     */
    public static String currentUsername() {
        Object principal = currentPrincipal();
        return principal instanceof SecurityUser su ? su.getUsername() : null;
    }

    /**
     * 从请求中解析客户端 IP，穿透常见代理头。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    public static String extractIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !UNKNOWN.equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), UNKNOWN);
    }
}