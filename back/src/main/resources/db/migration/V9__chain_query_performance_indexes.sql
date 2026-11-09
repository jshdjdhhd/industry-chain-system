-- 版本: V9
-- 产业链完整树查询、子树路径查询和有效关系聚合索引
CREATE INDEX `idx_chain_node_chain_deleted_path`
    ON `chain_node` (`chain_id`, `is_deleted`, `node_path`);

CREATE INDEX `idx_chain_node_chain_deleted_sort`
    ON `chain_node` (`chain_id`, `is_deleted`, `sort_order`, `id`);

CREATE INDEX `idx_enterprise_node_rel_node_deleted`
    ON `enterprise_node_rel` (`node_id`, `is_deleted`);
