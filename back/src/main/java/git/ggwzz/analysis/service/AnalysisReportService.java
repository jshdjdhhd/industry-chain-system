package git.ggwzz.analysis.service;

import git.ggwzz.analysis.dto.AnalysisReportQuery;
import git.ggwzz.analysis.dto.CreateAnalysisReportRequest;
import git.ggwzz.analysis.dto.ReportDownloadPayload;
import git.ggwzz.analysis.vo.AnalysisReportDetailVO;
import git.ggwzz.analysis.vo.AnalysisReportTaskVO;
import git.ggwzz.common.api.PageResult;

/**
 * 健康分析报告服务。
 *
 * <p>负责报告任务的创建、参数校验、范围展开、列表查询、详情查询与重试。
 * 报告正文生成由 {@link AnalysisReportWorker} 异步执行，本服务只负责编排与状态流转。</p>
 */
public interface AnalysisReportService {

    /**
     * 创建健康分析报告任务。
     *
     * @param request 创建请求
     * @return 任务 VO（含已展开的子树节点 ID）
     */
    AnalysisReportTaskVO createReport(CreateAnalysisReportRequest request);

    /**
     * 分页查询报告任务列表。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<AnalysisReportTaskVO> listReports(AnalysisReportQuery query);

    /**
     * 查询报告任务详情（含报告内容）。
     *
     * @param reportId 任务 ID
     * @return 报告详情 VO
     */
    AnalysisReportDetailVO getReport(Long reportId);

    /**
     * 重试失败/取消的报告任务。
     *
     * <p>仅允许 FAILED、CANCELLED 状态重试；创建新任务并设置 retryFromTaskId。</p>
     *
     * @param reportId 原任务 ID
     * @return 新任务 VO
     */
    AnalysisReportTaskVO retryReport(Long reportId);

    /**
     * 加载报告下载负载（PDF 走文件，MD 走内容兜底）。
     *
     * <p>仅 COMPLETED 任务可下载；PDF 模式下若 PDF 文件未生成或不存在则抛业务异常
     * （提示用户改用 MD 兜底下载）；MD 模式直接返回 Markdown 源内容。</p>
     *
     * @param reportId 任务 ID
     * @param format   导出格式 {@code pdf} / {@code md}
     * @return 下载负载
     */
    ReportDownloadPayload loadDownloadPayload(Long reportId, String format);
}