package git.ggwzz.matching.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能挂链推荐任务视图对象。
 */
@Data
public class MatchingTaskVO {

    private Long taskId;
    private Long enterpriseId;
    private String enterpriseName;
    private Long chainId;
    private String chainName;
    private String status;
    private Long retryFromTaskId;
    private Integer retryCount;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private String errorMessage;
    private Boolean alreadyLinkedInChain;
    private List<Long> existingLinkedNodeIds;
    private String linkWarning;
    private List<MatchingResultVO> results;
}
