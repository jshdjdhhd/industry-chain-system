package git.ggwzz.chain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量挂接企业汇总响应 VO。
 */
@Data
public class BatchLinkEnterpriseVO {

    /** 请求体中的企业 ID 总数，包含重复项 */
    private Integer total;

    /** 成功新增挂接数量 */
    private Integer linkedCount;

    /** 跳过数量 */
    private Integer skippedCount;

    /** 失败数量 */
    private Integer failedCount;

    /** 逐条处理明细 */
    private List<BatchLinkEnterpriseItemVO> items = new ArrayList<>();
}
