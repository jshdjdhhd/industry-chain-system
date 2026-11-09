package git.ggwzz.matching.embedding;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 构建 embedding 输入文本。
 */
@Component
public class EmbeddingProfileTextBuilder {

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_SCOPE_LENGTH = 320;
    private static final int MAX_PATH_LENGTH = 260;
    private static final int MAX_KEYWORD_COUNT = 24;

    private static final String ENTERPRISE_RETRIEVAL_INSTRUCTION =
            "任务：产业链挂链语义检索，判断企业最适合归属的产业链节点，重点关注主营产品、核心技术、经营范围和上下游环节。";
    private static final String NODE_RETRIEVAL_INSTRUCTION =
            "任务：候选产业链节点语义文档，描述该节点覆盖的产品、技术、业务环节和上下游位置。";

    private final DomainTermExpander domainTermExpander;

    public EmbeddingProfileTextBuilder() {
        this(new LocalDomainTermExpander());
    }

    @Autowired
    public EmbeddingProfileTextBuilder(DomainTermExpander domainTermExpander) {
        this.domainTermExpander = domainTermExpander;
    }

    public String buildEnterpriseProfile(RuleBasedMatchingProvider.EnterpriseInfo enterprise) {
        if (enterprise == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add(ENTERPRISE_RETRIEVAL_INSTRUCTION);
        addLine(lines, "字段权重提示", "主营产品和核心技术优先，经营范围作为辅助语义。");
        addLine(lines, "企业名称", normalizeText(enterprise.getName(), MAX_NAME_LENGTH));
        addLine(lines, "经营范围", normalizeText(enterprise.getBusinessScope(), MAX_SCOPE_LENGTH));
        addKeywordLine(lines, "主营产品与技术关键词", enterprise.getMainProducts());
        addKeywordLine(lines, "企业标签", enterprise.getTags());
        addLine(lines, "领域扩展词", domainTermExpander.expand(String.join("\n", lines)));
        return String.join("\n", lines);
    }

    public String buildNodeProfile(ChainNodeCandidateDTO node) {
        if (node == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add(NODE_RETRIEVAL_INSTRUCTION);
        addLine(lines, "字段权重提示", "节点路径和节点关键词优先，节点层级用于辅助区分上下游位置。");
        addLine(lines, "节点路径", normalizeText(node.getNodePath(), MAX_PATH_LENGTH));
        addLine(lines, "节点名称", normalizeText(node.getNodeName(), MAX_NAME_LENGTH));
        addKeywordLine(lines, "节点关键词", node.getKeywords());
        if (node.getLevel() != null) {
            addLine(lines, "节点层级", String.valueOf(node.getLevel()));
        }
        addLine(lines, "领域扩展词", domainTermExpander.expand(String.join("\n", lines)));
        return String.join("\n", lines);
    }

    private void addLine(List<String> lines, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        lines.add(label + "：" + value.trim());
    }

    private void addKeywordLine(List<String> lines, String label, String value) {
        addLine(lines, label, normalizeKeywordList(value));
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String normalizeKeywordList(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.split("[,，、;；/\\s]+");
        Set<String> keywords = new LinkedHashSet<>();
        for (String part : parts) {
            String keyword = normalizeText(part, MAX_NAME_LENGTH);
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
            if (keywords.size() >= MAX_KEYWORD_COUNT) {
                break;
            }
        }
        return String.join("，", keywords);
    }
}
