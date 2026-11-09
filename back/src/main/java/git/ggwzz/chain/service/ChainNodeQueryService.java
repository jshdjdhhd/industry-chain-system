package git.ggwzz.chain.service;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 产业链节点查询服务接口。
 *
 * <p>为智能挂链推荐提供节点数据查询能力。</p>
 */
public interface ChainNodeQueryService {

    /**
     * 查询产业链下的所有节点。
     *
     * @param chainId 产业链ID
     * @return 节点信息列表
     */
    List<ChainNodeCandidateDTO> listByChainId(Long chainId);

    /**
     * 构建节点完整路径。
     *
     * @param nodeId 节点 ID
     * @return 产业链/节点层级路径，不存在时返回 null
     */
    String buildNodePath(Long nodeId);

    /**
     * 批量构建节点完整路径。
     *
     * @param nodeIds 节点 ID 集合
     * @return 节点 ID 到产业链/节点层级路径的映射
     */
    Map<Long, String> buildNodePathMap(Collection<Long> nodeIds);
}
