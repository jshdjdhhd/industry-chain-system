package git.ggwzz.analysis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 健康分析报告模块配置，绑定 {@code analysis.report.*} 配置项。
 *
 * <p>当前仅包含 PDF 文件落地目录。配置风格遵循扁平小写，与项目既有
 * {@code matching.llm.*} 一致：默认值通过环境变量覆盖。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "analysis.report")
public class AnalysisReportProperties {

    /**
     * PDF 文件落地目录，默认 {@code ./data/reports/analysis}。
     *
     * <p>生产环境通过环境变量 {@code ANALYSIS_REPORT_DIR} 覆盖；目录由
     * {@link git.ggwzz.analysis.service.ReportExportService} 在写入前按需创建。</p>
     */
    private String storageDir = "./data/reports/analysis";
}
