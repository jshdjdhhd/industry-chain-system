package git.ggwzz.chain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单个节点排序请求项。
 */
@Data
public class SortNodeOrderRequest {

    /** 节点 ID */
    @NotNull(message = "节点 ID 不能为空")
    private Long nodeId;

    /** 同级排序 */
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
}
