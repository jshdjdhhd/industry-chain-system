package git.ggwzz.enterprise.vo;

import git.ggwzz.tag.vo.TagVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业列表 VO。
 *
 * <p>字段名与前端 domain.ts 中 EnterpriseListItem 接口对齐。</p>
 */
@Data
public class EnterpriseVO {

    /** 企业 ID */
    private Long id;

    /** 企业名称 */
    private String name;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 法定代表人 */
    private String legalPerson;

    /** 所属行业 */
    private String industry;

    /** 经营地址 */
    private String address;

    /** 经营范围 */
    private String businessScope;

    /** 主营产品 */
    private String mainProducts;

    /** 产值（万元） */
    private BigDecimal outputValue;

    /** 税收（万元） */
    private BigDecimal taxAmount;

    /** 状态 */
    private String status;

    /** 标签列表 */
    private List<TagVO> tags;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
