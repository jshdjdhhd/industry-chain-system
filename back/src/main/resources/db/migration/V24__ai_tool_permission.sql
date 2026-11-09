CREATE TABLE IF NOT EXISTS ai_tool_permission (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    tool_name       VARCHAR(100) NOT NULL COMMENT '工具方法名，如 deleteEnterprise',
    enabled         TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=允许AI调用 0=禁止',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_tool (user_id, tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI助手工具权限覆盖层';
