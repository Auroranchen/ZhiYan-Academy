-- =====================================================
-- 智学云课堂 · 功能扩充模块数据表
-- 包含：操作审计、在线考试、错题本、积分徽章、学习路径、查重报告
-- 字符集统一使用 utf8mb4，兼容中文与 JSON 文本
-- 可重复执行（IF NOT EXISTS / 幂等 seed）
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 模块H：操作审计日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_type` varchar(20) NULL DEFAULT NULL COMMENT '用户类型：admin/teacher/student/anon',
    `user_id` int NULL DEFAULT NULL COMMENT '用户ID',
    `user_name` varchar(100) NULL DEFAULT NULL COMMENT '用户名称',
    `action` varchar(40) NULL DEFAULT NULL COMMENT '动作：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/EXPORT/GRADE/OTHER',
    `module` varchar(60) NULL DEFAULT NULL COMMENT '模块名',
    `target` varchar(255) NULL DEFAULT NULL COMMENT '操作对象描述',
    `method` varchar(10) NULL DEFAULT NULL COMMENT 'HTTP方法',
    `uri` varchar(300) NULL DEFAULT NULL COMMENT '请求URI',
    `ip` varchar(64) NULL DEFAULT NULL COMMENT '客户端IP',
    `detail` text NULL COMMENT '详情/参数摘要',
    `status` int NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
    `cost_ms` int NULL DEFAULT 0 COMMENT '耗时(ms)',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user`(`user_type` ASC, `user_id` ASC) USING BTREE,
    INDEX `idx_action`(`action` ASC) USING BTREE,
    INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作审计日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块C：在线考试表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `exam` (
    `examid` int NOT NULL AUTO_INCREMENT COMMENT '考试ID',
    `title` varchar(200) NOT NULL COMMENT '考试标题',
    `courseid` int NOT NULL COMMENT '课程ID',
    `teacherid` int NOT NULL COMMENT '创建教师ID',
    `description` varchar(500) NULL DEFAULT NULL COMMENT '考试说明',
    `content` longtext NULL COMMENT '试题JSON（与作业题目同构）',
    `duration` int NULL DEFAULT 60 COMMENT '答题时长(分钟)',
    `total_score` int NULL DEFAULT 100 COMMENT '总分',
    `starttime` datetime NULL DEFAULT NULL COMMENT '开考时间',
    `endtime` datetime NULL DEFAULT NULL COMMENT '截止时间',
    `status` int NULL DEFAULT 1 COMMENT '状态：0草稿 1已发布 2已结束',
    `anti_cheat` int NULL DEFAULT 1 COMMENT '是否启用防作弊：1是 0否',
    `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`examid`) USING BTREE,
    INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
    INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '在线考试表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块C：考试作答记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `exam_record` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `examid` int NOT NULL COMMENT '考试ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `answers` longtext NULL COMMENT '学生作答JSON',
    `score` decimal(6,1) NULL DEFAULT NULL COMMENT '总得分',
    `objective_score` decimal(6,1) NULL DEFAULT NULL COMMENT '客观题得分',
    `status` int NULL DEFAULT 0 COMMENT '状态：0进行中 1已交卷 2已批改',
    `starttime` datetime NULL DEFAULT NULL COMMENT '开始作答时间',
    `submittime` datetime NULL DEFAULT NULL COMMENT '交卷时间',
    `used_seconds` int NULL DEFAULT 0 COMMENT '用时(秒)',
    `switch_count` int NULL DEFAULT 0 COMMENT '切屏/失焦次数(防作弊)',
    `auto_submit` int NULL DEFAULT 0 COMMENT '是否超时自动交卷：1是',
    `ai_comment` text NULL COMMENT 'AI评语',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_exam_student`(`examid` ASC, `studentid` ASC) USING BTREE,
    INDEX `idx_studentid`(`studentid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '考试作答记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块B：学生错题本表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `student_wrong_question` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `courseid` int NULL DEFAULT NULL COMMENT '课程ID',
    `sourceid` int NULL DEFAULT NULL COMMENT '来源作业/考试ID',
    `source` varchar(20) NULL DEFAULT 'homework' COMMENT '来源：homework/exam',
    `question_id` varchar(100) NOT NULL COMMENT '题目ID',
    `question_type` varchar(20) NULL DEFAULT NULL COMMENT '题型',
    `question_title` text NULL COMMENT '题干',
    `options_json` text NULL COMMENT '选项JSON',
    `correct_answer` text NULL COMMENT '正确答案',
    `student_answer` text NULL COMMENT '学生答案',
    `wrong_count` int NULL DEFAULT 1 COMMENT '累计错误次数',
    `mastered` int NULL DEFAULT 0 COMMENT '是否已掌握：0否 1是',
    `last_wrong_time` datetime NULL DEFAULT NULL COMMENT '最近错误时间',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收录时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_stu_q_src`(`studentid` ASC, `question_id` ASC, `source` ASC) USING BTREE,
    INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
    INDEX `idx_courseid`(`courseid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生错题本表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块D：学生积分汇总表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `student_points` (
    `studentid` int NOT NULL COMMENT '学生ID',
    `total_points` int NULL DEFAULT 0 COMMENT '累计积分',
    `level` int NULL DEFAULT 1 COMMENT '等级',
    `continuous_days` int NULL DEFAULT 0 COMMENT '连续打卡天数',
    `last_checkin` date NULL DEFAULT NULL COMMENT '最近打卡日期',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`studentid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生积分汇总表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块D：积分流水表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `points_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `points` int NOT NULL COMMENT '变动积分(正负)',
    `type` varchar(30) NULL DEFAULT NULL COMMENT '类型：CHECKIN/HOMEWORK/EXAM/MASTER/OTHER',
    `reason` varchar(150) NULL DEFAULT NULL COMMENT '说明',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_studentid`(`studentid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '积分流水表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块D：徽章定义表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `badge_def` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '徽章ID',
    `code` varchar(50) NOT NULL COMMENT '徽章编码',
    `name` varchar(50) NOT NULL COMMENT '徽章名称',
    `description` varchar(200) NULL DEFAULT NULL COMMENT '获得说明',
    `icon` varchar(60) NULL DEFAULT NULL COMMENT '图标',
    `condition_type` varchar(30) NULL DEFAULT NULL COMMENT '条件类型：POINTS/HOMEWORK/EXAM/CHECKIN/MASTER',
    `threshold` int NULL DEFAULT 0 COMMENT '达成阈值',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '徽章定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块D：学生徽章表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `student_badge` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `badge_code` varchar(50) NOT NULL COMMENT '徽章编码',
    `obtain_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_stu_badge`(`studentid` ASC, `badge_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生徽章表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块F：AI学习路径计划表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `learning_plan` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `courseid` int NULL DEFAULT NULL COMMENT '课程ID',
    `title` varchar(200) NULL DEFAULT NULL COMMENT '计划标题',
    `summary` varchar(500) NULL DEFAULT NULL COMMENT '学情概述',
    `content` longtext NULL COMMENT '路径JSON（阶段/知识点/任务）',
    `status` int NULL DEFAULT 1 COMMENT '状态：1有效 0归档',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_studentid`(`studentid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI学习路径计划表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 模块E：作业查重报告表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `plagiarism_report` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '报告ID',
    `homeworkid` int NOT NULL COMMENT '作业ID',
    `teacherid` int NULL DEFAULT NULL COMMENT '发起教师ID',
    `pair_count` int NULL DEFAULT 0 COMMENT '比对对数',
    `flagged_count` int NULL DEFAULT 0 COMMENT '超阈值疑似对数',
    `max_similarity` decimal(5,2) NULL DEFAULT 0 COMMENT '最高相似度(%)',
    `threshold` decimal(5,2) NULL DEFAULT 70 COMMENT '判定阈值(%)',
    `result_json` longtext NULL COMMENT '相似对明细JSON',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_homeworkid`(`homeworkid` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '作业查重报告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 徽章定义初始化数据（幂等）
-- ----------------------------
INSERT INTO `badge_def` (`code`,`name`,`description`,`icon`,`condition_type`,`threshold`) VALUES
('FIRST_SUBMIT','初露锋芒','完成第一次作业提交','ph:medal-duotone','HOMEWORK',1),
('HOMEWORK_10','勤学不辍','累计提交10次作业','ph:books-duotone','HOMEWORK',10),
('POINTS_100','积分新星','累计积分达到100','ph:star-duotone','POINTS',100),
('POINTS_500','积分达人','累计积分达到500','ph:sparkle-duotone','POINTS',500),
('CHECKIN_7','坚持一周','连续打卡7天','ph:fire-duotone','CHECKIN',7),
('CHECKIN_30','月度学霸','连续打卡30天','ph:crown-duotone','CHECKIN',30),
('EXAM_FULL','满分王者','单场考试获得满分','ph:trophy-duotone','EXAM',100),
('MASTER_20','纠错能手','错题本掌握20题','ph:target-duotone','MASTER',20)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `description`=VALUES(`description`), `icon`=VALUES(`icon`), `condition_type`=VALUES(`condition_type`), `threshold`=VALUES(`threshold`);

SET FOREIGN_KEY_CHECKS = 1;
