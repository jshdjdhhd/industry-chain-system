package git.ggwzz.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建健康分析报告请求。
 *
 * <p>校验规则：
 * <ul>
 *   <li>{@code reportType} 第一版只允许 HEALTH_ANALYSIS。</li>
 *   <li>{@code scopeType} 支持 SELECTED_NODES、ALL_CHAIN。</li>
 *   <li>SELECTED_NODES 时 {@code nodeIds} 不能为空。</li>
 *   <li>所有 {@code nodeIds} 必须属于同一 {@code chainId}。</li>
 * </ul>
 * 选择父节点时后端根据 node_path 展开完整子树，前端传来的只是用户选择口径。</p>
 */
@Data
public class CreateAnalysisReportRequest {

    /** 产业链 ID */
    @NotNull(message = "产业链ID不能为空")
    private Long chainId;

    /** 报告类型，第一版只允许 HEALTH_ANALYSIS */
    @NotBlank(message = "报告类型不能为空")
    private String reportType;

    /** 范围类型：SELECTED_NODES | ALL_CHAIN */
    @NotBlank(message = "范围类型不能为空")
    private String scopeType;

    /** 用户选择的节点 ID 列表，SELECTED_NODES 时必填 */
    private List<Long> nodeIds;

    /** 报告可选项 */
    private AnalysisReportOptions options;
}