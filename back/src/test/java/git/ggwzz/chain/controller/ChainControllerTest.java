package git.ggwzz.chain.controller;

import git.ggwzz.chain.dto.CreateChainRequest;
import git.ggwzz.chain.enums.ChainStageType;
import git.ggwzz.chain.service.ChainService;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.NodeVO;
import git.ggwzz.chain.vo.TreeNodeVO;
import git.ggwzz.common.api.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ChainController} 节点关键词搜索接口 MockMvc 测试。
 *
 * <p>覆盖 {@code GET /api/chains/nodes/search-by-keyword} 的路由、参数校验、
 * 统一响应结构以及向 Service 的委托。Service 以 {@code @MockBean} 隔离。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("unittest")
@WithMockUser(authorities = "chain:node:query")
class ChainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChainService chainService;

    @Test
    @WithMockUser(authorities = "chain:chain:query")
    void listChainOptionsShouldReturnLightweightOptions() throws Exception {
        ChainOptionVO option = new ChainOptionVO();
        option.setId(2L);
        option.setName("半导体产业链");
        when(chainService.listChainOptions()).thenReturn(List.of(option));

        mockMvc.perform(get("/api/chains/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].name").value("半导体产业链"));

        verify(chainService).listChainOptions();
    }

    @Test
    void listNodeCandidatesShouldReturnLightweightCandidates()
            throws Exception {
        ChainNodeCandidateVO candidate = new ChainNodeCandidateVO();
        candidate.setId(100L);
        candidate.setChainId(1L);
        candidate.setName("中游");
        candidate.setNodePath("/100/");
        when(chainService.listNodeCandidates(1L))
                .thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/chains/1/node-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].nodePath").value("/100/"));

        verify(chainService).listNodeCandidates(1L);
    }

    @Test
    void searchByKeywordShouldReturn200AndDelegateToService() throws Exception {
        // given：Service 返回一条命中节点
        NodeVO node = new NodeVO();
        node.setId(7L);
        node.setChainId(1L);
        node.setNodeName("电芯");
        node.setKeywords("动力电池,电芯,模组");
        node.setDescription("动力电池核心组件");
        node.setNodeLevel(2);
        node.setStageType(ChainStageType.UPSTREAM);
        PageResult<NodeVO> pageResult =
                new PageResult<>(List.of(node), 1L, 1L, 20L);
        when(chainService.searchByKeywords(eq("电芯"), eq(1L), anyInt(), anyInt()))
                .thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/chains/nodes/search-by-keyword")
                        .param("keyword", "电芯")
                        .param("chainId", "1")
                        .param("pageNum", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.records[0].id").value(7))
                .andExpect(jsonPath("$.data.records[0].keywords")
                        .value("动力电池,电芯,模组"))
                .andExpect(jsonPath("$.data.records[0].description")
                        .value("动力电池核心组件"))
                .andExpect(jsonPath("$.data.records[0].stageType")
                        .value("UPSTREAM"));

        verify(chainService).searchByKeywords("电芯", 1L, 1, 20);
    }

    @Test
    void searchByKeywordShouldDelegateWithoutChainIdWhenAbsent() throws Exception {
        // given：不传 chainId 时 controller 应以 null 传入 Service
        PageResult<NodeVO> empty = new PageResult<>(List.of(), 0L, 1L, 20L);
        when(chainService.searchByKeywords(eq("电芯"), eq(null), anyInt(), anyInt()))
                .thenReturn(empty);

        // when & then
        mockMvc.perform(get("/api/chains/nodes/search-by-keyword")
                        .param("keyword", "电芯"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());

        verify(chainService).searchByKeywords("电芯", null, 1, 20);
    }

    @Test
    @WithMockUser(authorities = "chain:chain:create")
    void createChainWithBlankNameShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chainName":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(chainService, never())
                .createChain(any(CreateChainRequest.class));
    }

    @Test
    @WithMockUser(authorities = "chain:node:create")
    void createNodeWithNegativeSortOrderShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chains/1/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeName": "测试节点",
                                  "sortOrder": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(chainService, never()).createNode(any());
    }

    @Test
    @WithMockUser(authorities = "chain:node:update")
    void updateNodeWithNegativeSortOrderShouldReturn400() throws Exception {
        mockMvc.perform(put("/api/chains/nodes/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sortOrder": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(chainService, never()).updateNode(eq(3L), any());
    }

    @Test
    @WithMockUser(authorities = "chain:node:update")
    void updateNodeWithoutParentChangeShouldRequireOnlyUpdatePermission()
            throws Exception {
        NodeVO result = new NodeVO();
        result.setId(3L);
        result.setNodeName("动力电池");
        when(chainService.updateNode(eq(3L), any())).thenReturn(result);

        mockMvc.perform(put("/api/chains/nodes/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeName": "动力电池",
                                  "parentChangeRequested": false
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "chain:node:update")
    void updateNodeWithParentChangeShouldRequireMovePermission()
            throws Exception {
        mockMvc.perform(put("/api/chains/nodes/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentChangeRequested": true,
                                  "parentId": 9
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(chainService, never()).updateNode(eq(3L), any());
    }

    @Test
    @WithMockUser(authorities = {
            "chain:node:update",
            "chain:node:move"
    })
    void updateNodeWithParentChangeShouldAllowCombinedPermissions()
            throws Exception {
        NodeVO result = new NodeVO();
        result.setId(3L);
        result.setParentId(9L);
        when(chainService.updateNode(eq(3L), any())).thenReturn(result);

        mockMvc.perform(put("/api/chains/nodes/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentChangeRequested": true,
                                  "parentId": 9
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(9));
    }

    @Test
    @WithMockUser(authorities = "chain:node:query")
    void getChainTreeShouldExposeParentAndSortOrder() throws Exception {
        TreeNodeVO root = new TreeNodeVO();
        root.setId(1L);
        root.setNodeName("新能源汽车产业链");
        TreeNodeVO child = new TreeNodeVO();
        child.setId(3L);
        child.setParentId(2L);
        child.setSortOrder(7);
        child.setNodeName("动力电池");
        root.setChildren(List.of(child));
        when(chainService.getChainTree(1L)).thenReturn(root);

        mockMvc.perform(get("/api/chains/1/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children[0].parentId")
                        .value(2))
                .andExpect(jsonPath("$.data.children[0].sortOrder")
                        .value(7));
    }

    @Test
    void searchByKeywordWithBlankKeywordShouldReturn400() throws Exception {
        // 空白关键词应被 @NotBlank 拦截，返回 400 且不进入 Service
        mockMvc.perform(get("/api/chains/nodes/search-by-keyword")
                        .param("keyword", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(chainService, never())
                .searchByKeywords(eq("   "), eq(null), anyInt(), anyInt());
    }
}
