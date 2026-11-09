package git.ggwzz.operationlog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * chain.http 操作日志清理请求 DTO。
 */
@Data
public class ChainHttpOperationLogCleanupRequest {

    /** 本次 chain.http 测试开始时间 */
    @NotNull(message = "测试开始时间不能为空")
    private LocalDateTime startedAt;

    /** 本次 chain.http 测试结束时间 */
    @NotNull(message = "测试结束时间不能为空")
    private LocalDateTime endedAt;

    /** 本次测试创建的产业链 ID */
    @NotNull(message = "产业链ID不能为空")
    private Long chainId;

    /** 本次测试创建的节点 ID 列表 */
    @NotEmpty(message = "节点ID列表不能为空")
    private List<@NotNull(message = "节点ID不能为空") Long> nodeIds;

    /** 本次测试使用的企业 ID 列表 */
    @NotEmpty(message = "企业ID列表不能为空")
    private List<@NotNull(message = "企业ID不能为空") Long> enterpriseIds;
}
