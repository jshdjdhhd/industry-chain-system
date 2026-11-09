package git.ggwzz.analysis.enums;

import lombok.Getter;

/**
 * 产业链节点健康度等级。
 *
 * <p>等级按健康分划分：EMPTY（无企业）、HEALTHY（>=80）、
 * NORMAL（>=60）、WEAK（>=30）、RISK（>=1）。</p>
 */
@Getter
public enum ChainHealthLevel {

    /** 子树无已挂载企业 */
    EMPTY("空白"),
    /** 健康：健康分 >= 80 */
    HEALTHY("健康"),
    /** 一般：健康分 >= 60 */
    NORMAL("一般"),
    /** 偏弱：健康分 >= 30 */
    WEAK("偏弱"),
    /** 风险：健康分 >= 1 */
    RISK("风险");

    /** 中文标签 */
    private final String label;

    ChainHealthLevel(String label) {
        this.label = label;
    }

    /**
     * 根据子树企业数与健康分解析等级。
     *
     * @param subtreeEnterpriseCount 子树有效企业数
     * @param healthScore            健康分（0-100）
     * @return 健康度等级
     */
    public static ChainHealthLevel of(int subtreeEnterpriseCount, int healthScore) {
        if (subtreeEnterpriseCount == 0) {
            return EMPTY;
        }
        if (healthScore >= 80) {
            return HEALTHY;
        }
        if (healthScore >= 60) {
            return NORMAL;
        }
        if (healthScore >= 30) {
            return WEAK;
        }
        return RISK;
    }
}
