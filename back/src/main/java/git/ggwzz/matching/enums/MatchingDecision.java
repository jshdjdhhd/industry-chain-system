package git.ggwzz.matching.enums;

/**
 * 匹配结果决策状态枚举。
 */
public enum MatchingDecision {
    /** 待决策 */
    PENDING,
    /** 已接受 */
    ACCEPTED,
    /** 已拒绝 */
    REJECTED,
    /** 人工确认 */
    MANUAL
}
