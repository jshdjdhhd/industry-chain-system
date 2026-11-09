package git.ggwzz.matching.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量创建智能挂链推荐任务请求。
 */
@Data
public class CreateBatchMatchingTaskRequest {

    /** 是否选择全部存续企业。 */
    private Boolean allEnterprises;

    /** 指定企业 ID 列表；allEnterprises=false 时必填。 */
    private List<Long> enterpriseIds;

    /** 是否选择全部产业链。 */
    private Boolean allChains;

    /** 指定产业链 ID 列表；allChains=false 时必填。 */
    private List<Long> chainIds;
}
