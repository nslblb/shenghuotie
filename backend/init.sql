-- ============================================
-- 生活帖 社交平台 - 数据库初始化脚本
-- 与 JPA 实体定义保持一致
-- 请在 MySQL 中执行以下 SQL
-- ============================================

CREATE DATABASE IF NOT EXISTS shenghuotiesql
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE shenghuotiesql;

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE `user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `phone`           VARCHAR(11)  NOT NULL COMMENT '手机号',
    `password`        VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname`        VARCHAR(50)  NOT NULL COMMENT '昵称',
    `avatar_url`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `bio`             VARCHAR(200) DEFAULT NULL COMMENT '个人简介',
    `role`            VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED/DELETED',
    `message_privacy` VARCHAR(20)  NOT NULL DEFAULT 'EVERYONE' COMMENT '私信权限: EVERYONE/FOLLOWED/NONE',
    `ban_reason`      VARCHAR(500) DEFAULT NULL COMMENT '封禁原因',
    `banned_at`       DATETIME     DEFAULT NULL COMMENT '封禁时间',
    `banned_by`       BIGINT       DEFAULT NULL COMMENT '封禁操作人ID',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 帖子表
-- ============================================
CREATE TABLE `post` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT        NOT NULL COMMENT '作者ID',
    `title`          VARCHAR(100)  NOT NULL COMMENT '标题',
    `content`        TEXT          NOT NULL COMMENT '正文内容',
    `cover_image`    VARCHAR(500)  DEFAULT NULL COMMENT '封面图URL',
    `topic`          VARCHAR(50)   DEFAULT NULL COMMENT '话题标签',
    `like_count`     INT           NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count`  INT           NOT NULL DEFAULT 0 COMMENT '评论数',
    `favorite_count` INT           NOT NULL DEFAULT 0 COMMENT '收藏数',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态: PUBLISHED/DRAFT',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_topic` (`topic`),
    KEY `idx_created_at` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子表';

