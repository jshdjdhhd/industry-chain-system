package git.ggwzz.enterprise.service;

import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.service.impl.EnterpriseRadarMetricCalculator;
import git.ggwzz.enterprise.vo.EnterpriseRadarMetricVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnterpriseRadarMetricCalculatorTest {

    private final EnterpriseRadarMetricCalculator calculator = new EnterpriseRadarMetricCalculator();

    @Test
    void calculateShouldReturnSixMetricsInFixedOrder() {
        EnterpriseProfileAnalysisRow row = fullRow();
        EnterpriseLinkStatRow linkStat = linkStat(2, "0.8000", true);

        EnterpriseRadarVO radar = calculator.calculate(row, List.of("智能制造", "新能源"), linkStat);

        assertEquals(List.of(
                "patentCount",
                "bidFrequency",
                "productTechRichness",
                "businessScopeBreadth",
                "chainLinkage",
                "businessScale"
        ), radar.getMetrics().stream().map(EnterpriseRadarMetricVO::getKey).toList());
    }

    @Test
    void emptyFieldsShouldScoreZero() {
        EnterpriseRadarVO radar = calculator.calculate(new EnterpriseProfileAnalysisRow(), List.of(), null);
        Map<String, EnterpriseRadarMetricVO> metrics = byKey(radar);

        assertEquals(0, metrics.get("patentCount").getValue());
        assertEquals(0, metrics.get("bidFrequency").getValue());
        assertEquals(0, metrics.get("productTechRichness").getValue());
        assertEquals(0, metrics.get("businessScopeBreadth").getValue());
        assertEquals(0, metrics.get("chainLinkage").getValue());
        assertEquals(0, metrics.get("businessScale").getValue());
    }

    @Test
    void countMetricsShouldCapAtOneHundred() {
        EnterpriseProfileAnalysisRow row = new EnterpriseProfileAnalysisRow();
        row.setPatentInfo("p1\np2\np3\np4\np5\np6");
        row.setBidInfo("b1；b2；b3；b4；b5；b6");

        Map<String, EnterpriseRadarMetricVO> metrics = byKey(calculator.calculate(row, List.of(), null));

        assertEquals(100, metrics.get("patentCount").getValue());
        assertEquals(100, metrics.get("bidFrequency").getValue());
    }

    @Test
    void productTechRichnessShouldDeduplicateKeywordsAndTags() {
        EnterpriseProfileAnalysisRow row = new EnterpriseProfileAnalysisRow();
        row.setMainProducts("电池,电芯,电池");
        row.setCoreTech("锂离子电池；电芯");
        row.setKeywords("储能、电池");

        Map<String, EnterpriseRadarMetricVO> metrics = byKey(
                calculator.calculate(row, List.of("储能", "智能制造"), null));

        assertEquals(50, metrics.get("productTechRichness").getValue());
        assertEquals("5 个关键词", metrics.get("productTechRichness").getRawValue());
    }

    @Test
    void chainLinkageShouldUseNodeCountConfidenceAndManualBonus() {
        EnterpriseLinkStatRow linkStat = linkStat(4, "0.9000", true);

        Map<String, EnterpriseRadarMetricVO> metrics = byKey(
                calculator.calculate(new EnterpriseProfileAnalysisRow(), List.of(), linkStat));

        assertEquals(97, metrics.get("chainLinkage").getValue());
    }

    @Test
    void businessScaleShouldUseOutputValueAndTaxAmountThresholds() {
        EnterpriseProfileAnalysisRow row = new EnterpriseProfileAnalysisRow();
        row.setOutputValue(new BigDecimal("1000.00"));
        row.setTaxAmount(new BigDecimal("100.00"));

        Map<String, EnterpriseRadarMetricVO> metrics = byKey(calculator.calculate(row, List.of(), null));

        assertEquals(100, metrics.get("businessScale").getValue());
        assertEquals("产值 1000.00 万元，税收 100.00 万元", metrics.get("businessScale").getRawValue());
    }

    private EnterpriseProfileAnalysisRow fullRow() {
        EnterpriseProfileAnalysisRow row = new EnterpriseProfileAnalysisRow();
        row.setPatentInfo("专利1\n专利2");
        row.setBidInfo("招标1；招标2");
        row.setMainProducts("动力电池,电芯");
        row.setCoreTech("锂离子电池");
        row.setKeywords("储能");
        row.setBusinessScope("动力电池研发、生产、销售；储能系统研发、销售。新能源汽车零部件销售。");
        row.setOutputValue(new BigDecimal("1200"));
        row.setTaxAmount(new BigDecimal("80"));
        return row;
    }

    private EnterpriseLinkStatRow linkStat(int linkedNodeCount, String maxConfidence, boolean hasManualLink) {
        EnterpriseLinkStatRow row = new EnterpriseLinkStatRow();
        row.setLinkedNodeCount(linkedNodeCount);
        row.setMaxConfidence(new BigDecimal(maxConfidence));
        row.setHasManualLink(hasManualLink);
        return row;
    }

    private Map<String, EnterpriseRadarMetricVO> byKey(EnterpriseRadarVO radar) {
        return radar.getMetrics().stream()
                .collect(Collectors.toMap(EnterpriseRadarMetricVO::getKey, metric -> metric));
    }
}
