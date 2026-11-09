package git.ggwzz.chain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.enums.BatchLinkEnterpriseStatus;
import git.ggwzz.chain.enums.LinkSource;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.service.EnterpriseNodeRelService;
import git.ggwzz.chain.vo.BatchLinkEnterpriseItemVO;
import git.ggwzz.chain.vo.BatchLinkEnterpriseVO;
import git.ggwzz.chain.vo.NodeEnterpriseVO;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 企业-节点关系服务实现。
 *
 * <p>提供 AI 推荐挂链、人工确认挂链、解绑和查询能力。
 * 所有写操作加 {@code @Transactional} 保证数据一致性，
 * 挂链/解绑操作通过 {@code @OperationLog} 注解记录审计日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseNodeRelServiceImpl
        extends ServiceImpl<EnterpriseNodeRelMapper, EnterpriseNodeRel>
        implements EnterpriseNodeRelService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ChainNodeMapper chainNodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkByAi(Long enterpriseId, Long nodeId, BigDecimal confidence,
                         String reason, Long operator) {
        // AI 与人工挂接共用节点有效性、企业 ACTIVE 状态和重复关系约束，保证正式关系口径一致。
        validateNodeExists(nodeId);
        validateEnterpriseLinkable(enterpriseId);

        if (isLinked(enterpriseId, nodeId)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "该企业已挂链到此节点");
        }

        saveRelation(enterpriseId, nodeId, LinkSource.AI, confidence, reason, operator);
        log.info("AI推荐挂链成功: enterpriseId={}, nodeId={}, confidence={}, operator={}",
                enterpriseId, nodeId, confidence, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkManually(Long enterpriseId, Long nodeId, String reason,
                             Long operator) {
        // 单个人工挂接先完成共享资源校验，再写入 MANUAL 来源和确认人。
        validateNodeExists(nodeId);
        validateEnterpriseLinkable(enterpriseId);

        if (isLinked(enterpriseId, nodeId)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "该企业已挂链到此节点");
        }

        saveManualRelation(enterpriseId, nodeId, reason, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchLinkEnterpriseVO linkManuallyBatch(Long nodeId, List<Long> enterpriseIds, Long operator) {
        if (enterpriseIds == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业ID列表不能为空");
        }
        // 节点是整批共享资源，先整体校验；企业则逐项处理，使单项失败不会中断其他合法企业。
        validateNodeExists(nodeId);

        BatchLinkEnterpriseVO result = new BatchLinkEnterpriseVO();
        result.setTotal(enterpriseIds.size());
        result.setLinkedCount(0);
        result.setSkippedCount(0);
        result.setFailedCount(0);

        Set<Long> processedEnterpriseIds = new HashSet<>();
        for (Long enterpriseId : enterpriseIds) {
            // 使用 Set 识别请求内重复项，并将其作为跳过明细返回而不是重复写库。
            if (!processedEnterpriseIds.add(enterpriseId)) {
                addItem(result, enterpriseId, BatchLinkEnterpriseStatus.DUPLICATE_IN_REQUEST,
                        "请求中存在重复企业 ID，已跳过");
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            if (isLinked(enterpriseId, nodeId)) {
                addItem(result, enterpriseId, BatchLinkEnterpriseStatus.ALREADY_LINKED,
                        "该企业已挂接到此节点，已跳过");
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            try {
                validateEnterpriseLinkable(enterpriseId);
                saveManualRelation(enterpriseId, nodeId, "人工挂链", operator);
                addItem(result, enterpriseId, BatchLinkEnterpriseStatus.LINKED, "挂接成功");
                result.setLinkedCount(result.getLinkedCount() + 1);
            } catch (BusinessException ex) {
                // 仅捕获可预期业务异常并记录 FAILED，数据库等系统异常仍向上抛出触发事务回滚。
                addItem(result, enterpriseId, BatchLinkEnterpriseStatus.FAILED,
                        "挂接失败：" + ex.getMessage());
                result.setFailedCount(result.getFailedCount() + 1);
            }
        }

        log.info("批量人工挂链完成: nodeId={}, total={}, linkedCount={}, skippedCount={}, failedCount={}, operator={}",
                nodeId, result.getTotal(), result.getLinkedCount(), result.getSkippedCount(),
                result.getFailedCount(), operator);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NodeEnterpriseVO> listByNodeId(Long nodeId) {
        validateNodeExists(nodeId);
        return baseMapper.selectNodeEnterpriseVosByNodeId(nodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlink(Long nodeId, Long enterpriseId, Long operator) {
        validateNodeExists(nodeId);

        LambdaQueryWrapper<EnterpriseNodeRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseNodeRel::getEnterpriseId, enterpriseId)
                .eq(EnterpriseNodeRel::getNodeId, nodeId)
                .eq(EnterpriseNodeRel::getDeleted, 0);

        EnterpriseNodeRel rel = baseMapper.selectOne(wrapper);
        if (rel == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,
                    "该企业未挂链到此节点");
        }

        removeById(rel.getId());
        log.info("解绑成功: enterpriseId={}, nodeId={}, operator={}", enterpriseId, nodeId, operator);
    }

    @Override
    public boolean isLinked(Long enterpriseId, Long nodeId) {
        LambdaQueryWrapper<EnterpriseNodeRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseNodeRel::getEnterpriseId, enterpriseId)
                .eq(EnterpriseNodeRel::getNodeId, nodeId)
                .eq(EnterpriseNodeRel::getDeleted, 0);
        return count(wrapper) > 0;
    }

    @Override
    public Set<Long> listLinkedNodeIdsByEnterpriseAndChain(Long enterpriseId, Long chainId) {
        if (enterpriseId == null || chainId == null) {
            return Set.of();
        }
        List<Long> nodeIds = baseMapper.selectLinkedNodeIdsByEnterpriseAndChain(
                enterpriseId, chainId);
        Set<Long> result = new HashSet<>();
        if (nodeIds != null) {
            for (Long nodeId : nodeIds) {
                if (nodeId != null) {
                    result.add(nodeId);
                }
            }
        }
        return result;
    }

    private void saveManualRelation(Long enterpriseId, Long nodeId, String reason, Long operator) {
        saveRelation(enterpriseId, nodeId, LinkSource.MANUAL, null, reason, operator);
        log.info("人工挂链成功: enterpriseId={}, nodeId={}, operator={}", enterpriseId, nodeId, operator);
    }

    private void saveRelation(Long enterpriseId,
                              Long nodeId,
                              LinkSource source,
                              BigDecimal confidence,
                              String reason,
                              Long operator) {
        EnterpriseNodeRel rel = new EnterpriseNodeRel();
        rel.setEnterpriseId(enterpriseId);
        rel.setNodeId(nodeId);
        rel.setSource(source.name());
        rel.setConfidence(confidence);
        rel.setReason(reason);
        rel.setConfirmedBy(operator);
        rel.setDeleted(0);

        try {
            if (baseMapper.restoreDeletedRelation(rel) > 0) {
                log.info("恢复挂链成功: enterpriseId={}, nodeId={}, source={}, operator={}",
                        enterpriseId, nodeId, source.name(), operator);
                return;
            }

            save(rel);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "该企业已挂链到此节点");
        }
    }

    private void addItem(BatchLinkEnterpriseVO result, Long enterpriseId,
                         BatchLinkEnterpriseStatus status, String message) {
        result.getItems().add(new BatchLinkEnterpriseItemVO(enterpriseId, status, message));
    }

    /**
     * 校验企业存在、未逻辑删除且状态允许挂接。
     *
     * @param enterpriseId 企业 ID
     */
    private void validateEnterpriseLinkable(Long enterpriseId) {
        if (enterpriseId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业 ID 不能为空");
        }
        String status = baseMapper.selectEnterpriseStatusById(enterpriseId);
        if (status == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!ACTIVE_STATUS.equals(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业状态不可挂接");
        }
    }

    /**
     * 校验节点存在且未逻辑删除。
     *
     * @param nodeId 节点 ID
     */
    private void validateNodeExists(Long nodeId) {
        if (nodeId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节点 ID 不能为空");
        }
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null || Integer.valueOf(1).equals(node.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在或已删除");
        }
    }
}