-- ============================================
-- 帖子图片表
-- ============================================
CREATE TABLE `post_image` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT       NOT NULL COMMENT '所属帖子ID',
    `image_url`  VARCHAR(500) NOT NULL COMMENT '图片URL',
    `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子图片表';

-- ============================================
-- 文件记录表
-- ============================================
CREATE TABLE `file_record` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL COMMENT '上传者ID',
    `file_name`  VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_size`  BIGINT       NOT NULL COMMENT '文件大小(字节)',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_name` (`file_name`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件记录表';

-- ============================================
-- 评论表（支持楼中楼）
-- ============================================
CREATE TABLE `comment` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `post_id`           BIGINT       NOT NULL COMMENT '所属帖子ID',
    `user_id`           BIGINT       NOT NULL COMMENT '评论者ID',
    `parent_id`         BIGINT       DEFAULT NULL COMMENT '父评论ID(NULL=顶级评论)',
    `reply_to_user_id`  BIGINT       DEFAULT NULL COMMENT '回复目标用户ID',
    `content`           VARCHAR(500) NOT NULL COMMENT '评论内容',
    `has_sensitive_word` TINYINT(1)  DEFAULT 0 COMMENT '是否包含敏感词',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ============================================
-- 点赞记录表
-- ============================================
CREATE TABLE `like_record` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '点赞用户ID',
    `post_id`    BIGINT   NOT NULL COMMENT '被点赞帖ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- ============================================
-- 收藏记录表
-- ============================================
CREATE TABLE `favorite_record` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '收藏用户ID',
    `post_id`    BIGINT   NOT NULL COMMENT '被收藏帖ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏记录表';

-- ============================================
-- 关注关系表
-- ============================================
CREATE TABLE `follow` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT,
    `follower_id`  BIGINT   NOT NULL COMMENT '关注者ID',
    `following_id` BIGINT   NOT NULL COMMENT '被关注者ID',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
    KEY `idx_following_id` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注关系表';

-- ============================================
-- 私信表
-- ============================================
CREATE TABLE `message` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `sender_id`          BIGINT       NOT NULL COMMENT '发送者ID',
    `receiver_id`        BIGINT       NOT NULL COMMENT '接收者ID',
    `content`            VARCHAR(1000) NOT NULL COMMENT '消息内容',
    `message_type`       VARCHAR(255) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT/IMAGE',
    `is_read`            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读',
    `is_recalled`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已撤回',
    `deleted_by_sender`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '发送者是否删除',
    `deleted_by_receiver` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '接收者是否删除',
    `created_at`         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_message_sender` (`sender_id`),
    KEY `idx_message_receiver` (`receiver_id`),
    KEY `idx_message_pair_time` (`sender_id`, `receiver_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信表';

-- ============================================
-- 消息通知表
-- ============================================
CREATE TABLE `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `receiver_id` BIGINT       NOT NULL COMMENT '接收者ID',
    `sender_id`   BIGINT       NOT NULL COMMENT '触发者ID',
    `type`        VARCHAR(30)  NOT NULL COMMENT '通知类型: LIKE/COMMENT/FOLLOW/REPLY/SYSTEM/REPORT/APPEAL',
    `post_id`     BIGINT       DEFAULT NULL COMMENT '相关帖子ID',
    `content`     VARCHAR(200) DEFAULT NULL COMMENT '通知摘要',
    `is_read`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_receiver_id` (`receiver_id`),
    KEY `idx_is_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- ============================================
-- 举报表
-- ============================================
CREATE TABLE `report` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `reporter_id`   BIGINT       NOT NULL COMMENT '举报人ID',
    `target_id`     BIGINT       NOT NULL COMMENT '被举报目标ID',
    `target_type`   VARCHAR(20)  NOT NULL COMMENT '目标类型: POST/COMMENT/USER',
    `reason`        VARCHAR(200) NOT NULL COMMENT '举报原因',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RESOLVED/DISMISSED',
    `handler_id`    BIGINT       DEFAULT NULL COMMENT '处理人ID',
    `handle_result` VARCHAR(200) DEFAULT NULL COMMENT '处理结果',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `handled_at`    DATETIME     DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报表';

-- ============================================
-- 敏感词表
-- ============================================
CREATE TABLE `sensitive_word` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `word`       VARCHAR(50)  NOT NULL COMMENT '敏感词',
    `category`   VARCHAR(20)  DEFAULT NULL COMMENT '分类',
    `is_enabled` TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词表';

-- ============================================
-- 自动处理规则表
-- ============================================
CREATE TABLE `auto_handle_rule` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `target_type`       VARCHAR(20) NOT NULL COMMENT '目标类型',
    `report_threshold`  INT         NOT NULL COMMENT '触发阈值',
    `action`            VARCHAR(20) NOT NULL COMMENT '处理动作',
    `time_window_hours` INT         DEFAULT NULL COMMENT '统计时间窗口(小时)',
    `enabled`           TINYINT(1)  DEFAULT 1 COMMENT '是否启用',
    `created_at`        DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动处理规则表';

-- ============================================
-- 申诉表
-- ============================================
CREATE TABLE `appeal` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `post_id`     BIGINT       NOT NULL COMMENT '申诉帖子ID',
    `user_id`     BIGINT       NOT NULL COMMENT '申诉人ID',
    `reason`      TEXT         NOT NULL COMMENT '申诉理由',
    `ban_reason`  VARCHAR(500) DEFAULT NULL COMMENT '封禁原因',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `handler_id`  BIGINT       DEFAULT NULL COMMENT '处理人ID',
    `handle_note` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
    `handled_at`  DATETIME     DEFAULT NULL COMMENT '处理时间',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申诉表';

-- ============================================
-- 管理员登录日志表
-- ============================================
CREATE TABLE `admin_login_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '管理员用户ID',
    `username`    VARCHAR(50)  DEFAULT NULL COMMENT '用户名',
    `ip`          VARCHAR(50)  NOT NULL COMMENT '登录IP',
    `location`    VARCHAR(200) DEFAULT NULL COMMENT '地理位置',
    `user_agent`  VARCHAR(500) DEFAULT NULL COMMENT 'UA头',
    `browser`     VARCHAR(50)  DEFAULT NULL COMMENT '浏览器',
    `os`          VARCHAR(50)  DEFAULT NULL COMMENT '操作系统',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAIL',
    `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员登录日志表';

-- ============================================
-- 系统配置表
-- ============================================
CREATE TABLE `system_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `config_key`   VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT         DEFAULT NULL COMMENT '配置值',
    `config_type`  VARCHAR(20)  DEFAULT 'STRING' COMMENT '配置类型',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置说明',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by`   BIGINT       DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================
-- 话题表
-- ============================================
CREATE TABLE `topic` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50) NOT NULL COMMENT '话题名称',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '话题描述',
    `icon`        VARCHAR(500) DEFAULT NULL COMMENT '话题图标',
    `enabled`     TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sort_order`  INT         DEFAULT 0 COMMENT '排序值',
    `post_count`  INT         DEFAULT 0 COMMENT '帖子数量',
    `created_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题表';

