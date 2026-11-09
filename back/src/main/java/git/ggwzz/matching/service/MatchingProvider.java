package git.ggwzz.matching.service;

import git.ggwzz.matching.entity.MatchingResult;

import java.util.List;

/**
 * 推荐提供者接口。
 *
 * <p>定义智能挂链推荐算法的统一抽象，支持多种推荐策略（规则匹配、AI模型等）。
 * 实现类负责分析企业信息并生成候选推荐结果，结果未持久化。</p>
 */
public interface MatchingProvider {

    /**
     * 推荐模型名称。
     *
     * @return 模型名称
     */
    default String getModelName() {
        return "RULE_BASED";
    }

    /**
     * 执行推荐。
     *
     * @param enterpriseId 企业ID
     * @param chainId      产业链ID
     * @return 推荐结果列表（未持久化，按置信度降序排列）
     */
    List<MatchingResult> match(Long enterpriseId, Long chainId);
}
