-- 学习效果闭环与课程向量 RAG
CREATE TABLE IF NOT EXISTS `learning_effect_evaluation` (
  `id` int NOT NULL AUTO_INCREMENT,
  `student_id` int NOT NULL,
  `course_id` int DEFAULT NULL,
  `resource_id` int DEFAULT NULL,
  `source_type` varchar(24) NOT NULL,
  `pre_mastery` decimal(5,1) DEFAULT NULL,
  `post_mastery` decimal(5,1) DEFAULT NULL,
  `correct_count` int DEFAULT NULL,
  `total_count` int DEFAULT NULL,
  `focus_points` text,
  `result_json` mediumtext,
  `recommendation_json` mediumtext,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_student_course` (`student_id`,`course_id`,`id`),
  KEY `idx_resource` (`student_id`,`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习效果评估历史';

CREATE TABLE IF NOT EXISTS `resource_practice_attempt` (
  `id` int NOT NULL AUTO_INCREMENT,
  `student_id` int NOT NULL,
  `resource_id` int NOT NULL,
  `course_id` int DEFAULT NULL,
  `answers_json` mediumtext,
  `score` decimal(5,1) DEFAULT NULL,
  `correct_count` int DEFAULT NULL,
  `total_count` int DEFAULT NULL,
  `detail_json` mediumtext,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_student_resource` (`student_id`,`resource_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源包练习作答记录';

CREATE TABLE IF NOT EXISTS `course_knowledge_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL,
  `resource_id` int NOT NULL,
  `chunk_index` int NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `content` mediumtext NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  `embedding_json` mediumtext NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_chunk` (`resource_id`,`chunk_index`),
  KEY `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识向量切片';
