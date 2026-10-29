-- 作业评论、课程资源、考勤、通知已读、课程评价、站内消息
--
-- 由 V1__EnterpriseSchemaBaseline 执行，可重复运行。
--
-- 本文件取代原先的 new_tables.sql：那份脚本每张表前都有 DROP TABLE IF EXISTS，
-- 在已有数据的库上执行会清空这六张业务表。这里一律改为 CREATE TABLE IF NOT EXISTS。

CREATE TABLE IF NOT EXISTS `homework_comment` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `homeworkid` int NOT NULL COMMENT '作业ID',
    `studentid` int NULL DEFAULT NULL COMMENT '学生ID（学生提问时）',
    `teacherid` int NULL DEFAULT NULL COMMENT '教师ID（教师回复时）',
    `parentid` int NULL DEFAULT 0 COMMENT '父评论ID（0表示顶级评论）',
    `content` text NOT NULL COMMENT '评论内容',
    `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_homeworkid` (`homeworkid`),
    INDEX `idx_studentid` (`studentid`),
    INDEX `idx_teacherid` (`teacherid`),
    INDEX `idx_parentid` (`parentid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作业评论表';

CREATE TABLE IF NOT EXISTS `course_resource` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `courseid` int NOT NULL COMMENT '课程ID',
    `teacherid` int NOT NULL COMMENT '上传教师ID',
    `title` varchar(200) NOT NULL COMMENT '资源标题',
    `description` text NULL COMMENT '资源说明',
    `filetype` varchar(50) NULL DEFAULT NULL COMMENT '文件类型（PPT/PDF/CODE/OTHER）',
    `filepath` varchar(500) NOT NULL COMMENT '文件路径',
    `filename` varchar(200) NULL DEFAULT NULL COMMENT '原始文件名',
    `filesize` bigint NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `downloadcount` int NULL DEFAULT 0 COMMENT '下载次数',
    `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_courseid` (`courseid`),
    INDEX `idx_teacherid` (`teacherid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程资源表';

CREATE TABLE IF NOT EXISTS `student_attendance` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '考勤ID',
    `courseid` int NOT NULL COMMENT '课程ID',
    `classid` int NOT NULL COMMENT '班级ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `attenddate` date NOT NULL COMMENT '上课日期',
    `status` int NOT NULL DEFAULT 1 COMMENT '出勤状态：1出勤 2迟到 3早退 4缺勤 5请假',
    `remark` varchar(200) NULL DEFAULT NULL COMMENT '备注',
    `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `teacherid` int NULL DEFAULT NULL COMMENT '记录教师ID',
    PRIMARY KEY (`id`),
    INDEX `idx_courseid` (`courseid`),
    INDEX `idx_classid` (`classid`),
    INDEX `idx_studentid` (`studentid`),
    INDEX `idx_attenddate` (`attenddate`),
    UNIQUE INDEX `uk_attendance` (`courseid`, `classid`, `studentid`, `attenddate`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生考勤表';

CREATE TABLE IF NOT EXISTS `notice_read` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `noticeid` int NULL DEFAULT NULL COMMENT '公告ID',
    `homeworkid` int NULL DEFAULT NULL COMMENT '作业ID',
    `studentid` int NULL DEFAULT NULL COMMENT '学生ID',
    `teacherid` int NULL DEFAULT NULL COMMENT '教师ID',
    `readtime` datetime NULL DEFAULT NULL COMMENT '阅读时间',
    PRIMARY KEY (`id`),
    INDEX `idx_noticeid` (`noticeid`),
    INDEX `idx_homeworkid` (`homeworkid`),
    INDEX `idx_studentid` (`studentid`),
    INDEX `idx_teacherid` (`teacherid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '阅读状态表';

CREATE TABLE IF NOT EXISTS `course_evaluation` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    `courseid` int NOT NULL COMMENT '课程ID',
    `studentid` int NOT NULL COMMENT '学生ID',
    `teachingscore` int NULL DEFAULT 0 COMMENT '教学态度评分（1-5）',
    `contentscore` int NULL DEFAULT 0 COMMENT '内容质量评分（1-5）',
    `difficultyscore` int NULL DEFAULT 0 COMMENT '难度适中评分（1-5）',
    `overallscore` int NULL DEFAULT 0 COMMENT '综合评分（1-5）',
    `comment` text NULL COMMENT '文字意见',
    `anonymous` int NULL DEFAULT 1 COMMENT '是否匿名：1是 0否',
    `createtime` datetime NULL DEFAULT NULL COMMENT '评价时间',
    PRIMARY KEY (`id`),
    INDEX `idx_courseid` (`courseid`),
    INDEX `idx_studentid` (`studentid`),
    UNIQUE INDEX `uk_course_student` (`courseid`, `studentid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程评价表';

CREATE TABLE IF NOT EXISTS `message` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `title` varchar(200) NOT NULL COMMENT '消息标题',
    `content` text NULL COMMENT '消息内容',
    `sendertype` int NOT NULL COMMENT '发送者类型：1管理员 2教师',
    `senderid` int NOT NULL COMMENT '发送者ID',
    `receivertype` int NOT NULL COMMENT '接收者类型：1学生 2教师 3班级',
    `receiverid` int NOT NULL COMMENT '接收者ID（学生ID/教师ID/班级ID）',
    `isread` int NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    `readtime` datetime NULL DEFAULT NULL COMMENT '阅读时间',
    `createtime` datetime NULL DEFAULT NULL COMMENT '发送时间',
    PRIMARY KEY (`id`),
    INDEX `idx_sendertype_senderid` (`sendertype`, `senderid`),
    INDEX `idx_receivertype_receiverid` (`receivertype`, `receiverid`),
    INDEX `idx_isread` (`isread`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息表';
