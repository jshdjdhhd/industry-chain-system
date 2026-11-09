package git.ggwzz.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.matching.entity.AiEmbeddingVector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 向量缓存 Mapper。
 */
@Mapper
public interface AiEmbeddingVectorMapper extends BaseMapper<AiEmbeddingVector> {

    @Select("SELECT id, object_type, object_id, model_name, dimension, text_hash, vector_data, "
            + "source_text_snapshot, status, create_time, update_time, is_deleted "
            + "FROM ai_embedding_vector "
            + "WHERE object_type = #{objectType} AND object_id = #{objectId} "
            + "AND model_name = #{modelName} AND is_deleted = 0 "
            + "ORDER BY update_time DESC, id DESC LIMIT 1")
    AiEmbeddingVector selectActive(@Param("objectType") String objectType,
                                   @Param("objectId") Long objectId,
                                   @Param("modelName") String modelName);

    @Update("UPDATE ai_embedding_vector SET is_deleted = 1 "
            + "WHERE object_type = #{objectType} AND object_id = #{objectId} "
            + "AND model_name = #{modelName} AND is_deleted = 0")
    int markActiveDeleted(@Param("objectType") String objectType,
                          @Param("objectId") Long objectId,
                          @Param("modelName") String modelName);

    @Select("<script>"
            + "SELECT id, object_type, object_id, model_name, dimension, text_hash, vector_data, "
            + "source_text_snapshot, status, create_time, update_time, is_deleted "
            + "FROM ai_embedding_vector "
            + "WHERE object_type = #{objectType} AND model_name = #{modelName} AND is_deleted = 0 "
            + "AND object_id IN "
            + "<foreach item='id' collection='objectIds' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + " ORDER BY object_id, update_time DESC, id DESC"
            + "</script>")
    List<AiEmbeddingVector> selectActiveBatch(@Param("objectType") String objectType,
                                              @Param("objectIds") List<Long> objectIds,
                                              @Param("modelName") String modelName);
}
