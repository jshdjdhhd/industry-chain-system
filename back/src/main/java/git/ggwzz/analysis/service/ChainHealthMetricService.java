package git.ggwzz.analysis.service;

import git.ggwzz.analysis.enums.ChainHealthLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 产业链节点健康度规则计算服务。
 *
 * <p>纯规则计算，不依赖任何外部数据访问。健康分口径严格如下：
 * </p>
 * <pre>
 * 节点健康分 = 企业承载得分 * 0.50 + 单点依赖得分 * 0.25
 *            + 同级均衡得分 * 0.15 + 企业质量得分 * 0.10
 * </pre>
 * 等级：EMPTY（无企业）、HEALTHY（>=80）、NORMAL（>=60）、WEAK（>=30）、RISK（>=1）。
 */
@Service
public class ChainHealthMetricService {

    /** 企业质量 5 项画像指标，每项权重 0.2 */
    private static final double QUALITY_ITEM_WEIGHT = 0.20;
    /** 企业质量默认最低分（有企业但画像全缺失） */
    private static final int QUALITY_FLOOR = 40;
    /** 企业质量满分 */
    private static final int QUALITY_FULL = 100;
    /** 无企业时企业质量分 */
    private static final int QUALITY_EMPTY = 0;
    /** 同级全空时的均衡得分 */
    private static final int SIBLING_ALL_EMPTY_SCORE = 50;

    /** 健康分各项权重 */
    private static final double WEIGHT_BEARING = 0.50;
    private static final double WEIGHT_SINGLE_POINT = 0.25;
    private static final double WEIGHT_BALANCE = 0.15;
    private static final double WEIGHT_QUALITY = 0.10;

    /** 健康分满分 */
    private static final int SCORE_FULL = 100;

    /** 弱项原因常量 */
    private static final String REASON_EMPTY = "该节点及子树暂无已挂载企业";
    private static final String REASON_BEARING_LOW = "子树企业承载偏低";
    private static final String REASON_SINGLE_POINT = "存在单点依赖风险";
    private static final String REASON_BALANCE_LOW = "明显低于同级平均企业承载";
    private static final String REASON_QUALITY_LOW = "企业产值、税收或画像信息不足";

    /**
     * 计算节点健康度。
     *
     * @param input 节点健康度输入
     * @return 节点健康度结果
     */
    public NodeHealthResult compute(NodeHealthInput input) {
        if (input == null) {
            throw new IllegalArgumentException("健康度输入不能为空");
        }
        int subtreeEnterpriseCount = input.getSubtreeEnterpriseCount();

        if (subtreeEnterpriseCount == 0) {
            ChainHealthLevel level = ChainHealthLevel.EMPTY;
            List<String> weakReasons = new ArrayList<>();
            weakReasons.add(REASON_EMPTY);
            return new NodeHealthResult(0, level, level.getLabel(), weakReasons);
        }

        int bearingScore = bearingScore(subtreeEnterpriseCount);
        int singlePointScore = singlePointScore(subtreeEnterpriseCount);
        int balanceScore = balanceScore(input.getSiblingAvgSubtreeCount(),
                subtreeEnterpriseCount, input.isAllSiblingsEmpty());
        int qualityScore = qualityScore(subtreeEnterpriseCount,
                input.getQualifiedEnterpriseCount());

        double score = bearingScore * WEIGHT_BEARING
                + singlePointScore * WEIGHT_SINGLE_POINT
                + balanceScore * WEIGHT_BALANCE
                + qualityScore * WEIGHT_QUALITY;
        // 取整策略：半上取整 (Math.round)，即小数部分 >= 0.5 进一，否则舍去
        int healthScore = (int) Math.round(score);
        if (healthScore > SCORE_FULL) {
            healthScore = SCORE_FULL;
        }
        if (healthScore < 1) {
            healthScore = 1;
        }

        List<String> weakReasons = new ArrayList<>();
        if (bearingScore < SCORE_FULL) {
            weakReasons.add(REASON_BEARING_LOW);
        }
        if (singlePointScore < SCORE_FULL) {
            weakReasons.add(REASON_SINGLE_POINT);
        }
        if (balanceScore < SCORE_FULL) {
            weakReasons.add(REASON_BALANCE_LOW);
        }
        if (qualityScore < SCORE_FULL) {
            weakReasons.add(REASON_QUALITY_LOW);
        }

        ChainHealthLevel level = ChainHealthLevel.of(subtreeEnterpriseCount, healthScore);
        return new NodeHealthResult(healthScore, level, level.getLabel(), weakReasons);
    }

