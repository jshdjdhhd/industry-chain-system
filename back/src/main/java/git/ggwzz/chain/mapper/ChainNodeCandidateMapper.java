package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产业链节点候选查询 Mapper。
 */
@Mapper
public interface ChainNodeCandidateMapper {

    /**
     * 查询某产业链下全部未删除节点候选。
     *
     * @param chainId 产业链 ID
     * @return 节点候选列表
     */
    @Select("SELECT id, chain_id, node_name AS name, node_path, keywords, description "
            + "FROM chain_node WHERE chain_id = #{chainId} AND is_deleted = 0 "
            + "ORDER BY sort_order, id")
    List<ChainNodeCandidateVO> listByChainId(@Param("chainId") Long chainId);
}
