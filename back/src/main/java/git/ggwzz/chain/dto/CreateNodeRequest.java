package git.ggwzz.chain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建产业链节点请求。
 *
 * <p>chainId 由路径参数提供（controller 注入），body 不必带、也不做 {@code @NotNull} 校验。
 */
@Data
public class CreateNodeRequest {

    /** 所属产业链 ID（由 controller 从路径注入） */
    private Long chainId;

    /** 父节点 ID，NULL 为一级节点 */
    private Long parentId;

    /** 节点名称 */
    @NotBlank(message = "节点名称不能为空")
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
}
