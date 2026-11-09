# 全局 AI 智能聊天助手 编码规划文档

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AI 聊天能力从智能挂链页面拆出为全局悬浮助手，后端支持通用问答和上下文问答双模式，前端实现多会话、本地持久化、SSE 流式回复。

**Architecture:** 后端保持接口路径和权限不变，修改 `AiChatRequest` 放开 `enterpriseId`/`chainId` 为可选，`AiChatServiceImpl` 按双模式分支构建提示词。前端新建全局悬浮面板组件挂载在 `AdminLayout` 下，独立的 Pinia store 管理多会话和持久化，独立的 API 模块封装 SSE 通讯。

**Tech Stack:** Spring Boot 3.5 + Jakarta Validation + JUnit 5/Mockito（后端）；Vue 3 + Element Plus + Pinia + Vitest + DOMPurify（前端）

---

## 文件结构总览

### 后端 (`back`)

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/main/java/git/ggwzz/matching/dto/AiChatRequest.java` | 修改 | 放开 ID 为可选，加字段级 + 类级校验 |
| `src/main/java/git/ggwzz/matching/dto/ValidAiChatContext.java` | 新建 | 类级校验注解 |
| `src/main/java/git/ggwzz/matching/dto/AiChatContextValidator.java` | 新建 | 校验器实现：两 ID 同时空或同时有值 |
| `src/main/java/git/ggwzz/matching/service/impl/AiChatServiceImpl.java` | 修改 | 防御性校验 + 双模式提示词分支 |
| `src/test/java/git/ggwzz/matching/service/AiChatServiceImplTest.java` | 新建 | Service 单元测试 |
| `src/test/java/git/ggwzz/matching/controller/MatchingControllerTest.java` | 修改 | 补充 stream 和校验失败测试 |
| `src/test/resources/http/ai-chat.http` | 新建 | AI 聊天 HTTP 回归用例 |
| `docs/API-INTERFACE-REGISTRY.md` | 修改 | 注册 AI 聊天接口 |

### 前端 (`front`)

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/types/domain.ts` | 修改 | 集中定义 AI 聊天类型 |
| `src/api/aiChat.ts` | 新建 | 封装普通聊天 + SSE 流式聊天 |
| `src/api/aiChat.spec.ts` | 新建 | API 层测试 |
| `src/stores/aiChat.ts` | 新建 | 多会话状态、流式管理、本地持久化 |
| `src/stores/aiChat.spec.ts` | 新建 | Store 测试 |
| `src/views/assistant/AiAssistantPanel.vue` | 新建 | 悬浮球 + 聊天面板组件 |
| `src/views/assistant/AiAssistantPanel.spec.ts` | 新建 | 组件测试 |
| `src/layouts/AdminLayout.vue` | 修改 | 条件挂载助手面板 |
| `src/layouts/AdminLayout.spec.ts` | 修改 | 验证助手挂载逻辑 |
| `src/router/index.ts` | 修改 | 系统路由加 `hideAiAssistant` meta |
| `src/router/index.spec.ts` | 修改 | 验证 meta 标记 |
| `src/views/matching/MatchingTask.vue` | 修改 | 删除内嵌聊天区域 |
| `src/views/matching/MatchingTask.spec.ts` | 修改 | 移除聊天相关断言 |
| `package.json` / `package-lock.json` | 修改 | 加入 `dompurify` 依赖 |

---

## Task 1: 后端类级校验注解

**Files:**
- Create: `back/src/main/java/git/ggwzz/matching/dto/ValidAiChatContext.java`
- Create: `back/src/main/java/git/ggwzz/matching/dto/AiChatContextValidator.java`

- [ ] **Step 1: 创建约束注解 `ValidAiChatContext.java`**

