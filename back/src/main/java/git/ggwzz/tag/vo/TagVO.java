package git.ggwzz.tag.vo;

import lombok.Data;

/**
 * 标签响应 VO。
 *
 * <p>字段名与前端 domain.ts 中 Tag 接口对齐。</p>
 */
@Data
public class TagVO {

    /** 标签 ID */
    private Long id;

    /** 标签名称（前端字段 name） */
    private String name;

    /** 标签类型（前端字段 type） */
    private String type;

    /** 颜色 */
    private String color;

    /** 标签说明 */
    private String description;

    /** 使用该标签的企业数量 */
    private Integer usedCount;
}
