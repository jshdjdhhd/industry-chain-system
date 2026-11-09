package git.ggwzz.system.dto;

import git.ggwzz.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {
    private String username;
    private Integer status;
}
