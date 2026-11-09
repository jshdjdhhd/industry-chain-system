package git.ggwzz.tag.dto;

import lombok.Data;

/**
 * 标签查询请求。
 */
@Data
public class TagQueryRequest {

    /** 关键词（标签名称模糊匹配） */
    private String keyword;

    /** 标签类型 */
    private String type;
}
