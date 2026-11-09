package git.ggwzz.chain.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 移动产业链节点请求。
 */
@Data
public class MoveNodeRequest {

    /** 新父节点 ID，NULL 表示移动为一级节点 */
    private Long newParentId;

    /** 移动后的同级排序 */
    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
}
