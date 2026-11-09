package git.ggwzz.chain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新产业链节点请求（部分更新，null 字段不更新）。
 */
@Data
public class UpdateNodeRequest {

    /** 节点名称 */
    @Size(max = 128, message = "节点名称不能超过 128 个字符")
    private String nodeName;

    /** 节点关键词，多个关键词用逗号分隔 */
    @Size(max = 500, message = "节点关键词不能超过 500 个字符")
    private String keywords;

    /** 节点描述 */
    @Size(max = 500, message = "节点描述不能超过 500 个字符")
    private String description;

    /** 同级排序 */
    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;

    /** 是否明确请求修改父节点；缺省为 false，兼容原有更新调用 */
    private boolean parentChangeRequested;

    /** 目标父节点 ID；请求修改且值为 null 时移动为一级节点 */
    private Long parentId;
}
