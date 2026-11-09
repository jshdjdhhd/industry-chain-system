package git.ggwzz.chain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.vo.BatchLinkEnterpriseVO;
import git.ggwzz.chain.vo.NodeEnterpriseVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 企业-节点关系服务接口。
 *
 * <p>提供 AI 推荐挂链、人工确认挂链、解绑和查询能力，
 * 供产业链页面和智能推荐共用。</p>
 */
public interface EnterpriseNodeRelService extends IService<EnterpriseNodeRel> {

    /**
     * AI推荐挂链。
     *
     * @param enterpriseId 企业ID
     * @param nodeId       节点ID
     * @param confidence   置信度（0~1）
     * @param reason       推荐理由
     * @param operator     操作人ID
     */
    void linkByAi(Long enterpriseId, Long nodeId, BigDecimal confidence, String reason, Long operator);

    /**
     * 人工确认挂链。
     *
     * @param enterpriseId 企业ID
     * @param nodeId       节点ID
     * @param reason       挂链理由
     * @param operator     操作人ID
     */
    void linkManually(Long enterpriseId, Long nodeId, String reason, Long operator);

    /**
     * 批量人工挂链。
     *
     * @param nodeId        节点ID
     * @param enterpriseIds 企业ID列表
     * @param operator      操作人ID
     * @return 批量处理结果
     */
    BatchLinkEnterpriseVO linkManuallyBatch(Long nodeId, List<Long> enterpriseIds, Long operator);

    /**
     * 查询指定节点下的未删除企业挂接关系。
     *
     * @param nodeId 节点 ID
     * @return 包含企业名称的节点企业列表
     */
    List<NodeEnterpriseVO> listByNodeId(Long nodeId);

    /**
     * 解绑（逻辑删除）。
     *
     * @param nodeId       节点ID
     * @param enterpriseId 企业ID
     * @param operator     操作人ID
     */
    void unlink(Long nodeId, Long enterpriseId, Long operator);

    /**
     * 检查企业是否已挂链到指定节点。
     *
     * @param enterpriseId 企业ID
     * @param nodeId       节点ID
     * @return true-已挂链，false-未挂链
     */
    boolean isLinked(Long enterpriseId, Long nodeId);

    /**
     * 查询企业在指定产业链下已挂接的有效节点。
     *
     * @param enterpriseId 企业 ID
     * @param chainId      产业链 ID
     * @return 已挂接节点 ID 集合
     */
    Set<Long> listLinkedNodeIdsByEnterpriseAndChain(Long enterpriseId, Long chainId);
}
