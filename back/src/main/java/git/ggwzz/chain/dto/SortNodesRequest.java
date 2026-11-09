package git.ggwzz.chain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量更新同级节点排序请求。
 */
@Data
public class SortNodesRequest {

    /** 父节点 ID，NULL 表示一级节点 */
    private Long parentId;

    /** 节点排序项 */
    @Valid
    @NotEmpty(message = "排序节点不能为空")
    private List<SortNodeOrderRequest> nodeOrders;
}
