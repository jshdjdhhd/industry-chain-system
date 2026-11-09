package git.ggwzz.enterprise.service;

import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;

import java.util.Collection;
import java.util.Map;

/**
 * 企业查询服务接口。
 *
 * <p>为智能挂链推荐提供企业数据查询能力。</p>
 */
public interface EnterpriseQueryService {

    /**
     * 根据ID查询企业信息。
     *
     * @param id 企业ID
     * @return 企业信息，不存在时返回 null
     */
    RuleBasedMatchingProvider.EnterpriseInfo getById(Long id);

    /**
     * 根据 ID 批量查询企业信息。
     *
     * @param ids 企业 ID 集合
     * @return 企业 ID 到企业信息的映射
     */
    Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> getByIds(Collection<Long> ids);
}
