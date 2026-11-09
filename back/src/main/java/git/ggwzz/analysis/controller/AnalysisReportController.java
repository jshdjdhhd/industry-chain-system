package git.ggwzz.analysis.controller;

import git.ggwzz.analysis.dto.AnalysisReportQuery;
import git.ggwzz.analysis.dto.CreateAnalysisReportRequest;
import git.ggwzz.analysis.dto.ReportDownloadPayload;
import git.ggwzz.analysis.service.AnalysisReportService;
import git.ggwzz.analysis.vo.AnalysisReportDetailVO;
import git.ggwzz.analysis.vo.AnalysisReportTaskVO;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.common.log.OperationLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 健康分析报告接口。
 *
 * <p>提供报告任务的创建、列表、详情、重试与下载。
 * 报告正文由异步 Worker 生成，前端通过轮询任务状态获取最终 Markdown。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis/reports")
@RequiredArgsConstructor
public class AnalysisReportController {

    private final AnalysisReportService analysisReportService;

    /**
     * 创建健康分析报告任务。
     *
     * @param request 创建请求
     * @return 任务 VO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('analysis:report:create')")
    @OperationLog(action = "create_analysis_report", targetType = "analysis_report",
            detail = "创建健康分析报告")
    public Result<AnalysisReportTaskVO> createReport(
            @Valid @RequestBody CreateAnalysisReportRequest request) {
        return Result.success(analysisReportService.createReport(request));
    }

    /**
     * 分页查询报告任务列表。
     *
     * @param chainId 可选产业链 ID 过滤
     * @param status  可选状态过滤
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('analysis:report:query')")
    public Result<PageResult<AnalysisReportTaskVO>> listReports(
            @RequestParam(required = false) Long chainId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        AnalysisReportQuery query = new AnalysisReportQuery();
        query.setChainId(chainId);
        query.setStatus(status);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return Result.success(analysisReportService.listReports(query));
    }

    /**
     * 查询报告任务详情。
     *
     * @param reportId 任务 ID
     * @return 报告详情 VO
     */
    @GetMapping("/{reportId}")
    @PreAuthorize("hasAuthority('analysis:report:query')")
    public Result<AnalysisReportDetailVO> getReport(@PathVariable Long reportId) {
        return Result.success(analysisReportService.getReport(reportId));
    }

    /**
     * 重试失败/取消的报告任务。
     *
     * @param reportId 原任务 ID
     * @return 新任务 VO
     */
    @PostMapping("/{reportId}/retry")
    @PreAuthorize("hasAuthority('analysis:report:retry')")
    @OperationLog(action = "retry_analysis_report", targetType = "analysis_report",
            targetId = "#reportId", detail = "重试健康分析报告")
    public Result<AnalysisReportTaskVO> retryReport(@PathVariable Long reportId) {
        return Result.success(analysisReportService.retryReport(reportId));
    }

    /**
     * 下载报告（PDF/MD）。
     *
     * <p>PDF 返回 {@code application/pdf} + attachment 文件名；MD 返回
     * {@code text/markdown; charset=UTF-8} + attachment 文件名。文件下载直接返回
     * {@code ResponseEntity<Resource>}，不套统一 {@link Result} 包装
     * （CLAUDE.md 第 3 节：文件下载直接写响应，不套 Result）。</p>
     *
     * <p>PDF 文件未生成（运行环境缺中文字体降级）时，Service 抛业务异常，由
     * GlobalExceptionHandler 映射为 409，提示用户改用 md 格式下载。MD 始终可下载，
     * 作为兜底。</p>
     *
     * @param reportId 任务 ID
     * @param format   导出格式 pdf / md
     * @return 文件资源响应
     * @throws IOException 读取 PDF 文件失败时抛出
     */
    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAuthority('analysis:report:download')")
    @OperationLog(action = "download_analysis_report", targetType = "analysis_report",
            targetId = "#reportId", detail = "下载健康分析报告")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long reportId,
                                                   @RequestParam String format)
            throws IOException {
        ReportDownloadPayload payload = analysisReportService.loadDownloadPayload(reportId, format);
        if (payload.getPdfFilePath() != null) {
            Path file = Paths.get(payload.getPdfFilePath());
            if (!Files.exists(file) || !Files.isReadable(file)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "PDF 文件不存在或不可读");
            }
            Resource resource = new ByteArrayResource(Files.readAllBytes(file));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            attachmentHeader(payload.getFileName(), ".pdf"))
                    .body(resource);
        }
        // MD 兜底
        byte[] mdBytes = payload.getMarkdownContent() == null
                ? new byte[0]
                : payload.getMarkdownContent().getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(mdBytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        attachmentHeader(payload.getFileName(), ".md"))
                .body(resource);
    }

    /**
     * 构造 Content-Disposition attachment 头，使用 RFC 5987 UTF-8 编码文件名。
     */
    private String attachmentHeader(String fileName, String ext) {
        String full = fileName + ext;
        // URLEncoder.encode(Charset) 不抛 UnsupportedEncodingException；UTF-8 恒存在
        String encoded = java.net.URLEncoder.encode(full, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + full + "\"; filename*=UTF-8''" + encoded;
    }
}