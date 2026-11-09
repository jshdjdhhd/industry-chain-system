ALTER TABLE `matching_task`
    ADD COLUMN `retry_from_task_id` BIGINT UNSIGNED NULL COMMENT '重试来源任务ID' AFTER `status`,
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数' AFTER `retry_from_task_id`,
    ADD COLUMN `cancelled_by` VARCHAR(64) NULL COMMENT '取消人ID' AFTER `retry_count`,
    ADD COLUMN `cancelled_at` DATETIME NULL COMMENT '取消时间' AFTER `cancelled_by`,
    ADD COLUMN `error_message` VARCHAR(500) NULL COMMENT '任务失败/超时信息' AFTER `cancelled_at`;

CREATE INDEX `idx_matching_task_retry_from_task_id` ON `matching_task` (`retry_from_task_id`);
