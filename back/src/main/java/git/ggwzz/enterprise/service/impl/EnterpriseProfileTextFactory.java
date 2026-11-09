package git.ggwzz.enterprise.service.impl;

import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业画像相似度检索文本工厂。
 */
@Component
public class EnterpriseProfileTextFactory {

    public String buildProfileText(EnterpriseProfileAnalysisRow row, List<String> tags) {
        if (row == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("任务：企业画像相似度检索，重点关注所属行业、主营产品、核心技术、经营范围、关键词和企业标签。");
        addLine(lines, "企业名称", row.getEnterpriseName());
        addLine(lines, "所属行业", row.getIndustry());
        addLine(lines, "经营范围", row.getBusinessScope());
        addLine(lines, "主营产品", row.getMainProducts());
        addLine(lines, "核心技术", row.getCoreTech());
        addLine(lines, "关键词", row.getKeywords());
        addLine(lines, "企业标签", tags == null ? "" : String.join("，", tags));
        return String.join("\n", lines);
    }

    private void addLine(List<String> lines, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        lines.add(label + "：" + value.trim());
    }
}
