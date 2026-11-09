package git.ggwzz.tag.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建标签请求。
 *
 * <p>前端发送 name/type，后端存储为 tagName/tagType。
 * 通过 {@code @JsonAlias} 兼容两种命名。</p>
 */
@Data
public class CreateTagRequest {

    /** 标签名称 */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 64, message = "标签名称不能超过 64 个字符")
    @JsonAlias("name")
    private String tagName;

    /** 标签类型 */
    @NotBlank(message = "标签类型不能为空")
    @Size(max = 32, message = "标签类型不能超过 32 个字符")
    @JsonAlias("type")
    private String tagType;

    /** 颜色 */
    @Size(max = 16, message = "颜色值不能超过 16 个字符")
    private String color;

    /** 标签说明 */
    @Size(max = 255, message = "标签说明不能超过 255 个字符")
    private String description;
}
