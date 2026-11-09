package git.ggwzz.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.mapper.projection.EnterpriseCountSummary;
import git.ggwzz.chain.mapper.projection.NodeEnterpriseCountRow;
import git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow;
import git.ggwzz.chain.vo.NodeEnterpriseVO;
import git.ggwzz.dashboard.mapper.projection.ChainEnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.EnterpriseCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 企业-节点关系 Mapper。
 */
@Mapper
public interface EnterpriseNodeRelMapper extends BaseMapper<EnterpriseNodeRel> {

    /**
     * 汇总活跃企业总数和已挂链企业数。
     *
     * @return 企业统计聚合结果
     */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM enterprise WHERE status = 'ACTIVE' AND is_deleted = 0) AS total, "
            + "COUNT(DISTINCT enterprise_id) AS linked "
            + "FROM enterprise_node_rel WHERE is_deleted = 0")
    EnterpriseCountRow selectEnterpriseStats();

    /**
     * 按产业链统计企业分布。
     *
     * @return 各产业链的企业数
     */
    List<ChainEnterpriseCountRow> selectEnterpriseCountByChain();

    /**
     * 查询未删除企业的状态。
     *
     * @param id 企业 ID
     * @return 企业状态；企业不存在或已删除时返回 null
     */
    @Select("SELECT status FROM enterprise WHERE id = #{id} AND is_deleted = 0")
    String selectEnterpriseStatusById(@Param("id") Long id);

    /**
     * 查询节点下未删除的企业挂接信息。
     *
     * @param nodeId 节点 ID
     * @return 包含真实企业名称的挂接信息列表
     */
    @Select("SELECT r.enterprise_id, e.enterprise_name, r.source, r.create_time "
            + "FROM enterprise_node_rel r "
            + "JOIN enterprise e ON e.id = r.enterprise_id "
            + "WHERE r.node_id = #{nodeId} AND r.is_deleted = 0 AND e.is_deleted = 0 "
            + "ORDER BY r.create_time DESC")
    List<NodeEnterpriseVO> selectNodeEnterpriseVosByNodeId(@Param("nodeId") Long nodeId);

    /**
     * 按节点统计产业链下直接挂接的去重有效企业数。
     *
     * @param chainId 产业链 ID
     * @return 有有效挂接企业的节点及其直接去重有效企业数
     */
    List<NodeEnterpriseCountRow> selectDirectCountsByChainId(
            @Param("chainId") Long chainId);

    /**
     * 按节点统计产业链下的子树去重有效企业数。
     *
     * @param chainId 产业链 ID
     * @return 产业链内所有有效节点及其子树去重有效企业数
     */
    List<NodeSubtreeEnterpriseCountRow> selectSubtreeCountsByChainId(
            @Param("chainId") Long chainId);

    /**
     * 统计节点直接挂接的去重有效企业数和子树去重有效企业总数。
     *
     * @param chainId        产业链 ID
     * @param nodeId         当前节点 ID
     * @param nodePathPattern 当前节点路径右模糊模式
     * @return 直接去重有效企业数和子树去重有效企业总数
     */
    EnterpriseCountSummary selectEnterpriseCountSummary(
            @Param("chainId") Long chainId,
            @Param("nodeId") Long nodeId,
            @Param("nodePathPattern") String nodePathPattern);

    /**
     * 按产业链批量逻辑删除有效挂接关系。
     *
     * @param chainId 产业链 ID
     * @return 受影响行数
     */
    int logicalDeleteByChainId(@Param("chainId") Long chainId);

    /**
     * 查询企业在指定产业链下已挂接的节点 ID。
     *
     * @param enterpriseId 企业 ID
     * @param chainId      产业链 ID
     * @return 已挂接节点 ID 列表
     */
    List<Long> selectLinkedNodeIdsByEnterpriseAndChain(
            @Param("enterpriseId") Long enterpriseId,
            @Param("chainId") Long chainId);

    /**
     * 恢复已逻辑删除的企业挂接关系，并刷新为本次人工挂接信息。
     *
     * @param relation 本次人工挂接信息
     * @return 更新行数；不存在已删除关系时返回 0
     */
    @Update("UPDATE enterprise_node_rel "
            + "SET source = #{rel.source}, confidence = #{rel.confidence}, reason = #{rel.reason}, "
            + "confirmed_by = #{rel.confirmedBy}, update_time = CURRENT_TIMESTAMP, is_deleted = 0 "
            + "WHERE enterprise_id = #{rel.enterpriseId} AND node_id = #{rel.nodeId} "
            + "AND is_deleted = 1")
    int restoreDeletedRelation(@Param("rel") EnterpriseNodeRel relation);
}
