package git.ggwzz.analysis.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import git.ggwzz.analysis.config.AnalysisReportProperties;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.springframework.stereotype.Service;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 报告导出服务：Markdown → HTML（套固定 CSS）→ PDF。
 *
 * <p>7.5 落地。Markdown→HTML 用 commonmark；HTML→PDF 用 openhtmltopdf-pdfbox。
 * HTML 外层套固定 CSS 模板，保证前端预览与 PDF 渲染样式一致。</p>
 *
 * <p><b>中文字体策略（务实方案）</b>：openhtmltopdf 默认不支持中文，需通过
 * {@code useFont} 注册中文字体。本服务优先从 {@code src/main/resources/fonts/}
 * 读取内嵌字体；若不存在，则扫描系统已安装中文字体家族（SimSun、Noto Sans CJK SC、
 * WenQuanYi Micro Hei 等）并注册其字体文件；若两者都不可用，则降级：跳过 PDF 生成，
 * 返回 {@code null}，仅保留 HTML，调用方应将 {@code pdf_file_path} 置空且不让任务失败。</p>
 *
 * <p>PDF 落地目录由 {@link AnalysisReportProperties#getStorageDir()} 配置，文件名
 * {@code {taskId}.pdf}。目录在写入前按需创建。</p>
 */
@Slf4j
@Service
public class ReportExportService {

    /** PDF 中文字体家族注册名（与 CSS font-family 对齐） */
    private static final String PDF_FONT_FAMILY = "ReportCJK";

    /** 候选内嵌字体资源路径（resources 下，未提供时为空） */
    private static final String EMBEDDED_FONT_RESOURCE = "/fonts/report-cjk.ttf";

    /** 候选系统字体家族（按优先级排序，命中第一个即用） */
    private static final String[] CANDIDATE_SYSTEM_FONTS = new String[] {
            "SimSun", "SimHei", "Microsoft YaHei", "Noto Sans CJK SC",
            "Noto Sans CJK TC", "Source Han Sans SC", "Source Han Sans CN",
            "WenQuanYi Micro Hei", "WenQuanYi Zen Hei", "Arial Unicode MS"
    };

    private final AnalysisReportProperties properties;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final java.util.List<org.commonmark.Extension> tableExtensions =
            java.util.List.of(TablesExtension.create());

    /**
     * 构造器注入配置；commonmark 解析器与渲染器在此初始化（线程安全，可复用）。
     * 启用 GFM 表格扩展，支持报告中的指标表格渲染。
     *
     * @param properties 报告模块配置
     */
    public ReportExportService(AnalysisReportProperties properties) {
        this.properties = properties;
        this.markdownParser = Parser.builder().extensions(tableExtensions).build();
        this.htmlRenderer = HtmlRenderer.builder().extensions(tableExtensions).build();
    }

    /**
     * 将 Markdown 渲染为完整 HTML 文档（含固定 CSS）。
     *
     * <p>HTML 包在 {@code <html><head><style>CSS</style></head><body>...</body></html>}
     * 中，CSS 定义中文字体、标题、表格、代码块等基础样式，保证预览与 PDF 一致。</p>
     *
     * @param markdown Markdown 源内容
     * @return 完整 HTML 文档字符串
     */
    public String renderHtml(String markdown) {
        String body = markdownToHtml(markdown);
        return wrapWithCss(body);
    }

    /**
     * 将 HTML 文档渲染为 PDF 并写入 {@code analysis.report.storage-dir}/{taskId}.pdf。
     *
     * <p>中文字体不可用时降级：log.warn 后返回 {@code null}，调用方应将
     * {@code pdf_file_path} 置空且不让任务失败（HTML 仍正常生成）。</p>
     *
     * @param fullHtml 完整 HTML 文档（含 CSS，由 {@link #renderHtml} 生成）
     * @param taskId   报告任务 ID，用于命名 PDF 文件
     * @return PDF 文件绝对路径；中文字体不可用时返回 {@code null}
     * @throws IOException 目录创建或文件写入失败时抛出
     */
    public String renderPdf(String fullHtml, Long taskId) throws IOException {
        FontSource fontSource = resolveChineseFont();
        if (fontSource == null) {
            log.warn("PDF 中文字体不可用，跳过 PDF 生成降级为仅 HTML：taskId={}", taskId);
            return null;
        }
        Path dir;
        Path pdfPath;
        try {
            dir = Paths.get(properties.getStorageDir());
            Files.createDirectories(dir);
            pdfPath = dir.resolve(taskId + ".pdf");
        } catch (Exception ex) {
            // 含 InvalidPathException 等：存储目录非法时降级返回 null，不抛异常
            log.warn("PDF 存储目录不可用，跳过 PDF 生成：taskId={}, reason={}",
                    taskId, ex.getClass().getSimpleName());
            return null;
        }
        // 先读取字体字节（构建器在 run() 时按需读取，需独立流），读取后立即关闭源流。
        byte[] fontBytes;
        try (InputStream fontStream = fontSource.openStream()) {
            fontBytes = fontStream.readAllBytes();
        } catch (IOException ex) {
            log.warn("中文字体流读取失败，跳过 PDF 生成：taskId={}, reason={}",
                    taskId, ex.getClass().getSimpleName());
            return null;
        }
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(fullHtml, dir.toAbsolutePath().toString());
            builder.useFont(() -> new java.io.ByteArrayInputStream(fontBytes), PDF_FONT_FAMILY);
            try (java.io.OutputStream output = Files.newOutputStream(pdfPath)) {
                builder.toStream(output);
                builder.run();
            }
        } catch (Exception ex) {
            log.warn("PDF 生成失败，降级为仅 HTML：taskId={}, reason={}",
                    taskId, ex.getClass().getSimpleName());
            // 清理可能产生的空文件
            try {
                Files.deleteIfExists(pdfPath);
            } catch (IOException ignore) {
                // 忽略清理失败
            }
            return null;
        }
        log.info("PDF 生成成功：taskId={}, path={}", taskId, pdfPath);
        return pdfPath.toAbsolutePath().toString();
    }

    // ==================== 内部实现 ====================

    private String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }

    private String wrapWithCss(String bodyHtml) {
        // openhtmltopdf 用 XHTML/XML 解析器，meta 等 void 元素必须自闭合（/>）。
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />"
                + "<style>" + REPORT_CSS + "</style></head><body>"
                + bodyHtml
                + "</body></html>";
    }

    /**
     * 解析可用中文字体来源：优先内嵌字体，其次系统字体。
     *
     * <p>可见性为 {@code protected} 以便单元测试注入已知可用字体，
     * 从而在无中文字体的 CI/测试环境稳定验证 PDF 生成成功路径
     * （见 {@code ReportExportServiceTest#shouldGeneratePdfToTempDirWhenFontAvailable}）。</p>
     *
     * @return 字体来源；不可用时返回 {@code null}
     */
    protected FontSource resolveChineseFont() {
        // 1. 内嵌字体（try-with-resources 确保所有路径关闭流）
        try (InputStream embedded = getClass().getResourceAsStream(EMBEDDED_FONT_RESOURCE)) {
            if (embedded == null) {
                // 未内嵌字体，进入系统字体查找
            } else {
                byte[] bytes = embedded.readAllBytes();
                log.info("PDF 使用内嵌中文字体：{}", EMBEDDED_FONT_RESOURCE);
                return new FontSource("embedded:" + EMBEDDED_FONT_RESOURCE,
                        () -> new ByteArrayInputStream(bytes));
            }
        } catch (IOException e) {
            log.warn("读取内嵌中文字体失败", e);
            // 进入系统字体查找
        }
        // 2. 系统字体
        return findSystemChineseFont();
    }

    private FontSource findSystemChineseFont() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] families = ge.getAvailableFontFamilyNames(Locale.SIMPLIFIED_CHINESE);
        if (families == null) {
            families = ge.getAvailableFontFamilyNames();
        }
        java.util.Set<String> familySet = new java.util.HashSet<>();
        if (families != null) {
            for (String f : families) {
                familySet.add(f);
            }
        }
        for (String candidate : CANDIDATE_SYSTEM_FONTS) {
            if (familySet.contains(candidate)) {
                File fontFile = locateFontFile(candidate);
                if (fontFile != null && fontFile.canRead()) {
                    log.info("PDF 使用系统中文字体：{} -> {}", candidate, fontFile.getAbsolutePath());
                    return new FontSource("system:" + candidate,
                            () -> {
                                try {
                                    return new FileInputStream(fontFile);
                                } catch (IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                }
            }
        }
        return null;
    }

    /**
     * 通过 AWT 字体路径定位系统字体文件。
     *
     * <p>Java AWT 不直接暴露字体文件路径，这里用 {@link java.awt.Font} 创建后无法取路径。
     * 采用保守方案：在常见系统字体目录下查找匹配文件名。若找不到返回 {@code null}，
     * 由调用方降级。</p>
     */
    private File locateFontFile(String familyName) {
        String[] candidateDirs = new String[] {
                "C:\\Windows\\Fonts",
                "/usr/share/fonts",
                "/usr/local/share/fonts",
                "/usr/share/fonts/truetype",
                System.getProperty("user.home") + "/.fonts"
        };
        String[] candidateFileNames = fileNameCandidates(familyName);
        for (String dir : candidateDirs) {
            File dirFile = new File(dir);
            if (!dirFile.isDirectory()) {
                continue;
            }
            for (String name : candidateFileNames) {
                File f = new File(dirFile, name);
                if (f.isFile() && f.canRead()) {
                    return f;
                }
            }
        }
        return null;
    }

    private String[] fileNameCandidates(String familyName) {
        switch (familyName) {
            case "SimSun":
                return new String[] {"simsun.ttc", "simsun.ttf", "SIMSUN.TTC"};
            case "SimHei":
                return new String[] {"simhei.ttf", "SIMHEI.TTF"};
            case "Microsoft YaHei":
                return new String[] {"msyh.ttc", "msyh.ttf", "MSYH.TTC"};
            case "Noto Sans CJK SC":
                return new String[] {"NotoSansCJKsc-Regular.otf",
                        "NotoSansCJKsc-Regular.ttf", "NotoSansSC-Regular.otf"};
            case "Noto Sans CJK TC":
                return new String[] {"NotoSansCJKtc-Regular.otf",
                        "NotoSansCJKtc-Regular.ttf"};
            case "Source Han Sans SC":
            case "Source Han Sans CN":
                return new String[] {"SourceHanSansSC-Regular.otf",
                        "SourceHanSansCN-Regular.otf"};
            case "WenQuanYi Micro Hei":
                return new String[] {"wqy-microhei.ttc", "wqy-microhei.ttf"};
            case "WenQuanYi Zen Hei":
                return new String[] {"wqy-zenhei.ttc", "wqy-zenhei.ttf"};
            case "Arial Unicode MS":
                return new String[] {"arialuni.ttf", "ARIALUNI.TTF"};
            default:
                return new String[0];
        }
    }

    /** 字体来源抽象，便于在 useFont 回调中按需打开独立流。 */
    protected record FontSource(String name, java.util.function.Supplier<InputStream> opener) {
        InputStream openStream() {
            return opener.get();
        }
    }

    /** 报告固定 CSS，保证预览与 PDF 一致。 */
    private static final String REPORT_CSS = ""
            + "@page { margin: 24mm 18mm; size: A4; }"
            + "body { font-family: '" + PDF_FONT_FAMILY + "', 'SimSun', 'Noto Sans CJK SC',"
            + " sans-serif; font-size: 12pt; line-height: 1.7; color: #222; }"
            + "h1 { font-size: 22pt; color: #1a4d8f; border-bottom: 2px solid #1a4d8f;"
            + " padding-bottom: 6px; margin-top: 0; }"
            + "h2 { font-size: 17pt; color: #1a4d8f; margin-top: 20px; }"
            + "h3 { font-size: 14pt; color: #333; margin-top: 16px; }"
            + "p { margin: 8px 0; }"
            + "table { border-collapse: collapse; width: 100%; margin: 12px 0; }"
            + "th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; }"
            + "th { background: #f0f4f8; font-weight: bold; }"
            + "code { background: #f6f6f6; padding: 2px 5px; border-radius: 3px;"
            + " font-family: monospace; font-size: 11pt; }"
            + "pre { background: #f6f6f6; padding: 10px; border-radius: 4px;"
            + " overflow-x: auto; }"
            + "pre code { background: transparent; padding: 0; }"
            + "blockquote { border-left: 4px solid #1a4d8f; margin: 10px 0;"
            + " padding: 4px 14px; color: #555; background: #f8f9fa; }"
            + "ul, ol { margin: 8px 0; padding-left: 24px; }"
            + "li { margin: 4px 0; }"
            + "strong { color: #1a4d8f; }";
}
