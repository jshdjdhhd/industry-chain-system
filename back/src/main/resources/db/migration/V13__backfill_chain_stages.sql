-- V13: 回填已有产业链阶段节点
-- 1. 将唯一且名称精确匹配的一级普通节点升级为阶段节点。
UPDATE `chain_node` candidate
JOIN (
    SELECT promoted.candidate_id,
           promoted.chain_id,
           promoted.stage_type,
           promoted.sort_order
    FROM (
        SELECT MIN(n.id) candidate_id,
               n.chain_id,
               CASE n.node_name
                   WHEN '上游' THEN 'UPSTREAM'
                   WHEN '中游' THEN 'MIDSTREAM'
                   WHEN '下游' THEN 'DOWNSTREAM'
               END stage_type,
               CASE n.node_name
                   WHEN '上游' THEN 1
                   WHEN '中游' THEN 2
                   WHEN '下游' THEN 3
               END sort_order
        FROM `chain_node` n
        WHERE n.is_deleted = 0
          AND n.parent_id IS NULL
          AND n.stage_type IS NULL
          AND n.node_name IN ('上游', '中游', '下游')
        GROUP BY n.chain_id, n.node_name
        HAVING COUNT(*) = 1
    ) promoted
) unique_stage
  ON unique_stage.candidate_id = candidate.id
LEFT JOIN `chain_node` existing
  ON existing.chain_id = unique_stage.chain_id
 AND existing.stage_type = unique_stage.stage_type
 AND existing.is_deleted = 0
SET candidate.stage_type = unique_stage.stage_type,
    candidate.parent_id = NULL,
    candidate.node_level = 1,
    candidate.node_path = CONCAT('/', candidate.id, '/'),
    candidate.sort_order = unique_stage.sort_order,
    candidate.update_time = CURRENT_TIMESTAMP
WHERE existing.id IS NULL;

-- 2. 为所有有效产业链补齐缺失阶段。
INSERT INTO `chain_node`
    (`chain_id`, `parent_id`, `node_name`, `keywords`, `node_level`,
     `description`, `node_path`, `sort_order`, `stage_type`)
SELECT c.id, NULL, stage.node_name, stage.keywords, 1,
       stage.description, '/', stage.sort_order, stage.stage_type
FROM `chain` c
JOIN (
    SELECT '上游' node_name, '上游,供应端,原材料' keywords,
           '产业链上游阶段' description, 1 sort_order,
           'UPSTREAM' stage_type
    UNION ALL
    SELECT '中游', '中游,制造端,核心环节',
           '产业链中游阶段', 2, 'MIDSTREAM'
    UNION ALL
    SELECT '下游', '下游,应用端,市场端',
           '产业链下游阶段', 3, 'DOWNSTREAM'
) stage
LEFT JOIN `chain_node` existing
  ON existing.chain_id = c.id
 AND existing.stage_type = stage.stage_type
 AND existing.is_deleted = 0
WHERE c.is_deleted = 0
  AND existing.id IS NULL;

-- 3. 规范有效阶段节点。
UPDATE `chain_node`
SET parent_id = NULL,
    node_level = 1,
    node_path = CONCAT('/', id, '/'),
    sort_order = CASE stage_type
        WHEN 'UPSTREAM' THEN 1
        WHEN 'MIDSTREAM' THEN 2
        WHEN 'DOWNSTREAM' THEN 3
    END,
    update_time = CURRENT_TIMESTAMP
WHERE stage_type IS NOT NULL
  AND is_deleted = 0;

-- 4. 只迁移项目验收数据中能够确定归属且名称唯一的演示节点。
UPDATE `chain_node` descendant
JOIN `chain_node` child
  ON descendant.chain_id = child.chain_id
 AND descendant.node_path LIKE CONCAT(child.node_path, '%')
JOIN (
    SELECT candidates.candidate_id,
           candidates.chain_id,
           candidates.node_name
    FROM (
        SELECT MIN(n.id) candidate_id,
               n.chain_id,
               n.node_name
        FROM `chain_node` n
        WHERE n.is_deleted = 0
          AND n.parent_id IS NULL
          AND n.stage_type IS NULL
        GROUP BY n.chain_id, n.node_name
        HAVING COUNT(*) = 1
    ) candidates
) unique_mapped_child
  ON unique_mapped_child.candidate_id = child.id
JOIN (
    SELECT 1 chain_id, '氢气制取' node_name, 'UPSTREAM' stage_type
    UNION ALL SELECT 1, '氢气储运', 'UPSTREAM'
    UNION ALL SELECT 1, '燃料电池', 'MIDSTREAM'
    UNION ALL SELECT 1, '动力电池', 'MIDSTREAM'
    UNION ALL SELECT 1, '电驱系统', 'MIDSTREAM'
    UNION ALL SELECT 1, '新能源整车', 'DOWNSTREAM'
    UNION ALL SELECT 1, '整车制造', 'DOWNSTREAM'
    UNION ALL SELECT 1, '充换电设施', 'DOWNSTREAM'
    UNION ALL SELECT 2, '芯片设计', 'UPSTREAM'
    UNION ALL SELECT 2, '半导体设备', 'UPSTREAM'
    UNION ALL SELECT 2, '半导体材料', 'UPSTREAM'
    UNION ALL SELECT 2, '晶圆代工', 'MIDSTREAM'
    UNION ALL SELECT 2, '晶圆制造', 'MIDSTREAM'
    UNION ALL SELECT 2, '封装测试', 'MIDSTREAM'
    UNION ALL SELECT 2, '功率半导体', 'MIDSTREAM'
) mapping
  ON mapping.chain_id = unique_mapped_child.chain_id
 AND mapping.node_name = unique_mapped_child.node_name
JOIN `chain_node` stage
  ON stage.chain_id = child.chain_id
 AND stage.stage_type = mapping.stage_type
 AND stage.is_deleted = 0
SET descendant.node_path = CONCAT(
        stage.node_path,
        SUBSTRING(descendant.node_path, 2)
    ),
    descendant.node_level = descendant.node_level + 1,
    descendant.parent_id = CASE
        WHEN descendant.id = child.id THEN stage.id
        ELSE descendant.parent_id
    END,
    descendant.update_time = CURRENT_TIMESTAMP
WHERE child.parent_id IS NULL
  AND child.stage_type IS NULL
  AND child.is_deleted = 0
  AND descendant.is_deleted = 0;