    /**
     * 企业承载得分：0=0、1=25、2-3=50、4-7=75、>=8=100。
     */
    private int bearingScore(int subtreeEnterpriseCount) {
        if (subtreeEnterpriseCount <= 0) {
            return 0;
        }
        if (subtreeEnterpriseCount == 1) {
            return 25;
        }
        if (subtreeEnterpriseCount <= 3) {
            return 50;
        }
        if (subtreeEnterpriseCount <= 7) {
            return 75;
        }
        return 100;
    }

    /**
     * 单点依赖得分：0=0、1=20、2=50、3-4=75、>=5=100。
     */
    private int singlePointScore(int subtreeEnterpriseCount) {
        if (subtreeEnterpriseCount <= 0) {
            return 0;
        }
        if (subtreeEnterpriseCount == 1) {
            return 20;
        }
        if (subtreeEnterpriseCount == 2) {
            return 50;
        }
        if (subtreeEnterpriseCount <= 4) {
            return 75;
        }
        return 100;
    }

    /**
     * 同级均衡得分：当前子树数 / 同父平均子树数；
     * >=1=100、0.7-1=80、0.4-0.7=55、0-0.4=25，同级全空时给 50。
     */
    private int balanceScore(double siblingAvgSubtreeCount,
                             int subtreeEnterpriseCount,
                             boolean allSiblingsEmpty) {
        if (allSiblingsEmpty) {
            return SIBLING_ALL_EMPTY_SCORE;
        }
        if (siblingAvgSubtreeCount <= 0) {
            return SIBLING_ALL_EMPTY_SCORE;
        }
        double ratio = subtreeEnterpriseCount / siblingAvgSubtreeCount;
        if (ratio >= 1.0) {
            return 100;
        }
        if (ratio >= 0.7) {
            return 80;
        }
        if (ratio >= 0.4) {
            return 55;
        }
        return 25;
    }

    /**
     * 企业质量得分：子树企业画像完整性综合；无企业=0、全缺失=40、满分=100。
     *
     * <p>每家企业的画像完整性 = 5 项指标非空比例 * 100；
     * 子树质量得分 = 所有企业完整性的平均值。无企业时返回 0。</p>
     */
    private int qualityScore(int subtreeEnterpriseCount, int qualifiedEnterpriseCount) {
        if (subtreeEnterpriseCount <= 0) {
            return QUALITY_EMPTY;
        }
        if (qualifiedEnterpriseCount <= 0) {
            return QUALITY_FLOOR;
        }
        // qualifiedEnterpriseCount 为子树企业画像完整性的累计满分比例
        // （每家最多 5 项，每项 1），归一化到 0-100 后取均值
        double fullItems = subtreeEnterpriseCount * 5.0;
        double ratio = qualifiedEnterpriseCount / fullItems;
        int score = (int) Math.round(ratio * QUALITY_FULL);
        if (score < QUALITY_FLOOR) {
            score = QUALITY_FLOOR;
        }
        return score;
    }

    /**
     * 健康度计算输入。
     */
    @Data
    @AllArgsConstructor
    public static class NodeHealthInput {

        /** 子树有效企业数 */
        private int subtreeEnterpriseCount;

        /** 同父节点子树企业数的平均值（不含自身则为 0） */
        private double siblingAvgSubtreeCount;

        /** 同级是否全部为空（无任何挂链企业） */
        private boolean allSiblingsEmpty;

        /**
         * 子树企业画像完整性累计项数。
         * <p>每家企业最多计 5 项（产值、税收、标签、主营产品、核心技术），
         * 全部满足则子树质量满分。无企业或全缺失传 0。</p>
         */
        private int qualifiedEnterpriseCount;
    }

    /**
     * 健康度计算结果。
     */
    @Data
    @AllArgsConstructor
    public static class NodeHealthResult {

        /** 健康分（0-100） */
        private int healthScore;

        /** 健康等级 */
        private ChainHealthLevel healthLevel;

        /** 中文标签 */
        private String healthLabel;

        /** 弱项原因列表 */
        private List<String> weakReasons;
    }
}
