package git.ggwzz.matching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import git.ggwzz.matching.entity.MatchingResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 智能挂链推荐结果 Mapper。
 */
@Mapper
public interface MatchingResultMapper extends BaseMapper<MatchingResult> {

    @Select("<script>"
            + "SELECT * FROM matching_result WHERE task_id IN "
            + "<foreach item='tid' collection='taskIds' open='(' separator=',' close=')'>"
            + "#{tid}"
            + "</foreach>"
            + " ORDER BY task_id, rank_no ASC"
            + "</script>")
    List<MatchingResult> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

    @Delete("<script>"
            + "DELETE FROM matching_result WHERE task_id IN "
            + "<foreach item='tid' collection='taskIds' open='(' separator=',' close=')'>"
            + "#{tid}"
            + "</foreach>"
            + "</script>")
    int deleteByTaskIds(@Param("taskIds") List<Long> taskIds);

    /**
     * 批量插入匹配结果。
     */
    @Insert("<script>"
            + "INSERT INTO matching_result (task_id, node_id, confidence, reason, evidence, rank_no, decision, reject_reason, decided_by, decided_at, created_at) VALUES "
            + "<foreach item='item' collection='list' separator=','>"
            + "(#{item.taskId}, #{item.nodeId}, #{item.confidence}, #{item.reason}, #{item.evidence}, #{item.rankNo}, #{item.decision}, #{item.rejectReason}, #{item.decidedBy}, #{item.decidedAt}, NOW())"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("list") List<MatchingResult> list);

    /**
     * 按决策汇总匹配结果数量和平均置信度。
     *
     * @return 决策编码到统计结果的映射
     */
    @MapKey("decision")
    Map<String, MatchingDecisionRow> selectDecisionSummary();

    /**
     * 汇总指定时间以来每天的推荐和采纳数量。
     *
     * @param weekStart 本周起始时间
     * @return 每日推荐统计
     */
    List<DayRecommendationRow> selectWeeklyRecommendations(
            @Param("weekStart") LocalDateTime weekStart);
}
