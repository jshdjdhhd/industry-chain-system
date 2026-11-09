START TRANSACTION;

-- 每次都先保证一级节点路径规范。
UPDATE chain_node
SET node_path = CONCAT('/', id, '/'),
    node_level = 1,
    update_time = CURRENT_TIMESTAMP
WHERE is_deleted = 0
  AND parent_id IS NULL
  AND (
      node_path IS NULL
      OR node_path <> CONCAT('/', id, '/')
      OR node_level <> 1
  );

-- 单次向下修复一层；重复执行即可覆盖任意深度，不使用递归 CTE 或存储过程。
UPDATE chain_node child
JOIN chain_node parent
  ON parent.id = child.parent_id
 AND parent.is_deleted = 0
SET child.node_path = CONCAT(parent.node_path, child.id, '/'),
    child.node_level = parent.node_level + 1,
    child.update_time = CURRENT_TIMESTAMP
WHERE child.is_deleted = 0
  AND parent.chain_id = child.chain_id
  AND parent.node_path <> '/'
  AND (
      child.node_path IS NULL
      OR child.node_path <> CONCAT(parent.node_path, child.id, '/')
      OR child.node_level <> parent.node_level + 1
  );

SELECT ROW_COUNT() AS repaired_descendants;

COMMIT;
