package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

/**
 * 企业标签文本批量查询行。
 */
@Data
public class EnterpriseTagTextRow {

    private Long enterpriseId;

    private String tagName;
}
