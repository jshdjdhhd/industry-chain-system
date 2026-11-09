package git.ggwzz.analysis.service;

import git.ggwzz.analysis.enums.ChainHealthLevel;
import git.ggwzz.analysis.service.ChainHealthMetricService.NodeHealthInput;
import git.ggwzz.analysis.service.ChainHealthMetricService.NodeHealthResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChainHealthMetricService} 健康度规则计算单元测试。
 *
 * <p>覆盖场景：空节点、单企业节点、同级不均衡、健康节点，
 * 以及等级边界（80/60/30）、RISK 等级、承载/单点/均衡分段边界。</p>
 *
 * <p>健康分口径：
 * <pre>
 * 健康 = 承载*0.5 + 单点*0.25 + 均衡*0.15 + 质量*0.10
 * </pre>
 * 取整策略为半上取整（Math.round，小数部分 >= 0.5 进一）。</p>
 *
 * <p>承载分段：0=0、1=25、2-3=50、4-7=75、>=8=100。
 * 单点分段：0=0、1=20、2=50、3-4=75、>=5=100。
 * 均衡分段：ratio>=1=100、>=0.7=80、>=0.4=55、>0=25，同级全空=50。
 * 质量分段：无企业=0、有企业但画像全缺失=40、满分=100，中间值按
 * qualifiedEnterpriseCount/(count*5) 归一化后取整。</p>
 */
class ChainHealthMetricServiceTest {

    private final ChainHealthMetricService service =
            new ChainHealthMetricService();

    // ==================== 1. 空节点 ====================

    @Test
    void should_return_empty_level_when_subtree_has_no_enterprise() {
        NodeHealthInput input = new NodeHealthInput(0, 0.0, true, 0);

        NodeHealthResult result = service.compute(input);

        assertEquals(0, result.getHealthScore());
        assertEquals(ChainHealthLevel.EMPTY, result.getHealthLevel());
        assertEquals("空白", result.getHealthLabel());
        assertFalse(result.getWeakReasons().isEmpty(),
                "空节点应给出弱项原因");
        assertTrue(result.getWeakReasons()
                .contains("该节点及子树暂无已挂载企业"));
    }

    // ==================== 2. 单企业节点 ====================

    @Test
    void should_flag_single_point_risk_when_only_one_enterprise() {
        // 子树 1 家企业、同级平均 1、质量画像满分 5 项
        NodeHealthInput input = new NodeHealthInput(1, 1.0, false, 5);

        NodeHealthResult result = service.compute(input);

        // 承载 25、单点 20、均衡 100、质量 100
        // 25*0.5 + 20*0.25 + 100*0.15 + 100*0.10 = 12.5+5+15+10 = 42.5 -> 43
        assertEquals(43, result.getHealthScore());
        assertEquals(ChainHealthLevel.WEAK, result.getHealthLevel());
        assertTrue(result.getWeakReasons()
                .contains("子树企业承载偏低"));
        assertTrue(result.getWeakReasons()
                .contains("存在单点依赖风险"));
    }

    // ==================== 3. 同级不均衡 ====================

    @Test
    void should_penalize_balance_when_subtree_far_below_sibling_avg() {
        // 自身子树 1 家，同级平均 10 家（ratio=0.1，落在 0-0.4 档，均衡 25）
        NodeHealthInput input = new NodeHealthInput(1, 10.0, false, 5);

        NodeHealthResult result = service.compute(input);

        // 承载 25、单点 20、均衡 25、质量 100
        // 12.5 + 5 + 3.75 + 10 = 31.25 -> 31
        assertEquals(31, result.getHealthScore());
        assertEquals(ChainHealthLevel.WEAK, result.getHealthLevel());
        assertTrue(result.getWeakReasons()
                .contains("明显低于同级平均企业承载"));
    }

    @Test
    void should_give_balance_50_when_all_siblings_empty() {
        // 同级全空，均衡得分固定 50
        NodeHealthInput input = new NodeHealthInput(3, 0.0, true, 15);

        NodeHealthResult result = service.compute(input);

        // 承载 50、单点 75、均衡 50、质量 100
        // 25 + 18.75 + 7.5 + 10 = 61.25 -> 61
        assertEquals(61, result.getHealthScore());
        assertEquals(ChainHealthLevel.NORMAL, result.getHealthLevel());
    }

