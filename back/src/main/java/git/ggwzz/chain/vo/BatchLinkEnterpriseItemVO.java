package git.ggwzz.chain.vo;

import git.ggwzz.chain.enums.BatchLinkEnterpriseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量挂接企业单项处理结果 VO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchLinkEnterpriseItemVO {

    /** 企业 ID */
    private Long enterpriseId;

    /** 处理状态 */
    private BatchLinkEnterpriseStatus status;

    /** 展示消息 */
    private String message;
}
