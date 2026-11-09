package git.ggwzz.analysis.service;

import git.ggwzz.analysis.entity.AnalysisReportContent;
import git.ggwzz.analysis.entity.AnalysisReportTask;
import git.ggwzz.analysis.mapper.AnalysisReportContentMapper;
import git.ggwzz.analysis.mapper.AnalysisReportTaskMapper;
import git.ggwzz.analysis.service.AnalysisReportContextBuilder.ReportContext;
import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.llm.LlmMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnalysisReportWorker} 单元测试（7.4 LLM 路径）。
 *
 * <p>纯 Mockito，mock 全部 Mapper、ContextBuilder、PromptBuilder、ResultParser、LlmClient。
 * 验证：正常路径写 content + 置 COMPLETED、LLM 调用失败置 FAILED、
 * 解析失败置 FAILED、任务缺失/CANCELLED 提前返回。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisReportWorkerTest {

    @Mock
    private AnalysisReportTaskMapper analysisReportTaskMapper;
    @Mock
    private AnalysisReportContentMapper analysisReportContentMapper;
    @Mock
    private ChainMapper chainMapper;
    @Mock
    private AnalysisReportContextBuilder contextBuilder;
    @Mock
    private AnalysisReportPromptBuilder promptBuilder;
    @Mock
    private AnalysisReportResultParser resultParser;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ReportExportService reportExportService;

    private AnalysisReportWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AnalysisReportWorker(
                analysisReportTaskMapper,
                analysisReportContentMapper,
                chainMapper,
                contextBuilder,
                promptBuilder,
                resultParser,
                llmClient,
                reportExportService);
    }

    @Test
    void shouldCompleteAndWriteContentWhenLlmSucceeds() throws Exception {
        AnalysisReportTask task = task(7L, "PROCESSING", 1L);
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "新能源汽车"));
        ReportContext reportContext = new ReportContext(sampleContext(), "{\"metrics\":{}}");
        when(contextBuilder.buildContext(any(), any())).thenReturn(reportContext);
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        when(llmClient.complete(any())).thenReturn(
                new LlmChatCompletion("model", "{\"markdown\":\"# 报告\\n正文\"}"));
        when(resultParser.parseMarkdown("{\"markdown\":\"# 报告\\n正文\"}"))
                .thenReturn("# 报告\n正文");
        when(reportExportService.renderHtml("# 报告\n正文"))
                .thenReturn("<html><body><h1>报告</h1></body></html>");
        when(reportExportService.renderPdf(any(), any()))
                .thenReturn("/tmp/7.pdf");

        worker.execute(7L);

        ArgumentCaptor<AnalysisReportContent> contentCaptor =
                ArgumentCaptor.forClass(AnalysisReportContent.class);
        verify(analysisReportContentMapper).insert(contentCaptor.capture());
        AnalysisReportContent content = contentCaptor.getValue();
        assertEquals(7L, content.getTaskId());
        assertEquals("新能源汽车健康分析报告", content.getTitle());
        assertEquals("# 报告\n正文", content.getContentMarkdown());
        assertEquals("<html><body><h1>报告</h1></body></html>", content.getContentHtml());
        assertEquals("/tmp/7.pdf", content.getPdfFilePath());
        assertEquals("{\"metrics\":{}}", content.getMetricsSnapshot());

        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        AnalysisReportTask updated = taskCaptor.getValue();
        assertEquals("COMPLETED", updated.getStatus());
        assertNotNull(updated.getCompletedAt());
        assertNull(updated.getErrorMessage());
    }

    @Test
    void shouldMarkFailedWhenLlmThrows() {
        AnalysisReportTask task = task(7L, "PROCESSING", 1L);
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "链"));
        when(contextBuilder.buildContext(any(), any()))
                .thenReturn(new ReportContext(sampleContext(), "{}"));
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        when(llmClient.complete(any())).thenThrow(new IllegalStateException("LLM 调用超时"));

        worker.execute(7L);

        verify(analysisReportContentMapper, never()).insert(any(AnalysisReportContent.class));
        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        AnalysisReportTask failed = taskCaptor.getValue();
        assertEquals("FAILED", failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("LLM 调用超时"));
        assertNotNull(failed.getCompletedAt());
    }

    @Test
    void shouldMarkFailedWhenResultParserThrows() {
        AnalysisReportTask task = task(7L, "PROCESSING", 1L);
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "链"));
        when(contextBuilder.buildContext(any(), any()))
                .thenReturn(new ReportContext(sampleContext(), "{}"));
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        when(llmClient.complete(any())).thenReturn(new LlmChatCompletion("model", "non-json"));
        when(resultParser.parseMarkdown("non-json"))
                .thenThrow(new IllegalStateException("LLM 响应解析失败：未找到 JSON 对象"));

        worker.execute(7L);

        verify(analysisReportContentMapper, never()).insert(any(AnalysisReportContent.class));
        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        AnalysisReportTask failed = taskCaptor.getValue();
        assertEquals("FAILED", failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("LLM 响应解析失败"));
    }

    @Test
    void shouldReturnEarlyWhenTaskNotFound() {
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(null);

        worker.execute(7L);

        verify(analysisReportContentMapper, never()).insert(any(AnalysisReportContent.class));
        verify(analysisReportTaskMapper, never()).updateById(any(AnalysisReportTask.class));
        verify(chainMapper, never()).selectById(any());
    }

    @Test
    void shouldReturnEarlyWhenTaskAlreadyCancelled() {
        AnalysisReportTask task = task(7L, "CANCELLED", 1L);
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);

        worker.execute(7L);

        verify(analysisReportContentMapper, never()).insert(any(AnalysisReportContent.class));
        verify(analysisReportTaskMapper, never()).updateById(any(AnalysisReportTask.class));
        verify(chainMapper, never()).selectById(any());
    }

    @Test
    void shouldMarkFailedWhenChainMissing() {
        AnalysisReportTask task = task(7L, "PROCESSING", 99L);
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);
        when(chainMapper.selectById(99L)).thenReturn(null);

        worker.execute(7L);

        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
        assertTrue(taskCaptor.getValue().getErrorMessage().contains("产业链不存在"));
    }

    @Test
    void shouldTruncateLongErrorMessage() {
        AnalysisReportTask task = task(7L, "PROCESSING", 1L);
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "链"));
        when(contextBuilder.buildContext(any(), any()))
                .thenReturn(new ReportContext(sampleContext(), "{}"));
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        String longMessage = "err".repeat(2000);
        when(llmClient.complete(any())).thenThrow(new IllegalStateException(longMessage));

        worker.execute(7L);

        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
        assertTrue(taskCaptor.getValue().getErrorMessage().length() <= 1000);
    }

    @Test
    void shouldSaveRealMetricsSnapshotNotPlaceholder() {
        AnalysisReportTask task = task(7L, "PROCESSING", 1L);
        task.setInputSnapshot("{\"legacy\":\"placeholder\"}");
        when(analysisReportTaskMapper.selectById(7L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "链"));
        String realMetrics = "{\"summary\":{\"nodeCount\":2,\"enterpriseCount\":5}}";
        when(contextBuilder.buildContext(any(), any()))
                .thenReturn(new ReportContext(sampleContext(), realMetrics));
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        when(llmClient.complete(any())).thenReturn(new LlmChatCompletion("m", "{\"markdown\":\"x\"}"));
        when(resultParser.parseMarkdown(any())).thenReturn("x");

        worker.execute(7L);

        ArgumentCaptor<AnalysisReportContent> contentCaptor =
                ArgumentCaptor.forClass(AnalysisReportContent.class);
        verify(analysisReportContentMapper).insert(contentCaptor.capture());
        assertEquals(realMetrics, contentCaptor.getValue().getMetricsSnapshot(),
                "metricsSnapshot 应为真实指标，而非 inputSnapshot 占位");
    }

    @Test
    void shouldRemainCompletedWhenPdfDegradesToNull() throws Exception {
        // PDF 中文字体不可用时 ReportExportService.renderPdf 返回 null，
        // 任务仍应 COMPLETED，content_html 正常写入，pdf_file_path 为 null。
        AnalysisReportTask task = task(8L, "PROCESSING", 1L);
        when(analysisReportTaskMapper.selectById(8L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "链"));
        when(contextBuilder.buildContext(any(), any()))
                .thenReturn(new ReportContext(sampleContext(), "{}"));
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        when(llmClient.complete(any())).thenReturn(new LlmChatCompletion("m", "{\"markdown\":\"x\"}"));
        when(resultParser.parseMarkdown(any())).thenReturn("# 报告");
        when(reportExportService.renderHtml("# 报告")).thenReturn("<html><body>x</body></html>");
        when(reportExportService.renderPdf(any(), any())).thenReturn(null);

        worker.execute(8L);

        ArgumentCaptor<AnalysisReportContent> contentCaptor =
                ArgumentCaptor.forClass(AnalysisReportContent.class);
        verify(analysisReportContentMapper).insert(contentCaptor.capture());
        AnalysisReportContent content = contentCaptor.getValue();
        assertEquals("<html><body>x</body></html>", content.getContentHtml());
        assertNull(content.getPdfFilePath(), "PDF 降级时 pdf_file_path 应为 null");

        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        assertEquals("COMPLETED", taskCaptor.getValue().getStatus(),
                "PDF 降级不应让任务失败");
    }

    @Test
    void shouldRemainCompletedWhenRenderPdfThrows() throws Exception {
        // renderPdf 抛异常时 Worker 应捕获并降级，任务仍 COMPLETED。
        AnalysisReportTask task = task(9L, "PROCESSING", 1L);
        when(analysisReportTaskMapper.selectById(9L)).thenReturn(task);
        when(chainMapper.selectById(1L)).thenReturn(chain(1L, "链"));
        when(contextBuilder.buildContext(any(), any()))
                .thenReturn(new ReportContext(sampleContext(), "{}"));
        when(promptBuilder.buildPrompt(any())).thenReturn(List.<LlmMessage>of());
        when(llmClient.complete(any())).thenReturn(new LlmChatCompletion("m", "{\"markdown\":\"x\"}"));
        when(resultParser.parseMarkdown(any())).thenReturn("# 报告");
        when(reportExportService.renderHtml("# 报告")).thenReturn("<html><body>x</body></html>");
        when(reportExportService.renderPdf(any(), any()))
                .thenThrow(new IllegalStateException("PDF 渲染崩溃"));

        worker.execute(9L);

        ArgumentCaptor<AnalysisReportContent> contentCaptor =
                ArgumentCaptor.forClass(AnalysisReportContent.class);
        verify(analysisReportContentMapper).insert(contentCaptor.capture());
        assertNull(contentCaptor.getValue().getPdfFilePath());
        ArgumentCaptor<AnalysisReportTask> taskCaptor =
                ArgumentCaptor.forClass(AnalysisReportTask.class);
        verify(analysisReportTaskMapper).updateById(taskCaptor.capture());
        assertEquals("COMPLETED", taskCaptor.getValue().getStatus());
    }

    // ==================== fixture ====================

    private Map<String, Object> sampleContext() {
        return new LinkedHashMap<>();
    }

    private Chain chain(Long id, String name) {
        Chain c = new Chain();
        c.setId(id);
        c.setChainName(name);
        return c;
    }

    private AnalysisReportTask task(Long id, String status, Long chainId) {
        AnalysisReportTask t = new AnalysisReportTask();
        t.setId(id);
        t.setStatus(status);
        t.setChainId(chainId);
        return t;
    }
}