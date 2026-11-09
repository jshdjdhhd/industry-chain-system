package git.ggwzz.analysis.enums;

import java.util.Locale;

/**
 * 健康分析报告范围类型。
 *
 * <ul>
 *   <li>{@link #SELECTED_NODES}：选中节点，后端按 node_path 展开完整子树。</li>
 *   <li>{@link #ALL_CHAIN}：整条产业链全部节点。</li>
 * </ul>
 */
public enum AnalysisScopeType {

    /** 选中节点（后端按 node_path 展开子树） */
    SELECTED_NODES,
    /** 整条产业链 */
    ALL_CHAIN;

    /**
     * 安全解析范围类型字符串，非法值返回 null。
     *
     * @param scopeType 范围类型字符串
     * @return 范围类型枚举，非法返回 null
     */
    public static AnalysisScopeType from(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return null;
        }
        try {
            return AnalysisScopeType.valueOf(scopeType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
