package git.ggwzz.enterprise.service.impl;

import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.vo.EnterpriseRadarMetricVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 企业六维雷达指标计算器。
 */
@Component
public class EnterpriseRadarMetricCalculator {

    private static final Pattern SCOPE_SEGMENT_SPLITTER = Pattern.compile("[。；;，,、\\n]+");
    private static final BigDecimal OUTPUT_HIGH_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal TAX_HIGH_THRESHOLD = new BigDecimal("100");

    public EnterpriseRadarVO calculate(EnterpriseProfileAnalysisRow row,
                                       List<String> tagTexts,
                                       EnterpriseLinkStatRow linkStat) {
        int patentCount = countEntries(row == null ? null : row.getPatentInfo());
        int bidCount = countEntries(row == null ? null : row.getBidInfo());
        int keywordCount = countKeywords(row, tagTexts);
        int businessScopeScore = scoreBusinessScope(row == null ? null : row.getBusinessScope());
        String businessScopeRaw = businessScopeRaw(row == null ? null : row.getBusinessScope());
        int linkageScore = scoreLinkage(linkStat);
        String linkageRaw = linkageRaw(linkStat);
        int scaleScore = scoreScale(row);
        String scaleRaw = scaleRaw(row);

        EnterpriseRadarVO radar = new EnterpriseRadarVO();
        radar.setMetrics(List.of(
                metric("patentCount", "专利数量", scoreByCount(patentCount, 5), patentCount + " 条",
                        "根据企业专利信息条目数计算，5 条及以上记满分"),
                metric("bidFrequency", "招投标频次", scoreByCount(bidCount, 5), bidCount + " 条",
                        "根据企业招投标信息条目数计算，5 条及以上记满分"),
                metric("productTechRichness", "产品/技术丰富度", Math.min(100, keywordCount * 10),
                        keywordCount + " 个关键词", "根据主营产品、核心技术、关键词和标签去重后计算，10 个及以上记满分"),
                metric("businessScopeBreadth", "经营范围广度", businessScopeScore, businessScopeRaw,
                        "根据经营范围文本长度和分句数量综合计分"),
                metric("chainLinkage", "产业链关联度", linkageScore, linkageRaw,
                        "根据有效挂接节点数、挂接来源和最高置信度计算"),
                metric("businessScale", "经营规模", scaleScore, scaleRaw,
                        "根据产值和税收字段是否存在及数值规模计算")
        ));
        return radar;
    }

    private EnterpriseRadarMetricVO metric(String key, String name, int value, String rawValue, String description) {
        EnterpriseRadarMetricVO metric = new EnterpriseRadarMetricVO();
        metric.setKey(key);
        metric.setName(name);
        metric.setValue(value);
        metric.setRawValue(rawValue);
        metric.setDescription(description);
        return metric;
    }

    private int scoreByCount(int count, int fullCount) {
        if (fullCount <= 0) {
            return 0;
        }
        return Math.min(100, count * (100 / fullCount));
    }

    private int countEntries(String text) {
        if (isBlank(text)) {
            return 0;
        }
        int count = 0;
        for (String item : text.split("[\\r\\n;；]+")) {
            if (!item.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private int countKeywords(EnterpriseProfileAnalysisRow row, List<String> tagTexts) {
        Set<String> keywords = new LinkedHashSet<>();
        if (row != null) {
            collectKeywords(keywords, row.getMainProducts());
            collectKeywords(keywords, row.getCoreTech());
            collectKeywords(keywords, row.getKeywords());
        }
        if (tagTexts != null) {
            tagTexts.forEach(tag -> collectKeywords(keywords, tag));
        }
        return keywords.size();
    }

    private void collectKeywords(Set<String> keywords, String text) {
        if (isBlank(text)) {
            return;
        }
        for (String item : text.split("[\\s,，;；、/|]+")) {
            String keyword = item.trim();
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
    }

    private int scoreBusinessScope(String businessScope) {
        if (isBlank(businessScope)) {
            return 0;
        }
        String trimmed = businessScope.trim();
        int lengthScore = Math.min(60, trimmed.length() / 10);
        int segmentScore = Math.min(40, countSegments(trimmed) * 8);
        return Math.min(100, lengthScore + segmentScore);
    }

    private String businessScopeRaw(String businessScope) {
        if (isBlank(businessScope)) {
            return "未填写经营范围";
        }
        return businessScope.trim().length() + " 字，" + countSegments(businessScope) + " 个分句";
    }

    private int countSegments(String text) {
        if (isBlank(text)) {
            return 0;
        }
        int count = 0;
        for (String segment : SCOPE_SEGMENT_SPLITTER.split(text.trim())) {
            if (!segment.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private int scoreLinkage(EnterpriseLinkStatRow linkStat) {
        if (linkStat == null) {
            return 0;
        }
        int linkedNodeCount = linkStat.getLinkedNodeCount() == null ? 0 : linkStat.getLinkedNodeCount();
        int nodeScore = Math.min(60, linkedNodeCount * 20);
        int confidenceScore = linkStat.getMaxConfidence() == null
                ? 0
                : linkStat.getMaxConfidence().multiply(new BigDecimal("30"))
                        .setScale(0, RoundingMode.DOWN).intValue();
        int manualScore = Boolean.TRUE.equals(linkStat.getHasManualLink()) ? 10 : 0;
        return Math.min(100, nodeScore + confidenceScore + manualScore);
    }

    private String linkageRaw(EnterpriseLinkStatRow linkStat) {
        if (linkStat == null || linkStat.getLinkedNodeCount() == null || linkStat.getLinkedNodeCount() == 0) {
            return "暂无挂接节点";
        }
        List<String> parts = new ArrayList<>();
        parts.add(linkStat.getLinkedNodeCount() + " 个挂接节点");
        if (linkStat.getMaxConfidence() != null) {
            parts.add("最高置信度 " + linkStat.getMaxConfidence());
        }
        if (Boolean.TRUE.equals(linkStat.getHasManualLink())) {
            parts.add("含人工挂链");
        }
        return String.join("，", parts);
    }

    private int scoreScale(EnterpriseProfileAnalysisRow row) {
        if (row == null) {
            return 0;
        }
        int score = 0;
        if (row.getOutputValue() != null) {
            score += 30;
            if (row.getOutputValue().compareTo(OUTPUT_HIGH_THRESHOLD) >= 0) {
                score += 20;
            }
        }
        if (row.getTaxAmount() != null) {
            score += 30;
            if (row.getTaxAmount().compareTo(TAX_HIGH_THRESHOLD) >= 0) {
                score += 20;
            }
        }
        return Math.min(100, score);
    }

    private String scaleRaw(EnterpriseProfileAnalysisRow row) {
        if (row == null || row.getOutputValue() == null && row.getTaxAmount() == null) {
            return "未填写产值/税收";
        }
        List<String> parts = new ArrayList<>();
        if (row.getOutputValue() != null) {
            parts.add("产值 " + row.getOutputValue() + " 万元");
        }
        if (row.getTaxAmount() != null) {
            parts.add("税收 " + row.getTaxAmount() + " 万元");
        }
        return String.join("，", parts);
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