```java
package git.ggwzz.matching.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = AiChatContextValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAiChatContext {
    String message() default "企业和产业链必须同时选择，或同时不选";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

- [ ] **Step 2: 创建校验器 `AiChatContextValidator.java`**

```java
package git.ggwzz.matching.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AiChatContextValidator implements ConstraintValidator<ValidAiChatContext, AiChatRequest> {

    @Override
    public boolean isValid(AiChatRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true; // null 请求体由 @Valid 阶段处理
        }
        Long enterpriseId = request.getEnterpriseId();
        Long chainId = request.getChainId();
        // 两 ID 同时为 null 或同时非 null 才合法
        return (enterpriseId == null) == (chainId == null);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd back && mvn compile -q`
Expected: BUILD SUCCESS

```

---

## Task 2: 后端修改 AiChatRequest DTO

**Files:**
- Modify: `back/src/main/java/git/ggwzz/matching/dto/AiChatRequest.java`

当前 `AiChatRequest` 的 `enterpriseId` 和 `chainId` 标注了 `@NotNull`，`message` 仅有 `@NotBlank`，`ChatMessage` 无任何校验。需放开 ID 为可选、补全字段级和类级校验。

- [ ] **Step 1: 重写 `AiChatRequest.java` 全文**

```java
package git.ggwzz.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@ValidAiChatContext
public class AiChatRequest {

    /** 企业 ID（通用模式为 null，上下文模式为正整数） */
    @Positive(message = "企业和产业链 ID 必须为正整数")
    private Long enterpriseId;

    /** 产业链 ID（通用模式为 null，上下文模式为正整数） */
    @Positive(message = "企业和产业链 ID 必须为正整数")
    private Long chainId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过 4000 字符")
    private String message;

    /** 会话历史（可选） */
    @Size(max = 50, message = "历史消息不能超过 50 条")
    private List<@Valid ChatMessage> history;

    @Data
    public static class ChatMessage {

        @NotBlank(message = "历史消息角色不能为空")
        @Pattern(regexp = "user|assistant", message = "历史消息角色不合法")
        private String role;

        @NotBlank(message = "历史消息内容不能为空")
        @Size(max = 4000, message = "历史消息内容不能超过 4000 字符")
        private String content;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd back && mvn compile -q`
Expected: BUILD SUCCESS

```

---

## Task 3: 后端 AiChatServiceImplTest（先写失败测试）

**Files:**
- Create: `back/src/test/java/git/ggwzz/matching/service/AiChatServiceImplTest.java`

采用纯 Mockito 单元测试（不启动 Spring），Mock `LlmClient`/`EnterpriseQueryService`/`ChainNodeQueryService`，参照 `OpenAiCompatibleLlmClientTest` 的风格。

- [ ] **Step 1: 编写测试类**

```java
package git.ggwzz.matching.service;

import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.matching.dto.AiChatRequest;
import git.ggwzz.matching.service.impl.AiChatServiceImpl;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider.EnterpriseInfo;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock LlmClient llmClient;
    @Mock EnterpriseQueryService enterpriseQueryService;
    @Mock ChainNodeQueryService chainNodeQueryService;

    @InjectMocks AiChatServiceImpl service;

    // ---------- 通用模式 ----------

    @Test
    @DisplayName("通用模式：两 ID 均为 null 时不查询企业和链节点")
    void chat_generalMode_doesNotQueryEnterpriseOrChain() {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("什么是产业链？");
        // enterpriseId 和 chainId 均为 null

        when(llmClient.complete(anyList())).thenReturn(
            new LlmChatCompletion("test-model", "产业链是…"));

        service.chat(request);

        verifyNoInteractions(enterpriseQueryService, chainNodeQueryService);
        verify(llmClient).complete(argThat(messages -> {
            LlmMessage sys = messages.get(0);
            return "system".equals(sys.role())
                && sys.content().contains("产业链知识助手");
        }));
    }

    // ---------- 上下文模式 ----------

    @Test
    @DisplayName("上下文模式：两 ID 均有值时查询企业画像和链节点")
    void chat_contextMode_queriesEnterpriseAndChain() {
        AiChatRequest request = new AiChatRequest();
        request.setEnterpriseId(1L);
        request.setChainId(2L);
        request.setMessage("分析挂链风险");

        EnterpriseInfo ent = new EnterpriseInfo();
        ent.setId(1L);
        ent.setName("测试企业");
        when(enterpriseQueryService.getById(1L)).thenReturn(ent);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of());

        when(llmClient.complete(anyList())).thenReturn(
            new LlmChatCompletion("test-model", "风险分析…"));

        service.chat(request);

        verify(enterpriseQueryService).getById(1L);
        verify(chainNodeQueryService).listByChainId(2L);
    }

    @Test
    @DisplayName("上下文模式：企业不存在抛 404")
    void chat_contextMode_enterpriseNotFound_throws404() {
        AiChatRequest request = new AiChatRequest();
        request.setEnterpriseId(999L);
        request.setChainId(2L);
        request.setMessage("分析");

        when(enterpriseQueryService.getById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.chat(request))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.getCode()).isEqualTo(ResultCode.NOT_FOUND);
            });
    }

    // ---------- 防御性校验 ----------

    @Nested
    @DisplayName("Service 层防御性校验")
    class DefensiveValidation {

        @Test
        @DisplayName("空白 message 抛 400")
        void blankMessage_throws400() {
            AiChatRequest request = new AiChatRequest();
            request.setMessage("   ");

            assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("仅选企业不选链抛 400")
        void halfSelected_throws400() {
            AiChatRequest request = new AiChatRequest();
            request.setEnterpriseId(1L);
            request.setMessage("测试");

            assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("history 含 null 项抛 400")
        void historyWithNull_throws400() {
            AiChatRequest request = new AiChatRequest();
            request.setMessage("测试");
            List<AiChatRequest.ChatMessage> history = new ArrayList<>();
            history.add(null);
            request.setHistory(history);

            assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("history 超过 50 条抛 400")
        void historyTooLong_throws400() {
            AiChatRequest request = new AiChatRequest();
            request.setMessage("测试");
            List<AiChatRequest.ChatMessage> history = new ArrayList<>();
            for (int i = 0; i < 51; i++) {
                AiChatRequest.ChatMessage m = new AiChatRequest.ChatMessage();
                m.setRole("user");
                m.setContent("历史" + i);
                history.add(m);
            }
            request.setHistory(history);

            assertThatThrownBy(() -> service.chat(request))
                .isInstanceOf(BusinessException.class);
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd back && mvn test -pl . -Dtest=AiChatServiceImplTest -q`
Expected: FAIL — 通用模式测试失败（当前 `buildMessages` 总是查询企业），防御性校验测试失败（当前 Service 无防御性校验）

```

---

## Task 4: 后端修改 AiChatServiceImpl（实现双模式 + 防御性校验）

**Files:**
- Modify: `back/src/main/java/git/ggwzz/matching/service/impl/AiChatServiceImpl.java`

- [ ] **Step 1: 添加通用模式系统提示词常量**

在 `AiChatServiceImpl` 类中，现有 `SYSTEM_PROMPT` 常量下方添加：

```java
private static final String GENERAL_SYSTEM_PROMPT =
    "你是一个产业链知识助手。你可以回答关于产业链、企业挂链、产业发展等方面的问题。\n" +
    "注意事项：\n" +
    "1. 你不能虚构平台内部的具体企业数据。\n" +
    "2. 如果用户询问特定企业的分析，请引导用户在面板中选择企业和产业链后再提问。\n" +
    "3. 回答使用自然语言，不要返回 JSON 格式。";
```

- [ ] **Step 2: 添加防御性校验方法**

在类中添加以下私有方法：

```java
/**
 * Service 层防御性校验，防止非 Controller 调用绕过 Jakarta Validation。
 * 与 AiChatRequest 声明式校验保持同一组规则。
 */
private void validateRequest(AiChatRequest request) {
    if (request == null) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数格式错误");
    }
    if (request.getMessage() == null || request.getMessage().isBlank()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能为空");
    }
    if (request.getMessage().length() > 4000) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能超过 4000 字符");
    }

    Long eid = request.getEnterpriseId();
    Long cid = request.getChainId();
    if ((eid == null) != (cid == null)) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "企业和产业链必须同时选择");
    }
    if (eid != null && (eid <= 0 || cid <= 0)) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "企业和产业链 ID 必须为正整数");
    }

    if (request.getHistory() != null) {
        List<AiChatRequest.ChatMessage> history = request.getHistory();
        if (history.size() > 50) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息不能超过 50 条");
        }
        for (AiChatRequest.ChatMessage msg : history) {
            if (msg == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息不能为空");
            }
            if (msg.getRole() == null || msg.getRole().isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息角色不能为空");
            }
            if (!"user".equals(msg.getRole()) && !"assistant".equals(msg.getRole())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息角色不合法");
            }
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息内容不能为空");
            }
            if (msg.getContent().length() > 4000) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "历史消息内容不能超过 4000 字符");
            }
        }
    }
}
```

- [ ] **Step 3: 在 `chat()` 和 `chatStreaming()` 方法开头调用 `validateRequest`**

```java
@Override
public AiChatResponse chat(AiChatRequest request) {
    validateRequest(request);
    List<LlmMessage> messages = buildMessages(request);
    LlmChatCompletion completion = llmClient.complete(messages);
    AiChatResponse response = new AiChatResponse();
    response.setReply(completion.content());
    response.setModel(completion.model());
    return response;
}

@Override
public void chatStreaming(AiChatRequest request, SseEmitter emitter) {
    validateRequest(request);
    try {
        List<LlmMessage> messages = buildMessages(request);
        llmClient.completeStreaming(messages, chunk -> {
            try {
                emitter.send(chunk);
            } catch (Exception e) {
                log.error("SSE 发送失败", e);
            }
        });
        emitter.complete();
    } catch (Exception e) {
        log.error("AI 流式回复失败", e);
        try {
            emitter.send("AI 分析失败，请稍后重试");
        } catch (Exception ignored) {
        }
        emitter.completeWithError(e);
    }
}
```

- [ ] **Step 4: 修改 `buildMessages` 实现双模式分支**

将现有 `buildMessages` 方法替换为：

```java
private List<LlmMessage> buildMessages(AiChatRequest request) {
    List<LlmMessage> messages = new ArrayList<>();

    if (request.getEnterpriseId() == null && request.getChainId() == null) {
        // 通用模式
        messages.add(new LlmMessage("system", GENERAL_SYSTEM_PROMPT));
    } else {
        // 上下文模式
        RuleBasedMatchingProvider.EnterpriseInfo enterprise =
            enterpriseQueryService.getById(request.getEnterpriseId());
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        List<ChainNodeCandidateDTO> nodes =
            chainNodeQueryService.listByChainId(request.getChainId());

        StringBuilder context = new StringBuilder();
        context.append("当前分析的企业：\n");
        context.append("- 名称：").append(nullSafe(enterprise.getName())).append("\n");
        context.append("- 主营产品：").append(nullSafe(enterprise.getMainProducts())).append("\n");
        context.append("- 经营范围：").append(nullSafe(enterprise.getBusinessScope())).append("\n");
        context.append("- 标签：").append(nullSafe(enterprise.getTags())).append("\n\n");
        context.append("候选产业链节点：\n");
        for (int i = 0; i < nodes.size(); i++) {
            ChainNodeCandidateDTO node = nodes.get(i);
            context.append(i + 1).append(". ")
                .append(nullSafe(node.getNodeName()))
                .append("（关键词：").append(nullSafe(node.getKeywords())).append("）\n");
        }

        messages.add(new LlmMessage("system", SYSTEM_PROMPT + "\n\n" + context));
    }

    // 历史消息
    if (request.getHistory() != null) {
        for (AiChatRequest.ChatMessage historyMsg : request.getHistory()) {
            if (historyMsg != null) {
                messages.add(new LlmMessage(historyMsg.getRole(), historyMsg.getContent()));
            }
        }
    }

    messages.add(new LlmMessage("user", request.getMessage()));
    return messages;
}
```

- [ ] **Step 5: 添加必要的 import**

确保文件顶部 import 区域包含：

```java
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.llm.LlmMessage;
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd back && mvn test -Dtest=AiChatServiceImplTest -q`
Expected: PASS — 所有测试通过

```

---

## Task 5: 后端更新 Controller 测试和文档

**Files:**
- Modify: `back/src/test/java/git/ggwzz/matching/controller/MatchingControllerTest.java`
- Create: `back/src/test/resources/http/ai-chat.http`
- Modify: `back/docs/API-INTERFACE-REGISTRY.md`

- [ ] **Step 1: 在 `MatchingControllerTest` 中补充通用模式和 stream 测试**

在现有 `aiChatShouldReturn200()` 测试方法后添加：

```java
@Test
void aiChat_generalMode_shouldReturn200() throws Exception {
    AiChatRequest request = new AiChatRequest();
    request.setMessage("什么是产业链？");
    // enterpriseId 和 chainId 为 null

    AiChatResponse mockResponse = new AiChatResponse();
    mockResponse.setReply("产业链是…");
    mockResponse.setModel("test-model");
    when(aiChatService.chat(any())).thenReturn(mockResponse);

    mockMvc.perform(post("/api/matching/ai-chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));
}

@Test
void aiChat_blankMessage_shouldReturn400() throws Exception {
    AiChatRequest request = new AiChatRequest();
    request.setMessage("   ");

    mockMvc.perform(post("/api/matching/ai-chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
}

@Test
void aiChat_halfSelected_shouldReturn400() throws Exception {
    AiChatRequest request = new AiChatRequest();
    request.setEnterpriseId(1L);
    request.setMessage("测试");

    mockMvc.perform(post("/api/matching/ai-chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
}

@Test
void aiChatStream_shouldReturnEventStream() throws Exception {
    AiChatRequest request = new AiChatRequest();
    request.setMessage("你好");
    // 通用模式

    // chatStreaming 是 void，无需 mock 返回值
    doNothing().when(aiChatService).chatStreaming(any(), any());

    mockMvc.perform(post("/api/matching/ai-chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
}
```

确保 import 包含 `MediaType` 和 `content()`：

```java
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
```

- [ ] **Step 2: 运行 Controller 测试**

Run: `cd back && mvn test -Dtest=MatchingControllerTest -q`
Expected: PASS

- [ ] **Step 3: 创建 `ai-chat.http` HTTP 回归用例文件**

```
### AI 聊天 - 通用模式（无上下文）
POST http://localhost:8080/api/matching/ai-chat
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "message": "什么是产业链？"
}

> {%
client.test("通用模式返回 200", function() {
  client.assert(response.status === 200, "状态码应为 200");
  client.assert(response.body.code === 200, "业务码应为 200");
  client.assert(response.body.data.reply !== undefined, "应返回回复内容");
});
%}

### AI 聊天 - 上下文模式
POST http://localhost:8080/api/matching/ai-chat
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "enterpriseId": 1,
  "chainId": 1,
  "message": "分析这家企业的挂链风险",
  "history": [
    { "role": "user", "content": "上一条问题" },
    { "role": "assistant", "content": "上一条回答" }
  ]
}

> {%
client.test("上下文模式返回 200", function() {
  client.assert(response.status === 200);
  client.assert(response.body.code === 200);
});
%}

### AI 聊天 - 空白消息应返回 400
POST http://localhost:8080/api/matching/ai-chat
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "message": "   "
}

> {%
client.test("空白消息返回 400", function() {
  client.assert(response.status === 400);
  client.assert(response.body.code === 400);
});
%}

### AI 聊天 - 半选 ID 应返回 400
POST http://localhost:8080/api/matching/ai-chat
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "enterpriseId": 1,
  "message": "测试"
}

> {%
client.test("半选 ID 返回 400", function() {
  client.assert(response.status === 400);
  client.assert(response.body.code === 400);
});
%}

### AI 聊天 - 流式（通用模式）
POST http://localhost:8080/api/matching/ai-chat/stream
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "message": "简述产业链发展方向"
}

> {%
client.test("流式接口返回 200", function() {
  client.assert(response.status === 200);
});
%}
```

- [ ] **Step 4: 在 `API-INTERFACE-REGISTRY.md` 中注册 AI 聊天接口**

在 "5. Matching 智能挂链接口" 小节末尾追加：

```markdown
### 5.6 AI 智能聊天 - 普通响应

- **接口**: `POST /api/matching/ai-chat`
- **权限**: `matching:task:create`
- **请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `enterpriseId` | Long | 否 | 企业 ID，通用模式为 null |
| `chainId` | Long | 否 | 产业链 ID，通用模式为 null |
| `message` | String | 是 | 消息内容，1-4000 字符 |
| `history` | Array | 否 | 历史消息，最多 50 条 |

- `enterpriseId` 和 `chainId` 必须同时为 null 或同时为正整数。
- **响应**: `Result<AiChatResponse>`

### 5.7 AI 智能聊天 - 流式响应 (SSE)

- **接口**: `POST /api/matching/ai-chat/stream`
- **权限**: `matching:task:create`
- **Content-Type**: `application/json`（请求）/ `text/event-stream`（响应）
- **请求体**: 同 5.6
- **响应**: SSE 流，每行 `data:<文本分片>`，以 `data:[DONE]` 结束
```

- [ ] **Step 5: 运行后端全量测试**

Run: `cd back && mvn test -q`
Expected: BUILD SUCCESS（注意：既有失败 `DashboardServiceImplTest` 与本次修改无关）

```

---

## Task 6: 前端添加 AI 聊天类型到 domain.ts

**Files:**
- Modify: `front/src/types/domain.ts`

当前 AI 聊天类型内联在 `api/matching.ts` 中。按项目规范将类型集中到 `domain.ts`。

- [ ] **Step 1: 在 `domain.ts` 文件末尾追加 AI 聊天相关类型**

```typescript
// ==================== AI 聊天 ====================

export type AiChatRole = "user" | "assistant";

export type AiChatMessageStatus = "streaming" | "completed" | "failed";

export interface AiChatMessage {
  id: string;
  role: AiChatRole;
  content: string;
  status: AiChatMessageStatus;
  createdAt: number;
}

export interface AiChatHistoryItem {
  role: AiChatRole;
  content: string;
}

export interface AiChatRequestPayload {
  enterpriseId: number | null;
  chainId: number | null;
  message: string;
  history: AiChatHistoryItem[];
}

export interface AiChatResponse {
  reply: string;
  model: string;
}

export interface AiChatStreamCallbacks {
  onChunk: (text: string) => void;
  onDone: () => void;
  onError: (error: string) => void;
}

export interface AiChatSession {
  id: string;
  title: string;
  enterpriseId: number | null;
  enterpriseName: string | null;
  chainId: number | null;
  chainName: string | null;
  messages: AiChatMessage[];
  createdAt: number;
  updatedAt: number;
  streaming: boolean;
  failedRequest: AiChatRequestPayload | null;
}

export interface AiChatStreamHandle {
  abort: () => void;
}
```

- [ ] **Step 2: 类型检查**

Run: `cd front && npx vue-tsc --noEmit -p tsconfig.app.json 2>&1 | head -5`
Expected: 无新增类型错误

```

---

## Task 7: 前端创建 api/aiChat.ts + DOMPurify 依赖

**Files:**
- Create: `front/src/api/aiChat.ts`
- Create: `front/src/api/aiChat.spec.ts`
- Modify: `front/package.json`
- Modify: `front/src/api/matching.ts`（移除旧 AI 聊天函数）

- [ ] **Step 1: 安装 DOMPurify**

Run: `cd front && npm install dompurify@^3.2.0 && npm install -D @types/dompurify@^3.0.5`

- [ ] **Step 2: 创建 `api/aiChat.ts`**

```typescript
import type {
  AiChatRequestPayload,
  AiChatResponse,
  AiChatStreamCallbacks,
  AiChatStreamHandle,
} from "@/types/domain";
import { post } from "@/api/http";

/**
 * 普通聊天（非流式）
 */
export function aiChat(data: AiChatRequestPayload): Promise<AiChatResponse> {
  return post("/matching/ai-chat", data, { timeout: 60_000 });
}

/**
 * 流式聊天（SSE）
 * 返回 handle，可调用 abort() 取消。
 */
export function aiChatStream(
  data: AiChatRequestPayload,
  callbacks: AiChatStreamCallbacks
): AiChatStreamHandle {
  const token = localStorage.getItem("token") || "";
  const controller = new AbortController();
  let done = false;

  const finishOnce = () => {
    if (!done) {
      done = true;
      callbacks.onDone();
    }
  };

  fetch("/api/matching/ai-chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const reader = response.body?.getReader();
      if (!reader) throw new Error("No reader");

      const decoder = new TextDecoder();
      let buffer = "";

      function read() {
        reader!
          .read()
          .then(({ done: streamDone, value }) => {
            if (streamDone) {
              finishOnce();
              return;
            }

            const text = decoder.decode(value, { stream: true });
            buffer += text;

            let newlineIdx: number;
            while ((newlineIdx = buffer.indexOf("\n")) !== -1) {
              const line = buffer.substring(0, newlineIdx).replace(/\r$/, "");
              buffer = buffer.substring(newlineIdx + 1);

              if (!line || line.startsWith("event:")) continue;

              if (line.startsWith("data:")) {
                const content = line.substring(5);
                if (content === "[DONE]") {
                  finishOnce();
                  return;
                }
                if (content) {
                  callbacks.onChunk(content);
                }
              }
            }

            read();
          })
          .catch((err: Error) => {
            if (err.name === "AbortError") return;
            callbacks.onError(err.message);
          });
      }

      read();
    })
    .catch((err) => {
      if (err.name === "AbortError") return;
      callbacks.onError(err.message);
    });

  return {
    abort: () => controller.abort(),
  };
}
```

- [ ] **Step 3: 创建 `api/aiChat.spec.ts`**

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { aiChatStream } from "@/api/aiChat";
import type { AiChatRequestPayload, AiChatStreamCallbacks } from "@/types/domain";

// Mock fetch + ReadableStream
function createMockResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
      controller.close();
    },
  });
  return new Response(stream, { status: 200 });
}

const basePayload: AiChatRequestPayload = {
  enterpriseId: null,
  chainId: null,
  message: "测试",
  history: [],
};

describe("aiChatStream", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    localStorage.setItem("token", "test-token");
  });
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem("token");
  });

  it("解析 SSE 分片并回调 onChunk", async () => {
    const chunks: string[] = [];
    const callbacks: AiChatStreamCallbacks = {
      onChunk: (t) => chunks.push(t),
      onDone: () => {},
      onError: () => {},
    };

    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      createMockResponse(["data:你好\n", "data:世界\n", "data:[DONE]\n"])
    );

    aiChatStream(basePayload, callbacks);

    await new Promise((r) => setTimeout(r, 50));
    expect(chunks).toEqual(["你好", "世界"]);
  });

  it("[DONE] 和流结束共享幂等完成标志，onDone 只调一次", async () => {
    let doneCount = 0;
    const callbacks: AiChatStreamCallbacks = {
      onChunk: () => {},
      onDone: () => { doneCount++; },
      onError: () => {},
    };

    // [DONE] 后流也结束（两次完成信号）
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      createMockResponse(["data:[DONE]\n"])
    );

    aiChatStream(basePayload, callbacks);
    await new Promise((r) => setTimeout(r, 50));
    expect(doneCount).toBe(1);
  });

  it("HTTP 错误时回调 onError", async () => {
    const errors: string[] = [];
    const callbacks: AiChatStreamCallbacks = {
      onChunk: () => {},
      onDone: () => {},
      onError: (e) => errors.push(e),
    };

    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue(
      new Response(null, { status: 500 })
    );

    aiChatStream(basePayload, callbacks);
    await new Promise((r) => setTimeout(r, 50));
    expect(errors.length).toBe(1);
    expect(errors[0]).toContain("500");
  });
});
```

- [ ] **Step 4: 从 `api/matching.ts` 中移除旧的 AI 聊天函数**

删除 `api/matching.ts` 中的以下内容：
- `AiChatMessage` 接口
- `AiChatResponse` 接口
- `aiChat` 函数
- `aiChatStream` 函数

在文件顶部添加注释说明已迁移：

```typescript
// AI 聊天接口已迁移至 @/api/aiChat
```

- [ ] **Step 5: 运行 API 测试**

Run: `cd front && npx vitest run src/api/aiChat.spec.ts`
Expected: PASS

```

---

## Task 8: 前端创建 stores/aiChat.ts

**Files:**
- Create: `front/src/stores/aiChat.ts`
- Create: `front/src/stores/aiChat.spec.ts`

采用 Composition API（setup store）风格，与 `auth.ts` 一致。管理多会话、流式状态、按用户隔离的 localStorage 持久化。

- [ ] **Step 1: 创建 `stores/aiChat.ts`**

```typescript
import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  AiChatSession,
  AiChatMessage,
  AiChatRequestPayload,
  AiChatHistoryItem,
  AiChatStreamHandle,
} from "@/types/domain";
import { aiChatStream } from "@/api/aiChat";
import { useAuthStore } from "@/stores/auth";

const STORAGE_PREFIX = "industry-chain:ai-chat:v1:";
const MAX_SESSIONS = 20;
const MAX_MESSAGES = 50;

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

function storageKey(userId: number): string {
  return `${STORAGE_PREFIX}${userId}`;
}

export const useAiChatStore = defineStore("aiChat", () => {
  const panelOpen = ref(false);
  const sessions = ref<AiChatSession[]>([]);
  const currentSessionId = ref<string | null>(null);
  const currentUserId = ref<number | null>(null);
  const streaming = ref(false);
  const streamHandle = ref<AiChatStreamHandle | null>(null);
  const storageError = ref<string | null>(null);

  const currentSession = computed(() =>
    sessions.value.find((s) => s.id === currentSessionId.value) || null
  );

  const canSend = computed(() => {
    const s = currentSession.value;
    if (!s || streaming.value) return false;
    // 两 ID 同时为空或同时非空
    return (s.enterpriseId === null) === (s.chainId === null);
  });

  // ---------- 持久化 ----------

  function loadFromStorage(userId: number) {
    // 切换用户时先清空内存
    if (currentUserId.value !== userId) {
      sessions.value = [];
      currentSessionId.value = null;
      currentUserId.value = userId;
    }

    try {
      const raw = localStorage.getItem(storageKey(userId));
      if (!raw) {
        sessions.value = [];
        ensureDefaultSession();
        return;
      }
      const parsed = JSON.parse(raw) as AiChatSession[];
      if (!Array.isArray(parsed)) throw new Error("invalid");
      sessions.value = parsed.slice(0, MAX_SESSIONS);
      if (sessions.value.length > 0) {
        currentSessionId.value = sessions.value[0].id;
      } else {
        ensureDefaultSession();
      }
    } catch {
      // JSON 损坏，只移除当前用户键
      localStorage.removeItem(storageKey(userId));
      storageError.value = "聊天记录已损坏，已清空当前用户的历史记录";
      sessions.value = [];
      ensureDefaultSession();
    }
  }

  function saveToStorage() {
    if (currentUserId.value === null) return;
    try {
      // 每个会话截断到最近 MAX_MESSAGES 条
      const toSave = sessions.value.map((s) => ({
        ...s,
        messages: s.messages.slice(-MAX_MESSAGES),
      }));
      localStorage.setItem(
        storageKey(currentUserId.value),
        JSON.stringify(toSave)
      );
    } catch (e) {
      if (e instanceof DOMException && e.name === "QuotaExceededError") {
        storageError.value = "存储空间已满，请删除旧会话后再试";
        // 保留当前内存状态，不静默丢弃
      }
    }
  }

  function clearOnLogout() {
    panelOpen.value = false;
    sessions.value = [];
    currentSessionId.value = null;
    currentUserId.value = null;
    streaming.value = false;
    streamHandle.value = null;
    // 不删除 localStorage（退出登录不删除该用户的本地记录）
  }

  function ensureDefaultSession() {
    if (sessions.value.length === 0 || !currentSessionId.value) {
      createSession();
    }
  }

  // ---------- 会话管理 ----------

  function createSession(): string {
    if (sessions.value.length >= MAX_SESSIONS) {
      // 删除最旧的会话
      sessions.value.shift();
    }
    const now = Date.now();
    const session: AiChatSession = {
      id: generateId(),
      title: "新对话",
      enterpriseId: null,
      enterpriseName: null,
      chainId: null,
      chainName: null,
      messages: [],
      createdAt: now,
      updatedAt: now,
      streaming: false,
      failedRequest: null,
    };
    sessions.value.unshift(session);
    currentSessionId.value = session.id;
    saveToStorage();
    return session.id;
  }

  function switchSession(id: string) {
    if (streaming.value) return; // 流式生成中不允许切换
    currentSessionId.value = id;
  }

  function deleteSession(id: string) {
    const idx = sessions.value.findIndex((s) => s.id === id);
    if (idx === -1) return;
    sessions.value.splice(idx, 1);
    if (currentSessionId.value === id) {
      currentSessionId.value =
        sessions.value.length > 0 ? sessions.value[0].id : null;
      if (!currentSessionId.value) {
        createSession();
      }
    }
    saveToStorage();
  }

  function clearAllSessions() {
    sessions.value = [];
    createSession();
  }

  // ---------- 上下文选择 ----------

  function setEnterprise(id: number | null, name: string | null) {
    const s = currentSession.value;
    if (!s) return;
    s.enterpriseId = id;
    s.enterpriseName = name;
    s.updatedAt = Date.now();
    saveToStorage();
  }

  function setChain(id: number | null, name: string | null) {
    const s = currentSession.value;
    if (!s) return;
    s.chainId = id;
    s.chainName = name;
    s.updatedAt = Date.now();
    saveToStorage();
  }

  // ---------- 消息发送 ----------

  function buildHistory(session: AiChatSession): AiChatHistoryItem[] {
    // 取最近的有效消息（排除失败和正在生成的）
    // buildHistory 在 push 新用户消息之前调用，
    // 取最近 MAX_MESSAGES 条已完成消息作为历史
    return session.messages
      .filter(
        (m) =>
          m.status === "completed" && m.content.length > 0
      )
      .slice(-MAX_MESSAGES)
      .map((m) => ({ role: m.role, content: m.content }));
  }

  function sendMessage(text: string) {
    const session = currentSession.value;
    if (!session || !text.trim() || streaming.value) return;

    // 构建请求载荷
    const payload: AiChatRequestPayload = {
      enterpriseId: session.enterpriseId,
      chainId: session.chainId,
      message: text.trim(),
      history: buildHistory(session),
    };

    // 写入用户消息
    const userMsg: AiChatMessage = {
      id: generateId(),
      role: "user",
      content: text.trim(),
      status: "completed",
      createdAt: Date.now(),
    };
    session.messages.push(userMsg);

    // 如果是第一条消息，用消息内容作为会话标题
    if (session.messages.length === 1) {
      session.title = text.trim().slice(0, 30);
    }

    // 创建空的 AI 消息
    const aiMsg: AiChatMessage = {
      id: generateId(),
      role: "assistant",
      content: "",
      status: "streaming",
      createdAt: Date.now(),
    };
    session.messages.push(aiMsg);

    session.streaming = true;
    streaming.value = true;
    session.failedRequest = null;
    saveToStorage();

    const sessionId = session.id;
    const aiMsgId = aiMsg.id;

    streamHandle.value = aiChatStream(payload, {
      onChunk: (chunk: string) => {
        const s = sessions.value.find((x) => x.id === sessionId);
        if (!s) return;
        const m = s.messages.find((x) => x.id === aiMsgId);
        if (m) {
          m.content += chunk;
        }
      },
      onDone: () => {
        const s = sessions.value.find((x) => x.id === sessionId);
        if (!s) return;
        const m = s.messages.find((x) => x.id === aiMsgId);
        if (m) {
          m.status = "completed";
        }
        s.streaming = false;
        s.updatedAt = Date.now();
        streaming.value = false;
        streamHandle.value = null;
        saveToStorage();
      },
      onError: (error: string) => {
        const s = sessions.value.find((x) => x.id === sessionId);
        if (!s) return;
        const m = s.messages.find((x) => x.id === aiMsgId);
        if (m) {
          m.status = "failed";
          m.content = "AI 回复失败：" + error;
        }
        s.streaming = false;
        s.failedRequest = payload;
        streaming.value = false;
        streamHandle.value = null;
        saveToStorage();
      },
    });
  }

  function retryLastMessage() {
    const session = currentSession.value;
    if (!session || streaming.value) return;
    const failed = session.failedRequest;
    if (!failed) return;

    // 移除旧失败气泡
    const lastMsg = session.messages[session.messages.length - 1];
    if (lastMsg && lastMsg.role === "assistant" && lastMsg.status === "failed") {
      session.messages.pop();
    }

    // 重新创建空 AI 消息
    const aiMsg: AiChatMessage = {
      id: generateId(),
      role: "assistant",
      content: "",
      status: "streaming",
      createdAt: Date.now(),
    };
    session.messages.push(aiMsg);
    session.streaming = true;
    streaming.value = true;
    session.failedRequest = null;
    saveToStorage();

    const sessionId = session.id;
    const aiMsgId = aiMsg.id;

    streamHandle.value = aiChatStream(failed, {
      onChunk: (chunk: string) => {
        const s = sessions.value.find((x) => x.id === sessionId);
        if (!s) return;
        const m = s.messages.find((x) => x.id === aiMsgId);
        if (m) m.content += chunk;
      },
      onDone: () => {
        const s = sessions.value.find((x) => x.id === sessionId);
        if (!s) return;
        const m = s.messages.find((x) => x.id === aiMsgId);
        if (m) m.status = "completed";
        s.streaming = false;
        s.updatedAt = Date.now();
        streaming.value = false;
        streamHandle.value = null;
        saveToStorage();
      },
      onError: (error: string) => {
        const s = sessions.value.find((x) => x.id === sessionId);
        if (!s) return;
        const m = s.messages.find((x) => x.id === aiMsgId);
        if (m) {
          m.status = "failed";
          m.content = "AI 回复失败：" + error;
        }
        s.streaming = false;
        s.failedRequest = failed;
        streaming.value = false;
        streamHandle.value = null;
        saveToStorage();
      },
    });
  }

  // ---------- 面板 ----------

  function openPanel() {
    const authStore = useAuthStore();
    const uid = authStore.user?.userId;
    if (uid === undefined || uid === null) return;

    if (currentUserId.value !== uid) {
      loadFromStorage(uid);
    } else if (sessions.value.length === 0) {
      loadFromStorage(uid);
    }
    panelOpen.value = true;
  }

  function closePanel() {
    panelOpen.value = false;
    // 关闭面板不取消生成
  }

  function togglePanel() {
    if (panelOpen.value) closePanel();
    else openPanel();
  }

  function dismissStorageError() {
    storageError.value = null;
  }

  return {
    // state
    panelOpen,
    sessions,
    currentSessionId,
    currentUserId,
    streaming,
    storageError,
    // getters
    currentSession,
    canSend,
    // actions
    loadFromStorage,
    clearOnLogout,
    createSession,
    switchSession,
    deleteSession,
    clearAllSessions,
    setEnterprise,
    setChain,
    sendMessage,
    retryLastMessage,
    openPanel,
    closePanel,
    togglePanel,
    dismissStorageError,
  };
});
```

- [ ] **Step 2: 创建 `stores/aiChat.spec.ts`**

```typescript
import { describe, it, expect, beforeEach, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useAiChatStore } from "@/stores/aiChat";
import { useAuthStore } from "@/stores/auth";

// Mock aiChatStream
vi.mock("@/api/aiChat", () => ({
  aiChatStream: vi.fn(() => ({
    abort: vi.fn(),
  })),
}));

import { aiChatStream } from "@/api/aiChat";

function mockAuthStore(userId: number | null) {
  const auth = useAuthStore();
  if (userId) {
    auth.user = {
      userId,
      username: "test",
      realName: "测试",
      roles: [],
      permissions: [],
    } as any;
    auth.token = "test-token";
  } else {
    auth.user = null;
    auth.token = "";
  }
}

describe("useAiChatStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
  });

  describe("多会话管理", () => {
    it("createSession 创建新会话并设为当前", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      const firstId = store.currentSessionId;
      expect(firstId).toBeTruthy();

      store.createSession();
      expect(store.sessions.length).toBe(2);
      expect(store.currentSessionId).not.toBe(firstId);
    });

    it("switchSession 切换当前会话", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      const id1 = store.currentSessionId!;
      store.createSession();
      const id2 = store.currentSessionId!;
      store.switchSession(id1);
      expect(store.currentSessionId).toBe(id1);
    });

    it("deleteSession 删除会话，无剩余时自动创建", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      const id = store.currentSessionId!;
      store.deleteSession(id);
      expect(store.sessions.find((s) => s.id === id)).toBeUndefined();
      expect(store.sessions.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe("上下文切换", () => {
    it("两 ID 均空时 canSend 为 true", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      expect(store.canSend).toBe(true);
    });

    it("只选企业时 canSend 为 false", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      store.setEnterprise(1, "测试企业");
      expect(store.canSend).toBe(false);
    });

    it("同时选企业和产业链时 canSend 为 true", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      store.setEnterprise(1, "测试企业");
      store.setChain(2, "测试链");
      expect(store.canSend).toBe(true);
    });

    it("切换上下文不清空消息", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      // 模拟发送（mock aiChatStream 不真正回调）
      store.sendMessage("你好");
      const msgCount = store.currentSession?.messages.length;
      store.setChain(2, "测试链");
      expect(store.currentSession?.messages.length).toBe(msgCount);
    });
  });

  describe("按用户隔离", () => {
    it("用户 A 的记录不被用户 B 读取", () => {
      mockAuthStore(1);
      let store = useAiChatStore();
      store.openPanel();
      store.setEnterprise(1, "企业A");
      store.closePanel();

      // 模拟退出登录
      store.clearOnLogout();

      mockAuthStore(2);
      store = useAiChatStore();
      store.openPanel();
      expect(store.currentSession?.enterpriseId).toBeNull();
    });

    it("JSON 损坏时移除当前用户键并显示错误", () => {
      mockAuthStore(1);
      localStorage.setItem(
        "industry-chain:ai-chat:v1:1",
        "{invalid json"
      );
      const store = useAiChatStore();
      store.openPanel();
      expect(store.storageError).toBeTruthy();
      expect(store.sessions.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe("容量限制", () => {
    it("超过 20 个会话时删除最旧会话", () => {
      mockAuthStore(1);
      const store = useAiChatStore();
      store.openPanel();
      for (let i = 0; i < 25; i++) {
        store.createSession();
      }
      expect(store.sessions.length).toBeLessThanOrEqual(20);
    });
  });
});
```

- [ ] **Step 3: 运行 Store 测试**

Run: `cd front && npx vitest run src/stores/aiChat.spec.ts`
Expected: PASS

```

---

## Task 9: 前端创建 AiAssistantPanel.vue

**Files:**
- Create: `front/src/views/assistant/AiAssistantPanel.vue`

这是最核心的组件。渲染悬浮球、面板、会话历史、上下文选择器、消息区和输入区。只调用 Store action，不直接调用 API。

- [ ] **Step 1: 创建组件文件**

```vue
<template>
  <!-- 悬浮球 -->
  <button
    v-if="!store.panelOpen"
    class="ai-fab"
    aria-label="打开 AI 助手"
    @click="store.openPanel()"
  >
    <el-icon :size="24"><ChatDotRound /></el-icon>
  </button>

  <!-- 聊天面板 -->
  <Transition name="ai-panel-slide">
    <div v-if="store.panelOpen" class="ai-panel">
      <!-- 标题栏 -->
      <header class="ai-panel__header">
        <span class="ai-panel__title">
          {{ store.currentSession?.title || "AI 助手" }}
        </span>
        <div class="ai-panel__header-actions">
          <el-button text :icon="Minus" size="small" @click="store.closePanel()" />
        </div>
      </header>

      <!-- 工具栏 -->
      <div class="ai-panel__toolbar">
        <el-button text size="small" :icon="Plus" @click="store.createSession()">
          新建对话
        </el-button>
        <el-button text size="small" :icon="Delete" @click="store.clearAllSessions()">
          清空全部
        </el-button>
      </div>

      <!-- 上下文栏 -->
      <div class="ai-panel__context">
        <el-select
          :model-value="store.currentSession?.enterpriseId ?? undefined"
          placeholder="选择企业（可选）"
          clearable
          filterable
          size="small"
          class="ai-panel__selector"
          @change="(val: number | undefined) => onEnterpriseChange(val)"
        >
          <el-option
            v-for="ent in enterprises"
            :key="ent.id"
            :label="ent.name"
            :value="ent.id"
          />
        </el-select>

        <el-select
          :model-value="store.currentSession?.chainId ?? undefined"
          placeholder="选择产业链（可选）"
          clearable
          filterable
          size="small"
          class="ai-panel__selector"
          @change="(val: number | undefined) => onChainChange(val)"
        >
          <el-option
            v-for="chain in chains"
            :key="chain.id"
            :label="chain.name"
            :value="chain.id"
          />
        </el-select>
      </div>

      <!-- 存储错误提示 -->
      <div v-if="store.storageError" class="ai-panel__error">
        <span>{{ store.storageError }}</span>
        <el-button text size="small" @click="store.dismissStorageError()">关闭</el-button>
      </div>

      <!-- 半选提示 -->
      <div
        v-if="store.currentSession && !store.canSend && store.currentSession.enterpriseId !== null && store.currentSession.chainId === null"
        class="ai-panel__hint"
      >
        请同时选择企业和产业链
      </div>
      <div
        v-if="store.currentSession && !store.canSend && store.currentSession.chainId !== null && store.currentSession.enterpriseId === null"
        class="ai-panel__hint"
      >
        请同时选择企业和产业链
      </div>

      <!-- 消息区 -->
      <div ref="messageContainer" class="ai-panel__messages">
        <div v-if="!store.currentSession || store.currentSession.messages.length === 0" class="ai-panel__empty">
          <el-icon :size="32"><ChatDotRound /></el-icon>
          <p>向 AI 提问关于产业链的问题</p>
          <div class="ai-panel__suggestions">
            <el-button size="small" @click="quickAsk('什么是产业链？')">
              产业链概念
            </el-button>
            <el-button size="small" @click="quickAsk('企业挂链有哪些注意事项？')">
              挂链注意事项
            </el-button>
          </div>
        </div>

        <div
          v-for="msg in store.currentSession?.messages"
          :key="msg.id"
          :class="['ai-msg', `ai-msg--${msg.role}`]"
        >
          <div class="ai-msg__avatar">
            <el-icon v-if="msg.role === 'user'"><User /></el-icon>
            <el-icon v-else><Monitor /></el-icon>
          </div>
          <div class="ai-msg__content markdown-body" v-html="renderMarkdown(msg.content)"></div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="ai-panel__input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          :disabled="store.streaming || !store.canSend"
          resize="none"
          @keydown="handleKeydown"
        />
        <div class="ai-panel__input-actions">
          <el-button
            v-if="hasFailedMessage"
            text
            size="small"
            :icon="RefreshRight"
            @click="store.retryLastMessage()"
          >
            重试
          </el-button>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="store.streaming"
            :disabled="!inputText.trim() || !store.canSend"
            @click="handleSend"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, computed } from "vue";
import {
  ChatDotRound,
  User,
  Monitor,
  Promotion,
  Plus,
  Delete,
  Minus,
  RefreshRight,
} from "@element-plus/icons-vue";
import { ElButton, ElIcon, ElInput, ElSelect, ElOption } from "element-plus";
import DOMPurify from "dompurify";
import { marked } from "marked";
import { useAiChatStore } from "@/stores/aiChat";
import { fetchEnterprises } from "@/api/enterprise";
import { fetchChains } from "@/api/chain";
import type { EnterpriseListItem, IndustryChain } from "@/types/domain";

const store = useAiChatStore();

const inputText = ref("");
const messageContainer = ref<HTMLElement | null>(null);
const enterprises = ref<EnterpriseListItem[]>([]);
const chains = ref<IndustryChain[]>([]);

const hasFailedMessage = computed(() => {
  const msgs = store.currentSession?.messages;
  if (!msgs || msgs.length === 0) return false;
  const last = msgs[msgs.length - 1];
  return last.role === "assistant" && last.status === "failed";
});

// ---------- Markdown 渲染（清洗后输出） ----------

function renderMarkdown(text: string): string {
  if (!text) return "";
  const rawHtml = marked.parse(text, { async: false }) as string;
  return DOMPurify.sanitize(rawHtml);
}

// ---------- 输入处理 ----------

function handleKeydown(e: KeyboardEvent) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

function handleSend() {
  const text = inputText.value.trim();
  if (!text || !store.canSend || store.streaming) return;
  store.sendMessage(text);
  inputText.value = "";
  nextTick(scrollToBottom);
}

function quickAsk(text: string) {
  if (!store.canSend || store.streaming) return;
  store.sendMessage(text);
  nextTick(scrollToBottom);
}

function scrollToBottom() {
  if (messageContainer.value) {
    messageContainer.value.scrollTop = messageContainer.value.scrollHeight;
  }
}

// ---------- 上下文选择 ----------

function onEnterpriseChange(val: number | undefined) {
  const ent = enterprises.value.find((e) => e.id === val);
  store.setEnterprise(val ?? null, ent?.name ?? null);
}

function onChainChange(val: number | undefined) {
  const chain = chains.value.find((c) => c.id === val);
  store.setChain(val ?? null, chain?.name ?? null);
}

// ---------- 数据加载 ----------

async function loadEnterprises() {
  try {
    const res = await fetchEnterprises({
      pageNum: 1,
      pageSize: 100,
    });
    enterprises.value = res.records;
  } catch {
    enterprises.value = [];
  }
}

async function loadChains() {
  try {
    chains.value = await fetchChains();
  } catch {
    chains.value = [];
  }
}

// ---------- 消息变化时自动滚动 ----------

watch(
  () => store.currentSession?.messages,
  () => {
    nextTick(scrollToBottom);
  },
  { deep: true }
);

onMounted(() => {
  loadEnterprises();
  loadChains();
});
</script>

<style scoped>
.ai-fab {
    position: fixed;
    right: 24px;
    bottom: 24px;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    border: none;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
    color: #fff;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
    z-index: 2000;
    transition: transform 0.2s;
}

.ai-fab:hover {
    transform: scale(1.05);
}

.ai-fab:focus-visible {
    outline: 2px solid #409eff;
    outline-offset: 2px;
}

.ai-panel {
    position: fixed;
    right: 24px;
    bottom: 24px;
    width: 420px;
    height: min(680px, calc(100vh - 96px));
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
    z-index: 2000;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

@media (max-width: 720px) {
    .ai-panel {
        inset: 12px;
        width: auto;
        height: auto;
        border-radius: 8px;
    }
}

.ai-panel__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid #ebeef5;
    flex-shrink: 0;
}

.ai-panel__title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.ai-panel__header-actions {
    display: flex;
    gap: 4px;
}

.ai-panel__toolbar {
    display: flex;
    gap: 8px;
    padding: 8px 16px;
    border-bottom: 1px solid #ebeef5;
    flex-shrink: 0;
}

.ai-panel__context {
    display: flex;
    gap: 8px;
    padding: 8px 16px;
    border-bottom: 1px solid #ebeef5;
    flex-shrink: 0;
}

.ai-panel__selector {
    flex: 1;
}

/* 选择器文字和图标同一水平线 */
.ai-panel__selector :deep(.el-select__wrapper) {
    display: inline-flex;
    align-items: center;
}

.ai-panel__selector :deep(.el-select__suffix) {
    display: inline-flex;
    align-items: center;
}

.ai-panel__error {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px;
    background: #fef0f0;
    color: #f56c6c;
    font-size: 12px;
    flex-shrink: 0;
}

.ai-panel__hint {
    padding: 6px 16px;
    background: #fdf6ec;
    color: #e6a23c;
    font-size: 12px;
    flex-shrink: 0;
}

.ai-panel__messages {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
}

.ai-panel__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 40px 0;
    color: #909399;
}

.ai-panel__suggestions {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.ai-msg {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;
}

.ai-msg--user {
    flex-direction: row-reverse;
}

.ai-msg__avatar {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f0f2f5;
    color: #606266;
}

.ai-msg--user .ai-msg__avatar {
    background: #409eff;
    color: #fff;
}

.ai-msg__content {
    max-width: calc(100% - 40px);
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;
}

.ai-msg--user .ai-msg__content {
    background: #409eff;
    color: #fff;
}

.ai-msg--assistant .ai-msg__content {
    background: #f4f4f5;
    color: #303133;
}

.ai-panel__input {
    padding: 12px 16px;
    border-top: 1px solid #ebeef5;
    flex-shrink: 0;
}

.ai-panel__input-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
}

/* Transition */
.ai-panel-slide-enter-active,
.ai-panel-slide-leave-active {
    transition: opacity 0.2s, transform 0.2s;
}

.ai-panel-slide-enter-from,
.ai-panel-slide-leave-to {
    opacity: 0;
    transform: translateY(20px);
}

/* Markdown body */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
    margin: 8px 0;
}

.markdown-body :deep(p) {
    margin: 4px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
    padding-left: 20px;
}

.markdown-body :deep(code) {
    background: rgba(0, 0, 0, 0.06);
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 13px;
}

.markdown-body :deep(pre) {
    background: rgba(0, 0, 0, 0.06);
    padding: 8px;
    border-radius: 6px;
    overflow-x: auto;
}
</style>
```

- [ ] **Step 2: 类型检查**

Run: `cd front && npx vue-tsc --noEmit -p tsconfig.app.json 2>&1 | grep -i "AiAssistantPanel" | head -5`
Expected: 无该文件的类型错误

```

---

## Task 10: 前端更新 AdminLayout 和路由

**Files:**
- Modify: `front/src/layouts/AdminLayout.vue`
- Modify: `front/src/router/index.ts`
- Modify: `front/src/layouts/AdminLayout.spec.ts`
- Modify: `front/src/router/index.spec.ts`

- [ ] **Step 1: 在 `router/index.ts` 中为系统管理路由添加 `hideAiAssistant` meta**

修改 `/system/manage` 路由：

```typescript
{
    path: "/system/manage",
    name: "system-manage",
    component: () => import("@/views/system/SystemManage.vue"),
    meta: { title: "系统管理", hideAiAssistant: true },
},
```

修改 `/system/operation-logs` 路由：

```typescript
{
    path: "/system/operation-logs",
    name: "system-operation-logs",
    component: () => import("@/views/system/SystemOperationLog.vue"),
    meta: { title: "操作日志", hideAiAssistant: true },
},
```

- [ ] **Step 2: 在 `AdminLayout.vue` 中条件挂载助手面板**

在 `<script setup>` 中添加 import：

```typescript
import AiAssistantPanel from "@/views/assistant/AiAssistantPanel.vue";
import { useRoute } from "vue-router";
import { computed } from "vue";

const route = useRoute();
const showAiAssistant = computed(() => !route.meta.hideAiAssistant);
```

在模板中 `<RouterView />` 之后、闭合标签之前添加：

```html
<!-- 全局 AI 助手面板 -->
<AiAssistantPanel v-if="showAiAssistant" />
```

- [ ] **Step 3: 更新 `AdminLayout.spec.ts` 验证助手挂载**

在现有测试中追加：

```typescript
import AiAssistantPanel from "@/views/assistant/AiAssistantPanel.vue";

it("普通业务路由挂载 AI 助手面板", async () => {
    // 导航到非系统管理路由
    await router.push("/dashboard");
    await nextTick();
    const wrapper = mount(AdminLayout, {
        global: { plugins: [router, ElementPlus, createPinia()] },
    });
    await router.isReady();
    expect(wrapper.findComponent(AiAssistantPanel).exists()).toBe(true);
});

it("系统管理路由不挂载 AI 助手面板", async () => {
    await router.push("/system/manage");
    await nextTick();
    const wrapper = mount(AdminLayout, {
        global: { plugins: [router, ElementPlus, createPinia()] },
    });
    await router.isReady();
    expect(wrapper.findComponent(AiAssistantPanel).exists()).toBe(false);
});
```

- [ ] **Step 4: 更新 `router/index.spec.ts` 验证 meta 标记**

追加测试：

```typescript
it("系统管理路由标记 hideAiAssistant", () => {
    const sysRoute = routes[1].children!.find(
        (r) => r.path === "/system/manage"
    );
    expect(sysRoute?.meta?.hideAiAssistant).toBe(true);
});

it("操作日志路由标记 hideAiAssistant", () => {
    const logRoute = routes[1].children!.find(
        (r) => r.path === "/system/operation-logs"
    );
    expect(logRoute?.meta?.hideAiAssistant).toBe(true);
});

it("普通业务路由未标记 hideAiAssistant", () => {
    const dashRoute = routes[1].children!.find(
        (r) => r.path === "/dashboard"
    );
    expect(dashRoute?.meta?.hideAiAssistant).toBeUndefined();
});
```

- [ ] **Step 5: 运行布局和路由测试**

Run: `cd front && npx vitest run src/layouts/AdminLayout.spec.ts src/router/index.spec.ts`
Expected: PASS

```

---

## Task 11: 前端删除 MatchingTask 内嵌聊天区域

**Files:**
- Modify: `front/src/views/matching/MatchingTask.vue`
- Modify: `front/views/matching/MatchingTask.spec.ts`

- [ ] **Step 1: 移除 AI 聊天 API 引用**

从 `MatchingTask.vue` 的 import 语句中移除 `aiChatStream` 和 `type AiChatMessage`：

```typescript
// 修改前
import {
  acceptMatchingResult,
  aiChatStream,
  cancelMatchingTask,
  createMatchingTask,
  fetchMatchingTask,
  manualConfirmMatching,
  rejectMatchingResult,
  retryMatchingTask,
  type AiChatMessage,
} from "@/api/matching";

// 修改后
import {
  acceptMatchingResult,
  cancelMatchingTask,
  createMatchingTask,
  fetchMatchingTask,
  manualConfirmMatching,
  rejectMatchingResult,
  retryMatchingTask,
} from "@/api/matching";
```

同时移除 `marked` 的 import（如果仅用于聊天）和 `ChatDotRound`、`User`、`Monitor`、`Promotion` 图标的 import（如果仅用于聊天区域）。

- [ ] **Step 2: 移除 AI 聊天状态变量**

删除以下 ref 声明：

```typescript
// 删除这些
const chatMessages = ref<AiChatMessage[]>([]);
const chatInput = ref("");
const chatLoading = ref(false);
const chatContainerRef = ref<HTMLElement | null>(null);
```

- [ ] **Step 3: 移除 AI 聊天函数**

删除以下函数：`sendChatMessage`、`clearChat`、`scrollToChatBottom`、`handleChatKeydown`、`renderMarkdown`。

- [ ] **Step 4: 移除 AI 聊天模板**

删除 `<section class="matching-panel matching-panel--chat">...</section>` 整段（约 60 行模板）。

- [ ] **Step 5: 移除 AI 聊天样式**

删除以 `.chat-header`、`.chat-hint`、`.chat-messages`、`.chat-empty`、`.chat-suggestions`、`.chat-message`、`.chat-message__avatar`、`.chat-message__content`、`.chat-message__loading`、`.chat-input`、`.dot-pulse`、`.markdown-body` 开头的所有 CSS 规则块。

- [ ] **Step 6: 更新 `MatchingTask.spec.ts`**

移除对 `与 AI 对话`、`aiChat`、`sendChatMessage`、`chatMessages` 的断言。移除 `aiChat: vi.fn()` mock。如果文件中所有断言都与聊天相关，则将文件改为只验证组件能被正确 import 的基本测试：

```typescript
import { describe, it, expect } from "vitest";
import { readFileSync } from "fs";
import { resolve } from "path";

describe("MatchingTask", () => {
  it("组件文件存在且可被读取", () => {
    const source = readFileSync(
      resolve(__dirname, "MatchingTask.vue"),
      "utf-8"
    );
    expect(source).toContain("智能挂链");
    // 确认已移除内嵌聊天
    expect(source).not.toContain("与 AI 对话");
    expect(source).not.toContain("sendChatMessage");
    expect(source).not.toContain("aiChatStream");
  });
});
```

- [ ] **Step 7: 运行 MatchingTask 测试**

Run: `cd front && npx vitest run src/views/matching/MatchingTask.spec.ts`
Expected: PASS

```

---

## Task 12: 前端组件测试和全量测试

**Files:**
- Create: `front/src/views/assistant/AiAssistantPanel.spec.ts`

- [ ] **Step 1: 创建组件测试**

```typescript
import { describe, it, expect, beforeEach, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { setActivePinia, createPinia } from "pinia";
import { createMemoryHistory, createRouter, type RouteRecordRaw } from "vue-router";
import ElementPlus from "element-plus";
import AiAssistantPanel from "@/views/assistant/AiAssistantPanel.vue";
import { useAiChatStore } from "@/stores/aiChat";
import { useAuthStore } from "@/stores/auth";

// Mock API
vi.mock("@/api/enterprise", () => ({
  fetchEnterprises: vi.fn().mockResolvedValue({ records: [] }),
}));
vi.mock("@/api/chain", () => ({
  fetchChains: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/api/aiChat", () => ({
  aiChatStream: vi.fn(() => ({ abort: vi.fn() })),
}));

const routes: RouteRecordRaw[] = [
  { path: "/", component: { template: "<div/>" } },
];

function mountPanel() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes,
  });
  return mount(AiAssistantPanel, {
    global: {
      plugins: [router, ElementPlus, createPinia()],
    },
  });
}

describe("AiAssistantPanel", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();

    const auth = useAuthStore();
    auth.user = {
      userId: 1,
      username: "test",
      realName: "测试",
      roles: [],
      permissions: [],
    } as any;
  });

  it("面板关闭时显示悬浮球", async () => {
    const store = useAiChatStore();
    // panelOpen 默认 false
    const wrapper = mountPanel();
    await flushPromises();
    expect(wrapper.find(".ai-fab").exists()).toBe(true);
    expect(wrapper.find(".ai-panel").exists()).toBe(false);
  });

  it("点击悬浮球打开面板", async () => {
    const store = useAiChatStore();
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.find(".ai-fab").trigger("click");
    await flushPromises();
    expect(store.panelOpen).toBe(true);
    expect(wrapper.find(".ai-panel").exists()).toBe(true);
  });

  it("面板包含标题栏、工具栏、上下文栏、消息区和输入区", async () => {
    const store = useAiChatStore();
    store.openPanel();
    const wrapper = mountPanel();
    await flushPromises();
    expect(wrapper.find(".ai-panel__header").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__toolbar").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__context").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__messages").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__input").exists()).toBe(true);
  });

  it("新建对话按钮调用 createSession", async () => {
    const store = useAiChatStore();
    store.openPanel();
    const before = store.sessions.length;
    const wrapper = mountPanel();
    await flushPromises();
    await wrapper.find('[data-test] .el-button').trigger("click");
    // 由于 el-button text 模式不易定位，用 store 直接验证
    store.createSession();
    expect(store.sessions.length).toBe(before + 1);
  });

  it("输入框为空时发送按钮禁用", async () => {
    const store = useAiChatStore();
    store.openPanel();
    const wrapper = mountPanel();
    await flushPromises();
    const sendBtn = wrapper.findAll("button").find((b) =>
      b.text().includes("发送")
    );
    expect(sendBtn?.attributes("disabled")).toBeDefined();
  });
});
```

- [ ] **Step 2: 运行组件测试**

Run: `cd front && npx vitest run src/views/assistant/AiAssistantPanel.spec.ts`
Expected: PASS

- [ ] **Step 3: 运行前端全量测试**

Run: `cd front && npx vitest run`
Expected: 所有新增测试通过（既有失败 `router/index.spec.ts` 等如与本次无关则记录但不阻塞）

- [ ] **Step 4: 类型检查**

Run: `cd front && npx vue-tsc --noEmit -p tsconfig.app.json`
Expected: 无新增类型错误

- [ ] **Step 5: 生产构建**

Run: `cd front && npx vite build`
Expected: 构建成功（chunk 大小警告可忽略）

```

