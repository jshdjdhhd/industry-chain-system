package git.ggwzz.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.matching.entity.MatchingRecommendationCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 智能挂链推荐结果缓存 Mapper。
 */
@Mapper
public interface MatchingRecommendationCacheMapper extends BaseMapper<MatchingRecommendationCache> {

    @Select("SELECT id, enterprise_id, chain_id, model_name, enterprise_text_hash, chain_nodes_hash, "
            + "top_k, vector_weight, rule_weight, result_json, status, computed_at, expire_at, "
            + "create_time, update_time, is_deleted "
            + "FROM matching_recommendation_cache "
            + "WHERE enterprise_id = #{enterpriseId} AND chain_id = #{chainId} "
            + "AND model_name = #{modelName} "
            + "AND enterprise_text_hash = #{enterpriseTextHash} "
            + "AND chain_nodes_hash = #{chainNodesHash} "
            + "AND top_k = #{topK} AND vector_weight = #{vectorWeight} AND rule_weight = #{ruleWeight} "
            + "AND status = 'ACTIVE' AND is_deleted = 0 "
            + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP) "
            + "ORDER BY computed_at DESC, id DESC LIMIT 1")
    MatchingRecommendationCache selectActiveByFingerprint(
            @Param("enterpriseId") Long enterpriseId,
            @Param("chainId") Long chainId,
            @Param("modelName") String modelName,
            @Param("enterpriseTextHash") String enterpriseTextHash,
            @Param("chainNodesHash") String chainNodesHash,
            @Param("topK") Integer topK,
            @Param("vectorWeight") Double vectorWeight,
            @Param("ruleWeight") Double ruleWeight);

    @Select("<script>"
            + "SELECT id, enterprise_id, chain_id, model_name, enterprise_text_hash, chain_nodes_hash, "
            + "top_k, vector_weight, rule_weight, result_json, status, computed_at, expire_at, "
            + "create_time, update_time, is_deleted "
            + "FROM matching_recommendation_cache "
            + "WHERE chain_id = #{chainId} "
            + "AND model_name = #{modelName} "
            + "AND chain_nodes_hash = #{chainNodesHash} "
            + "AND top_k = #{topK} AND vector_weight = #{vectorWeight} AND rule_weight = #{ruleWeight} "
            + "AND status = 'ACTIVE' AND is_deleted = 0 "
            + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP) "
            + "AND enterprise_id IN "
            + "<foreach item='enterpriseId' collection='enterpriseIds' open='(' separator=',' close=')'>"
            + "#{enterpriseId}"
            + "</foreach> "
            + "AND enterprise_text_hash IN "
            + "<foreach item='enterpriseTextHash' collection='enterpriseTextHashes' open='(' separator=',' close=')'>"
            + "#{enterpriseTextHash}"
            + "</foreach> "
            + "ORDER BY computed_at DESC, id DESC"
            + "</script>")
    List<MatchingRecommendationCache> selectActiveCandidates(
            @Param("chainId") Long chainId,
            @Param("modelName") String modelName,
            @Param("chainNodesHash") String chainNodesHash,
            @Param("topK") Integer topK,
            @Param("vectorWeight") Double vectorWeight,
            @Param("ruleWeight") Double ruleWeight,
            @Param("enterpriseIds") List<Long> enterpriseIds,
            @Param("enterpriseTextHashes") List<String> enterpriseTextHashes);

    @Select("<script>"
            + "SELECT id, enterprise_id, chain_id, model_name, enterprise_text_hash, chain_nodes_hash, "
            + "top_k, vector_weight, rule_weight, result_json, status, computed_at, expire_at, "
            + "create_time, update_time, is_deleted "
            + "FROM matching_recommendation_cache "
            + "WHERE chain_id = #{chainId} "
            + "AND model_name = #{modelName} "
            + "AND top_k = #{topK} AND vector_weight = #{vectorWeight} AND rule_weight = #{ruleWeight} "
            + "AND status = 'ACTIVE' AND is_deleted = 0 "
            + "AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP) "
            + "AND enterprise_id IN "
            + "<foreach item='enterpriseId' collection='enterpriseIds' open='(' separator=',' close=')'>"
            + "#{enterpriseId}"
            + "</foreach> "
            + "AND enterprise_text_hash IN "
            + "<foreach item='enterpriseTextHash' collection='enterpriseTextHashes' open='(' separator=',' close=')'>"
            + "#{enterpriseTextHash}"
            + "</foreach> "
            + "ORDER BY computed_at DESC, id DESC"
            + "</script>")
    List<MatchingRecommendationCache> selectLatestActiveCandidates(
            @Param("chainId") Long chainId,
            @Param("modelName") String modelName,
            @Param("topK") Integer topK,
            @Param("vectorWeight") Double vectorWeight,
            @Param("ruleWeight") Double ruleWeight,
            @Param("enterpriseIds") List<Long> enterpriseIds,
            @Param("enterpriseTextHashes") List<String> enterpriseTextHashes);

    @Update("UPDATE matching_recommendation_cache SET is_deleted = 1 "
            + "WHERE enterprise_id = #{enterpriseId} AND chain_id = #{chainId} "
            + "AND model_name = #{modelName} "
            + "AND enterprise_text_hash = #{enterpriseTextHash} "
            + "AND chain_nodes_hash = #{chainNodesHash} "
            + "AND top_k = #{topK} AND vector_weight = #{vectorWeight} AND rule_weight = #{ruleWeight} "
            + "AND is_deleted = 0")
    int markActiveDeleted(
            @Param("enterpriseId") Long enterpriseId,
            @Param("chainId") Long chainId,
            @Param("modelName") String modelName,
            @Param("enterpriseTextHash") String enterpriseTextHash,
            @Param("chainNodesHash") String chainNodesHash,
            @Param("topK") Integer topK,
            @Param("vectorWeight") Double vectorWeight,
            @Param("ruleWeight") Double ruleWeight);
}
