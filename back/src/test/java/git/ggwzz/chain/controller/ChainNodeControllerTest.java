package git.ggwzz.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.BatchLinkEnterpriseRequest;
import git.ggwzz.chain.enums.BatchLinkEnterpriseStatus;
import git.ggwzz.chain.service.EnterpriseNodeRelService;
import git.ggwzz.chain.vo.BatchLinkEnterpriseItemVO;
import git.ggwzz.chain.vo.BatchLinkEnterpriseVO;
import git.ggwzz.chain.vo.NodeEnterpriseVO;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.service.MatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ChainNodeController} MockMvc 测试。
 *
 * <p>覆盖批量挂接接口路由、统一响应结构和请求体参数校验。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("unittest")
@WithMockUser(authorities = {
        "chain:node:query",
        "chain:node:link-enterprise",
        "chain:node:batch-link-enterprise",
        "chain:node:unlink-enterprise"
})
class ChainNodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnterpriseNodeRelService enterpriseNodeRelService;

    @MockBean
    private EnterpriseQueryService enterpriseQueryService;

    @MockBean
    private MatchingService matchingService;

    @Test
    void batchLinkEnterprisesShouldReturn200() throws Exception {
        BatchLinkEnterpriseRequest request = new BatchLinkEnterpriseRequest();
        request.setEnterpriseIds(List.of(1L, 2L));

        BatchLinkEnterpriseVO vo = new BatchLinkEnterpriseVO();
        vo.setTotal(2);
        vo.setLinkedCount(2);
        vo.setSkippedCount(0);
        vo.setFailedCount(0);
        vo.setItems(List.of(
                new BatchLinkEnterpriseItemVO(1L, BatchLinkEnterpriseStatus.LINKED, "挂接成功"),
                new BatchLinkEnterpriseItemVO(2L, BatchLinkEnterpriseStatus.LINKED, "挂接成功")
        ));
        when(enterpriseNodeRelService.linkManuallyBatch(eq(100L), eq(List.of(1L, 2L)), any()))
                .thenReturn(vo);

        mockMvc.perform(post("/api/nodes/100/enterprises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.linkedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].enterpriseId").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("LINKED"));
    }

    @Test
    void batchLinkEnterprisesWithEmptyEnterpriseIdsShouldReturn400() throws Exception {
        BatchLinkEnterpriseRequest request = new BatchLinkEnterpriseRequest();
        request.setEnterpriseIds(List.of());

        mockMvc.perform(post("/api/nodes/100/enterprises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void batchLinkEnterprisesWithNullEnterpriseIdShouldReturn400() throws Exception {
        String body = "{\"enterpriseIds\":[1,null]}";

        mockMvc.perform(post("/api/nodes/100/enterprises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void linkEnterpriseShouldReturn404WhenNodeNotFound() throws Exception {
        doThrow(nodeNotFound())
                .when(enterpriseNodeRelService)
                .linkManually(eq(1L), eq(999L), eq("人工挂链"), any());

        mockMvc.perform(post("/api/nodes/999/enterprises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("节点不存在或已删除"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void batchLinkEnterprisesShouldReturn404WhenNodeNotFound() throws Exception {
        when(enterpriseNodeRelService.linkManuallyBatch(eq(999L), eq(List.of(1L, 2L)), any()))
                .thenThrow(nodeNotFound());

        mockMvc.perform(post("/api/nodes/999/enterprises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseIds\":[1,2]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("节点不存在或已删除"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void listEnterprisesShouldReturn404WhenNodeNotFound() throws Exception {
        when(enterpriseNodeRelService.listByNodeId(999L)).thenThrow(nodeNotFound());

        mockMvc.perform(get("/api/nodes/999/enterprises"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("节点不存在或已删除"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unlinkEnterpriseShouldReturn404WhenNodeNotFound() throws Exception {
        doThrow(nodeNotFound())
                .when(enterpriseNodeRelService)
                .unlink(eq(999L), eq(1L), any());

        mockMvc.perform(delete("/api/nodes/999/enterprises/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("节点不存在或已删除"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void listEnterprisesShouldDelegateToService() throws Exception {
        NodeEnterpriseVO enterprise = new NodeEnterpriseVO();
        enterprise.setEnterpriseId(1L);
        enterprise.setEnterpriseName("合肥测试企业");
        enterprise.setSource("MANUAL");
        when(enterpriseNodeRelService.listByNodeId(100L)).thenReturn(List.of(enterprise));

        mockMvc.perform(get("/api/nodes/100/enterprises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].enterpriseId").value(1))
                .andExpect(jsonPath("$.data[0].enterpriseName").value("合肥测试企业"))
                .andExpect(jsonPath("$.data[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.data[0].nodeId").doesNotExist());

        verify(enterpriseNodeRelService).listByNodeId(100L);
    }

    private BusinessException nodeNotFound() {
        return new BusinessException(ResultCode.NOT_FOUND, "节点不存在或已删除");
    }
}
