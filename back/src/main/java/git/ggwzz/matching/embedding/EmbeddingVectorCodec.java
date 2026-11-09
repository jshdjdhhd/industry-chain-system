package git.ggwzz.matching.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 向量 JSON 编解码。
 */
@Component
@RequiredArgsConstructor
public class EmbeddingVectorCodec {

    private final ObjectMapper objectMapper;

    public String encode(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "序列化向量失败");
        }
    }

    public float[] decode(String vectorData) {
        try {
            JsonNode root = objectMapper.readTree(vectorData);
            if (!root.isArray()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "向量缓存格式不正确");
            }
            float[] vector = new float[root.size()];
            for (int i = 0; i < root.size(); i++) {
                vector[i] = (float) root.get(i).asDouble();
            }
            return vector;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "解析向量缓存失败");
        }
    }
}
