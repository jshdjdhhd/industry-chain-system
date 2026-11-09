package git.ggwzz.security.filter;

import git.ggwzz.security.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>继承 {@link OncePerRequestFilter}，从请求头 {@code Authorization: Bearer <token>} 提取
 * 并校验 Token；校验成功后才写入 SecurityContext。校验失败不抛异常，交由
 * {@link git.ggwzz.security.handler.AuthenticationEntryPointImpl} 统一返回 401。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 仅处理合法的 Bearer Token；缺失或无效令牌不建立认证，由后续 Security 流程统一返回 401。
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && jwtTokenProvider.validate(token)) {
            try {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.get("username", String.class);
                List<String> permissions = extractPermissions(claims);
                // 将令牌中的权限编码转换为 Spring Security Authority，并恢复到当前请求的安全上下文。
                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(buildPrincipal(userId, username, authorities),
                                null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                log.debug("JWT 解析失败，清空上下文: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPermissions(Claims claims) {
        Object perms = claims.get("permissions");
        if (perms instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return Collections.emptyList();
    }

    private git.ggwzz.security.service.SecurityUser buildPrincipal(Long userId, String username,
                                                                   List<SimpleGrantedAuthority> authorities) {
        return new git.ggwzz.security.service.SecurityUser(userId, username, null, true, authorities);
    }
}
