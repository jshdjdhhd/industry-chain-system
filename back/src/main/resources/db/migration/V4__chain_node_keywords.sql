-- =====================================================================
-- 产业链节点关键词扩展
-- 版本: V4
-- 数据库: MySQL 5.7 | 引擎: InnoDB | 字符集: utf8mb4
-- 说明: 为智能挂链提供专门的节点关键词字段，避免仅依赖 node_name。
-- =====================================================================

ALTER TABLE `chain_node`
    ADD COLUMN `keywords` VARCHAR(500) NULL COMMENT '节点关键词，多个关键词用逗号分隔' AFTER `node_name`;

UPDATE `chain_node`
SET `keywords` = CASE `id`
    WHEN 101 THEN '氢气制取,电解水制氢,工业副产氢,绿氢,电解槽'
    WHEN 102 THEN '氢气储运,高压储氢,液氢,氢气运输,加氢站'
    WHEN 201 THEN '燃料电池,燃料电池电堆,氢燃料电池,质子交换膜,核心零部件'
    WHEN 202 THEN '动力电池,锂离子电池,电芯,模组,磷酸铁锂,三元锂'
    WHEN 203 THEN '电驱系统,驱动电机,电控,新能源动力总成'
    WHEN 301 THEN '新能源整车,新能源汽车,纯电动,插电混动,整车制造'
    WHEN 302 THEN '充换电设施,充电桩,充电站,换电站,智能充电'
    ELSE `node_name`
END
WHERE `keywords` IS NULL;
