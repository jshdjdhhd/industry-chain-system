package git.ggwzz.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.matching.entity.MatchingTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 智能挂链推荐任务 Mapper。
 */
@Mapper
public interface MatchingTaskMapper extends BaseMapper<MatchingTask> {

    @Insert("<script>"
            + "INSERT INTO matching_task "
            + "(enterprise_id, chain_id, input_snapshot, model_name, status, retry_count, created_by, created_at, updated_at) "
            + "VALUES "
            + "<foreach item='item' collection='list' separator=','>"
            + "(#{item.enterpriseId}, #{item.chainId}, #{item.inputSnapshot}, #{item.modelName}, "
            + "#{item.status}, #{item.retryCount}, #{item.createdBy}, #{item.createdAt}, #{item.updatedAt})"
            + "</foreach>"
            + "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBatch(@Param("list") List<MatchingTask> tasks);

    @Select("<script>"
            + "SELECT * FROM matching_task WHERE id IN "
            + "<foreach item='id' collection='taskIds' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<MatchingTask> selectBatchByIds(@Param("taskIds") List<Long> taskIds);

    @Update("<script>"
            + "UPDATE matching_task SET "
            + "status = CASE id "
            + "<foreach item='item' collection='list'>"
            + "WHEN #{item.id} THEN #{item.status} "
            + "</foreach>"
            + "END, "
            + "input_snapshot = CASE id "
            + "<foreach item='item' collection='list'>"
            + "WHEN #{item.id} THEN #{item.inputSnapshot} "
            + "</foreach>"
            + "END, "
            + "updated_at = CURRENT_TIMESTAMP "
            + "WHERE id IN "
            + "<foreach item='item' collection='list' open='(' separator=',' close=')'>"
            + "#{item.id}"
            + "</foreach>"
            + "</script>")
    int updateBatchStatusAndSnapshot(@Param("list") List<MatchingTask> tasks);
}