    // ==================== 4. 健康节点 ====================

    @Test
    void should_return_healthy_when_subtree_well_populated_and_balanced() {
        // 子树 10 家、同级平均 10、质量满分 50 项
        NodeHealthInput input = new NodeHealthInput(10, 10.0, false, 50);

        NodeHealthResult result = service.compute(input);

        // 承载 100、单点 100、均衡 100、质量 100 -> 100
        assertEquals(100, result.getHealthScore());
        assertEquals(ChainHealthLevel.HEALTHY, result.getHealthLevel());
        assertEquals("健康", result.getHealthLabel());
        assertTrue(result.getWeakReasons().isEmpty(),
                "健康节点不应有弱项原因");
    }

    @Test
    void should_return_healthy_when_score_exactly_80() {
        // 构造健康分恰好 80：
        //   count=4 -> 承载 75、单点 75
        //   siblingAvg=4.0 -> ratio=1.0 -> 均衡 100
        //   qualifiedEnterpriseCount=18 -> 质量 = round(18/(4*5)*100) = round(90) = 90
        //   健康 = 75*0.5 + 75*0.25 + 100*0.15 + 90*0.10
        //        = 37.5 + 18.75 + 15 + 9 = 80.25 -> 半上取整 = 80
        NodeHealthInput input = new NodeHealthInput(4, 4.0, false, 18);

        NodeHealthResult result = service.compute(input);

        assertEquals(80, result.getHealthScore());
        assertEquals(ChainHealthLevel.HEALTHY, result.getHealthLevel());
    }

    // ==================== 5. 等级边界与 RISK ====================

    @Test
    void should_return_risk_when_score_below_30() {
        // count=1 -> 承载 25、单点 20
        // siblingAvg=10 -> ratio=0.1 -> 均衡 25
        // qualifiedEnterpriseCount=0 -> 质量 40（有企业但画像全缺失）
        // 健康 = 12.5 + 5 + 3.75 + 4 = 25.25 -> 25 -> RISK
        NodeHealthInput input = new NodeHealthInput(1, 10.0, false, 0);

        NodeHealthResult result = service.compute(input);

        assertEquals(25, result.getHealthScore());
        assertEquals(ChainHealthLevel.RISK, result.getHealthLevel());
        assertEquals("风险", result.getHealthLabel());
    }

    @Test
    void should_return_weak_when_score_exactly_30() {
        // count=1 -> 承载 25、单点 20
        // siblingAvg=2.5 -> ratio=0.4 -> 均衡 55（0.4 档下界）
        // qualifiedEnterpriseCount=0 -> 质量 40
        // 健康 = 12.5 + 5 + 8.25 + 4 = 29.75 -> 半上取整 = 30 -> WEAK
        NodeHealthInput input = new NodeHealthInput(1, 2.5, false, 0);

        NodeHealthResult result = service.compute(input);

        assertEquals(30, result.getHealthScore());
        assertEquals(ChainHealthLevel.WEAK, result.getHealthLevel());
    }

    @Test
    void should_return_normal_when_score_exactly_60() {
        // count=2 -> 承载 50、单点 50
        // siblingAvg=2.0 -> ratio=1.0 -> 均衡 100
        // qualifiedEnterpriseCount=7 -> 质量 = round(7/(2*5)*100) = round(70) = 70
        // 健康 = 25 + 12.5 + 15 + 7 = 59.5 -> 半上取整 = 60 -> NORMAL
        NodeHealthInput input = new NodeHealthInput(2, 2.0, false, 7);

        NodeHealthResult result = service.compute(input);

        assertEquals(60, result.getHealthScore());
        assertEquals(ChainHealthLevel.NORMAL, result.getHealthLevel());
    }

    // ==================== 6. 承载得分分段边界 ====================

