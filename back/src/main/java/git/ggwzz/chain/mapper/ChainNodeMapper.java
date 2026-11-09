package git.ggwzz.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产业链节点 Mapper。
 *
 * <p>核心树形查询用命名 {@code @Select}，便于单测 mock。</p>
 */
@Mapper
public interface ChainNodeMapper extends BaseMapper<ChainNode> {

    /**
     * 统计所有根节点子树内的去重企业数。
     *
     * @return 根节点及其子树企业数
     */
    List<NodeSubtreeCountRow> selectRootNodeSubtreeCounts();

    /**
     * 统计直接子节点数（用于删除前置校验）。
     *
     * @param parentId 父节点 ID
     * @return 子节点数
     */
    @Select("SELECT COUNT(*) FROM chain_node WHERE parent_id = #{parentId} AND is_deleted = 0")
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 查询某产业链下全部未删除节点（按 sort_order、id 排序）。
     *
     * @param chainId 产业链 ID
     * @return 节点列表
     */
    @Select("SELECT id, chain_id, parent_id, node_name, keywords, node_level, description, node_path, sort_order, "
            + "stage_type, create_time, update_time, is_deleted "
            + "FROM chain_node WHERE chain_id = #{chainId} AND is_deleted = 0 "
            + "ORDER BY sort_order, id")
    List<ChainNode> selectByChainId(@Param("chainId") Long chainId);

    /**
     * 批量重写子树路径、层级和根节点元数据。
     *
     * @param chainId       产业链 ID
     * @param nodeId        子树根节点 ID
     * @param newParentId   新父节点 ID，null 表示移动为根节点
     * @param sortOrder     新排序，null 表示保持原值
     * @param oldPath       子树根节点旧路径
     * @param oldPathPattern 旧路径右模糊模式
     * @param newPath       子树根节点新路径
     * @param levelOffset   新旧层级差
     * @return 受影响行数
     */
    int updateSubtree(
            @Param("chainId") Long chainId,
            @Param("nodeId") Long nodeId,
            @Param("newParentId") Long newParentId,
            @Param("sortOrder") Integer sortOrder,
            @Param("oldPath") String oldPath,
            @Param("oldPathPattern") String oldPathPattern,
            @Param("newPath") String newPath,
            @Param("levelOffset") Integer levelOffset);

}
