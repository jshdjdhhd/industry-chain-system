package git.ggwzz.security.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 令牌生成与校验。
 *
 * <p>Token claims 至少包含用户 ID、用户名、签发时间与过期时间。密钥从 {@link JwtProperties}
 * 注入，有效期统一为配置值（默认 7200 秒）。禁止在代码中写死密钥或权限。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        String secret = jwtProperties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥未配置或长度不足 32 字节，请通过环境变量 JWT_SECRET 注入高强度随机密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param permissions 权限编码集合
     * @return JWT 字符串
     */
    public String generate(Long userId, String username, List<String> permissions) {
        long now = System.currentTimeMillis();
        long expirationMs = jwtProperties.getJwt().getExpiration() * 1000L;
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("username", username)
                .claim("permissions", permissions)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 JWT，返回 claims。
     *
     * @param token JWT 字符串
     * @return claims
     * @throws JwtException token 无效或过期时抛出
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 token 是否有效。
     *
     * @param token JWT 字符串
     * @return 有效返回 true
     */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 获取有效期（秒）。
     *
     * @return 有效期秒数
     */
    public long getExpirationSeconds() {
        return jwtProperties.getJwt().getExpiration();
    }
}