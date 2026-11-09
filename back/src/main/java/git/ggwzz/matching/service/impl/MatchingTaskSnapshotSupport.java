package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.matching.entity.MatchingTask;

import java.time.LocalDateTime;

/**
 * matching_task.input_snapshot 的最小 JSON 读写工具。
 */
final class MatchingTaskSnapshotSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MatchingTaskSnapshotSupport() {
    }

    static String initialSnapshot(String executionMode) {
        ObjectNode snapshot = readSnapshot(null);
        snapshot.put("execution_mode", executionMode);
        snapshot.put("requested_at", LocalDateTime.now().toString());
        return writeSnapshot(snapshot, executionMode);
    }

    static String retrySnapshot(MatchingTask sourceTask, Long operator) {
        ObjectNode snapshot = readSnapshot(sourceTask == null ? null : sourceTask.getInputSnapshot());
        snapshot.put("execution_mode", "RETRY");
        if (sourceTask != null && sourceTask.getId() != null) {
            snapshot.put("retry_from_task_id", sourceTask.getId());
        }
        snapshot.put("retried_by", String.valueOf(operator));
        snapshot.put("retried_at", LocalDateTime.now().toString());
        return writeSnapshot(snapshot, "RETRY");
    }

    static String cancellationSnapshot(String currentSnapshot,
                                       Long operator,
                                       LocalDateTime cancelledAt) {
        ObjectNode snapshot = readSnapshot(currentSnapshot);
        snapshot.put("cancelled_by", String.valueOf(operator));
        snapshot.put("cancelled_at", cancelledAt.toString());
        snapshot.put("cancel_requested", true);
        return writeSnapshot(snapshot, snapshot.path("execution_mode").asText("ASYNC"));
    }

    static String completionSnapshot(String currentSnapshot,
                                     String executionMode,
                                     long elapsedMs,
                                     int resultCount) {
        ObjectNode snapshot = readSnapshot(currentSnapshot);
        snapshot.put("execution_mode", executionMode);
        snapshot.put("elapsed_ms", elapsedMs);
        snapshot.put("result_count", resultCount);
        snapshot.put("finished_at", LocalDateTime.now().toString());
        snapshot.remove("error_type");
        snapshot.remove("error_message");
        snapshot.remove("timed_out");
        return writeSnapshot(snapshot, executionMode);
    }

    static String failureSnapshot(String currentSnapshot,
                                  String executionMode,
                                  long elapsedMs,
                                  Exception error,
                                  boolean timedOut) {
        ObjectNode snapshot = readSnapshot(currentSnapshot);
        snapshot.put("execution_mode", executionMode);
        snapshot.put("elapsed_ms", elapsedMs);
        snapshot.put("result_count", 0);
        snapshot.put("finished_at", LocalDateTime.now().toString());
        snapshot.put("timed_out", timedOut);
        if (error != null) {
            snapshot.put("error_type", error.getClass().getSimpleName());
            snapshot.put("error_message", limit(error.getMessage(), 240));
        }
        return writeSnapshot(snapshot, executionMode);
    }

    static String timeoutSnapshot(String currentSnapshot,
                                  String executionMode,
                                  long elapsedMs,
                                  String errorMessage) {
        ObjectNode snapshot = readSnapshot(currentSnapshot);
        snapshot.put("execution_mode", executionMode);
        snapshot.put("elapsed_ms", elapsedMs);
        snapshot.put("result_count", 0);
        snapshot.put("finished_at", LocalDateTime.now().toString());
        snapshot.put("timed_out", true);
        snapshot.put("error_type", "Timeout");
        snapshot.put("error_message", limit(errorMessage, 240));
        return writeSnapshot(snapshot, executionMode);
    }

    static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private static ObjectNode readSnapshot(String currentSnapshot) {
        if (currentSnapshot == null || currentSnapshot.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return (ObjectNode) OBJECT_MAPPER.readTree(currentSnapshot);
        } catch (Exception ignored) {
            ObjectNode snapshot = OBJECT_MAPPER.createObjectNode();
            snapshot.put("raw_snapshot", currentSnapshot);
            return snapshot;
        }
    }

    private static String writeSnapshot(ObjectNode snapshot, String fallbackExecutionMode) {
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (Exception ignored) {
            return "{\"execution_mode\":\"" + fallbackExecutionMode + "\"}";
        }
    }
}