-- ============================================
-- 操作日志表
-- ============================================
CREATE TABLE `operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL COMMENT '操作用户ID',
    `username`   VARCHAR(50)  DEFAULT NULL COMMENT '用户名',
    `operation`  VARCHAR(30)  NOT NULL COMMENT '操作类型',
    `method`     VARCHAR(100) NOT NULL COMMENT '请求方法',
    `params`     VARCHAR(200) DEFAULT NULL COMMENT '请求参数',
    `url`        VARCHAR(200) NOT NULL COMMENT '请求URL',
    `ip`         VARCHAR(50)  NOT NULL COMMENT '请求IP',
    `duration`   BIGINT       NOT NULL COMMENT '耗时(毫秒)',
    `status`     VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAIL',
    `error_msg`  VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `trace_id`   VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪ID',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ============================================
-- 初始化数据
-- ============================================

-- 话题数据
INSERT INTO `topic` (`name`, `description`, `enabled`, `sort_order`, `created_at`, `updated_at`) VALUES
    ('美食',  NULL, 1, 0, NOW(), NOW()),
    ('旅行',  NULL, 1, 1, NOW(), NOW()),
    ('穿搭',  NULL, 1, 2, NOW(), NOW()),
    ('健身',  NULL, 1, 3, NOW(), NOW()),
    ('学习',  NULL, 1, 4, NOW(), NOW()),
    ('生活',  NULL, 1, 5, NOW(), NOW()),
    ('科技',  NULL, 1, 6, NOW(), NOW());


-- ============================================
-- AI 对话表（每用户唯一）
-- ============================================
CREATE TABLE `ai_conversation` (
                                   `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
                                   `user_id`                 BIGINT       NOT NULL COMMENT '用户ID',
                                   `title`                   VARCHAR(100) NOT NULL DEFAULT '我的助手' COMMENT '对话标题',
                                   `summary`                 TEXT         DEFAULT NULL COMMENT '摘要压缩结果',
                                   `last_summarized_message_id` BIGINT    DEFAULT NULL COMMENT '最后已摘要消息ID（游标）',
                                   `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话';

-- ============================================
-- AI 消息表（可逐条删/复制）
-- ============================================
CREATE TABLE `ai_message` (
                              `id`              BIGINT       NOT NULL AUTO_INCREMENT,
                              `conversation_id` BIGINT       NOT NULL COMMENT '对话ID',
                              `role`            VARCHAR(20)  NOT NULL COMMENT '角色: USER/ASSISTANT',
                              `content`         TEXT         NOT NULL COMMENT '消息内容',
                              `sources`         JSON         DEFAULT NULL COMMENT '引用来源',
                              `from_knowledge`  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=知识库命中,0=AI兜底',
                              `status`          VARCHAR(20)  NOT NULL DEFAULT 'DONE' COMMENT '状态: STREAMING/DONE/FAILED',
                              `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              KEY `idx_conv_time` (`conversation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI消息';

-- ============================================
-- AI 知识库表（人工维护）
-- ============================================
CREATE TABLE `ai_knowledge` (
                                `id`          BIGINT       NOT NULL AUTO_INCREMENT,
                                `source_type` VARCHAR(20)  NOT NULL COMMENT '类型: MANUAL_TEXT/POST_REF',
                                `title`       VARCHAR(200) NOT NULL COMMENT '标题',
                                `content`     TEXT         NOT NULL COMMENT '正文',
                                `post_id`     BIGINT       DEFAULT NULL COMMENT '来源笔记ID',
                                `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
                                `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                PRIMARY KEY (`id`),
                                KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI知识库';

-- ============================================
-- AI 向量同步补偿任务表
-- ============================================
CREATE TABLE `ai_vector_sync_task` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `knowledge_id`  BIGINT      NOT NULL COMMENT '知识库ID',
    `operation`     VARCHAR(20) NOT NULL COMMENT '操作: SYNC/DELETE',
    `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
    `fail_count`    INT         NOT NULL DEFAULT 0 COMMENT '失败次数',
    `next_retry_at` DATETIME    DEFAULT NULL COMMENT '下次重试时间（指数退避）',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_operation` (`knowledge_id`, `operation`),
    KEY `idx_status` (`status`),
    KEY `idx_next_retry` (`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI向量同步补偿任务';



