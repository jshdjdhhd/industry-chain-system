package git.ggwzz.security.token;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JWT 与安全相关配置属性，绑定 {@code security.*} 配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class JwtProperties {

    /** JWT 配置 */
    private Jwt jwt = new Jwt();

    /** CORS 配置 */
    private Cors cors = new Cors();

    /**
     * JWT 子配置。
     */
    @Data
    public static class Jwt {
        /** 签名密钥，通过环境变量注入 */
        private String secret;
        /** 有效期，单位秒，默认 7200（2 小时） */
        private long expiration = 7200L;
    }

    /**
     * CORS 子配置。
     */
    @Data
    public static class Cors {
        /** 允许的前端地址，禁止通配 */
        private List<String> allowedOrigins = java.util.Collections.emptyList();
    }
}