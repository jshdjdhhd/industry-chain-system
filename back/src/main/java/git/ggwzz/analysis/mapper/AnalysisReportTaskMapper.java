package git.ggwzz.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康分析报告任务 Mapper。
 *
 * <p>单表 CRUD 走 {@link BaseMapper}，列表分页用 {@code selectPage}。
 * 报告任务表为追加式历史，不做逻辑删除。</p>
 */
@Mapper
public interface AnalysisReportTaskMapper extends BaseMapper<AnalysisReportTask> {
}