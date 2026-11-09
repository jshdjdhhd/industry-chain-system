package git.ggwzz.matching.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量生成客户端。
 */
public interface EmbeddingClient {

    String modelName();

    int dimension();

    float[] embed(String text);

    default List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }
}
