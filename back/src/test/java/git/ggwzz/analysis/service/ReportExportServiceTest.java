package git.ggwzz.analysis.service;

import git.ggwzz.analysis.config.AnalysisReportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link ReportExportService} 单元测试。
 *
 * <p>验证：Markdown→HTML 含固定 CSS 与中英文正文；PDF 生成到临时目录可成功
 * （通过注入测试字体 {@code src/test/resources/fonts/test-font.ttf} 稳定走成功路径，
 * 不依赖测试环境是否安装中文字体）；存储目录非法时降级返回 null。</p>
 */
class ReportExportServiceTest {

    /** 测试字体资源路径；不存在时回退到本机系统字体。 */
    private static final String TEST_FONT_RESOURCE = "/fonts/test-font.ttf";

    /** Windows 常见字体候选；测试内容是英文，Arial 等字体足够用于 PDF 成功路径。 */
    private static final List<Path> LOCAL_FONT_CANDIDATES = List.of(
            Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "arial.ttf"),
            Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "calibri.ttf"),
            Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts", "times.ttf"));

    @TempDir
    Path tempDir;

    private ReportExportService.FontSource loadTestFontSource() {
        try (InputStream in = getClass().getResourceAsStream(TEST_FONT_RESOURCE)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return new ReportExportService.FontSource("test:" + TEST_FONT_RESOURCE,
                        () -> new java.io.ByteArrayInputStream(bytes));
            }
        } catch (IOException e) {
            throw new AssertionError("加载测试字体失败", e);
        }

        Path localFont = LOCAL_FONT_CANDIDATES.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        assumeTrue(localFont != null, "未找到测试字体资源或本机系统字体，跳过 PDF 字体相关测试");
        return new ReportExportService.FontSource("system:" + localFont,
                () -> {
                    try {
                        return Files.newInputStream(localFont);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    /**
     * 构造被测服务，{@code resolveChineseFont} 被重写为始终返回测试字体，
     * 使 PDF 生成成功路径在任意环境（含无中文字体的 CI）稳定可验证。
     */
    private ReportExportService newService() {
        AnalysisReportProperties props = new AnalysisReportProperties();
        props.setStorageDir(tempDir.toAbsolutePath().toString());
        return new ReportExportService(props) {
            @Override
            protected FontSource resolveChineseFont() {
                return loadTestFontSource();
            }
        };
    }

    @Test
    void shouldRenderMarkdownToHtmlWithFixedCss() {
        ReportExportService service = newService();
        String md = "# 健康分析报告\n\n## 产业链概览\n\n节点数量 **5**，企业 **12** 家。\n\n"
                + "| 指标 | 值 |\n| --- | --- |\n| 健康度 | 82 |\n";
        String html = service.renderHtml(md);

        assertNotNull(html);
        assertTrue(html.contains("<html"), "应包在完整 html 文档中");
        assertTrue(html.contains("<style>"), "应包含固定 CSS");
        assertTrue(html.contains("font-family"), "CSS 应含字体定义");
        assertTrue(html.contains("<h1>健康分析报告</h1>"), "应渲染中文标题");
        assertTrue(html.contains("<table>"), "应渲染表格");
        assertTrue(html.contains("<strong>5</strong>"), "应渲染粗体");
    }

    @Test
    void shouldRenderEmptyMarkdownToEmptyHtml() {
        ReportExportService service = newService();
        String html = service.renderHtml(null);
        assertNotNull(html);
        assertTrue(html.contains("<html"));
        assertFalse(html.contains("<h1>"));
    }

    @Test
    void shouldGeneratePdfToTempDirWhenFontAvailable() throws Exception {
        ReportExportService service = newService();
        // 用英文内容生成 PDF（测试字体为 DejaVu Sans，含拉丁字形），
        // 通过注入字体确保成功路径稳定执行，不再因字体缺失而跳过。
        String html = service.renderHtml("# Report\n\nHello world content.\n");
        String path = service.renderPdf(html, 99001L);

        assertNotNull(path, "注入测试字体后 PDF 应生成成功，不应降级返回 null");
        Path pdf = Path.of(path);
        assertTrue(Files.exists(pdf), "PDF 文件应存在");
        assertTrue(Files.size(pdf) > 0, "PDF 文件应非空");
        assertTrue(pdf.getFileName().toString().equals("99001.pdf"),
                "文件名应为 {taskId}.pdf");
    }

    @Test
    void shouldReturnNullWhenStorageDirInvalidAndDegradeGracefully() throws Exception {
        AnalysisReportProperties props = new AnalysisReportProperties();
        // 指向一个无法创建的路径（Windows 下根盘的非法子路径）触发降级
        props.setStorageDir("Z:/:::invalid:::/" + System.nanoTime());
        ReportExportService service = new ReportExportService(props) {
            @Override
            protected FontSource resolveChineseFont() {
                return loadTestFontSource();
            }
        };
        String html = service.renderHtml("# x");
        String path = service.renderPdf(html, 99002L);
        // 降级：目录创建失败应被 catch，返回 null 而非抛异常
        assertNull(path);
    }
}

