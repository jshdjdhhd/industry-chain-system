package git.ggwzz.matching.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能挂链推荐结果视图对象。
 */
@Data
public class MatchingResultVO {

    private Long resultId;
    private Long nodeId;
    private String nodePath;
    private BigDecimal confidence;
    private String reason;
    private String evidence;
    private String decision;
    private String rejectReason;
    private Boolean alreadyLinked;
    private String linkWarning;
}
