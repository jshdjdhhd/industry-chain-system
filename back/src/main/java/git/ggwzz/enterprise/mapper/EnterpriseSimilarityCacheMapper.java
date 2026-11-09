package git.ggwzz.enterprise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.enterprise.entity.EnterpriseSimilarityCache;
import git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow;
import git.ggwzz.enterprise.mapper.projection.TargetHashParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 企业相似度缓存数据访问。
 */
@Mapper
public interface EnterpriseSimilarityCacheMapper extends BaseMapper<EnterpriseSimilarityCache> {

    List<EnterpriseSimilarityCacheRow> selectMatchedCache(
            @Param("sourceEnterpriseId") Long sourceEnterpriseId,
            @Param("modelName") String modelName,
            @Param("sourceTextHash") String sourceTextHash,
            @Param("targetHashes") List<TargetHashParam> targetHashes);

    int upsertSimilarity(@Param("sourceEnterpriseId") Long sourceEnterpriseId,
                         @Param("targetEnterpriseId") Long targetEnterpriseId,
                         @Param("modelName") String modelName,
                         @Param("sourceTextHash") String sourceTextHash,
                         @Param("targetTextHash") String targetTextHash,
                         @Param("similarity") BigDecimal similarity,
                         @Param("reasons") String reasons);
}
