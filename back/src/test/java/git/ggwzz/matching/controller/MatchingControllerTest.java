package git.ggwzz.matching.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.dto.AiChatRequest;
import git.ggwzz.matching.dto.CreateBatchMatchingTaskRequest;
import git.ggwzz.matching.dto.CreateMatchingTaskRequest;
import git.ggwzz.matching.dto.ManualConfirmRequest;
import git.ggwzz.matching.dto.RejectMatchingResultRequest;
import git.ggwzz.matching.service.AiChatService;
import git.ggwzz.matching.service.MatchingService;
import git.ggwzz.matching.service.impl.MatchingEvaluationService;
import git.ggwzz.matching.vo.AiChatResponse;
import git.ggwzz.matching.vo.ManualConfirmVO;
import git.ggwzz.matching.vo.BatchMatchingTaskVO;
import git.ggwzz.matching.vo.MatchingDecisionVO;
import git.ggwzz.matching.vo.MatchingEvaluationReport;
import git.ggwzz.matching.vo.MatchingResultVO;
import git.ggwzz.matching.vo.MatchingTaskVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("unittest")
@WithMockUser(authorities = {
        "matching:task:create",
        "matching:task:query",
        "matching:task:execute",
        "matching:task:cancel",
        "matching:task:retry",
        "matching:result:accept",
        "matching:result:reject",
        "matching:manual-confirm:create",
        "matching:evaluation:run"
})
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MatchingService matchingService;

    @MockBean
    private MatchingEvaluationService matchingEvaluationService;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private EnterpriseQueryService enterpriseQueryService;

    @MockBean
    private ChainNodeQueryService chainNodeQueryService;

    @Test
    void createTaskShouldReturn200() throws Exception {
        CreateMatchingTaskRequest request = new CreateMatchingTaskRequest();
        request.setEnterpriseId(100L);
        request.setChainId(1L);

        when(matchingService.createTask(any(CreateMatchingTaskRequest.class), any())).thenReturn(buildTaskVO());

        mockMvc.perform(post("/api/matching/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void createTaskAsyncShouldReturnProcessingTask() throws Exception {
        CreateMatchingTaskRequest request = new CreateMatchingTaskRequest();
        request.setEnterpriseId(100L);
        request.setChainId(1L);

        MatchingTaskVO vo = buildTaskVO();
        vo.setStatus("PROCESSING");
        vo.setResults(List.of());
        when(matchingService.createTaskAsync(any(CreateMatchingTaskRequest.class), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/tasks/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void createBatchTasksAsyncShouldReturnSubmittedSummary() throws Exception {
        CreateBatchMatchingTaskRequest request = new CreateBatchMatchingTaskRequest();
        request.setAllEnterprises(true);
        request.setAllChains(false);
        request.setChainIds(List.of(1L));

        BatchMatchingTaskVO vo = new BatchMatchingTaskVO();
        vo.setEnterpriseCount(2);
        vo.setChainCount(1);
        vo.setTotalPairs(2);
        vo.setSubmittedCount(2);
        vo.setTasks(List.of(buildTaskVO()));
        when(matchingService.createBatchTasksAsync(any(CreateBatchMatchingTaskRequest.class), any()))
                .thenReturn(vo);

        mockMvc.perform(post("/api/matching/tasks/batch-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPairs").value(2))
                .andExpect(jsonPath("$.data.submittedCount").value(2));
    }

    @Test
    void getTaskShouldReturn200() throws Exception {
        when(matchingService.getTask(1L)).thenReturn(buildTaskVO());

        mockMvc.perform(get("/api/matching/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].nodePath").value("动力电池制造"));
    }

    @Test
    void cancelTaskShouldReturnTask() throws Exception {
        MatchingTaskVO vo = buildTaskVO();
        vo.setStatus("CANCELLED");
        vo.setCancelledBy("1");
        vo.setErrorMessage("任务已取消");
        when(matchingService.cancelTask(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/tasks/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.errorMessage").value("任务已取消"));
    }

    @Test
    void revokeTaskShouldReturnCancelledTask() throws Exception {
        MatchingTaskVO vo = buildTaskVO();
        vo.setStatus("CANCELLED");
        vo.setResults(List.of());
        vo.setErrorMessage("任务已撤回，推荐结果已清空");
        when(matchingService.revokeTask(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/tasks/1/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.results").isEmpty())
                .andExpect(jsonPath("$.data.errorMessage").value("任务已撤回，推荐结果已清空"));
    }

    @Test
    void retryTaskShouldReturnRetryingTask() throws Exception {
        MatchingTaskVO vo = buildTaskVO();
        vo.setTaskId(2L);
        vo.setStatus("RETRYING");
        vo.setRetryFromTaskId(1L);
        vo.setRetryCount(1);
        vo.setResults(List.of());
        when(matchingService.retryTask(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/tasks/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(2))
                .andExpect(jsonPath("$.data.status").value("RETRYING"))
                .andExpect(jsonPath("$.data.retryFromTaskId").value(1))
                .andExpect(jsonPath("$.data.retryCount").value(1));
    }

    @Test
    void acceptResultShouldReturn200() throws Exception {
        MatchingDecisionVO vo = new MatchingDecisionVO();
        vo.setResultId(1L);
        vo.setDecision("ACCEPTED");
        when(matchingService.acceptResult(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/results/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("ACCEPTED"));
    }

    @Test
    void rejectResultShouldReturn200() throws Exception {
        RejectMatchingResultRequest request = new RejectMatchingResultRequest();
        request.setRejectReason("不符合业务需求");

        MatchingDecisionVO vo = new MatchingDecisionVO();
        vo.setResultId(1L);
        vo.setDecision("REJECTED");
        when(matchingService.rejectResult(eq(1L), any(RejectMatchingResultRequest.class), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/results/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("REJECTED"));
    }

    @Test
    void manualConfirmShouldReturn200() throws Exception {
        ManualConfirmRequest request = new ManualConfirmRequest();
        request.setEnterpriseId(100L);
        request.setNodeId(101L);
        request.setReason("人工确认挂链");

        ManualConfirmVO vo = new ManualConfirmVO();
        vo.setEnterpriseId(100L);
        vo.setNodeId(101L);
        vo.setSource("MANUAL");
        vo.setLinked(true);
        when(matchingService.manualConfirm(any(ManualConfirmRequest.class), any())).thenReturn(vo);

        mockMvc.perform(post("/api/matching/manual-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linked").value(true));
    }

    @Test
    void aiChatShouldReturn200() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setEnterpriseId(100L);
        request.setChainId(1L);
        request.setMessage("这个企业适合挂接到哪个节点？");

        AiChatResponse response = new AiChatResponse();
        response.setReply("根据企业画像分析，该企业最适合挂接到动力电池节点。");
        response.setModel("mimo-v2.5-pro-ultraspeed");
        when(aiChatService.chat(any(AiChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/matching/ai-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("根据企业画像分析，该企业最适合挂接到动力电池节点。"))
                .andExpect(jsonPath("$.data.model").value("mimo-v2.5-pro-ultraspeed"));
    }

    @Test
    void aiChatGeneralModeShouldReturn200() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("什么是产业链？");

        AiChatResponse response = new AiChatResponse();
        response.setReply("产业链是……");
        response.setModel("test-model");
        when(aiChatService.chat(any(AiChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/matching/ai-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void aiChatBlankMessageShouldReturn400() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("   ");

        mockMvc.perform(post("/api/matching/ai-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void aiChatHalfSelectedContextShouldReturn400() throws Exception {
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
    void aiChatStreamShouldReturnEventStream() throws Exception {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("你好");
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(1);
            emitter.send("测试分片");
            emitter.complete();
            return null;
        }).when(aiChatService).chatStreaming(any(), any());

        MvcResult result = mockMvc.perform(post("/api/matching/ai-chat/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
    @Test
    void runEvaluationShouldReturnReports() throws Exception {
        MatchingEvaluationReport rule = new MatchingEvaluationReport();
        rule.setProviderName("RULE_BASED");
        rule.setChainId(1L);
        rule.setSampleCount(3);
        rule.setTopK(5);
        rule.setRecallAtK(new BigDecimal("0.6667"));

        MatchingEvaluationReport llm = new MatchingEvaluationReport();
        llm.setProviderName("LLM_RERANK_V3");
        llm.setChainId(1L);
        llm.setSampleCount(3);
        llm.setTopK(3);
        llm.setFallbackCount(1);
        llm.setRecallAtK(new BigDecimal("1.0000"));

        when(matchingEvaluationService.evaluateAll(1L)).thenReturn(List.of(rule, llm));

        mockMvc.perform(post("/api/matching/evaluations/run").param("chainId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerName").value("RULE_BASED"))
                .andExpect(jsonPath("$.data[1].providerName").value("LLM_RERANK_V3"))
                .andExpect(jsonPath("$.data[1].fallbackCount").value(1));
    }

    @Test
    void notFoundShouldMapTo404() throws Exception {
        when(matchingService.getTask(999L))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "任务不存在"));

        mockMvc.perform(get("/api/matching/tasks/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/matching/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMatchingTaskRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void controllerMethodsShouldDeclareExpectedAuthorities() throws Exception {
        assertAuthority("createTask", "hasAuthority('matching:task:create')", CreateMatchingTaskRequest.class);
        assertAuthority("createTaskAsync", "hasAuthority('matching:task:execute')", CreateMatchingTaskRequest.class);
        assertAuthority("createBatchTasksAsync", "hasAuthority('matching:task:execute')", CreateBatchMatchingTaskRequest.class);
        assertAuthority("getTask", "hasAuthority('matching:task:query')", Long.class);
        assertAuthority("cancelTask", "hasAuthority('matching:task:cancel')", Long.class);
        assertAuthority("revokeTask", "hasAuthority('matching:task:cancel')", Long.class);
        assertAuthority("retryTask", "hasAuthority('matching:task:retry')", Long.class);
        assertAuthority("acceptResult", "hasAuthority('matching:result:accept')", Long.class);
        assertAuthority("rejectResult", "hasAuthority('matching:result:reject')", Long.class, RejectMatchingResultRequest.class);
        assertAuthority("manualConfirm", "hasAuthority('matching:manual-confirm:create')", ManualConfirmRequest.class);
        assertAuthority("aiChat", "hasAuthority('matching:task:create')", AiChatRequest.class);
        assertAuthority("aiChatStream", "hasAuthority('matching:task:create')", AiChatRequest.class);
        assertAuthority("runEvaluation", "hasAuthority('matching:evaluation:run')", Long.class);
    }

    private void assertAuthority(String methodName, String expected, Class<?>... parameterTypes) throws Exception {
        Method method = MatchingController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(expected, preAuthorize.value());
    }

    private MatchingTaskVO buildTaskVO() {
        MatchingResultVO resultVO = new MatchingResultVO();
        resultVO.setResultId(1L);
        resultVO.setNodeId(101L);
        resultVO.setNodePath("动力电池制造");
        resultVO.setConfidence(new BigDecimal("0.9500"));
        resultVO.setReason("高度匹配");
        resultVO.setEvidence("{}");
        resultVO.setDecision("PENDING");

        MatchingTaskVO taskVO = new MatchingTaskVO();
        taskVO.setTaskId(1L);
        taskVO.setEnterpriseId(100L);
        taskVO.setChainId(1L);
        taskVO.setStatus("COMPLETED");
        taskVO.setRetryCount(0);
        taskVO.setResults(List.of(resultVO));
        return taskVO;
    }
}
