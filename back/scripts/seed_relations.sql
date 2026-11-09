-- 标签
INSERT INTO tag (tag_name, tag_type, color, description) VALUES
('高新技术企业', 'SYSTEM', '#1f6fff', '国家高新技术企业认定'),
('专精特新', 'SYSTEM', '#18a66a', '专精特新小巨人企业'),
('上市公司', 'SYSTEM', '#ff9810', 'A股/港股/美股上市公司'),
('独角兽', 'CUSTOM', '#7b61ff', '估值超10亿美元的未上市企业'),
('国产替代', 'SYSTEM', '#ef4444', '关键领域国产替代企业'),
('新能源', 'CUSTOM', '#00A896', '新能源产业链相关企业'),
('半导体', 'CUSTOM', '#2F6BFF', '半导体产业链相关企业'),
('人工智能', 'CUSTOM', '#F59E0B', 'AI及大模型相关企业');

-- 企业标签关联 (enterprise_id, tag_id)
-- tag: 1=高新技术 2=专精特新 3=上市公司 4=独角兽 5=国产替代 6=新能源 7=半导体 8=人工智能
INSERT INTO enterprise_tag_rel (enterprise_id, tag_id) VALUES
-- 氢能源链 1-5
(1,1),(1,3),(1,6), (2,1),(2,3),(2,6), (3,1),(3,3),(3,7),
(4,1),(4,3),(4,6), (5,1),(5,6),
-- 光通信 10-13
(10,1),(10,3),(10,7), (11,1),(11,3),(11,7), (12,1),(12,3),(12,7), (13,1),(13,3),
-- 消费电子 14-19
(14,1),(14,3),(14,4),(14,8), (15,1),(15,4), (16,1),(16,3),(16,5),(16,8),
(17,1),(17,3), (18,1),(18,3), (19,1),(19,3),
-- 新能源汽车 20-25
(20,1),(20,3),(20,6), (21,1),(21,3),(21,6), (22,3),(22,6),(22,4),
(23,1),(23,6),(23,4), (24,1),(24,6),(24,4), (25,3),(25,6),
-- 半导体 26-37
(26,1),(26,3),(26,7),(26,5), (27,1),(27,3),(27,7),(27,5), (28,1),(28,8),(28,5),
(29,1),(29,3),(29,7), (30,1),(30,3),(30,7), (31,1),(31,3),(31,7),
(32,1),(32,3),(32,7), (33,1),(33,3),(33,7), (34,1),(34,3),(34,7),
(35,1),(35,7), (36,1),(36,7), (37,1),(37,3),(37,7),
-- 光伏储能 38-41
(38,1),(38,3),(38,6), (39,1),(39,3),(39,6), (40,1),(40,3),(40,6), (41,1),(41,3),(41,6),
-- 互联网 42-45
(42,3),(42,8), (43,3),(43,8), (44,3),(44,8), (45,4),(45,8);

-- 氢能源链企业-节点关联 (已有的不动)
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by) VALUES
(10, 301, 'MANUAL', 1.0, '比亚迪整车制造', 1),
(11, 301, 'MANUAL', 1.0, '上汽整车制造', 1),
(12, 301, 'MANUAL', 1.0, '蔚来整车制造', 1),
(13, 301, 'MANUAL', 1.0, '小鹏整车制造', 1),
(14, 301, 'MANUAL', 1.0, '理想整车制造', 1),
(15, 301, 'MANUAL', 1.0, '特斯拉整车制造', 1);

-- 半导体链企业-节点关联
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 26, id, 'MANUAL', 1.0, '中芯国际晶圆代工', 1 FROM chain_node WHERE chain_id=2 AND node_name='晶圆制造' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 27, id, 'MANUAL', 1.0, '海光信息CPU/DCU芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 28, id, 'MANUAL', 1.0, '寒武纪AI芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 29, id, 'MANUAL', 1.0, '中微半导体刻蚀设备', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体设备' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 30, id, 'MANUAL', 1.0, '北方华创半导体设备', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体设备' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 31, id, 'MANUAL', 1.0, '韦尔半导体图像传感器设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 32, id, 'MANUAL', 1.0, '兆易创新存储/MCU芯片设计', 1 FROM chain_node WHERE chain_id=2 AND node_name='芯片设计' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 33, id, 'MANUAL', 1.0, '长电科技先进封装', 1 FROM chain_node WHERE chain_id=2 AND node_name='封装测试' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 34, id, 'MANUAL', 1.0, '通富微电封装测试', 1 FROM chain_node WHERE chain_id=2 AND node_name='封装测试' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 35, id, 'MANUAL', 1.0, '沪硅产业半导体硅片材料', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体材料' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 36, id, 'MANUAL', 1.0, '雅克科技光刻胶/前驱体材料', 1 FROM chain_node WHERE chain_id=2 AND node_name='半导体材料' AND is_deleted=0 LIMIT 1;
INSERT INTO enterprise_node_rel (enterprise_id, node_id, source, confidence, reason, confirmed_by)
SELECT 37, id, 'MANUAL', 1.0, '闻泰科技功率半导体IDM', 1 FROM chain_node WHERE chain_id=2 AND node_name='功率半导体' AND is_deleted=0 LIMIT 1;
