-- =====================================================================
-- 产业链上中下游阶段层级
-- 版本: V12
-- 数据库: MySQL 5.7
-- =====================================================================

ALTER TABLE `chain_node`
    ADD COLUMN `stage_type` VARCHAR(16) NULL
        COMMENT '产业链阶段：UPSTREAM/MIDSTREAM/DOWNSTREAM，NULL 为普通节点'
        AFTER `sort_order`,
    ADD UNIQUE KEY `uk_chain_node_stage`
        (`chain_id`, `stage_type`, `is_deleted`);

-- 只初始化规格中已确认的演示产业链，不推断未知历史数据。
INSERT INTO `chain_node`
    (`chain_id`, `parent_id`, `node_name`, `keywords`, `node_level`,
     `description`, `node_path`, `sort_order`, `stage_type`)
SELECT c.id, NULL, s.node_name, s.keywords, 1,
       s.description, '/', s.sort_order, s.stage_type
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
) s
WHERE c.id IN (1, 2)
  AND c.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM `chain_node` n
      WHERE n.chain_id = c.id
        AND n.stage_type = s.stage_type
        AND n.is_deleted = 0
  );

UPDATE `chain_node`
SET node_path = CONCAT('/', id, '/'),
    node_level = 1,
    parent_id = NULL
WHERE stage_type IS NOT NULL
  AND is_deleted = 0;

-- 新能源汽车演示节点的归属来自既有验收规格。
UPDATE `chain_node` child
JOIN `chain_node` stage
  ON stage.chain_id = child.chain_id
 AND stage.stage_type = CASE
     WHEN child.id IN (101, 102) THEN 'UPSTREAM'
     WHEN child.id IN (201, 202, 203) THEN 'MIDSTREAM'
     WHEN child.id IN (301, 302) THEN 'DOWNSTREAM'
 END
 AND stage.is_deleted = 0
SET child.parent_id = stage.id,
    child.node_level = 2,
    child.node_path = CONCAT(stage.node_path, child.id, '/'),
    child.update_time = CURRENT_TIMESTAMP
WHERE child.chain_id = 1
  AND child.id IN (101, 102, 201, 202, 203, 301, 302)
  AND child.is_deleted = 0;