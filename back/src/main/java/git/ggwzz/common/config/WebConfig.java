package git.ggwzz.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>预留全局 Web 定制点。CORS 交由 Spring Security 统一控制（见 security 模块），
 * 不在此处另行开启，避免通配放行。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // 全局 Web 定制可在此扩展。
    // 注意：CORS 由 Security 统一处理，此处不再开启 controller-level CORS。
}