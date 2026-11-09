package git.ggwzz.chain.vo;

import lombok.Data;

/**
 * 产业链节点候选投影 VO。
 *
 * <p>只含智能挂链候选节点需要的字段，不含企业数统计。</p>
 */
@Data
public class ChainNodeCandidateVO {

    /** 节点 ID */
    private Long id;

    /** 所属产业链 ID */
    private Long chainId;

    /** 节点名称 */
    private String name;

    /** 节点路径 */
    private String nodePath;

    /** 关键词，逗号分隔 */
    private String keywords;

    /** 节点定义或描述 */
    private String description;
}
