package git.ggwzz.enterprise.controller;

import git.ggwzz.enterprise.service.EnterpriseProfileAnalysisService;
import git.ggwzz.enterprise.service.EnterpriseService;
import git.ggwzz.enterprise.service.ImportService;
import git.ggwzz.enterprise.vo.EnterpriseProfileAnalysisVO;
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarMetricVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("unittest")
@WithMockUser(authorities = "enterprise:enterprise:query")
class EnterpriseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnterpriseService enterpriseService;

    @MockBean
    private ImportService importService;

    @MockBean
    private EnterpriseProfileAnalysisService enterpriseProfileAnalysisService;

    @Test
    void optionsShouldReturnLightweightEnterpriseOptions() throws Exception {
        EnterpriseOptionVO option = new EnterpriseOptionVO();
        option.setId(1L);
        option.setName("天齐锂业股份有限公司");
        when(enterpriseService.listOptions("天齐", 20))
                .thenReturn(List.of(option));

        mockMvc.perform(get("/api/enterprises/options")
                        .param("keyword", "天齐")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name")
                        .value("天齐锂业股份有限公司"));

        verify(enterpriseService).listOptions("天齐", 20);
    }

    @Test
    void profileAnalysisShouldReturnProfileAnalysis() throws Exception {
        EnterpriseProfileAnalysisVO vo = new EnterpriseProfileAnalysisVO();
        vo.setEnterpriseId(1L);
        vo.setEnterpriseName("宁德时代");
        vo.setRadar(new EnterpriseRadarVO());
        vo.setSimilarEnterprises(List.of());
        when(enterpriseProfileAnalysisService.analyze(1L, 10)).thenReturn(vo);

        mockMvc.perform(get("/api/enterprises/1/profile-analysis")
                        .param("similarLimit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andExpect(jsonPath("$.data.enterpriseName").value("宁德时代"));

        verify(enterpriseProfileAnalysisService).analyze(1L, 10);
    }

    @Test
    void radarShouldReturnRadarMetricsOnly() throws Exception {
        EnterpriseRadarMetricVO metric = new EnterpriseRadarMetricVO();
        metric.setKey("patentCount");
        metric.setName("专利数量");
        metric.setValue(80);
        EnterpriseRadarVO radar = new EnterpriseRadarVO();
        radar.setMetrics(List.of(metric));
        when(enterpriseProfileAnalysisService.analyzeRadar(1L)).thenReturn(radar);

        mockMvc.perform(get("/api/enterprises/1/profile-analysis/radar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.metrics[0].key").value("patentCount"))
                .andExpect(jsonPath("$.data.metrics[0].value").value(80));

        verify(enterpriseProfileAnalysisService).analyzeRadar(1L);
    }

    @Test
    @WithMockUser(authorities = "enterprise:enterprise:update")
    void warmupProfileVectorsShouldDelegateRequestBody() throws Exception {
        VectorWarmupResultVO vo = new VectorWarmupResultVO();
        vo.setTotalCount(2);
        vo.setSuccessCount(2);
        when(enterpriseProfileAnalysisService.warmupVectors(argThat(request ->
                request != null && request.getEnterpriseIds().equals(List.of(1L, 2L)))))
                .thenReturn(vo);

        mockMvc.perform(post("/api/enterprises/profile-analysis/vector-warmup")
                        .contentType("application/json")
                        .content("{\"enterpriseIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2));
    }
}
