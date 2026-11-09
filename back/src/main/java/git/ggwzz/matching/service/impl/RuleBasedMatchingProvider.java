package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.service.MatchingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于规则的推荐提供者实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedMatchingProvider implements MatchingProvider {

    private static final int MAX_RESULT_SIZE = 5;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EnterpriseQueryService enterpriseQueryService;
    private final ChainNodeQueryService chainNodeQueryService;

    @Override
    public List<MatchingResult> match(Long enterpriseId, Long chainId) {
        EnterpriseInfo enterprise = enterpriseQueryService.getById(enterpriseId);
        if (enterprise == null) {
            log.warn("enterprise not found: enterpriseId={}", enterpriseId);
            return List.of();
        }

        List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(chainId);
        if (nodes == null || nodes.isEmpty()) {
            log.warn("chain nodes not found: chainId={}", chainId);
            return List.of();
        }

        return match(enterprise, nodes);
    }

    /**
     * 使用已加载的企业画像和候选节点做规则匹配。
     * 批量挂链会复用这个入口，避免每个任务重复查企业和节点。
     */
    public List<MatchingResult> match(EnterpriseInfo enterprise, List<ChainNodeCandidateDTO> nodes) {
        if (enterprise == null || nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        Set<String> enterpriseKeywords = extractEnterpriseKeywords(enterprise);
        List<MatchingResult> results = new ArrayList<>();
        for (ChainNodeCandidateDTO node : nodes) {
            NodeScore score = scoreNode(node, enterprise, enterpriseKeywords);
            if (score.totalScore <= 0) {
                continue;
            }

            MatchingResult result = new MatchingResult();
            result.setNodeId(node.getNodeId());
            result.setConfidence(calculateConfidence(score.totalScore));
            result.setReason(buildReason(score));
            result.setEvidence(buildEvidence(score));
            results.add(result);
        }

        return results.stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence).reversed())
                .limit(MAX_RESULT_SIZE)
                .collect(Collectors.toList());
    }

    private Set<String> extractEnterpriseKeywords(EnterpriseInfo enterprise) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(splitKeywords(enterprise.getName()));
        keywords.addAll(splitKeywords(enterprise.getBusinessScope()));
        keywords.addAll(splitKeywords(enterprise.getMainProducts()));
        keywords.addAll(splitKeywords(enterprise.getTags()));
        return keywords;
    }

    private NodeScore scoreNode(ChainNodeCandidateDTO node,
                                EnterpriseInfo enterprise,
                                Set<String> enterpriseKeywords) {
        NodeScore score = new NodeScore();
        Set<String> nodeKeywords = extractNodeKeywords(node);

        score.nameHits = countHits(splitKeywords(enterprise.getName()), nodeKeywords);
        score.totalScore += score.nameHits * 3;

        score.productHits = countHits(splitKeywords(enterprise.getMainProducts()), nodeKeywords);
        score.totalScore += score.productHits * 4;

        score.scopeHits = countHits(splitKeywords(enterprise.getBusinessScope()), nodeKeywords);
        score.totalScore += score.scopeHits * 2;

        score.tagHits = countHits(splitKeywords(enterprise.getTags()), nodeKeywords);
        score.totalScore += score.tagHits * 2;

        for (String keyword : enterpriseKeywords) {
            if (nodeKeywords.stream().anyMatch(nodeKeyword -> isKeywordHit(keyword, nodeKeyword))) {
                score.matchedKeywords.add(keyword);
            }
        }
        return score;
    }

    private Set<String> extractNodeKeywords(ChainNodeCandidateDTO node) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(splitKeywords(node.getNodeName()));
        keywords.addAll(splitKeywords(node.getKeywords()));
        keywords.addAll(splitKeywords(node.getNodePath()));
        return keywords;
    }

    private Set<String> splitKeywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return keywords;
        }
        String[] parts = text.split("[,，、;；/\\s]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return keywords;
    }

    private int countHits(Set<String> source, Set<String> target) {
        int count = 0;
        for (String keyword : source) {
            if (target.stream().anyMatch(targetKeyword -> isKeywordHit(keyword, targetKeyword))) {
                count++;
            }
        }
        return count;
    }

    private boolean isKeywordHit(String sourceKeyword, String targetKeyword) {
        if (sourceKeyword == null || targetKeyword == null) {
            return false;
        }
        String source = sourceKeyword.trim().toLowerCase(Locale.ROOT);
        String target = targetKeyword.trim().toLowerCase(Locale.ROOT);
        if (source.isEmpty() || target.isEmpty()) {
            return false;
        }
        return source.equals(target)
                || source.length() >= 2 && target.contains(source)
                || target.length() >= 2 && source.contains(target);
    }

    private BigDecimal calculateConfidence(int score) {
        double confidence = Math.min(0.95D, score / 10.0D);
        return BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP);
    }

    private String buildReason(NodeScore score) {
        List<String> parts = new ArrayList<>();
        if (score.nameHits > 0) {
            parts.add("企业名称命中" + score.nameHits + "个关键词");
        }
        if (score.productHits > 0) {
            parts.add("主营产品命中" + score.productHits + "个关键词");
        }
        if (score.scopeHits > 0) {
            parts.add("经营范围命中" + score.scopeHits + "个关键词");
        }
        if (score.tagHits > 0) {
            parts.add("标签命中" + score.tagHits + "个关键词");
        }
        return String.join("；", parts);
    }

    private String buildEvidence(NodeScore score) {
        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("keyword_match", score.matchedKeywords);
            return OBJECT_MAPPER.writeValueAsString(evidence);
        } catch (Exception ex) {
            log.warn("build rule evidence failed", ex);
            return "{\"keyword_match\":[]}";
        }
    }

    private static class NodeScore {
        private int totalScore;
        private int nameHits;
        private int productHits;
        private int scopeHits;
        private int tagHits;
        private final List<String> matchedKeywords = new ArrayList<>();
    }

    /**
     * 智能挂链所需的企业特征。
     */
    public static class EnterpriseInfo {
        private Long id;
        private String name;
        private String businessScope;
        private String mainProducts;
        private String tags;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBusinessScope() {
            return businessScope;
        }

        public void setBusinessScope(String businessScope) {
            this.businessScope = businessScope;
        }

        public String getMainProducts() {
            return mainProducts;
        }

        public void setMainProducts(String mainProducts) {
            this.mainProducts = mainProducts;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }
    }
}
