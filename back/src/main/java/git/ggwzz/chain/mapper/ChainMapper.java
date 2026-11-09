package git.ggwzz.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.mapper.projection.ChainSummaryCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 产业链 Mapper。
 */
@Mapper
public interface ChainMapper extends BaseMapper<Chain> {

    /**
     * 批量查询产业链的有效节点数和有效企业数。
     *
     * @param chainIds 产业链 ID 列表
     * @return 产业链汇总数量
     */
    List<ChainSummaryCountRow> selectSummaryCountsByChainIds(
            @Param("chainIds") List<Long> chainIds);
}
