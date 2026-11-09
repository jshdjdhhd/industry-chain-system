package git.ggwzz.chain.enums;

/** 产业链固定阶段。 */
public enum ChainStageType {
    UPSTREAM("上游", 1),
    MIDSTREAM("中游", 2),
    DOWNSTREAM("下游", 3);

    private final String defaultNodeName;
    private final int defaultSortOrder;

    ChainStageType(String defaultNodeName, int defaultSortOrder) {
        this.defaultNodeName = defaultNodeName;
        this.defaultSortOrder = defaultSortOrder;
    }

    public String getDefaultNodeName() {
        return defaultNodeName;
    }

    public int getDefaultSortOrder() {
        return defaultSortOrder;
    }
}