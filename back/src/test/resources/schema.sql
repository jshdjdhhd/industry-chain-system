-- 测试期 H2 内存库建表脚本（MySQL 兼容模式）。
-- 覆盖鉴权表与 operation_log（按《04-数据库设计.md》字段），供集成测试使用。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    status INT NOT NULL DEFAULT 1,
    deleted INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    deleted INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT,
    detail CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- 智能挂链依赖的企业与产业链基础表
-- =====================================================================
CREATE TABLE IF NOT EXISTS enterprise (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_name VARCHAR(200) NOT NULL,
    unified_code VARCHAR(32),
    legal_person VARCHAR(64),
    region VARCHAR(64),
    industry VARCHAR(64),
    address VARCHAR(255),
    registered_capital VARCHAR(64),
    establish_date DATE,
    business_scope CLOB,
    main_products CLOB,
    core_tech CLOB,
    patent_info CLOB,
    bid_info CLOB,
    project_info CLOB,
    contact_person VARCHAR(64),
    contact_phone VARCHAR(32),
    keywords VARCHAR(500),
    output_value DECIMAL(18,2),
    tax_amount DECIMAL(18,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_enterprise_status_deleted_create_time
    ON enterprise(status, is_deleted, create_time, id);
CREATE INDEX IF NOT EXISTS idx_enterprise_status_deleted_name
    ON enterprise(status, is_deleted, enterprise_name, id);
CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(64) NOT NULL,
    tag_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
    color VARCHAR(16),
    description VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS enterprise_tag_rel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS chain (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_name VARCHAR(128) NOT NULL,
    description CLOB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS chain_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    parent_id BIGINT,
    node_name VARCHAR(128) NOT NULL,
    keywords VARCHAR(500),
    node_level INT NOT NULL DEFAULT 1,
    description CLOB,
    node_path VARCHAR(255) NOT NULL DEFAULT '/',
    sort_order INT NOT NULL DEFAULT 0,
    stage_type VARCHAR(16),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chain_node_stage
    ON chain_node(chain_id, stage_type, is_deleted);

-- =====================================================================
-- 业务核心表（智能挂链/正式挂链）
-- =====================================================================
CREATE TABLE IF NOT EXISTS matching_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    input_snapshot CLOB NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS matching_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    evidence VARCHAR(1000),
    rank_no INT NOT NULL DEFAULT 0,
    decision VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(500),
    decided_by VARCHAR(64),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ai_embedding_vector (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    object_type VARCHAR(32) NOT NULL,
    object_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    dimension INT NOT NULL,
    text_hash CHAR(64) NOT NULL,
    vector_data CLOB NOT NULL,
    source_text_snapshot CLOB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS matching_recommendation_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    enterprise_text_hash CHAR(64) NOT NULL,
    chain_nodes_hash CHAR(64) NOT NULL,
    top_k INT NOT NULL,
    vector_weight DECIMAL(6,4) NOT NULL,
    rule_weight DECIMAL(6,4) NOT NULL,
    result_json CLOB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_matching_recommendation_fingerprint
    ON matching_recommendation_cache (
        enterprise_id, chain_id, model_name, enterprise_text_hash, chain_nodes_hash,
        top_k, vector_weight, rule_weight, status, is_deleted
    );
CREATE TABLE IF NOT EXISTS industry_term_alias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_term VARCHAR(100) NOT NULL,
    expanded_terms CLOB NOT NULL,
    industry VARCHAR(100),
    priority INT NOT NULL DEFAULT 0,
    enabled INT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS matching_evaluation_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sample_name VARCHAR(200) NOT NULL,
    enterprise_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    expected_node_id BIGINT NOT NULL,
    reason CLOB,
    enabled INT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS enterprise_node_rel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    confidence DECIMAL(5,4),
    reason CLOB,
    confirmed_by BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_rel_node_deleted_enterprise
    ON enterprise_node_rel(node_id, is_deleted, enterprise_id);
CREATE INDEX IF NOT EXISTS idx_rel_enterprise_deleted_node
    ON enterprise_node_rel(enterprise_id, is_deleted, node_id);


CREATE TABLE IF NOT EXISTS enterprise_similarity_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_enterprise_id BIGINT NOT NULL,
    target_enterprise_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    source_text_hash CHAR(64) NOT NULL,
    target_text_hash CHAR(64) NOT NULL,
    similarity DECIMAL(6,5) NOT NULL,
    reasons VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_enterprise_similarity_pair_model_hash
    ON enterprise_similarity_cache(source_enterprise_id, target_enterprise_id, model_name, source_text_hash, target_text_hash);
