package git.ggwzz.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 报告下载负载，供 Controller 构造 {@code ResponseEntity<Resource>} 使用。
 *
 * <p>由 {@link git.ggwzz.analysis.service.AnalysisReportService#loadDownloadPayload} 组装：
 * PDF 走本地文件，MD 走内容字符串兜底。Controller 据此设置 Content-Type 与 Content-Disposition。</p>
 */
@Data
@AllArgsConstructor
public class ReportDownloadPayload {

    /** 导出文件名（不含扩展名，通常为报告标题，已做文件名安全处理） */
    private String fileName;

    /** 内容类型：{@code application/pdf} 或 {@code text/markdown} */
    private String contentType;

    /**
     * PDF 模式下的本地文件绝对路径；MD 模式为 {@code null}。
     * 当 PDF 模式但文件不存在/未生成时由 Service 抛业务异常，不会以 null 透传到 Controller。
     */
    private String pdfFilePath;

    /**
     * MD 模式下的 Markdown 源内容字节；PDF 模式为 {@code null}。
     * 用 UTF-8 编码的原始 Markdown 字符串。
     */
    private String markdownContent;
}