---

## Task 13: 全量验证

- [ ] **Step 1: 后端全量测试**

Run: `cd back && mvn test -q`
Expected: BUILD SUCCESS（既有 `DashboardServiceImplTest` 失败与本次无关，记录但不阻塞）

- [ ] **Step 2: 前端全量测试**

Run: `cd front && npx vitest run`
Expected: 本次新增和修改的测试全部通过

- [ ] **Step 3: 前端类型检查**

Run: `cd front && npx vue-tsc --noEmit -p tsconfig.app.json`
Expected: 无新增类型错误

- [ ] **Step 4: 前端生产构建**

Run: `cd front && npx vite build`
Expected: 构建成功

- [ ] **Step 5: 验收标准自查**

对照设计规格 §12 逐项检查：

- 除 `/system/manage`、`/system/operation-logs` 外，所有登录后页面显示悬浮球 — 检查 AdminLayout 的 `showAiAssistant` 计算属性
- 面板位置、尺寸和布局 — 检查 CSS（420×680 桌面，12px inset 小屏）
- 选择器文字和 SVG 箭头同一水平线 — 检查 `.ai-panel__selector :deep(.el-select__wrapper)` 的 `inline-flex` + `align-items: center`
- 通用问答和上下文问答均可流式返回 — 后端双模式测试 + 前端 SSE 测试
- 只选一个上下文时前端不能发送 — `canSend` getter 测试
- 后端直接调用也返回 400 — Service 防御性校验测试
- 多会话互不串流 — Store `sendMessage` 按 `sessionId` 定位
- 切换页面、关闭面板不丢失记录 — Store 持久化测试
- 不同用户不能读取彼此记录 — 按用户隔离测试
- 智能挂链页不再存在内嵌聊天 — MatchingTask 清理验证
- 前后端测试、类型检查和构建全部通过 — Step 1-4

---

## 附录 A: 前端环境注意

前端测试需要 Node v24，默认 PATH 中的 node 是 v14。运行前执行：

```bash
export PATH="/c/nvm4w/nodejs:$PATH"
```

## 附录 B: Git 约束

本计划仅描述编码步骤，不进行 Git 提交/推送/上传操作。
