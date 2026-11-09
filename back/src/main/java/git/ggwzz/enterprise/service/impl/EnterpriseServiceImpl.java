package git.ggwzz.enterprise.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.dto.CreateEnterpriseRequest;
import git.ggwzz.enterprise.dto.EnterpriseQueryRequest;
import git.ggwzz.enterprise.dto.UpdateEnterpriseRequest;
import git.ggwzz.enterprise.entity.Enterprise;
import git.ggwzz.enterprise.entity.EnterpriseTagRel;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.enterprise.mapper.EnterpriseOptionMapper;
import git.ggwzz.enterprise.mapper.EnterpriseTagRelMapper;
import git.ggwzz.enterprise.service.EnterpriseService;
import git.ggwzz.enterprise.vo.EnterpriseDetailVO;
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import git.ggwzz.enterprise.vo.EnterpriseVO;
import git.ggwzz.enterprise.vo.LinkedNodeVO;
import git.ggwzz.tag.entity.Tag;
import git.ggwzz.tag.mapper.TagMapper;
import git.ggwzz.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 企业管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class EnterpriseServiceImpl implements EnterpriseService {

    private static final String STATUS_UNREGISTERED = "UNREGISTERED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_OUTBOUND = "OUTBOUND";

    private final EnterpriseMapper enterpriseMapper;
    private final EnterpriseOptionMapper enterpriseOptionMapper;
    private final EnterpriseTagRelMapper enterpriseTagRelMapper;
    private final EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    private final TagMapper tagMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final ChainNodeQueryService chainNodeQueryService;

    @Override
    public PageResult<EnterpriseVO> list(EnterpriseQueryRequest query) {
        if (query == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        Page<Enterprise> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();

        // 关键词模糊匹配
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w
                    .like(Enterprise::getEnterpriseName, keyword)
                    .or().like(Enterprise::getUnifiedCode, keyword)
                    .or().like(Enterprise::getLegalPerson, keyword)
                    .or().like(Enterprise::getBusinessScope, keyword)
                    .or().like(Enterprise::getMainProducts, keyword)
            );
        }

        // 状态筛选
        if (StringUtils.isNotBlank(query.getStatus())) {
            wrapper.eq(Enterprise::getStatus, query.getStatus());
        }

        // 标签条件位于关系表：先收集关联企业 ID，再回到企业主表执行统一分页查询。
        if (query.getTagId() != null) {
            LambdaQueryWrapper<EnterpriseTagRel> tagRelWrapper = new LambdaQueryWrapper<>();
            tagRelWrapper.eq(EnterpriseTagRel::getTagId, query.getTagId());
            Set<Long> enterpriseIds = enterpriseTagRelMapper.selectList(tagRelWrapper)
                    .stream().map(EnterpriseTagRel::getEnterpriseId).collect(Collectors.toSet());
            if (enterpriseIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(Enterprise::getId, enterpriseIds);
        }

        // 节点筛选采用相同的“两阶段查询”，避免在通用单表分页中拼接多表业务逻辑。
        if (query.getNodeId() != null) {
            LambdaQueryWrapper<EnterpriseNodeRel> nodeRelWrapper = new LambdaQueryWrapper<>();
            nodeRelWrapper.eq(EnterpriseNodeRel::getNodeId, query.getNodeId());
            Set<Long> enterpriseIds = enterpriseNodeRelMapper.selectList(nodeRelWrapper)
                    .stream().map(EnterpriseNodeRel::getEnterpriseId).collect(Collectors.toSet());
            if (enterpriseIds.isEmpty()) {
                return PageResult.empty(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(Enterprise::getId, enterpriseIds);
        }

        wrapper.orderByDesc(Enterprise::getCreateTime);

        IPage<Enterprise> result = enterpriseMapper.selectPage(page, wrapper);

        List<Long> enterpriseIds = result.getRecords().stream()
                .map(Enterprise::getId)
                .collect(Collectors.toList());
        Map<Long, List<TagVO>> tagMap = loadTagsByEnterpriseIds(enterpriseIds);
        List<EnterpriseVO> voList = result.getRecords().stream()
                .map(enterprise -> toVO(
                        enterprise,
                        tagMap.getOrDefault(enterprise.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        return new PageResult<>(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<EnterpriseOptionVO> listOptions(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return enterpriseOptionMapper.searchActiveOptions(safeKeyword, safeLimit);
    }

    @Override
    public EnterpriseDetailVO getById(Long id) {
        Enterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        return toDetailVO(enterprise);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseDetailVO create(CreateEnterpriseRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        // 唯一性校验：统一社会信用代码
        if (StringUtils.isNotBlank(request.getCreditCode())) {
            LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Enterprise::getUnifiedCode, request.getCreditCode());
            if (enterpriseMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "统一社会信用代码已存在");
            }
        }

        Enterprise enterprise = new Enterprise();
        copyProperties(request, enterprise);
        // 新采集企业统一进入未入库状态，后续必须通过入库动作才能参与挂接和匹配。
        enterprise.setStatus(STATUS_UNREGISTERED);
        enterpriseMapper.insert(enterprise);

        return toDetailVO(enterprise);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseDetailVO update(Long id, UpdateEnterpriseRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        Enterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }

        // 唯一性校验：统一社会信用代码（排除自身）
        if (StringUtils.isNotBlank(request.getCreditCode())
                && !request.getCreditCode().equals(enterprise.getUnifiedCode())) {
            LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Enterprise::getUnifiedCode, request.getCreditCode())
                    .ne(Enterprise::getId, id);
            if (enterpriseMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "统一社会信用代码已存在");
            }
        }

        copyProperties(request, enterprise);
        enterpriseMapper.updateById(enterprise);

        return toDetailVO(enterprise);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Enterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!STATUS_UNREGISTERED.equals(enterprise.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有未入库企业可以删除");
        }
        // 删除关联的标签关系
        LambdaQueryWrapper<EnterpriseTagRel> tagRelWrapper = new LambdaQueryWrapper<>();
        tagRelWrapper.eq(EnterpriseTagRel::getEnterpriseId, id);
        enterpriseTagRelMapper.delete(tagRelWrapper);

        enterpriseMapper.deleteById(id);
    }

    @Override
    public void inbound(Long id) {
        // 企业状态按 UNREGISTERED/OUTBOUND → ACTIVE 流转，支持出库复核后重新入库。
        Enterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!canInbound(enterprise.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有未入库或已出库企业可以入库");
        }
        enterprise.setStatus(STATUS_ACTIVE);
        enterpriseMapper.updateById(enterprise);
    }

    @Override
    public void outbound(Long id) {
        // 出库进入单独的 OUTBOUND 复核池，避免与新采集的未入库企业混淆。
        // 历史标签和挂链关系继续保留用于审计与展示。
        Enterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!STATUS_ACTIVE.equals(enterprise.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已入库企业可以出库");
        }
        enterprise.setStatus(STATUS_OUTBOUND);
        enterpriseMapper.updateById(enterprise);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInbound(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业ID列表不能为空");
        }
        int successCount = 0;
        // 批量入库处理 UNREGISTERED 与 OUTBOUND 状态的企业，其余状态跳过。
        for (Long id : ids) {
            Enterprise enterprise = enterpriseMapper.selectById(id);
            if (enterprise != null && canInbound(enterprise.getStatus())) {
                enterprise.setStatus(STATUS_ACTIVE);
                enterpriseMapper.updateById(enterprise);
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchOutbound(List<Long> ids) {
        if (ids == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "企业ID列表不能为空");
        }
        // 批量操作复用单条状态校验，并由外层事务保证任一失败时整批回滚。
        for (Long id : ids) {
            outbound(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTag(Long enterpriseId, Long tagId) {
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }

        // 重复绑定按幂等成功处理：已存在关系直接返回，避免重复插入触发唯一索引。
        LambdaQueryWrapper<EnterpriseTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseTagRel::getEnterpriseId, enterpriseId)
                .eq(EnterpriseTagRel::getTagId, tagId);
        if (enterpriseTagRelMapper.selectCount(wrapper) > 0) {
            return; // 已绑定，静默返回
        }

        EnterpriseTagRel rel = new EnterpriseTagRel();
        rel.setEnterpriseId(enterpriseId);
        rel.setTagId(tagId);
        enterpriseTagRelMapper.insert(rel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindTag(Long enterpriseId, Long tagId) {
        LambdaQueryWrapper<EnterpriseTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseTagRel::getEnterpriseId, enterpriseId)
                .eq(EnterpriseTagRel::getTagId, tagId);
        enterpriseTagRelMapper.delete(wrapper);
    }

    // ==================== 私有方法 ====================

    private boolean canInbound(String status) {
        return STATUS_UNREGISTERED.equals(status) || STATUS_OUTBOUND.equals(status);
    }

    /**
     * 企业实体转列表 VO。
     */
    private EnterpriseVO toVO(Enterprise e, List<TagVO> tags) {
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(e.getId());
        vo.setName(e.getEnterpriseName());
        vo.setCreditCode(e.getUnifiedCode());
        vo.setLegalPerson(e.getLegalPerson());
        vo.setIndustry(e.getIndustry());
        vo.setAddress(e.getAddress());
        vo.setBusinessScope(e.getBusinessScope());
        vo.setMainProducts(e.getMainProducts());
        vo.setOutputValue(e.getOutputValue());
        vo.setTaxAmount(e.getTaxAmount());
        vo.setStatus(e.getStatus());
        vo.setTags(tags);
        vo.setCreatedAt(e.getCreateTime());
        vo.setUpdatedAt(e.getUpdateTime());
        return vo;
    }

    /**
     * 批量查询多企业的标签，避免列表接口逐条查询形成 N+1。
     *
     * @param enterpriseIds 企业 ID 列表
     * @return 企业 ID 到标签列表的映射
     */
    private Map<Long, List<TagVO>> loadTagsByEnterpriseIds(List<Long> enterpriseIds) {
        if (enterpriseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EnterpriseTagRel> rels = enterpriseTagRelMapper.selectList(
                new LambdaQueryWrapper<EnterpriseTagRel>()
                        .in(EnterpriseTagRel::getEnterpriseId, enterpriseIds));
        if (rels.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> tagIds = rels.stream()
                .map(EnterpriseTagRel::getTagId)
                .collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));
        Map<Long, List<TagVO>> result = new HashMap<>();
        for (EnterpriseTagRel rel : rels) {
            Tag tag = tagMap.get(rel.getTagId());
            if (tag != null) {
                result.computeIfAbsent(rel.getEnterpriseId(), key -> new ArrayList<>())
                        .add(toTagVO(tag));
            }
        }
        return result;
    }

    private TagVO toTagVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getTagName());
        vo.setType(tag.getTagType());
        vo.setColor(tag.getColor());
        vo.setDescription(tag.getDescription());
        vo.setUsedCount(0);
        return vo;
    }

    /**
     * 企业实体转详情 VO（含标签和挂链节点）。
     */
    private EnterpriseDetailVO toDetailVO(Enterprise e) {
        EnterpriseDetailVO vo = new EnterpriseDetailVO();
        // 复制基础字段
        vo.setId(e.getId());
        vo.setName(e.getEnterpriseName());
        vo.setCreditCode(e.getUnifiedCode());
        vo.setLegalPerson(e.getLegalPerson());
        vo.setIndustry(e.getIndustry());
        vo.setAddress(e.getAddress());
        vo.setBusinessScope(e.getBusinessScope());
        vo.setMainProducts(e.getMainProducts());
        vo.setOutputValue(e.getOutputValue());
        vo.setTaxAmount(e.getTaxAmount());
        vo.setStatus(e.getStatus());
        vo.setCreatedAt(e.getCreateTime());
        vo.setUpdatedAt(e.getUpdateTime());

        // 详情 VO 在基础字段之外聚合标签和挂链节点，Entity 不直接暴露给接口层。
        vo.setTags(getTagsByEnterpriseId(e.getId()));
        vo.setLinkedNodes(getLinkedNodesByEnterpriseId(e.getId()));

        return vo;
    }

    /**
     * 查询企业关联的标签列表。
     */
    private List<TagVO> getTagsByEnterpriseId(Long enterpriseId) {
        LambdaQueryWrapper<EnterpriseTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseTagRel::getEnterpriseId, enterpriseId);
        List<EnterpriseTagRel> rels = enterpriseTagRelMapper.selectList(wrapper);
        if (rels.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tagIds = rels.stream().map(EnterpriseTagRel::getTagId).collect(Collectors.toList());
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);

        return tags.stream().map(tag -> {
            TagVO tagVO = new TagVO();
            tagVO.setId(tag.getId());
            tagVO.setName(tag.getTagName());
            tagVO.setType(tag.getTagType());
            tagVO.setColor(tag.getColor());
            tagVO.setDescription(tag.getDescription());
            tagVO.setUsedCount(0); // 详情中不需要精确usedCount
            return tagVO;
        }).collect(Collectors.toList());
    }

    /**
     * 查询企业挂链的节点列表。
     */
    private List<LinkedNodeVO> getLinkedNodesByEnterpriseId(Long enterpriseId) {
        LambdaQueryWrapper<EnterpriseNodeRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnterpriseNodeRel::getEnterpriseId, enterpriseId);
        List<EnterpriseNodeRel> rels = enterpriseNodeRelMapper.selectList(wrapper);
        if (rels.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> nodeIds = rels.stream().map(EnterpriseNodeRel::getNodeId).collect(Collectors.toSet());
        // 批量加载节点并建立 ID 索引，避免按每条关系逐个查询节点产生 N+1 请求。
        Map<Long, ChainNode> nodeMap = chainNodeMapper.selectBatchIds(nodeIds).stream()
                .collect(Collectors.toMap(ChainNode::getId, n -> n));

        return rels.stream().map(rel -> {
            LinkedNodeVO vo = new LinkedNodeVO();
            vo.setNodeId(rel.getNodeId());
            vo.setSource(rel.getSource());
            vo.setConfidence(rel.getConfidence());
            vo.setCreatedAt(rel.getCreateTime());

            ChainNode node = nodeMap.get(rel.getNodeId());
            if (node != null) {
                vo.setNodeName(node.getNodeName());
                // 使用 buildNodePath 生成中文展示路径，而不是数据库物化路径
                vo.setNodePath(chainNodeQueryService.buildNodePath(rel.getNodeId()));
                vo.setNodeMissing(false);
            } else {
                // 节点不存在时的兜底处理
                vo.setNodeName("节点已删除或不存在");
                vo.setNodePath("节点已删除或不存在（ID: " + rel.getNodeId() + "）");
                vo.setNodeMissing(true);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 将 CreateEnterpriseRequest 属性复制到 Enterprise 实体。
     */
    private void copyProperties(CreateEnterpriseRequest request, Enterprise e) {
        e.setEnterpriseName(request.getName());
        e.setUnifiedCode(request.getCreditCode());
        e.setLegalPerson(request.getLegalPerson());
        e.setRegion(request.getRegion());
        e.setIndustry(request.getIndustry());
        e.setAddress(request.getAddress());
        e.setRegisteredCapital(request.getRegisteredCapital());
        e.setEstablishDate(request.getEstablishDate());
        e.setBusinessScope(request.getBusinessScope());
        e.setMainProducts(request.getMainProducts());
        e.setCoreTech(request.getCoreTech());
        e.setPatentInfo(request.getPatentInfo());
        e.setBidInfo(request.getBidInfo());
        e.setProjectInfo(request.getProjectInfo());
        e.setContactPerson(request.getContactPerson());
        e.setContactPhone(request.getContactPhone());
        e.setKeywords(request.getKeywords());
        e.setOutputValue(request.getOutputValue());
        e.setTaxAmount(request.getTaxAmount());
    }

    /**
     * 将 UpdateEnterpriseRequest 属性复制到 Enterprise 实体（非 null 字段）。
     */
    private void copyProperties(UpdateEnterpriseRequest request, Enterprise e) {
        if (request.getName() != null) e.setEnterpriseName(request.getName());
        if (request.getCreditCode() != null) e.setUnifiedCode(request.getCreditCode());
        if (request.getLegalPerson() != null) e.setLegalPerson(request.getLegalPerson());
        if (request.getRegion() != null) e.setRegion(request.getRegion());
        if (request.getIndustry() != null) e.setIndustry(request.getIndustry());
        if (request.getAddress() != null) e.setAddress(request.getAddress());
        if (request.getRegisteredCapital() != null) e.setRegisteredCapital(request.getRegisteredCapital());
        if (request.getEstablishDate() != null) e.setEstablishDate(request.getEstablishDate());
        if (request.getBusinessScope() != null) e.setBusinessScope(request.getBusinessScope());
        if (request.getMainProducts() != null) e.setMainProducts(request.getMainProducts());
        if (request.getCoreTech() != null) e.setCoreTech(request.getCoreTech());
        if (request.getPatentInfo() != null) e.setPatentInfo(request.getPatentInfo());
        if (request.getBidInfo() != null) e.setBidInfo(request.getBidInfo());
        if (request.getProjectInfo() != null) e.setProjectInfo(request.getProjectInfo());
        if (request.getContactPerson() != null) e.setContactPerson(request.getContactPerson());
        if (request.getContactPhone() != null) e.setContactPhone(request.getContactPhone());
        if (request.getKeywords() != null) e.setKeywords(request.getKeywords());
        if (request.getOutputValue() != null) e.setOutputValue(request.getOutputValue());
        if (request.getTaxAmount() != null) e.setTaxAmount(request.getTaxAmount());
    }
}
