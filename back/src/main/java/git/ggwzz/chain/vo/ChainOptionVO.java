package git.ggwzz.chain.vo;

import lombok.Data;

/**
 * 产业链选项投影 VO，只含 ID 与名称。
 *
 * <p>不触发节点数、企业数统计，专用于下拉框。</p>
 */
@Data
public class ChainOptionVO {

    /** 产业链 ID */
    private Long id;

    /** 产业链名称 */
    private String name;
}
