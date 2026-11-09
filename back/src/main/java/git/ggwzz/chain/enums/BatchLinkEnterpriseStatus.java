package git.ggwzz.chain.enums;

/**
 * 批量企业挂接处理状态。
 */
public enum BatchLinkEnterpriseStatus {

    /** 新增挂接成功 */
    LINKED,

    /** 企业已挂接到目标节点 */
    ALREADY_LINKED,

    /** 企业 ID 在本次请求中重复 */
    DUPLICATE_IN_REQUEST,

    /** 单个企业挂接失败 */
    FAILED
}
