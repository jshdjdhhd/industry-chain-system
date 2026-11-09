package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "新密码长度 6-128")
    private String newPassword;
}
