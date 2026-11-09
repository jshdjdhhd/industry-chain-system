package git.ggwzz.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssignPermissionsRequest {
    private List<Long> permissionIds;
}
