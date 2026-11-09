package git.ggwzz.analysis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.analysis.dto.CreateAnalysisReportRequest;
import git.ggwzz.analysis.dto.ReportDownloadPayload;
import git.ggwzz.analysis.service.AnalysisReportService;
import git.ggwzz.analysis.vo.AnalysisReportDetailVO;
import git.ggwzz.analysis.vo.AnalysisReportTaskVO;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AnalysisReportController} MockMvc 测试。
 *
 * <p>采用 standaloneSetup 直接装配控制器，避免全量 Spring 上下文加载。
 * 用 Mockito mock Service，验证路由、统一响应结构、参数绑定与委托。</p>
 */
class AnalysisReportControllerTest {

    private MockMvc mockMvc;
    private AnalysisReportService analysisReportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        analysisReportService = Mockito.mock(AnalysisReportService.class);
        AnalysisReportController controller =
                new AnalysisReportController(analysisReportService);
        // 装配 GlobalExceptionHandler，使 BusinessException 按 code 映射 HTTP 状态码，
        // 保证 HTTP 状态码与响应体 code 一致（standaloneSetup 默认不装配 ControllerAdvice）。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateReportAndReturn200() throws Exception {
        CreateAnalysisReportRequest req = new CreateAnalysisReportRequest();
        req.setChainId(1L);
        req.setReportType("HEALTH_ANALYSIS");
        req.setScopeType("ALL_CHAIN");
        when(analysisReportService.createReport(any())).thenReturn(taskVO(1001L, "PROCESSING"));

        mockMvc.perform(post("/api/analysis/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void shouldReturnPageList() throws Exception {
        PageResult<AnalysisReportTaskVO> page = new PageResult<>(
                List.of(taskVO(1001L, "COMPLETED")), 1, 1, 10);
        when(analysisReportService.listReports(any())).thenReturn(page);

        mockMvc.perform(get("/api/analysis/reports")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1001));
    }

    @Test
    void shouldReturnDetail() throws Exception {
        AnalysisReportDetailVO detail = new AnalysisReportDetailVO();
        detail.setId(1001L);
        detail.setChainId(1L);
        detail.setChainName("测试链");
        detail.setStatus("COMPLETED");
        detail.setTitle("测试链健康分析报告");
        detail.setContentMarkdown("# 报告正文");
        when(analysisReportService.getReport(eq(1001L))).thenReturn(detail);

        mockMvc.perform(get("/api/analysis/reports/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.chainName").value("测试链"))
                .andExpect(jsonPath("$.data.contentMarkdown").value("# 报告正文"));
    }

    @Test
    void shouldRetryReportAndReturn200() throws Exception {
        when(analysisReportService.retryReport(eq(1001L)))
                .thenReturn(taskVO(1002L, "PROCESSING"));

        mockMvc.perform(post("/api/analysis/reports/1001/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1002))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void shouldDownloadPdfWithCorrectHeaders(@TempDir Path tempDir) throws Exception {
        // 准备一个真实 PDF 文件供 Controller 读取
        Path pdf = tempDir.resolve("1001.pdf");
        Files.writeString(pdf, "%PDF-1.4 fake pdf body", StandardCharsets.UTF_8);
        ReportDownloadPayload payload = new ReportDownloadPayload(
                "测试链健康分析报告", "application/pdf",
                pdf.toAbsolutePath().toString(), null);
        when(analysisReportService.loadDownloadPayload(eq(1001L), eq("pdf")))
                .thenReturn(payload);

        mockMvc.perform(get("/api/analysis/reports/1001/download")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));
    }

    @Test
    void shouldDownloadMarkdownAsFallback() throws Exception {
        ReportDownloadPayload payload = new ReportDownloadPayload(
                "测试链健康分析报告", "text/markdown; charset=UTF-8",
                null, "# 报告正文\n中文内容");
        when(analysisReportService.loadDownloadPayload(eq(1001L), eq("md")))
                .thenReturn(payload);

        mockMvc.perform(get("/api/analysis/reports/1001/download")
                        .param("format", "md"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".md")))
                .andExpect(content().bytes("# 报告正文\n中文内容".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldReturn409WhenPdfNotGenerated() throws Exception {
        // PDF 未生成（中文字体降级），Service 抛 CONFLICT，提示改用 md
        when(analysisReportService.loadDownloadPayload(eq(1001L), eq("pdf")))
                .thenThrow(new BusinessException(ResultCode.CONFLICT,
                        "该报告未生成 PDF（可能因运行环境缺少中文字体降级），请改用 md 格式下载"));

        mockMvc.perform(get("/api/analysis/reports/1001/download")
                        .param("format", "pdf"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("改用 md 格式下载")));
    }

    @Test
    void shouldReturn400ForUnsupportedFormat() throws Exception {
        when(analysisReportService.loadDownloadPayload(eq(1001L), eq("docx")))
                .thenThrow(new BusinessException(ResultCode.BAD_REQUEST,
                        "不支持的下载格式：docx（仅支持 pdf、md）"));

        mockMvc.perform(get("/api/analysis/reports/1001/download")
                        .param("format", "docx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private AnalysisReportTaskVO taskVO(Long id, String status) {
        AnalysisReportTaskVO vo = new AnalysisReportTaskVO();
        vo.setId(id);
        vo.setChainId(1L);
        vo.setChainName("测试链");
        vo.setReportType("HEALTH_ANALYSIS");
        vo.setScopeType("ALL_CHAIN");
        vo.setStatus(status);
        vo.setRetryCount(0);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }
}
