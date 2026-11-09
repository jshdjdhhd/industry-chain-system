package git.ggwzz.analysis.controller;

import git.ggwzz.analysis.service.ChainDiagnosisTreeService;
import git.ggwzz.analysis.vo.DiagnosisTreeNodeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ChainDiagnosisController} 诊断树接口 MockMvc 测试。
 *
 * <p>采用 standaloneSetup 直接装配控制器，避免全量 Spring 上下文加载
 * （项目既有 LlmProperties 多 Bean 冲突会阻塞 @SpringBootTest 上下文）。
 * 用 Mockito mock Service，验证路由、统一响应结构与委托。</p>
 */
class ChainDiagnosisControllerTest {

    private MockMvc mockMvc;

    private ChainDiagnosisTreeService chainDiagnosisTreeService;

    @BeforeEach
    void setUp() {
        chainDiagnosisTreeService = Mockito.mock(ChainDiagnosisTreeService.class);
        ChainDiagnosisController controller =
                new ChainDiagnosisController(chainDiagnosisTreeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturn200AndDiagnosisTree() throws Exception {
        DiagnosisTreeNodeVO leaf = new DiagnosisTreeNodeVO();
        leaf.setNodeId(3L);
        leaf.setNodeName("原材料");
        leaf.setNodeLevel(2);
        leaf.setNodePath("/2/3/");
        leaf.setDirectEnterpriseCount(2);
        leaf.setSubtreeEnterpriseCount(2);
        leaf.setHealthScore(69);
        leaf.setHealthLevel("NORMAL");
        leaf.setHealthLabel("一般");
        leaf.setWeakReasons(new ArrayList<>());

        DiagnosisTreeNodeVO up = new DiagnosisTreeNodeVO();
        up.setNodeId(2L);
        up.setNodeName("上游");
        up.setNodeLevel(1);
        up.setNodePath("/2/");
        up.setDirectEnterpriseCount(1);
        up.setSubtreeEnterpriseCount(3);
        up.setHealthScore(75);
        up.setHealthLevel("NORMAL");
        up.setHealthLabel("一般");
        up.setWeakReasons(new ArrayList<>());
        up.setChildren(List.of(leaf));

        DiagnosisTreeNodeVO root = new DiagnosisTreeNodeVO();
        root.setNodeId(1L);
        root.setNodeName("新能源汽车");
        root.setNodeLevel(0);
        root.setDirectEnterpriseCount(0);
        root.setSubtreeEnterpriseCount(3);
        root.setHealthScore(80);
        root.setHealthLevel("HEALTHY");
        root.setHealthLabel("健康");
        root.setWeakReasons(new ArrayList<>());
        root.setChildren(List.of(up));

        when(chainDiagnosisTreeService.getDiagnosisTree(1L)).thenReturn(root);

        mockMvc.perform(get("/api/analysis/chains/1/diagnosis-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nodeId").value(1))
                .andExpect(jsonPath("$.data.nodeName").value("新能源汽车"))
                .andExpect(jsonPath("$.data.nodeLevel").value(0))
                .andExpect(jsonPath("$.data.subtreeEnterpriseCount").value(3))
                .andExpect(jsonPath("$.data.healthScore").value(80))
                .andExpect(jsonPath("$.data.healthLevel").value("HEALTHY"))
                .andExpect(jsonPath("$.data.healthLabel").value("健康"))
                .andExpect(jsonPath("$.data.children[0].nodeName").value("上游"))
                .andExpect(jsonPath("$.data.children[0].healthLevel")
                        .value("NORMAL"))
                .andExpect(jsonPath("$.data.children[0].children[0].nodeName")
                        .value("原材料"));
    }

    @Test
    void shouldDelegateToServiceWithChainId() throws Exception {
        DiagnosisTreeNodeVO root = new DiagnosisTreeNodeVO();
        root.setNodeId(2L);
        root.setNodeName("空产业链");
        root.setNodeLevel(0);
        root.setSubtreeEnterpriseCount(0);
        root.setHealthScore(0);
        root.setHealthLevel("EMPTY");
        root.setHealthLabel("空白");
        root.setWeakReasons(List.of("该节点及子树暂无已挂载企业"));
        root.setChildren(new ArrayList<>());

        when(chainDiagnosisTreeService.getDiagnosisTree(2L)).thenReturn(root);

        mockMvc.perform(get("/api/analysis/chains/2/diagnosis-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.healthLevel").value("EMPTY"))
                .andExpect(jsonPath("$.data.weakReasons[0]")
                        .value("该节点及子树暂无已挂载企业"));
    }
}
