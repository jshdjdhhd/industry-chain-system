package git.ggwzz.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.auth.dto.LoginRequest;
import git.ggwzz.auth.service.AuthService;
import git.ggwzz.auth.vo.LoginVO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthController} 集成测试（SpringBootTest + H2，隔离 AuthService）。
 *
 * <p>验证登录成功、参数校验失败、业务异常状态码、请求体格式错误与统一响应结构。
 * 走真实 Security 过滤链但放行 {@code /api/auth/login}。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private EnterpriseQueryService enterpriseQueryService;

    @MockBean
    private ChainNodeQueryService chainNodeQueryService;

    @Test
    void loginShouldReturn200WithToken() throws Exception {
        LoginVO vo = LoginVO.builder()
                .token("TOKEN").tokenType("Bearer").expiresIn(7200L)
                .userId(1L).username("admin").realName("管理员")
                .permissions(List.of("enterprise:enterprise:query"))
                .build();
        when(authService.login(any(LoginRequest.class))).thenReturn(vo);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("TOKEN"));
    }

    @Test
    void loginWithBlankFieldsShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void loginWithBusinessExceptionShouldReturn401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void loginWithMalformedJsonShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}