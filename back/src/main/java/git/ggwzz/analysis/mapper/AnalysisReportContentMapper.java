package git.ggwzz.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.analysis.entity.AnalysisReportContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康分析报告内容 Mapper。
 *
 * <p>与任务 1:1 关联，通过 {@code taskId} 查询内容。</p>
 */
@Mapper
public interface AnalysisReportContentMapper extends BaseMapper<AnalysisReportContent> {
}