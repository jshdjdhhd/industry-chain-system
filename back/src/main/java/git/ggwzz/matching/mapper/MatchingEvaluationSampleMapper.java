package git.ggwzz.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.matching.entity.MatchingEvaluationSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能挂链评估样本 Mapper。
 */
@Mapper
public interface MatchingEvaluationSampleMapper extends BaseMapper<MatchingEvaluationSample> {

    @Select("SELECT id, sample_name, enterprise_id, chain_id, expected_node_id, reason, "
            + "enabled, create_time, update_time, is_deleted "
            + "FROM matching_evaluation_sample "
            + "WHERE chain_id = #{chainId} AND enabled = 1 AND is_deleted = 0 "
            + "ORDER BY id ASC")
    List<MatchingEvaluationSample> selectEnabledByChainId(@Param("chainId") Long chainId);
}
