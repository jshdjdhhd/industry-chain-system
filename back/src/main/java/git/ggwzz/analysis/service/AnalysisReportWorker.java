package git.ggwzz.analysis.service;

import git.ggwzz.analysis.entity.AnalysisReportContent;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import git.ggwzz.analysis.enums.AnalysisReportStatus;
import git.ggwzz.analysis.mapper.AnalysisReportContentMapper;
import git.ggwzz.analysis.mapper.AnalysisReportTaskMapper;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.llm.LlmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康分析报告异步执行 Worker。
 *
 * <p>7.4 起，模板 Markdown 被"LLM 基于结构化上下文生成的 Markdown"取代。
 * 流程：查任务 → CANCELLED 跳过 → 上下文构建 → 提示词构建 → 调大模型 →
 * 解析 markdown → 写 analysis_report_content（含真实指标快照） → 置 COMPLETED；
 * 任意异常 → 置 FAILED + error_message 截断 1000。</p>
 *
 * <p>本流程严格按规格：LLM 调用或解析失败即置 FAILED，不自动回退模板，
 * 由用户手动重试。{@link AnalysisReportTemplateBuilder} 类保留在 {@code analysis.service}
 * 包内以备未来手动触发兜底，但本 Worker 不再持有其引用（避免死依赖）。</p>
 *
 * <p>{@link #execute} 本身不加 {@code @Transactional}（异步方法事务语义不可靠），
 * 内部写库操作为单表写，无需显式事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportWorker {

    /** 写入 analysis_report_task.error_message 的最大长度（数据库列宽 1000） */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final AnalysisReportTaskMapper analysisReportTaskMapper;
    private final AnalysisReportContentMapper analysisReportContentMapper;
    private final ChainMapper chainMapper;
    private final AnalysisReportContextBuilder contextBuilder;
    private final AnalysisReportPromptBuilder promptBuilder;
    private final AnalysisReportResultParser resultParser;
    private final LlmClient llmClient;
    private final ReportExportService reportExportService;

    /**
     * 异步执行报告生成任务。
     *
     * @param taskId 报告任务 ID
     */
    @Async("analysisReportExecutor")
    public void execute(Long taskId) {
        AnalysisReportTask task = analysisReportTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("analysis report task not found: taskId={}", taskId);
            return;
        }
        AnalysisReportStatus status = AnalysisReportStatus.from(task.getStatus());
        if (status == AnalysisReportStatus.CANCELLED) {
            log.info("analysis report task already cancelled: taskId={}", taskId);
            return;
        }
        try {
            Chain chain = chainMapper.selectById(task.getChainId());
            if (chain == null) {
                throw new IllegalStateException("产业链不存在: chainId=" + task.getChainId());
            }

            // 结构化上下文 + 真实指标快照
            AnalysisReportContextBuilder.ReportContext reportContext =
                    contextBuilder.buildContext(task, chain);

            // 提示词 + 大模型调用
            List<LlmMessage> messages = promptBuilder.buildPrompt(reportContext.context());
            LlmChatCompletion completion = llmClient.complete(messages);
            String markdown = resultParser.parseMarkdown(completion.content());

            // 写报告内容：7.5 起 Markdown → HTML（套固定 CSS）→ PDF（中文字体可用时）
            String contentHtml = reportExportService.renderHtml(markdown);
            String pdfFilePath = null;
            try {
                pdfFilePath = reportExportService.renderPdf(contentHtml, taskId);
            } catch (Exception ex) {
                // PDF 生成失败不应让整个报告任务失败：HTML 与 Markdown 仍可用
                log.warn("report PDF export failed, degrade to HTML only: taskId={}, reason={}",
                        taskId, ex.getClass().getSimpleName());
                log.debug("report PDF export failure detail", ex);
            }

            AnalysisReportContent content = new AnalysisReportContent();
            content.setTaskId(taskId);
            content.setTitle(chain.getChainName() + "健康分析报告");
            content.setContentMarkdown(markdown);
            content.setContentHtml(contentHtml);
            content.setPdfFilePath(pdfFilePath);
            content.setMetricsSnapshot(reportContext.metricsSnapshot());
            analysisReportContentMapper.insert(content);

            AnalysisReportTask update = new AnalysisReportTask();
            update.setId(taskId);
            update.setStatus(AnalysisReportStatus.COMPLETED.name());
            update.setCompletedAt(LocalDateTime.now());
            update.setErrorMessage(null);
            analysisReportTaskMapper.updateById(update);
            log.info("analysis report task completed: taskId={}, chainId={}",
                    taskId, task.getChainId());
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message == null || message.isBlank()) {
                message = ex.getClass().getSimpleName();
            }
            if (message.length() > ERROR_MESSAGE_MAX_LENGTH) {
                message = message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
            }
            AnalysisReportTask update = new AnalysisReportTask();
            update.setId(taskId);
            update.setStatus(AnalysisReportStatus.FAILED.name());
            update.setErrorMessage(message);
            update.setCompletedAt(LocalDateTime.now());
            analysisReportTaskMapper.updateById(update);
            log.warn("analysis report task failed: taskId={}, reason={}",
                    taskId, ex.getClass().getSimpleName());
            log.debug("analysis report task failure detail", ex);
        }
    }
}
