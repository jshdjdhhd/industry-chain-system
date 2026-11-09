package git.ggwzz.matching.enums;

/**
 * 匹配任务状态。
 */
public enum MatchingTaskStatus {
    PROCESSING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT;

    public boolean isCancellable() {
        return this == PROCESSING || this == RETRYING;
    }

    public boolean canRetry() {
        return this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    public static MatchingTaskStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return MatchingTaskStatus.valueOf(value.trim().toUpperCase());
    }
}
