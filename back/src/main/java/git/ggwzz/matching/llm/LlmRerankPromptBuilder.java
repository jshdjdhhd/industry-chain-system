package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.entity.MatchingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 构建智能挂链 V3 大模型重排序 prompt。
 */
@Component
@RequiredArgsConstructor
public class LlmRerankPromptBuilder {

    public static final String PROMPT_VERSION = "matching-v3-project-adapted-rerank-2026-06-30";
    public static final String PROMPT_PROFILE = "PROJECT_ADAPTED_RUBRIC_FEWSHOT";

    private static final String SYSTEM_PROMPT = """
            你是产业链智能挂链专家。只基于候选节点判断企业最适合的位置，重点关注主营产品、核心技术、节点路径和产业链上下游环节。
            置信度必须校准：证据明确且环节一致给高分(0.85+)，泛化、仅名称相似或环节不一致降分(<0.6)。只输出 JSON：
            {"ranked_results":[{"node_id":数字,"confidence":0-0.99,"reason":"一句话理由","risk":"风险或无"}]}
            """;

    private final ObjectMapper objectMapper;

    public List<LlmMessage> build(List<MatchingResult> candidates, int resultTopK) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt_version", PROMPT_VERSION);
        payload.put("prompt_profile", PROMPT_PROFILE);
        payload.put("task", "产业链节点重排序");
        payload.put("result_top_k", resultTopK);
        payload.put("enterprise_profile", firstEnterpriseProfile(candidates));
        payload.set("project_context", projectContext());
        payload.set("scoring_rubric", scoringRubric());
        payload.set("confidence_calibration", confidenceCalibration());
        payload.set("few_shot_examples", fewShotExamples());
        payload.set("domain_terms", domainTerms(candidates));

        ArrayNode candidateArray = payload.putArray("candidates");
        int rank = 1;
        for (MatchingResult candidate : candidates) {
            JsonNode evidence = parseEvidence(candidate.getEvidence());
            ObjectNode item = candidateArray.addObject();
            item.put("v21_rank", rank++);
            item.put("node_id", candidate.getNodeId());
            item.put("v21_confidence", decimalToDouble(candidate.getConfidence()));
            item.put("v21_reason", safe(candidate.getReason()));
            item.put("vector_similarity", evidence.path("vector_similarity").asDouble(0.0D));
            item.put("rule_confidence", evidence.path("rule_confidence").asDouble(0.0D));
            item.put("final_score", evidence.path("final_score").asDouble(decimalToDouble(candidate.getConfidence())));
            item.put("node_profile", evidence.path("node_profile").asText(""));
            if (evidence.has("rule_evidence")) {
                item.set("rule_evidence", evidence.get("rule_evidence"));
            }
        }

        return List.of(
                new LlmMessage("system", SYSTEM_PROMPT),
                new LlmMessage("user", payload.toString()));
    }

    private ObjectNode projectContext() {
        ObjectNode context = objectMapper.createObjectNode();
        context.put("system_name", "产业链智能挂链");
        context.put("objective", "将企业挂接到最合适的产业链节点，优先保证产业环节正确、主营产品匹配和推荐理由可解释。");
        context.put("data_boundary", "只能使用 enterprise_profile、candidates、V2.1 分数和领域词提示，不得扩展候选列表之外的节点。");
        return context;
    }

    private ArrayNode scoringRubric() {
        ArrayNode rubric = objectMapper.createArrayNode();
        rubric.add("主营产品与核心技术匹配：企业主营产品、核心技术、经营范围是否直接覆盖候选节点。");
        rubric.add("产业链上下游环节：节点路径中的上游、中游、下游位置是否符合企业业务定位。");
        rubric.add("节点关键词与领域扩展词：节点关键词、领域词、企业标签是否互相支持。");
        rubric.add("V2.1 分数一致性：vector_similarity 与 rule_confidence 是否共同支持该节点。");
        rubric.add("误挂风险：若只是企业名称、泛化经营范围或跨行业语义相似，不应给高置信度。");
        return rubric;
    }

    private ObjectNode confidenceCalibration() {
        ObjectNode calibration = objectMapper.createObjectNode();
        calibration.put("high", "0.85-0.99：主营产品或核心技术与节点直接匹配，产业链环节一致，V2.1 分数也支持。");
        calibration.put("medium", "0.65-0.84：语义相关且有部分业务证据，但主营产品、环节或规则证据存在不完整。");
        calibration.put("low", "0.00-0.60：仅泛化相关、跨产业链相似、节点路径不一致或证据不足。");
        return calibration;
    }

    private ArrayNode fewShotExamples() {
        ArrayNode examples = objectMapper.createArrayNode();
        examples.add(example(
                "中际旭创股份有限公司",
                "高速光模块、数据中心光模块、硅光模块",
                "光通信产业链 / 中游 / 光模块",
                "主营产品直接落在光模块制造和光通信器件环节，应优先于泛化的信息通信节点。"));
        examples.add(example(
                "宁德时代新能源科技股份有限公司",
                "动力电池、电芯、电池系统",
                "新能源汽车产业链 / 中游 / 动力电池",
                "主营产品和核心技术直接对应动力电池与电芯环节。"));
        examples.add(example(
                "苹果公司",
                "智能手机、消费电子终端、软硬件生态",
                "消费电子产业链 / 下游 / 智能终端整机",
                "品牌和产品应落在终端整机或消费电子应用环节，不应误挂到上游零部件。"));
        return examples;
    }

    private ObjectNode example(String enterprise, String products, String expectedNode, String reason) {
        ObjectNode example = objectMapper.createObjectNode();
        example.put("enterprise", enterprise);
        example.put("products", products);
        example.put("expected_node_path", expectedNode);
        example.put("reason", reason);
        return example;
    }

    private ArrayNode domainTerms(List<MatchingResult> candidates) {
        Set<String> terms = new LinkedHashSet<>();
        if (!candidates.isEmpty()) {
            addDomainTerms(terms, firstEnterpriseProfile(candidates));
        }
        int candidateLimit = Math.min(3, candidates.size());
        for (int i = 0; i < candidateLimit; i++) {
            JsonNode evidence = parseEvidence(candidates.get(i).getEvidence());
            addDomainTerms(terms, evidence.path("node_profile").asText(""));
        }
        ArrayNode array = objectMapper.createArrayNode();
        terms.stream()
                .filter(term -> term.length() >= 2)
                .limit(32)
                .forEach(array::add);
        return array;
    }

    private void addDomainTerms(Set<String> terms, String profile) {
        if (profile == null || profile.isBlank()) {
            return;
        }
        String[] lines = profile.split("\\R");
        for (String line : lines) {
            if (!line.contains("领域扩展词")) {
                continue;
            }
            int separatorIndex = Math.max(line.indexOf('：'), line.indexOf(':'));
            String termPart = separatorIndex >= 0 ? line.substring(separatorIndex + 1) : line;
            for (String part : termPart.split("[,，;；、\\s]+")) {
                String term = part.trim();
                if (!term.isBlank()) {
                    terms.add(term);
                }
            }
        }
    }

    private String firstEnterpriseProfile(List<MatchingResult> candidates) {
        for (MatchingResult candidate : candidates) {
            String profile = parseEvidence(candidate.getEvidence()).path("enterprise_profile").asText("");
            if (!profile.isBlank()) {
                return profile;
            }
        }
        return "";
    }

    private JsonNode parseEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(evidence);
            return node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private double decimalToDouble(BigDecimal value) {
        return value == null ? 0.0D : value.doubleValue();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
