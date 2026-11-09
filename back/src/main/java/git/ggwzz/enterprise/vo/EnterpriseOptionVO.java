package git.ggwzz.enterprise.vo;

import lombok.Data;

/**
 * 企业选项投影 VO，只含下拉框需要的 ID 与名称。
 *
 * <p>与企业列表 VO 区分：不返回经营范围、主营产品、标签等字段，
 * 专用于智能挂链、AI 助手等下拉选项场景。</p>
 */
@Data
public class EnterpriseOptionVO {

    /** 企业 ID */
    private Long id;

    /** 企业名称 */
    private String name;
}
