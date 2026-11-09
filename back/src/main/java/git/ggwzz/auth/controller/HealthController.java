package git.ggwzz.auth.controller;

import git.ggwzz.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口，匿名放行。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 健康检查，返回服务状态。
     *
     * @return status=up
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>(2);
        body.put("status", "up");
        return Result.success(body);
    }
}