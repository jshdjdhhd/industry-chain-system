-- 查找 parent_id、node_path、node_level 不一致的未删除节点。
SELECT
    child.id,
    child.chain_id,
    child.parent_id,
    child.node_level,
    child.node_path,
    parent.node_level AS parent_level,
    parent.node_path AS parent_path
FROM chain_node child
LEFT JOIN chain_node parent
    ON parent.id = child.parent_id
   AND parent.is_deleted = 0
WHERE child.is_deleted = 0
  AND (
      child.node_path IS NULL
      OR child.node_path = '/'
      OR (
          child.parent_id IS NULL
          AND (
              child.node_level <> 1
              OR child.node_path <> CONCAT('/', child.id, '/')
          )
      )
      OR (
          child.parent_id IS NOT NULL
          AND (
              parent.id IS NULL
              OR parent.chain_id <> child.chain_id
              OR child.node_level <> parent.node_level + 1
              OR child.node_path <> CONCAT(parent.node_path, child.id, '/')
          )
      )
  )
ORDER BY child.chain_id, child.node_level, child.id;

-- 同一产业链内的物化路径必须唯一。
SELECT
    chain_id,
    node_path,
    COUNT(*) AS duplicate_count
FROM chain_node
WHERE is_deleted = 0
GROUP BY chain_id, node_path
HAVING COUNT(*) > 1
ORDER BY chain_id, node_path;
