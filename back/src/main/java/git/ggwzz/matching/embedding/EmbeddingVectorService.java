package git.ggwzz.matching.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.matching.entity.AiEmbeddingVector;
import git.ggwzz.matching.mapper.AiEmbeddingVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量生成与缓存服务（带 JVM 本地缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingVectorService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AiEmbeddingVectorMapper aiEmbeddingVectorMapper;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorCodec vectorCodec;
    private final TransactionTemplate transactionTemplate;

    /**
     * L1: decoded vector 缓存。
     * key = objectType:objectId:modelName:textHash, value = float[]
     */
    private final Cache<String, float[]> decodedVectorCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public String getCurrentModelName() {
        return embeddingClient.modelName();
    }

    @Transactional(rollbackFor = Exception.class)
    public float[] getOrCreateVector(String objectType, Long objectId, String profileText) {
        validateRequest(objectType, objectId, profileText);
        String modelName = embeddingClient.modelName();
        String textHash = TextHash.sha256(profileText);

        // 先查 JVM 本地缓存
        String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHash;
        float[] cached = decodedVectorCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 再查数据库
        AiEmbeddingVector active = aiEmbeddingVectorMapper.selectActive(objectType, objectId, modelName);
        if (active != null && textHash.equals(active.getTextHash())) {
            float[] decoded = vectorCodec.decode(active.getVectorData());
            decodedVectorCache.put(cacheKey, decoded);
            return decoded;
        }

        // 缓存未命中，调用 embedding 服务
        float[] vector = embeddingClient.embed(profileText);
        if (vector == null || vector.length == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "embedding 服务返回空向量");
        }

        aiEmbeddingVectorMapper.markActiveDeleted(objectType, objectId, modelName);
        AiEmbeddingVector record = new AiEmbeddingVector();
        record.setObjectType(objectType);
        record.setObjectId(objectId);
        record.setModelName(modelName);
        record.setDimension(vector.length);
        record.setTextHash(textHash);
        record.setVectorData(vectorCodec.encode(vector));
        record.setSourceTextSnapshot(profileText);
        record.setStatus(STATUS_ACTIVE);
        record.setDeleted(0);
        aiEmbeddingVectorMapper.insert(record);

        decodedVectorCache.put(cacheKey, vector);
        return vector;
    }

    /**
     * 批量获取已缓存的向量。只返回数据库中已有且 textHash 匹配的向量。
     * 不会调用 embedding 服务生成新向量。
     *
     * @return objectId → float[] 映射，未命中的 objectId 不包含在结果中
     */
    public Map<Long, float[]> getBatchCachedVectors(String objectType,
                                                     List<Long> objectIds,
                                                     String modelName,
                                                     Map<Long, String> objectIdToTextHash) {
        Map<Long, float[]> result = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();

        // 先从 JVM 缓存取
        for (Long objectId : objectIds) {
            String textHash = objectIdToTextHash.get(objectId);
            if (textHash == null) {
                missedIds.add(objectId);
                continue;
            }
            String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHash;
            float[] cachedVec = decodedVectorCache.getIfPresent(cacheKey);
            if (cachedVec != null) {
                result.put(objectId, cachedVec);
            } else {
                missedIds.add(objectId);
            }
        }

        if (missedIds.isEmpty()) {
            return result;
        }

        // 批量查数据库（已按 object_id, update_time DESC 排序，每组取第一条）
        List<AiEmbeddingVector> dbVectors = aiEmbeddingVectorMapper.selectActiveBatch(
                objectType, missedIds, modelName);
        if (dbVectors != null) {
            java.util.Set<Long> processedIds = new java.util.HashSet<>();
            for (AiEmbeddingVector vec : dbVectors) {
                if (processedIds.contains(vec.getObjectId())) {
                    continue; // 跳过同一 objectId 的旧记录
                }
                processedIds.add(vec.getObjectId());
                String expectedHash = objectIdToTextHash.get(vec.getObjectId());
                if (expectedHash != null && expectedHash.equals(vec.getTextHash())) {
                    float[] decoded = vectorCodec.decode(vec.getVectorData());
                    String cacheKey = objectType + ":" + vec.getObjectId() + ":" + modelName + ":" + expectedHash;
                    decodedVectorCache.put(cacheKey, decoded);
                    result.put(vec.getObjectId(), decoded);
                }
            }
        }

        return result;
    }

    /**
     * 直接获取或创建向量（指定 modelName，不依赖 embeddingClient 线程上下文）。
     * 注意：此方法不使用 @Transactional，因为 embedding 服务调用可能很慢，
     * 不应该在事务中执行以避免长时间占用数据库连接。
     */
    public float[] getOrCreateVectorDirect(String objectType, Long objectId,
                                             String modelName, String profileText) {
        String textHash = TextHash.sha256(profileText);

        String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHash;
        float[] cached = decodedVectorCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查询数据库（不需要事务）
        AiEmbeddingVector active = aiEmbeddingVectorMapper.selectActive(objectType, objectId, modelName);
        if (active != null && textHash.equals(active.getTextHash())) {
            float[] decoded = vectorCodec.decode(active.getVectorData());
            decodedVectorCache.put(cacheKey, decoded);
            return decoded;
        }

        // 调用 embedding 服务（可能很慢，不在事务中）
        float[] vector = embeddingClient.embed(profileText);
        if (vector == null || vector.length == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "embedding 服务返回空向量");
        }

        // 保存到数据库（使用独立事务）
        saveVectorToDatabase(objectType, objectId, modelName, textHash, vector, profileText);

        decodedVectorCache.put(cacheKey, vector);
        return vector;
    }

    /**
     * 批量获取或创建向量。缓存命中的对象不调用 embedding 服务；
     * 未命中的对象合并为一次 batch embedding 请求。
     */
    public Map<Long, float[]> getOrCreateVectorsDirectBatch(String objectType,
                                                            Map<Long, String> objectIdToProfileText,
                                                            String modelName) {
        if (objectIdToProfileText == null || objectIdToProfileText.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> textHashes = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : objectIdToProfileText.entrySet()) {
            textHashes.put(entry.getKey(), TextHash.sha256(entry.getValue()));
        }

        Map<Long, float[]> result = new LinkedHashMap<>(getBatchCachedVectors(
                objectType, new ArrayList<>(objectIdToProfileText.keySet()), modelName, textHashes));

        List<Long> missingIds = objectIdToProfileText.keySet().stream()
                .filter(id -> !result.containsKey(id))
                .toList();
        if (missingIds.isEmpty()) {
            return result;
        }

        List<String> missingProfiles = missingIds.stream()
                .map(objectIdToProfileText::get)
                .toList();
        List<float[]> generatedVectors = embeddingClient.embedBatch(missingProfiles);
        if (generatedVectors.size() != missingIds.size()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "embedding 服务返回向量数量不正确");
        }

        saveVectorsToDatabase(objectType, modelName, missingIds, textHashes,
                generatedVectors, objectIdToProfileText);
        for (int i = 0; i < missingIds.size(); i++) {
            Long objectId = missingIds.get(i);
            float[] vector = generatedVectors.get(i);
            result.put(objectId, vector);
            String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHashes.get(objectId);
            decodedVectorCache.put(cacheKey, vector);
        }

        return result;
    }

    /**
     * 保存向量到数据库（使用独立事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveVectorToDatabase(String objectType, Long objectId, String modelName,
                                      String textHash, float[] vector, String profileText) {
        transactionTemplate.executeWithoutResult(status -> {
            aiEmbeddingVectorMapper.markActiveDeleted(objectType, objectId, modelName);
            aiEmbeddingVectorMapper.insert(buildRecord(
                    objectType, objectId, modelName, textHash, vector, profileText));
        });
    }

    private void saveVectorsToDatabase(String objectType,
                                       String modelName,
                                       List<Long> objectIds,
                                       Map<Long, String> textHashes,
                                       List<float[]> vectors,
                                       Map<Long, String> profileTexts) {
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < objectIds.size(); i++) {
                Long objectId = objectIds.get(i);
                float[] vector = vectors.get(i);
                aiEmbeddingVectorMapper.markActiveDeleted(objectType, objectId, modelName);
                aiEmbeddingVectorMapper.insert(buildRecord(
                        objectType, objectId, modelName, textHashes.get(objectId),
                        vector, profileTexts.get(objectId)));
            }
        });
    }

    private AiEmbeddingVector buildRecord(String objectType,
                                          Long objectId,
                                          String modelName,
                                          String textHash,
                                          float[] vector,
                                          String profileText) {
        AiEmbeddingVector record = new AiEmbeddingVector();
        record.setObjectType(objectType);
        record.setObjectId(objectId);
        record.setModelName(modelName);
        record.setDimension(vector.length);
        record.setTextHash(textHash);
        record.setVectorData(vectorCodec.encode(vector));
        record.setSourceTextSnapshot(profileText);
        record.setStatus(STATUS_ACTIVE);
        record.setDeleted(0);
        return record;
    }

    private void validateRequest(String objectType, Long objectId, String profileText) {
        if (objectType == null || objectType.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "向量对象类型不能为空");
        }
        if (objectId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "向量对象 ID 不能为空");
        }
        if (profileText == null || profileText.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "画像文本不能为空");
        }
    }
}
