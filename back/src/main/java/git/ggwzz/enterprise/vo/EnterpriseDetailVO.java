package git.ggwzz.enterprise.vo;

import git.ggwzz.tag.vo.TagVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 企业详情 VO（含标签和挂链节点）。
 *
 * <p>字段名与前端 domain.ts 中 EnterpriseDetail 接口对齐。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseDetailVO extends EnterpriseVO {

    /** 标签列表 */
    private List<TagVO> tags;

    /** 已挂接节点列表 */
    private List<LinkedNodeVO> linkedNodes;
}
