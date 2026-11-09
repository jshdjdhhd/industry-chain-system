package git.ggwzz.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnalysisReportResultParser} 单元测试。
 *
 * <p>覆盖：正常 JSON、空 markdown 字段、非 JSON 文本、带 markdown code fence 的 JSON、
 * 多余前后文本包裹的 JSON。</p>
 */
class AnalysisReportResultParserTest {

    private AnalysisReportResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new AnalysisReportResultParser(new ObjectMapper());
    }

    @Test
    void shouldParseMarkdownFromJsonObject() {
        String content = "{\"markdown\":\"# 报告标题\\n\\n正文内容\"}";

        String markdown = parser.parseMarkdown(content);

        assertEquals("# 报告标题\n\n正文内容", markdown);
    }

    @Test
    void shouldExtractJsonObjectFromCodeFence() {
        String content = "```json\n{\"markdown\":\"## 一、分析范围\\n- 节点 A\"}\n```";

        String markdown = parser.parseMarkdown(content);

        assertEquals("## 一、分析范围\n- 节点 A", markdown);
    }

    @Test
    void shouldExtractJsonObjectSurroundedByText() {
        String content = "好的，这是报告：\n{\"markdown\":\"# 报告\"}\n以上为结果。";

        String markdown = parser.parseMarkdown(content);

        assertEquals("# 报告", markdown);
    }

    @Test
    void shouldExtractFirstJsonObjectWhenTrailingTextContainsBraces() {
        // JSON 后附含 } 的自然语言，括号配平只取首个完整 JSON，不会贪婪到末尾 }
        String content = "{\"markdown\":\"# 报告\"}\n注：本报告由系统生成，禁止外传。}";

        String markdown = parser.parseMarkdown(content);

        assertEquals("# 报告", markdown);
    }

    @Test
    void shouldExtractFirstJsonObjectWhenMultipleObjectsPresent() {
        String content = "{\"markdown\":\"# 第一份\"}\n后续无关对象 {\"other\":\"x\"}";

        String markdown = parser.parseMarkdown(content);

        assertEquals("# 第一份", markdown);
    }

    @Test
    void shouldHandleNestedBracesInMarkdownValue() {
        // markdown 值内含 } 字符（字符串内括号不计入配平）
        String content = "{\"markdown\":\"代码示例：\\n```\\nif (x) { }\\n```\\n结束\"}";

        String markdown = parser.parseMarkdown(content);

        assertTrue(markdown.contains("if (x) { }"));
    }

    @Test
    void shouldThrowWhenMarkdownFieldEmpty() {
        String content = "{\"markdown\":\"\"}";

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parseMarkdown(content));
        assertTrue(ex.getMessage().contains("markdown 字段为空"));
    }

    @Test
    void shouldThrowWhenNoJsonObjectFound() {
        String content = "这是一段没有 JSON 的纯文本报告。";

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parseMarkdown(content));
        assertTrue(ex.getMessage().contains("LLM 响应解析失败"));
    }

    @Test
    void shouldThrowWhenMarkdownFieldMissing() {
        String content = "{\"other\":\"value\"}";

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parseMarkdown(content));
        assertTrue(ex.getMessage().contains("markdown 字段缺失"));
    }

    @Test
    void shouldThrowWhenContentNull() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parseMarkdown(null));
        assertTrue(ex.getMessage().contains("LLM 响应解析失败"));
    }
}
