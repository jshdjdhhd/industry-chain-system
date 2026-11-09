package git.ggwzz.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据看板概览视图对象。
 */
@Data
public class DashboardOverviewVO {

    private List<Metric> metrics;
    private List<ChainDistribution> chainDistribution;
    private List<NodeStatistic> nodeStatistics;
    private MatchingSummary matchingSummary;
    private List<WeeklyProgress> weeklyProgress;
    private List<RecentOperation> recentOperations;

    @Data
    public static class Metric {
        private String label;
        private Object value;
        private String unit;
        private String trend;
        private String tone;
    }

    @Data
    public static class ChainDistribution {
        private String name;
        private int value;
        private String percent;
    }

    @Data
    public static class NodeStatistic {
        private String name;
        private int value;
        private String tone;
    }

    @Data
    public static class MatchingSummary {
        private int pending;
        private int accepted;
        private int rejected;
        private int manual;
        private double averageConfidence;
        private int weeklyRecommendations;
    }

    @Data
    public static class WeeklyProgress {
        private String date;
        private int inbound;
        private int nodes;
        private int recommendations;
        private int accepted;
    }

    @Data
    public static class RecentOperation {
        private Long id;
        private String operator;
        private String action;
        private String targetType;
        private Long targetId;
        private String createdAt;
        private String targetName;
        private String result;
    }
}