    @Test
    void should_give_bearing_75_when_count_exactly_4() {
        // count=4 落在承载 4-7 档下界，承载=75、单点=75
        // siblingAvg=4.0 -> 均衡 100；qualifiedEnterpriseCount=20 -> 质量满分 100
        // 健康 = 37.5 + 18.75 + 15 + 10 = 81.25 -> 81
        // 由健康分反推承载=75：若承载=50 则健康 = 25+18.75+15+10=68.75 -> 69，
        // 当前 81 仅与承载=75 一致，覆盖 count=4 承载边界。
        NodeHealthInput input = new NodeHealthInput(4, 4.0, false, 20);

        NodeHealthResult result = service.compute(input);

        assertEquals(81, result.getHealthScore());
    }

    @Test
    void should_give_bearing_75_when_count_exactly_7() {
        // count=7 落在承载 4-7 档上界，承载=75；单点=100（>=5）
        // siblingAvg=7.0 -> 均衡 100；qualifiedEnterpriseCount=35 -> 质量满分 100
        // 健康 = 37.5 + 25 + 15 + 10 = 87.5 -> 88
        // 由健康分反推承载=75：若承载=100 则健康 = 50+25+15+10=100，
        // 当前 88 仅与承载=75 一致，覆盖 count=7 承载边界。
        NodeHealthInput input = new NodeHealthInput(7, 7.0, false, 35);

        NodeHealthResult result = service.compute(input);

        assertEquals(88, result.getHealthScore());
    }

    // ==================== 7. 单点依赖得分分段边界 ====================

    @Test
    void should_give_single_point_75_when_count_exactly_4() {
        // count=4 落在单点 3-4 档上界，单点=75；承载=75
        // siblingAvg=4.0 -> 均衡 100；qualifiedEnterpriseCount=20 -> 质量 100
        // 健康 = 37.5 + 18.75 + 15 + 10 = 81.25 -> 81
        // 由健康分反推单点=75：若单点=100 则健康 = 37.5+25+15+10=87.5 -> 88，
        // 当前 81 仅与单点=75 一致，覆盖 count=4 单点边界。
        NodeHealthInput input = new NodeHealthInput(4, 4.0, false, 20);

        NodeHealthResult result = service.compute(input);

        assertEquals(81, result.getHealthScore());
    }

    @Test
    void should_give_single_point_100_when_count_exactly_5() {
        // count=5 跨过单点 >=5 档下界，单点=100；承载=75（4-7 档）
        // siblingAvg=5.0 -> 均衡 100；qualifiedEnterpriseCount=25 -> 质量 100
        // 健康 = 37.5 + 25 + 15 + 10 = 87.5 -> 88
        // 由健康分反推单点=100：若单点=75 则健康 = 37.5+18.75+15+10=81.25 -> 81，
        // 当前 88 仅与单点=100 一致，覆盖 count=5 单点边界。
        NodeHealthInput input = new NodeHealthInput(5, 5.0, false, 25);

        NodeHealthResult result = service.compute(input);

        assertEquals(88, result.getHealthScore());
    }

    // ==================== 企业质量得分边界 ====================

    @Test
    void should_use_quality_floor_40_when_all_profiles_missing() {
        // 子树 2 家、质量画像全缺失（qualifiedEnterpriseCount=0），质量分应取下限 40
        NodeHealthInput input = new NodeHealthInput(2, 2.0, false, 0);

        NodeHealthResult result = service.compute(input);

        // 承载 50、单点 50、均衡 100、质量 40
        // 25 + 12.5 + 15 + 4 = 56.5 -> 57
        assertEquals(57, result.getHealthScore());
        assertNotNull(result.getWeakReasons());
        assertTrue(result.getWeakReasons()
                .contains("企业产值、税收或画像信息不足"));
    }

    @Test
    void should_score_normal_when_subtree_three_enterprises() {
        // 子树 3 家、同级平均 3、质量满分 15 项
        NodeHealthInput input = new NodeHealthInput(3, 3.0, false, 15);

        NodeHealthResult result = service.compute(input);

        // 承载 50、单点 75、均衡 100、质量 100
        // 25 + 18.75 + 15 + 10 = 68.75 -> 69
        assertEquals(69, result.getHealthScore());
        assertEquals(ChainHealthLevel.NORMAL, result.getHealthLevel());
    }
}
