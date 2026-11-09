package git.ggwzz.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Size(min = 1, max = 64, message = "真实姓名长度 1-64")
    private String realName;
    private String email;
    private String phone;
}
