package git.ggwzz.matching.embedding;

/**
 * 产业领域词扩展接口。
 */
@FunctionalInterface
public interface DomainTermExpander {

    /**
     * 根据输入文本扩展产业领域相关词。
     */
    String expand(String text);
}
