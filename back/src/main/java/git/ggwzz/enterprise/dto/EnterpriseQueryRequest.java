package git.ggwzz.enterprise.dto;

import git.ggwzz.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 企业查询请求（分页 + 筛选条件）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseQueryRequest extends PageQuery {

    /** 关键词（企业名称/信用代码/法人模糊匹配） */
    private String keyword;

    /** 企业状态：UNREGISTERED/ACTIVE */
    private String status;

    /** 标签 ID（按标签筛选） */
    private Long tagId;

    /** 产业链节点 ID（按挂链节点筛选） */
    private Long nodeId;
}
