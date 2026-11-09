package git.ggwzz.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.enterprise.service.EnterpriseService;
import git.ggwzz.matching.service.MatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
class SecurityFlowTest {

    private static final String PASSWORD = "admin123";
    private static final String ADMIN = "admin";
    private static final String VIEWER = "viewer";
    private static final Set<String> CANONICAL_PERMISSION_CODES = Set.of(
            "enterprise:enterprise:query",
            "enterprise:enterprise:create",
            "enterprise:enterprise:update",
            "enterprise:enterprise:delete",
            "enterprise:enterprise:inbound",
            "enterprise:enterprise:outbound",
            "enterprise:enterprise:batch-outbound",
            "enterprise:enterprise:import",
            "enterprise:tag:bind",
            "enterprise:tag:unbind",
            "enterprise:tag:query",
            "enterprise:tag:create",
            "enterprise:tag:update",
            "enterprise:tag:delete",
            "chain:chain:query",
            "chain:chain:create",
            "chain:chain:update",
            "chain:chain:delete",
            "chain:node:query",
            "chain:node:create",
            "chain:node:update",
            "chain:node:move",
            "chain:node:sort",
            "chain:node:delete",
            "chain:node:link-enterprise",
            "chain:node:batch-link-enterprise",
            "chain:node:unlink-enterprise",
            "matching:task:query",
            "matching:task:create",
            "matching:task:execute",
            "matching:task:cancel",
            "matching:task:retry",
            "matching:result:accept",
            "matching:result:reject",
            "matching:manual-confirm:create",
            "matching:evaluation:run"
    );
    private static final Set<String> VIEWER_PERMISSION_CODES =
            Set.of("enterprise:enterprise:query");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnterpriseService enterpriseService;

    @MockBean
    private MatchingService matchingService;

    @MockBean
    private EnterpriseQueryService enterpriseQueryService;

    @MockBean
    private ChainNodeQueryService chainNodeQueryService;

    @Test
    void anonymousAccessToProtectedEndpointShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/enterprises"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void invalidTokenShouldBeRejectedAs401() throws Exception {
        mockMvc.perform(get("/api/enterprises")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminLoginShouldExposeCanonicalPermissions() throws Exception {
        JsonNode loginBody = login(ADMIN, PASSWORD);
        String token = extractToken(loginBody);

        assertEquals(CANONICAL_PERMISSION_CODES, readPermissions(loginBody));

        MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(ADMIN))
                .andReturn();

        JsonNode meBody = objectMapper.readTree(meResult.getResponse().getContentAsString());
        assertEquals(CANONICAL_PERMISSION_CODES, readPermissions(meBody));
    }

    @Test
    void restrictedUserWithQueryPermissionShouldAccessEnterpriseList() throws Exception {
        when(enterpriseService.list(any())).thenReturn(PageResult.empty(1, 10));

        JsonNode loginBody = login(VIEWER, PASSWORD);
        String token = extractToken(loginBody);

        assertEquals(VIEWER_PERMISSION_CODES, readPermissions(loginBody));

        mockMvc.perform(get("/api/enterprises")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void restrictedUserWithoutEnterpriseWritePermissionShouldReturn403() throws Exception {
        String token = extractToken(login(VIEWER, PASSWORD));

        mockMvc.perform(delete("/api/enterprises/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void restrictedUserWithoutChainQueryPermissionShouldReturn403() throws Exception {
        String token = extractToken(login(VIEWER, PASSWORD));

        mockMvc.perform(get("/api/chains")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void restrictedUserWithoutMatchingWritePermissionShouldReturn403() throws Exception {
        String token = extractToken(login(VIEWER, PASSWORD));

        mockMvc.perform(post("/api/matching/results/999999/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void healthShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String extractToken(JsonNode body) {
        return body.get("data").get("token").asText();
    }

    private Set<String> readPermissions(JsonNode body) {
        Set<String> permissions = new LinkedHashSet<>();
        body.get("data").get("permissions").forEach(node -> permissions.add(node.asText()));
        return permissions;
    }
}
