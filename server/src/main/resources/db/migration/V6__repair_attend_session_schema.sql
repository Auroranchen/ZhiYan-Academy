-- 修正 attend_session 的列名
--
-- V1 基线早期版本把该表建成了 session_code / session_name / created_at，
-- 而 AttendSessionMapper.xml 读写的是 checkin_code / title / type /
-- late_minutes / duration / create_time。两者对不上，在这种库上课堂签到
-- 一调用就会报 Unknown column。V1 已被修正，但已经执行过 V1 的库不会重跑，
-- 需要这一版来收尾。
--
-- 重建条件很严：表存在、缺 checkin_code、且没有任何数据。
-- 列名对不上时应用根本插不进数据，因此「无数据」等价于「这张表从没被用过」，
-- 此时重建不会丢任何东西。有数据则说明是正确的旧表，原样保留。

SET @wrong_schema := (
    SELECT CASE
        WHEN (SELECT COUNT(*) FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attend_session') = 1
         AND (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attend_session'
                AND COLUMN_NAME = 'checkin_code') = 0
        THEN 1 ELSE 0
    END
);

SET @row_count := 1;
SET @probe := IF(@wrong_schema = 1, 'SELECT COUNT(*) INTO @row_count FROM attend_session', 'DO 0');
PREPARE stmt FROM @probe;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @rebuild := IF(@wrong_schema = 1 AND @row_count = 0, 'DROP TABLE attend_session', 'DO 0');
PREPARE stmt FROM @rebuild;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `attend_session` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `courseid` INT NOT NULL COMMENT '课程ID',
    `classid` INT NOT NULL COMMENT '班级ID',
    `teacherid` INT NOT NULL COMMENT '发起教师ID',
    `checkin_code` VARCHAR(10) NOT NULL COMMENT '签到码',
    `title` VARCHAR(100) DEFAULT NULL COMMENT '签到标题',
    `type` TINYINT NOT NULL DEFAULT 1 COMMENT '1=签到码 2=一键签到 3=手势',
    `late_minutes` INT NOT NULL DEFAULT 5 COMMENT '迟到阈值(分钟)',
    `duration` INT NOT NULL DEFAULT 10 COMMENT '签到持续时长(分钟)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=进行中 2=已结束',
    `start_time` DATETIME NOT NULL COMMENT '签到开始时间',
    `end_time` DATETIME NOT NULL COMMENT '签到截止时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_attend_session_owner` (`courseid`, `classid`, `status`),
    INDEX `idx_attend_session_time` (`start_time`, `end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课堂签到会话';
