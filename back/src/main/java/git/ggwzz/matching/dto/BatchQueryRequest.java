package git.ggwzz.matching.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量查询任务请求。
 */
@Data
public class BatchQueryRequest {

    /** 任务 ID 列表 */
    private List<Long> taskIds;
}
