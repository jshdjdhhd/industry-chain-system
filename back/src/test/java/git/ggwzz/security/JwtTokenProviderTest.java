package git.ggwzz.security;

import git.ggwzz.security.token.JwtProperties;
import git.ggwzz.security.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenProvider} 单元测试：生成、解析、校验、过期处理。
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.getJwt().setSecret("test-secret-strong-enough-for-hs256-signing-key-xxxxxxxx");
        props.getJwt().setExpiration(2L);
        provider = new JwtTokenProvider(props);
        provider.init();
    }

    @Test
    void generateAndParseShouldCarryClaims() {
        String token = provider.generate(1L, "admin", List.of("enterprise:enterprise:query"));
        assertTrue(provider.validate(token));
        Claims claims = provider.parse(token);
        assertEquals(1L, claims.get("userId", Long.class));
        assertEquals("admin", claims.get("username", String.class));
    }

    @Test
    void validateShouldRejectGarbageToken() {
        assertFalse(provider.validate("not-a-jwt"));
    }

    @Test
    void validateShouldRejectExpiredToken() throws InterruptedException {
        String token = provider.generate(1L, "admin", List.of());
        // expiration=2s，等待过期
        Thread.sleep(2500L);
        assertFalse(provider.validate(token));
    }

    @Test
    void expirationSecondsShouldMatchConfig() {
        assertEquals(2L, provider.getExpirationSeconds());
    }
}