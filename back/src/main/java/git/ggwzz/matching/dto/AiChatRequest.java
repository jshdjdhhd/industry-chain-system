package git.ggwzz.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 聊天请求。
 *
 * <p>企业与产业链都不选择时使用通用问答模式；选择任意企业、任意产业链或二者组合时使用上下文问答模式。</p>
 */
@Data
public class AiChatRequest {

    /** 企业 ID。 */
    @Positive(message = "企业和产业链 ID 必须为正整数")
    private Long enterpriseId;

    /** 产业链 ID。 */
    @Positive(message = "企业和产业链 ID 必须为正整数")
    private Long chainId;

    /** 批量企业 ID。 */
    @Size(max = 20, message = "单次最多选择 20 家企业")
    private List<@Positive(message = "企业和产业链 ID 必须为正整数") Long> enterpriseIds;

    /** 批量产业链 ID。 */
    @Size(max = 20, message = "单次最多选择 20 条产业链")
    private List<@Positive(message = "企业和产业链 ID 必须为正整数") Long> chainIds;

    /** 当前消息。 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过 4000 字符")
    private String message;

    /** 会话历史。 */
    @Size(max = 50, message = "历史消息不能超过 50 条")
    private List<@Valid ChatMessage> history;

    /**
     * AI 聊天历史消息。
     */
    @Data
    public static class ChatMessage {

        /** 消息角色。 */
        @NotBlank(message = "历史消息角色不能为空")
        @Pattern(regexp = "user|assistant", message = "历史消息角色不合法")
        private String role;

        /** 消息内容。 */
        @NotBlank(message = "历史消息内容不能为空")
        @Size(max = 4000, message = "历史消息内容不能超过 4000 字符")
        private String content;
    }
}
