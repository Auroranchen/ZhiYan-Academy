/*
 Navicat Premium Dump SQL

 Source Server         : mysql8
 Source Server Type    : MySQL
 Source Server Version : 80043 (8.0.43)
 Source Host           : localhost:3306
 Source Schema         : zhzyai

 Target Server Type    : MySQL
 Target Server Version : 80043 (8.0.43)
 File Encoding         : 65001

 Date: 30/07/2026 19:33:53
*/
CREATE DATABASE IF NOT EXISTS zhzyai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zhzyai;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for academic_term
-- ----------------------------
DROP TABLE IF EXISTS `academic_term`;
CREATE TABLE `academic_term`  (
  `term_id` int NOT NULL AUTO_INCREMENT COMMENT '学期ID',
  `term_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学期编号，如2025-2026-2',
  `term_name` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '显示名称',
  `start_date` date NULL DEFAULT NULL COMMENT '开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '结束日期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/ACTIVE/ARCHIVED',
  `is_current` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否当前学期',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`term_id`) USING BTREE,
  UNIQUE INDEX `uk_academic_term_code`(`term_code` ASC) USING BTREE,
  INDEX `idx_academic_term_state`(`is_current` ASC, `status` ASC) USING BTREE,
  INDEX `idx_academic_term_dates`(`start_date` ASC, `end_date` ASC) USING BTREE,
  CONSTRAINT `chk_academic_term_dates` CHECK ((`end_date` is null) or (`start_date` is null) or (`end_date` >= `start_date`)),
  CONSTRAINT `chk_academic_term_status` CHECK (`status` in (_utf8mb4'PLANNED',_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED'))
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学年学期主数据' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of academic_term
-- ----------------------------
INSERT INTO `academic_term` VALUES (1, '2025-2026-2', '2025-2026学年第2学期', '2026-02-01', '2026-08-31', 'ACTIVE', 1, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (2, '2025-2026-1', '2025-2026学年第1学期', '2025-09-01', '2026-01-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (3, '2022-2023-1', '2022-2023学年第1学期', '2022-09-01', '2023-01-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (4, '2023-2024-1', '2023-2024学年第1学期', '2023-09-01', '2024-01-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (5, '2024-2025-1', '2024-2025学年第1学期', '2024-09-01', '2025-01-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (6, '2026-2027-1', '2026-2027学年第1学期', '2026-09-01', '2027-01-31', 'PLANNED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (7, '2022-2023-2', '2022-2023学年第2学期', '2023-02-01', '2023-08-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (8, '2023-2024-2', '2023-2024学年第2学期', '2024-02-01', '2024-08-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (9, '2024-2025-2', '2024-2025学年第2学期', '2025-02-01', '2025-08-31', 'ARCHIVED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');
INSERT INTO `academic_term` VALUES (10, '2026-2027-2', '2026-2027学年第2学期', '2027-02-01', '2027-08-31', 'PLANNED', 0, '2026-07-18 17:49:01', '2026-07-18 18:46:52');

-- ----------------------------
-- Table structure for admin
-- ----------------------------
DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin`  (
  `adminid` int NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '密码',
  `realname` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '真实姓名',
  `avatar` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '头像路径',
  PRIMARY KEY (`adminid`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '管理员表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of admin
-- ----------------------------
INSERT INTO `admin` VALUES (1, 'admin', '$2a$12$2dNtTl6Ovt0NB3MEoGO5oeDFpx0lUZrb/Lw23/yt3SVY89VNJvoDG', '教务人员', NULL);

-- ----------------------------
-- Table structure for ai_call_log
-- ----------------------------
DROP TABLE IF EXISTS `ai_call_log`;
CREATE TABLE `ai_call_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scene` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `success` tinyint NOT NULL,
  `elapsed_ms` int NOT NULL,
  `error` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `prompt_tokens` int NULL DEFAULT 0,
  `completion_tokens` int NULL DEFAULT 0,
  `total_tokens` int NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scene_time`(`scene` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI调用监控日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_call_log
-- ----------------------------
INSERT INTO `ai_call_log` VALUES (1, 'agent.explainer', 1, 55544, NULL, '2026-07-11 09:04:51', NULL, 0, 0, 0);

-- ----------------------------
-- Table structure for ai_explain_log
-- ----------------------------
DROP TABLE IF EXISTS `ai_explain_log`;
CREATE TABLE `ai_explain_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `homework_id` int NOT NULL COMMENT '作业ID',
  `student_id` int NOT NULL COMMENT '学生ID',
  `question_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题目ID（对应JSON中的id）',
  `key_points` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关键知识点',
  `error_analysis` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错因分析',
  `steps` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '解题步骤',
  `expansion` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '举一反三拓展题',
  `reference_code` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '参考代码',
  `common_mistakes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '常见错误',
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'v1.0' COMMENT '版本号',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  `duration` int NULL DEFAULT NULL COMMENT '生成耗时(ms)',
  `is_cache_hit` tinyint(1) NULL DEFAULT 0 COMMENT '是否命中缓存',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_hw_stu_q`(`homework_id` ASC, `student_id` ASC, `question_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 93 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI讲解记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_explain_log
-- ----------------------------
INSERT INTO `ai_explain_log` VALUES (79, 826, 309, 'q_1770609916719_j8g03f', '数组是一种顺序数据结构，它通过数组的下标直接访问元素。', '', '[\"理解数组的定义：数组是一种线性数据结构，它允许存储一系列元素。\",\"了解数组的特性：数组中的元素按照一定的顺序排列，可以通过下标直接访问任意位置的元素。\",\"根据题目要求，填写正确的答案：顺序\"]', '{\"question\":\"数组是一种____数据结构，它通过数组的下标直接访问元素。\",\"answer\":\"顺序\",\"analysis\":\"数组是一种顺序数据结构，它通过数组的下标直接访问元素，这是数组的基本特性。\"}', '', '[\"将数组误认为是链式数据结构，忽略了数组的顺序特性。\",\"忘记数组的下标访问特性，认为只能通过遍历访问元素\"]', 'v1.0', '2026-02-11 15:38:08', 10605, 0);
INSERT INTO `ai_explain_log` VALUES (80, 826, 309, 'q_1770609916719_b1tg4l', '线性表的基本概念，包括其特点和常见的几种类型（栈、树、队列、散列表）', '', '[\"步骤1：理解线性表的定义和特点，线性表是一种数据结构，其中的元素排列成一定的顺序。\",\"步骤2：了解线性表的不同类型，包括栈、树、队列和散列表。\",\"步骤3：分析每种数据结构是否符合线性表的定义，栈是一种后进先出的线性表，队列是一种先进先出的线性表，散列表是一种通过散列函数映射到表中的位置，而树是一种非线性结构。\",\"步骤4：根据上述分析，确定哪种数据结构是线性表的一种。\"]', '{\"question\":\"以下哪个是线性表的一种？\",\"answer\":\"栈\",\"analysis\":\"线性表是一种数据结构，其中的元素排列成一定的顺序。栈是一种特殊的线性表，它支持两种基本操作：push（进栈）和pop（出栈），其中元素按照后进先出的原则进行操作。树和散列表不是线性表，因为它们不满足线性表的定义。队列虽然是一种线性表，但在本题的选项中并未列出。\"}', '', '[\"错误1：误认为树是线性表，因为树中的元素虽然也有顺序，但它是一种非线性结构。\",\"错误2：误认为散列表是线性表，因为散列表的元素是通过散列函数映射到表中的位置，并不保证元素的顺序性。\"]', 'v1.0', '2026-02-11 15:38:09', 11709, 0);
INSERT INTO `ai_explain_log` VALUES (81, 826, 309, 'q_1770609916719_fvlhtf', '链表的插入操作时间复杂度分析，指针操作，链表结构', '学生可能没有充分理解链表结构的特性，误以为插入操作的时间复杂度与数组类似。', '[\"步骤1：理解链表的结构。链表是由节点组成的线性结构，每个节点包含数据和指向下一个节点的指针。\",\"步骤2：分析插入操作。在链表中插入一个节点，需要找到正确的插入位置，然后进行指针的重新链接。\",\"步骤3：确定时间复杂度。在链表中查找插入位置需要遍历链表，因此插入操作的时间复杂度为O(n)。\"]', '{\"question\":\"在链表中，插入一个节点的时间复杂度是O(n)。\",\"answer\":\"正确\",\"analysis\":\"在链表中插入一个节点的时间复杂度确实是O(n)，因为需要遍历链表以找到正确的插入位置，并进行指针的重新链接。与数组不同，数组插入操作的时间复杂度取决于插入位置，平均情况下是O(n)。\"}', '', '[\"错误1：误以为链表的插入操作时间复杂度与数组类似，是O(1)。\",\"错误2：没有考虑指针操作带来的额外开销，认为插入节点的时间复杂度应该是O(1)。\"]', 'v1.0', '2026-02-11 15:38:09', 11773, 0);
INSERT INTO `ai_explain_log` VALUES (82, 826, 309, 'q_1770609916719_dxgnw6', '栈（Stack）是一种特殊的线性表，其特点是只能在表的一端进行插入和删除操作。这种操作遵循后进先出（LIFO）的原则。', '', '[\"步骤1：理解栈的定义，它是一种特殊的线性数据结构。\",\"步骤2：了解栈的操作特点，即插入和删除操作都在一端进行。\",\"步骤3：确认栈的操作原则为后进先出（LIFO）。\"]', '{\"question\":\"栈是一种特殊的线性表，其插入和删除操作都在一端进行。\",\"answer\":\"正确\",\"analysis\":\"栈确实是一种特殊的线性表，其所有的插入与删除操作都在栈顶进行，且遵循后进先出的原则。\"}', '', '[\"错误1：误认为栈可以像普通数组或链表一样在任意位置插入或删除元素。\",\"错误2：混淆栈与队列的操作原则，错误地将栈的操作原则认为是先进先出（FIFO）。\"]', 'v1.0', '2026-02-11 15:38:10', 12309, 0);
INSERT INTO `ai_explain_log` VALUES (83, 826, 309, 'q_1770609916719_qcs292', '队列的基本操作：入队（enqueue）、出队（dequeue）、遍历（traverse）、删除（delete）。', '', '[\"步骤1：了解队列的定义和基本操作。\",\"步骤2：分析每个选项是否符合队列操作的定义。\",\"步骤3：确认正确答案并匹配学生的答案。\"]', '{\"question\":\"以下哪些是队列操作？\",\"answer\":\"[0,1]\",\"analysis\":\"入队和出队是队列的基本操作。入队是指将元素添加到队列的末尾，出队是指从队列的头部移除元素。遍历和删除虽然可以在队列上进行，但它们不是队列的基本操作。在这个问题中，0代表入队操作，1代表出队操作，这两个操作都是队列的标准操作。\"}', '', '[\"错误1：混淆队列操作和栈操作，如将出队误认为入队。\",\"错误2：将遍历误认为是一种修改操作，而非只读操作。\"]', 'v1.0', '2026-02-11 15:38:12', 14565, 0);
INSERT INTO `ai_explain_log` VALUES (84, 826, 309, 'q_1770609916719_0kdv6s', '非线性数据结构是指数据元素之间不存在线性关系的数据结构。常见非线性数据结构包括树和图。', '', '[\"步骤1: 理解非线性数据结构的定义，即数据元素之间不存在线性关系的数据结构。\",\"步骤2: 分析给出的选项，判断每个数据结构是否符合非线性定义。\",\"步骤3: 确定链表是线性结构，树是非线性结构，哈希表通常被视为近似线性结构。\",\"步骤4: 根据分析结果，选择正确答案。\"]', '{\"question\":\"以下哪个数据结构是非线性的？\",\"answer\":\"2\",\"analysis\":\"非线性数据结构是指数据元素之间不存在线性关系的数据结构。在给出的选项中，链表是线性结构，树是非线性结构，哈希表通常被视为近似线性结构。因此，正确答案是树，对应选项2。\"}', '', '[\"错误1: 将链表误认为是非线性结构，实际上链表是线性结构。\",\"错误2: 将哈希表误认为是非线性结构，虽然哈希表的结构不是完全线性的，但通常被视为近似线性结构。\"]', 'v1.0', '2026-02-11 15:38:13', 15134, 0);
INSERT INTO `ai_explain_log` VALUES (85, 826, 309, 'q_1770609916719_s15klf', '二叉树的特点包括：每个节点最多有两个子节点，左子树和右子树的节点顺序固定，可以递归地定义，所有节点的度数都是2。', '', '[\"步骤1：理解二叉树的定义，二叉树是一种特殊的树形结构，每个节点最多有两个子节点。\",\"步骤2：理解左子树和右子树的节点顺序固定，通常左子树的节点排在右子树的节点前面。\",\"步骤3：理解二叉树可以递归地定义，即二叉树本身可以看作是根节点和左右子树构成的。\",\"步骤4：理解所有节点的度数都是2，即每个节点最多有两个子节点，度数为子节点数量的二进制表示，对于二叉树来说，度数总是2。\",\"步骤5：对比题目选项，验证每个选项是否符合二叉树的特点。\"]', '{\"question\":\"以下哪些是二叉树的特点？\",\"answer\":\"[0, 1, 2]\",\"analysis\":\"二叉树的特点包括：每个节点最多有两个子节点，左子树和右子树的节点顺序固定，可以递归地定义，所有节点的度数都是2。学生答案正确地识别了这些特点。\"}', '', '[\"错误1：误认为每个节点只能有一个子节点，忽略了二叉树定义中每个节点最多有两个子节点的特点。\",\"错误2：忽略了左子树和右子树的节点顺序固定这一特点，认为顺序可以随意。\"]', 'v1.0', '2026-02-11 15:38:13', 15735, 0);
INSERT INTO `ai_explain_log` VALUES (86, 826, 309, 'q_1770609916719_wljacu', '链表节点的前驱、后继、本身、后代查找与删除节点的关系', '学生可能对链表节点的定义和操作理解不够深入，或者混淆了查找与删除节点的操作。', '[\"步骤1：确定删除节点的前一个节点（前驱）的位置。\",\"步骤2：通过前一个节点访问并删除目标节点。\",\"步骤3：更新前一个节点的后继指针，指向要删除节点的后继节点（如果存在）。\",\"步骤4：释放被删除节点的内存资源。\"]', '{\"question\":\"在链表中，删除一个节点需要做以下哪项操作？\",\"answer\":\"查找节点的前一个节点\",\"analysis\":\"在链表中删除一个节点，首先需要找到该节点的前一个节点，然后通过这个前一个节点来访问并删除目标节点，同时更新前一个节点的后继指针。\"}', '// 注释\n#include <stdio.h>\n#include <stdlib.h>\n\n// 定义链表节点结构体\ntypedef struct Node {\n    int data;\n    struct Node* next;\n} Node;\n\n// 删除链表中的节点\nvoid deleteNode(Node** headRef, Node* delNode) {\n    // 如果头节点就是要删除的节点\n    if (*headRef == delNode) {\n        *headRef = delNode->next;\n    } else {\n        // 找到要删除节点的前一个节点\n        Node* current = *headRef;\n        while (current->next != NULL && current->next != delNode) {\n            current = current->next;\n        }\n        // 如果找到了前一个节点\n        if (current->next != NULL) {\n            current->next = delNode->next;\n        }\n    }\n    // 释放内存\n    free(delNode);\n}\n', '[\"错误1：误以为删除节点需要查找节点本身。\",\"错误2：忘记更新删除节点的前一个节点的后继指针。\"]', 'v1.0', '2026-02-11 15:38:16', 18248, 0);
INSERT INTO `ai_explain_log` VALUES (87, 826, 309, 'q_1770609916719_45dxyg', '在链表中查找节点的时间复杂度是O(n)，因为需要遍历整个链表。', '学生可能误以为链表查找节点的时间复杂度是O(n^2)，是因为没有理解链表与数组的区别。数组可以通过索引直接访问，而链表需要从头节点开始遍历。', '[\"步骤1：从链表的头节点开始遍历。\",\"步骤2：对于每个节点，检查节点的值是否与目标值匹配。\",\"步骤3：如果找到匹配的节点，返回该节点。如果遍历结束仍未找到，则返回null或相应指示未找到的信息。\"]', '{\"question\":\"在链表中，查找一个节点的时间复杂度是____。\",\"answer\":\"O(n)\",\"analysis\":\"链表查找节点的时间复杂度是O(n)，因为需要从头节点开始遍历整个链表，直到找到目标节点或遍历结束。\"}', '', '[\"错误1：混淆链表和数组的查找时间复杂度。\",\"错误2：未考虑链表遍历的线性特性。\"]', 'v1.0', '2026-02-11 15:41:30', 11833, 0);
INSERT INTO `ai_explain_log` VALUES (88, 824, 301, 'q1', '两数之和问题，哈希表的使用，避免重复遍历，正确设置返回值和返回大小', '学生答案中存在以下错误：\n1. 内层循环的终止条件错误，应为j < numsSize而非j > numsSize。\n2. 在找到符合条件的两个数后，应该将索引存储在res[0]和res[1]中，而非res[0]和res[2]。', '[\"定义函数twoSum，接收整数数组nums、数组大小numsSize、目标值target和返回值大小指针returnSize。\",\"为结果分配一个大小为2的整数数组res，用于存储两个数的索引。\",\"初始化returnSize为2，表示预期返回两个索引。\",\"使用双层循环遍历数组，外层循环变量i从0到numsSize-1，内层循环变量j从i+1到numsSize。\",\"在内层循环中，检查nums[i] + nums[j]是否等于target。\",\"如果找到符合条件的两个数，将它们的索引分别存储在res[0]和res[1]中，并返回res。\",\"如果遍历结束仍未找到符合条件的两个数，返回NULL。\"]', '{\"question\":\"给定一个整数数组nums和一个目标值target，请你在该数组中找出和为目标值的所有整数对，并返回它们的数组下标。\",\"answer\":\"可以使用哈希表来存储每个数的索引，然后遍历数组，对于每个数，计算与目标值之差，并检查这个差值是否已经在哈希表中。如果存在，则找到了一个整数对。\",\"analysis\":\"这是一个扩展问题，需要学生理解哈希表在解决此类问题中的应用，并能够处理多个整数对的情况。\"}', 'int* twoSum(int* nums, int numsSize, int target, int* returnSize) {\n int* res = malloc(2 * sizeof(int));\n *returnSize = 2;\n for (int i = 0; i < numsSize; i++)\n for (int j = i + 1; j < numsSize; j++)\n if (nums[i] + nums[j] == target) {\n res[0] = i;\n res[1] = j;\n return res;\n }\n free(res);\n return NULL;\n}', '[\"忘记修改内层循环的终止条件。\",\"错误地设置返回值数组的大小或索引位置。\"]', 'v2.0-f', '2026-06-08 11:02:32', 37583, 0);
INSERT INTO `ai_explain_log` VALUES (89, 805, 301, 'q1', '软件需求分析文档的核心是明确系统“必须做什么”，通常包括：功能需求（用例、用户故事）、非功能需求（性能、安全、可用性等）、数据需求、界面原型或约束条件；文档应聚焦于需求本身，不包含设计、实现或测试细节。', '学生提交的是一份完整的毕业论文初稿，虽然其中第二章为“系统需求分析”，但整体文档超出了“软件需求分析文档”的范畴，未按要求单独形成需求规格说明。就需求部分而言，功能需求描述基本清晰，使用了用例图，但存在以下不足：(1) 缺少非功能需求（如性能指标、并发用户数、安全性要求、浏览器兼容性等）；(2) 需求分析中出现了“购物车”“订单管理”等电子商务功能，但论文题目为“非遗民族文化资源管理系统”，若该系统同时包含商城模块，应在需求中明确业务边界，否则易产生理解偏差；(3) 英文摘要存在语法错误，如“So we developed ...”时态不当，且结构松散；(4) 章节编号层级有误，如2.2.1标题为“管理端需求”，正文首句却写为“2.2.1 管理员需求分析”，不一致。若以纯需求文档标准衡量，尚需补充非功能约束、数据字典或界面原型，并移除设计与实现内容。', '[\"审题：题目要求提交一份针对实际项目的软件需求分析文档，理想答案应为一篇结构完整、仅关注需求层面的说明文档，不含系统设计、实现或测试。\",\"检查学生作答：学生提交的文档是整篇毕业论文，包含绪论、需求分析、技术介绍、系统设计、实现、测试、结论等。其中只有第二章与需求直接相关，其余章节超出了作业范围，未满足‘仅提交需求分析文档’的要求。\",\"分析需求部分的质量：第二章进行了系统整体分析、功能需求分析（管理员/用户用例图及功能列表）、可行性分析。用例图绘制较规范，功能点罗列较全，但完全未提及非功能需求，如响应时间、数据安全、最大并发用户数、故障恢复等。\",\"指出改进方向：应单独抽取需求分析章节，删除后续设计、实现、测试等章节；补充非功能需求章节；明确系统业务边界（是否包含商城？避免功能混杂）；修正英文摘要语法错误和章节编号一致性问题。\"]', '{\"question\":\"若需为“非遗民族文化资源管理系统”补充非功能需求，请写出至少3条并简要说明理由。\",\"answer\":\"1. 并发用户数：系统应支持至少500个并发用户同时浏览非遗资源页面，以保证高峰时段用户体验。2. 页面响应时间：在正常网络条件下，非遗资源详情页的加载时间不超过3秒。3. 数据安全：所有用户密码必须加密存储，密码重置链接有效期为10分钟，防止用户信息泄露。\",\"analysis\":\"非功能需求定义了系统的质量属性，也是对功能需求的补充约束，有助于架构设计和测试用例的制定。\"}', '', '[\"将整个项目设计文档或论文作为需求分析文档提交，未区分需求与设计的界限，导致文档冗长且重点模糊。\",\"只描述用户可见的功能，忽略系统在性能、安全、可靠性等方面的非功能需求，使需求分析不完整。\"]', 'v2.0-f', '2026-06-08 11:38:32', 31112, 0);
INSERT INTO `ai_explain_log` VALUES (90, 815, 301, 'q1', '学生提交的答案为“My C answer”，无实际解题内容，无法提取核心知识点。', '学生的作答仅为占位文本，未展示任何与题目相关的思路、步骤或结论，因此无法判断正确与否。该情况常出现在未理解题目要求、时间不足或误操作时。建议重新阅读题目，明确所要求解答的内容（如算法步骤、数据结构操作、C语言代码实现等），并用完整语言或代码作答。', '[\"1. 学生需先获取原题具体内容（课后习题1-5），明确题目要求（如实现某算法、分析复杂度、编写函数等）。\",\"2. 根据题目要求，拟定解题步骤（如画图、写伪代码、设计测试用例等），并逐步实现。\",\"3. 最终提交完整的答案，包含必要的注释和解释，而非仅提供占位文字。\"]', '{\"question\":\"假设课后习题1-5要求：用C语言实现一个函数 int factorial(int n)，计算n的阶乘，并处理n<0的异常情况。请写出完整的函数代码及简要说明。\",\"answer\":\"int factorial(int n) {\\n if (n < 0) return -1; // 约定返回-1表示输入无效\\n if (n == 0) return 1;\\n int result = 1;\\n for (int i = 1; i <= n; ++i) result *= i;\\n return result;\\n}\\n说明：对于n<0，函数返回-1以标识错误；否则通过循环计算阶乘。也可用递归实现，需注意整型溢出问题。\",\"analysis\":\"此题考察循环或递归实现，以及错误处理意识。学生常见错误包括：未处理负数输入、递归未设置终止条件、返回类型选择不当（如使用long long可能更安全）等。通过此题可以类比课后习题1-5可能的编程要求，学会分情况讨论与防御性编程。\"}', '', '[\"1. 仅提交占位符（如“My C answer”）而不写实质内容，导致无法评分。\",\"2. 在未理解题意的情况下直接复制粘贴不相关的代码，或给出离题的描述。\"]', 'v2.0-f', '2026-06-08 11:42:39', 23070, 0);
INSERT INTO `ai_explain_log` VALUES (91, 831, 301, 'q1', '词法分析器将源代码字符流转换为 Token 序列，需要定义词法单元类型（标识符、关键字、整数、运算符、界符），通过有限状态自动机实现识别；关键字需要与保留字表比对，标识符与数值常量需存入符号表；输出格式应包含 Token 类型、词素值及符号表条目。', '学生作答仅一句话描述使用 Java 实现并支持全部 Token 和测试用例，与题目要求差距很大。题目明确要求“附测试用例与运行截图”，且需要给出可运行的实现代码、符号表输出。该回答没有提供任何代码、设计思路、符号表构建方法，不符合实验报告或作业提交的基本要求。改进方向：提交完整的 Java 或 C/C++ 源代码，包含词法分析主循环、Token 结构定义、符号表数据结构、关键字表、整数识别、运算符/界符识别，并附上至少一个测试用例（如输入一段简单代码）的 Token 序列和符号表输出的截图。', '[\"1. 定义 Token 结构：包含类型（如 TK_IDENTIFIER、TK_KEYWORD、TK_INTEGER、TK_OPERATOR、TK_DELIMITER）、词素字符串、行号等信息。\",\"2. 构建关键字表（如 if、else、while、int 等），用于区分标识符和关键字。\",\"3. 实现主扫描循环：跳过空白字符；若当前字符为字母或下划线，收集所有字母数字下划线，形成字符串后查关键字表，若是关键字则记录 TK_KEYWORD，否则记录 TK_IDENTIFIER 并加入符号表（如果尚未存在）。\",\"4. 识别整数：遇到数字则连续读取数字字符，转换为整数值（或保留字符串），记录 TK_INTEGER 并可选将常量加入符号表。\",\"5. 识别运算符与界符：对常见单字符（如 +、-、*、/、(、)、{、}、;、, 等）直接返回对应 Token；对可能的多字符运算符（如 ==、!=、<=、>=）进行检查，若第二个字符匹配则组合为一个 Token。\",\"6. 符号表管理：可使用哈希表或链表，存储标识符名称、可能的数据类型、作用域等信息，每次遇到标识符或常量时查找或插入。\",\"7. 测试用例设计：准备至少一个输入文件，包含关键字、标识符、整数、各类运算符和界符，执行词法分析器，打印 Token 序列（类型+词素）和最终的符号表内容，并截图运行结果。\"]', '{\"question\":\"在现有词法分析器基础上，增加对浮点数（如 3.14、.5、2.0e10）和字符串常量（\\\"hello world\\\"）的支持，输出对应的 Token 类型（如 TK_FLOAT、TK_STRING），并更新符号表。\",\"answer\":\"修改整数识别部分：识别数字后若遇到小数点或 e/E，继续读取后续数字或指数部分，形成完整的浮点数词素，判定为 TK_FLOAT；字符串识别：遇到双引号则进入字符串状态，读取直到下一个双引号（处理转义字符如 \\\\\\\"），将内容作为 TK_STRING，不放入符号表（或按需求放入）。\",\"analysis\":\"此扩展考察对有限自动机的修改能力，浮点数状态转移需要处理小数点前后数字可选、指数部分可带符号等情况；字符串识别需处理未闭合引号的错误。实现时保持主循环结构不变，增加两个状态分支即可。\"}', '#include <stdio.h>\n#include <ctype.h>\n#include <string.h>\n#include <stdlib.h>\n\n/* Token 类型枚举 */\ntypedef enum {\n    TK_IDENTIFIER, TK_KEYWORD, TK_INTEGER,\n    TK_OPERATOR, TK_DELIMITER, TK_EOF\n} TokenType;\n\n/* Token 结构 */\ntypedef struct {\n    TokenType type;\n    char lexeme[64];\n    int line;\n} Token;\n\n/* 关键字表 */\nconst char *keywords[] = {\"if\",\"else\",\"while\",\"int\",\"return\",NULL};\n\n/* 判断是否为关键字，返回索引，不是则返回 -1 */\nint isKeyword(const char *str) {\n    for (int i = 0; keywords[i] != NULL; i++)\n        if (strcmp(str, keywords[i]) == 0) return i;\n    return -1;\n}\n\n/* 符号表条目 */\ntypedef struct Symbol {\n    char name[64];\n    int type;  // 0:var, 1:const\n    struct Symbol *next;\n} Symbol;\n\nSymbol *symTable = NULL;\n\n/* 向符号表插入标识符或常量，若已存在则不重复 */\nvoid insertSymbol(const char *name, int kind) {\n    Symbol *cur = symTable;\n    while (cur) {\n        if (strcmp(cur->name, name) == 0) return; // 已存在\n        cur = cur->next;\n    }\n    Symbol *newSym = (Symbol*)malloc(sizeof(Symbol));\n    strcpy(newSym->name, name);\n    newSym->type = kind;\n    newSym->next = symTable;\n    symTable = newSym;\n}\n\n/* 简单词法分析器：从标准输入读取，输出 Token 及符号表 */\nvoid lex() {\n    int ch;\n    char buffer[64];\n    int bi = 0;\n    int line = 1;\n    while ((ch = getchar()) != EOF) {\n        if (isspace(ch)) {\n            if (ch == \'\\n\') line++;\n            continue;\n        }\n        /* 标识符或关键字 */\n        if (isalpha(ch) || ch == \'_\') {\n            bi = 0;\n            do {\n                buffer[bi++] = ch;\n                ch = getchar();\n            } while (isalnum(ch) || ch == \'_\');\n            ungetc(ch, stdin);\n            buffer[bi] = \'\\0\';\n            if (isKeyword(buffer) != -1)\n                printf(\"<KEYWORD, %s>\\n\", buffer);\n            else {\n                printf(\"<IDENTIFIER, %s>\\n\", buffer);\n                insertSymbol(buffer, 0);\n            }\n        }\n        /* 整数 */\n        else if (isdigit(ch)) {\n            bi = 0;\n            do {\n                buffer[bi++] = ch;\n                ch = getchar();\n            } while (isdigit(ch));\n            ungetc(ch, stdin);\n            buffer[bi] = \'\\0\';\n            printf(\"<INTEGER, %s>\\n\", buffer);\n            insertSymbol(buffer, 1);\n        }\n        /* 运算符或界符 */\n        else {\n            switch (ch) {\n                case \'+\': printf(\"<OPERATOR, +>\\n\"); break;\n                case \'-\': printf(\"<OPERATOR, ->\\n\"); break;\n                case \'*\': printf(\"<OPERATOR, *>\\n\"); break;\n                case \'/\': printf(\"<OPERATOR, />\\n\"); break;\n                case \'=\': {\n                    int next = getchar();\n                    if (next == \'=\') printf(\"<OPERATOR, ==>\\n\");\n                    else { ungetc(next, stdin); printf(\"<OPERATOR, =>\\n\"); }\n                    break;\n                }\n                case \'(\': printf(\"<DELIMITER, (>\\n\"); break;\n                case \')\': printf(\"<DELIMITER, )>\\n\"); break;\n                case \'{\': printf(\"<DELIMITER, {>\\n\"); break;\n                case \'}\': printf(\"<DELIMITER, }>\\n\"); break;\n                case \';\': printf(\"<DELIMITER, ;>\\n\"); break;\n                case \',\': printf(\"<DELIMITER, ,>\\n\"); break;\n                default: fprintf(stderr, \"未知字符 %c at line %d\\n\", ch, line);\n            }\n        }\n    }\n    printf(\"<EOF>\\n\");\n    /* 输出符号表 */\n    printf(\"\\n符号表:\\n\");\n    Symbol *cur = symTable;\n    while (cur) {\n        printf(\"%s (%s)\\n\", cur->name, cur->type == 0 ? \"变量\" : \"常量\");\n        cur = cur->next;\n    }\n}\n\nint main() {\n    lex();\n    return 0;\n}', '[\"未区分关键字和标识符：直接将所有字母序列视为标识符，导致 if、while 等被错误识别，正确做法应查关键字表。\",\"未构建符号表：仅输出 Token 序列而忽略符号表的生成与输出，题目明确要求输出符号表，需在识别标识符和整数时插入并维护符号表。\"]', 'v2.0-f', '2026-07-11 08:19:44', 34855, 0);
INSERT INTO `ai_explain_log` VALUES (92, 828, 301, 'q1', '有序递增数组去重算法核心：利用有序性，使用双指针（快慢指针）原地修改，快指针遍历所有元素，慢指针记录已保留的不重复元素末尾。时间复杂度O(n)，空间复杂度O(1)。', '作答内容与题目要求完全不符。题目要求针对给定有序递增数组设计高效去重算法并分析复杂度，但学生提交的是“顺序表的增删改查，附完整代码”，属于答非所问。需要重新设计算法，紧扣“有序递增”这一关键条件，采用双指针原地去重，避免额外空间开销，并给出时间O(n)、空间O(1)的说明。', '[\"检查数组是否为空，若为空则直接返回长度0。\",\"设置慢指针slow初始为0，表示当前已保留的不重复元素末尾位置。\",\"使用快指针fast从1开始遍历数组，当nums[fast] != nums[slow]时，将slow后移一位，并将nums[fast]的值复制到nums[slow]处；若相等则仅移动fast。\",\"遍历结束后，不重复元素长度为slow+1，返回该长度，数组前slow+1个元素即为去重结果。\"]', '{\"question\":\"给定两个有序递增的单链表，请设计算法将它们归并为一个新的有序递增链表，且新链表中不能包含重复元素。分析时间与空间复杂度。\",\"answer\":\"利用双指针分别遍历两个链表，比较当前节点值，取较小者加入新链表，若值相等则只取一次，并同时移动两个指针。时间复杂度O(m+n)，空间复杂度O(1)（若允许原地调整指针）或O(m+n)（若创建新节点）。具体实现需注意链表节点连接和重复跳过逻辑。\",\"analysis\":\"该题类比数组去重和有序合并，进一步练习有序数据结构的线性合并与去重。关键点：利用有序性，归并过程中跳过重复值，指针移动需谨慎处理相等情况，避免内存泄漏。\"}', 'int removeDuplicates(int* nums, int numsSize) {\n    if (numsSize == 0) return 0;  // 空数组直接返回\n    int slow = 0;                 // 慢指针，指向已处理不重复元素的最后位置\n    for (int fast = 1; fast < numsSize; ++fast) {\n        if (nums[fast] != nums[slow]) {  // 遇到新元素\n            ++slow;                      // 扩展不重复区间\n            nums[slow] = nums[fast];     // 覆盖写入，保留新元素\n        }\n    }\n    return slow + 1;  // 不重复元素个数\n}', '[\"未利用数组有序的条件，盲目使用哈希表或双重循环去重，导致时间复杂度O(n^2)或额外空间开销，未能达到O(n)时间和O(1)空间的最优解。\",\"双指针实现时，没有正确处理慢指针的初始位置，或忘记在发现不同元素时先移动慢指针再赋值，导致第一个元素被覆盖或遗漏。\"]', 'v2.0-f', '2026-07-11 10:25:20', 17268, 0);

-- ----------------------------
-- Table structure for ai_feedback
-- ----------------------------
DROP TABLE IF EXISTS `ai_feedback`;
CREATE TABLE `ai_feedback`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` int NOT NULL,
  `message_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `scene` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `rating` tinyint NOT NULL,
  `reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `question_preview` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `answer_preview` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_message`(`user_role` ASC, `user_id` ASC, `message_key` ASC) USING BTREE,
  INDEX `idx_scene_time`(`scene` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_rating_time`(`rating` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI回答质量反馈' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_feedback
-- ----------------------------

-- ----------------------------
-- Table structure for ai_report
-- ----------------------------
DROP TABLE IF EXISTS `ai_report`;
CREATE TABLE `ai_report`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `report_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '报告类型：student / class',
  `ref_id` int NOT NULL COMMENT '关联对象ID（学生ID / 课程ID）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '报告正文（Markdown）',
  `stats_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '统计数据JSON（用于图表）',
  `create_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type_ref`(`report_type` ASC, `ref_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1403 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI学情报告缓存' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_report
-- ----------------------------
INSERT INTO `ai_report` VALUES (1401, 'student', 301, '李豪 - AI学习周报（第15周）', '本周共完成 3 次作业、1 场考试，平均分 88.3，较上周提升 4.2 分。Java 集合框架专项练习正确率从 55% 提升至 82%，进步显著；多线程相关题目仍有 2 道重复出错，建议结合错题本中的「线程池参数」专题进行复盘。本周连续打卡 5 天，学习节奏稳定，继续保持。', '{\"homeworkCount\":3,\"examCount\":1,\"avgScore\":88.3,\"scoreDelta\":4.2,\"checkinDays\":5,\"weakPoints\":[\"线程池参数\",\"JVM内存模型\"]}', '2026-06-10 08:00:00');
INSERT INTO `ai_report` VALUES (1402, 'class', 201, '计科2101班 - AI学情周报（第15周）', '本周全班作业提交率 92%，平均分 81.6。「Java基础练习」中 final 关键字与集合遍历两类题目错误率最高（38%），建议下次课安排 15 分钟专项讲评。两名同学连续两次未按时提交作业，已自动发送提醒消息。班级错题本活跃度上升 25%，AI 智能练习使用人数达 14 人。', '{\"submitRate\":92,\"avgScore\":81.6,\"topErrors\":[\"final关键字语义\",\"集合遍历删除元素\"],\"lateStudents\":2,\"aiPracticeUsers\":14}', '2026-06-10 08:05:00');

-- ----------------------------
-- Table structure for attend_session
-- ----------------------------
DROP TABLE IF EXISTS `attend_session`;
CREATE TABLE `attend_session`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `courseid` int NOT NULL COMMENT '课程ID',
  `classid` int NOT NULL COMMENT '班级ID',
  `teacherid` int NOT NULL COMMENT '发起教师ID',
  `checkin_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '签到码(4-6位)',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签到标题(如第3周签到)',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '签到方式: 1=签到码, 2=一键签到',
  `late_minutes` int NOT NULL DEFAULT 5 COMMENT '迟到阈值(分钟)',
  `duration` int NOT NULL DEFAULT 10 COMMENT '签到持续时长(分钟)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 1=进行中, 2=已结束',
  `start_time` datetime NOT NULL COMMENT '签到开始时间',
  `end_time` datetime NOT NULL COMMENT '签到截止时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_class`(`courseid` ASC, `classid` ASC) USING BTREE,
  INDEX `idx_teacher`(`teacherid` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '签到考勤会话' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attend_session
-- ----------------------------
INSERT INTO `attend_session` VALUES (1, 601, 201, 401, '1234', 'test考情', 3, 5, 10, 2, '2026-06-02 15:35:40', '2026-06-02 15:45:40', '2026-06-02 15:35:40');
INSERT INTO `attend_session` VALUES (2, 601, 201, 401, '03678', 'test签到', 3, 5, 10, 2, '2026-06-02 15:52:09', '2026-06-02 16:02:09', '2026-06-02 15:52:09');
INSERT INTO `attend_session` VALUES (3, 607, 202, 401, '03678', 'dddd', 3, 5, 10, 2, '2026-06-02 16:05:13', '2026-06-02 16:15:13', '2026-06-02 16:05:13');
INSERT INTO `attend_session` VALUES (4, 601, 202, 401, '1234', 'sss', 1, 5, 10, 2, '2026-06-02 16:20:03', '2026-06-02 16:30:03', '2026-06-02 16:20:03');
INSERT INTO `attend_session` VALUES (5, 601, 201, 401, '1234', '第五周', 1, 5, 10, 2, '2026-06-02 16:41:26', '2026-06-02 16:51:26', '2026-06-02 16:41:26');

-- ----------------------------
-- Table structure for audit_log
-- ----------------------------
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户类型：admin/teacher/student/anon',
  `user_id` int NULL DEFAULT NULL COMMENT '用户ID',
  `user_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名称',
  `action` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '动作：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/EXPORT/GRADE/OTHER',
  `module` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模块名',
  `target` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作对象描述',
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'HTTP方法',
  `uri` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求URI',
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端IP',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '详情/参数摘要',
  `status` int NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
  `cost_ms` int NULL DEFAULT 0 COMMENT '耗时(ms)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_type` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_action`(`action` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 188 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作审计日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of audit_log
-- ----------------------------
INSERT INTO `audit_log` VALUES (1, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 135, '2026-06-02 14:27:38');
INSERT INTO `audit_log` VALUES (2, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 13, '2026-06-02 14:27:38');
INSERT INTO `audit_log` VALUES (3, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-02 14:29:14');
INSERT INTO `audit_log` VALUES (4, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 6, '2026-06-02 14:29:20');
INSERT INTO `audit_log` VALUES (5, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 9, '2026-06-02 14:29:20');
INSERT INTO `audit_log` VALUES (6, 'student', 301, '李豪', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 11, '2026-06-02 14:29:20');
INSERT INTO `audit_log` VALUES (7, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 4, '2026-06-02 15:07:26');
INSERT INTO `audit_log` VALUES (8, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 49, '2026-06-02 15:07:31');
INSERT INTO `audit_log` VALUES (9, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 12, '2026-06-02 15:07:32');
INSERT INTO `audit_log` VALUES (10, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 603, '2026-06-02 15:34:54');
INSERT INTO `audit_log` VALUES (11, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 33, '2026-06-02 15:34:54');
INSERT INTO `audit_log` VALUES (12, 'teacher', 401, '陈建国', 'CREATE', 'attend-session', '/attend-session/create', 'POST', '/HomeworkSmartCollection/attend-session/create', '127.0.0.1', 'teacherid=401', 1, 53, '2026-06-02 15:35:40');
INSERT INTO `audit_log` VALUES (13, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-02 15:35:45');
INSERT INTO `audit_log` VALUES (14, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 3, '2026-06-02 15:35:58');
INSERT INTO `audit_log` VALUES (15, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 2, '2026-06-02 15:35:58');
INSERT INTO `audit_log` VALUES (16, 'student', 301, '李豪', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 15, '2026-06-02 15:35:58');
INSERT INTO `audit_log` VALUES (17, 'student', 301, '李豪', 'CREATE', 'attend-session', '/attend-session/checkin', 'POST', '/HomeworkSmartCollection/attend-session/checkin', '127.0.0.1', NULL, 1, 21, '2026-06-02 15:36:34');
INSERT INTO `audit_log` VALUES (18, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-06-02 15:36:58');
INSERT INTO `audit_log` VALUES (19, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 0, '2026-06-02 15:37:04');
INSERT INTO `audit_log` VALUES (20, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 6, '2026-06-02 15:37:04');
INSERT INTO `audit_log` VALUES (21, 'teacher', 401, '陈建国', 'CREATE', 'attend-session', '/attend-session/close/1', 'POST', '/HomeworkSmartCollection/attend-session/close/1', '127.0.0.1', 'teacherid=401', 1, 30, '2026-06-02 15:37:11');
INSERT INTO `audit_log` VALUES (22, 'anon', NULL, NULL, 'CREATE', 'attend-session', '/attend-session/create', 'POST', '/HomeworkSmartCollection/attend-session/create', '127.0.0.1', 'teacherid=401', 1, 56, '2026-06-02 15:52:09');
INSERT INTO `audit_log` VALUES (23, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 0, '2026-06-02 15:52:11');
INSERT INTO `audit_log` VALUES (24, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 24, '2026-06-02 15:52:21');
INSERT INTO `audit_log` VALUES (25, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 11, '2026-06-02 15:52:21');
INSERT INTO `audit_log` VALUES (26, 'student', 301, '李豪', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 8, '2026-06-02 15:52:21');
INSERT INTO `audit_log` VALUES (27, 'student', 301, '李豪', 'CREATE', 'attend-session', '/attend-session/checkin', 'POST', '/HomeworkSmartCollection/attend-session/checkin', '127.0.0.1', NULL, 1, 13, '2026-06-02 15:52:46');
INSERT INTO `audit_log` VALUES (28, 'student', 301, '李豪', 'CREATE', 'attend-session', '/attend-session/checkin', 'POST', '/HomeworkSmartCollection/attend-session/checkin', '127.0.0.1', NULL, 1, 19, '2026-06-02 15:52:49');
INSERT INTO `audit_log` VALUES (29, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 5, '2026-06-02 15:55:26');
INSERT INTO `audit_log` VALUES (30, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 10, '2026-06-02 15:55:36');
INSERT INTO `audit_log` VALUES (31, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 3, '2026-06-02 15:55:36');
INSERT INTO `audit_log` VALUES (32, 'anon', NULL, NULL, 'CREATE', 'attend-session', '/attend-session/create', 'POST', '/HomeworkSmartCollection/attend-session/create', '127.0.0.1', 'teacherid=401', 1, 104, '2026-06-02 16:05:13');
INSERT INTO `audit_log` VALUES (33, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 2, '2026-06-02 16:08:09');
INSERT INTO `audit_log` VALUES (34, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 15, '2026-06-02 16:08:13');
INSERT INTO `audit_log` VALUES (35, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 4, '2026-06-02 16:08:13');
INSERT INTO `audit_log` VALUES (36, 'student', 301, '李豪', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 9, '2026-06-02 16:08:13');
INSERT INTO `audit_log` VALUES (37, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 165, '2026-06-02 16:19:43');
INSERT INTO `audit_log` VALUES (38, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 61, '2026-06-02 16:19:49');
INSERT INTO `audit_log` VALUES (39, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 19, '2026-06-02 16:19:49');
INSERT INTO `audit_log` VALUES (40, 'teacher', 401, '陈建国', 'CREATE', 'attend-session', '/attend-session/create', 'POST', '/HomeworkSmartCollection/attend-session/create', '127.0.0.1', 'teacherid=401', 1, 73, '2026-06-02 16:20:04');
INSERT INTO `audit_log` VALUES (41, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-02 16:20:20');
INSERT INTO `audit_log` VALUES (42, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 4, '2026-06-02 16:20:33');
INSERT INTO `audit_log` VALUES (43, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 3, '2026-06-02 16:20:33');
INSERT INTO `audit_log` VALUES (44, 'student', 310, '徐国栋', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 11, '2026-06-02 16:20:33');
INSERT INTO `audit_log` VALUES (45, 'student', 310, '徐国栋', 'CREATE', 'attend-session', '/attend-session/checkin', 'POST', '/HomeworkSmartCollection/attend-session/checkin', '127.0.0.1', NULL, 1, 20, '2026-06-02 16:20:40');
INSERT INTO `audit_log` VALUES (46, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-06-02 16:20:45');
INSERT INTO `audit_log` VALUES (47, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 4, '2026-06-02 16:20:49');
INSERT INTO `audit_log` VALUES (48, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 7, '2026-06-02 16:20:49');
INSERT INTO `audit_log` VALUES (49, 'teacher', 401, '陈建国', 'CREATE', 'attend-session', '/attend-session/proxy-checkin', 'POST', '/HomeworkSmartCollection/attend-session/proxy-checkin', '127.0.0.1', NULL, 1, 14, '2026-06-02 16:22:46');
INSERT INTO `audit_log` VALUES (50, 'anon', NULL, NULL, 'CREATE', 'attend-session', '/attend-session/create', 'POST', '/HomeworkSmartCollection/attend-session/create', '127.0.0.1', 'teacherid=401', 1, 80, '2026-06-02 16:41:26');
INSERT INTO `audit_log` VALUES (51, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 46, '2026-06-07 09:51:04');
INSERT INTO `audit_log` VALUES (52, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 13, '2026-06-07 09:51:04');
INSERT INTO `audit_log` VALUES (53, 'teacher', 401, '陈建国', 'CREATE', 'exam', '/exam/create', 'POST', '/HomeworkSmartCollection/exam/create', '127.0.0.1', NULL, 1, 47, '2026-06-07 10:02:13');
INSERT INTO `audit_log` VALUES (54, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-07 10:02:48');
INSERT INTO `audit_log` VALUES (55, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 13, '2026-06-07 10:02:57');
INSERT INTO `audit_log` VALUES (56, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 3, '2026-06-07 10:02:57');
INSERT INTO `audit_log` VALUES (57, 'anon', NULL, '(登录尝试)', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 9, '2026-06-07 10:02:57');
INSERT INTO `audit_log` VALUES (58, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 5, '2026-06-07 10:03:10');
INSERT INTO `audit_log` VALUES (59, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 7, '2026-06-07 10:03:10');
INSERT INTO `audit_log` VALUES (60, 'student', 310, '徐国栋', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 380, '2026-06-07 10:03:11');
INSERT INTO `audit_log` VALUES (61, 'student', 310, '徐国栋', 'CREATE', 'exam', '/exam/start/1', 'POST', '/HomeworkSmartCollection/exam/start/1', '127.0.0.1', NULL, 1, 30, '2026-06-07 10:03:14');
INSERT INTO `audit_log` VALUES (62, 'student', 310, '徐国栋', 'CREATE', 'exam', '/exam/save/1', 'POST', '/HomeworkSmartCollection/exam/save/1', '127.0.0.1', NULL, 1, 15, '2026-06-07 10:03:44');
INSERT INTO `audit_log` VALUES (63, 'student', 310, '徐国栋', 'CREATE', 'exam', '/exam/save/1', 'POST', '/HomeworkSmartCollection/exam/save/1', '127.0.0.1', NULL, 1, 5, '2026-06-07 10:04:15');
INSERT INTO `audit_log` VALUES (64, 'student', 310, '徐国栋', 'CREATE', 'exam', '/exam/start/1', 'POST', '/HomeworkSmartCollection/exam/start/1', '127.0.0.1', NULL, 1, 4, '2026-06-07 10:04:15');
INSERT INTO `audit_log` VALUES (65, 'student', 310, '徐国栋', 'CREATE', 'exam', '/exam/submit/1', 'POST', '/HomeworkSmartCollection/exam/submit/1', '127.0.0.1', NULL, 1, 537, '2026-06-07 10:04:42');
INSERT INTO `audit_log` VALUES (66, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 0, '2026-06-07 10:04:44');
INSERT INTO `audit_log` VALUES (67, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 9, '2026-06-07 10:04:51');
INSERT INTO `audit_log` VALUES (68, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 4, '2026-06-07 10:04:51');
INSERT INTO `audit_log` VALUES (69, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 44, '2026-06-07 10:22:07');
INSERT INTO `audit_log` VALUES (70, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 12, '2026-06-07 10:22:07');
INSERT INTO `audit_log` VALUES (71, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 30, '2026-06-07 10:31:18');
INSERT INTO `audit_log` VALUES (72, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 16, '2026-06-07 10:31:18');
INSERT INTO `audit_log` VALUES (73, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-07 10:36:53');
INSERT INTO `audit_log` VALUES (74, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 18, '2026-06-07 10:36:58');
INSERT INTO `audit_log` VALUES (75, 'anon', NULL, '(登录尝试)', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 3, '2026-06-07 10:36:58');
INSERT INTO `audit_log` VALUES (76, 'student', 310, '徐国栋', 'LOGIN', 'student', '/student/login', 'POST', '/HomeworkSmartCollection/student/login', '127.0.0.1', NULL, 1, 6, '2026-06-07 10:36:58');
INSERT INTO `audit_log` VALUES (77, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-06-07 10:37:08');
INSERT INTO `audit_log` VALUES (78, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 7, '2026-06-07 10:37:12');
INSERT INTO `audit_log` VALUES (79, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 3, '2026-06-07 10:37:12');
INSERT INTO `audit_log` VALUES (80, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 42, '2026-06-07 10:42:44');
INSERT INTO `audit_log` VALUES (81, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 15, '2026-06-07 10:42:44');
INSERT INTO `audit_log` VALUES (82, 'anon', NULL, '(登录尝试)', 'LOGIN', 'admin', '/admin/login', 'POST', '/HomeworkSmartCollection/admin/login', '127.0.0.1', NULL, 1, 34, '2026-06-07 11:33:45');
INSERT INTO `audit_log` VALUES (83, 'teacher', 401, '陈建国', 'LOGIN', 'teacher', '/teacher/login', 'POST', '/HomeworkSmartCollection/teacher/login', '127.0.0.1', NULL, 1, 16, '2026-06-07 11:33:45');
INSERT INTO `audit_log` VALUES (84, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-07 11:33:55');
INSERT INTO `audit_log` VALUES (85, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 24, '2026-06-08 10:42:10');
INSERT INTO `audit_log` VALUES (86, 'teacher', 401, '陈建国', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 174, '2026-06-08 10:42:15');
INSERT INTO `audit_log` VALUES (87, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 23, '2026-06-08 10:45:48');
INSERT INTO `audit_log` VALUES (88, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 1, '2026-06-08 10:49:31');
INSERT INTO `audit_log` VALUES (89, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 2, '2026-06-08 10:52:44');
INSERT INTO `audit_log` VALUES (90, 'teacher', 401, '陈建国', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 13, '2026-06-08 10:52:44');
INSERT INTO `audit_log` VALUES (91, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-08 11:01:40');
INSERT INTO `audit_log` VALUES (92, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 1, '2026-06-08 11:01:49');
INSERT INTO `audit_log` VALUES (93, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 7, '2026-06-08 11:01:49');
INSERT INTO `audit_log` VALUES (94, 'student', 301, '李豪', 'CREATE', 'gamification', '/gamification/checkin', 'POST', '/HomeworkSmartCollection/gamification/checkin', '127.0.0.1', NULL, 1, 98, '2026-06-08 11:03:00');
INSERT INTO `audit_log` VALUES (95, 'student', 301, '李豪', 'CREATE', 'noticeRead', '/noticeRead/homework', 'POST', '/HomeworkSmartCollection/noticeRead/homework', '127.0.0.1', NULL, 1, 31, '2026-06-08 11:04:48');
INSERT INTO `audit_log` VALUES (96, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-06-08 11:12:39');
INSERT INTO `audit_log` VALUES (97, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 25, '2026-06-08 11:37:51');
INSERT INTO `audit_log` VALUES (98, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 150, '2026-06-08 11:37:52');
INSERT INTO `audit_log` VALUES (99, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-06-08 11:39:23');
INSERT INTO `audit_log` VALUES (100, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 2, '2026-06-08 11:39:32');
INSERT INTO `audit_log` VALUES (101, 'teacher', 401, '陈建国', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 22, '2026-06-08 11:39:32');
INSERT INTO `audit_log` VALUES (102, 'teacher', 401, '陈建国', 'CREATE', 'ai', '/ai/generateQuestions', 'POST', '/HomeworkSmartCollection/ai/generateQuestions', '127.0.0.1', NULL, 1, 9634, '2026-06-08 11:40:22');
INSERT INTO `audit_log` VALUES (103, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 50, '2026-06-08 11:41:36');
INSERT INTO `audit_log` VALUES (104, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 15, '2026-06-08 11:42:11');
INSERT INTO `audit_log` VALUES (105, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 42, '2026-06-08 11:42:11');
INSERT INTO `audit_log` VALUES (106, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 16, '2026-06-11 11:27:35');
INSERT INTO `audit_log` VALUES (107, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 110, '2026-06-11 11:27:35');
INSERT INTO `audit_log` VALUES (108, 'student', 301, '李豪', 'CREATE', 'ai', '/ai/chat-stream', 'POST', '/HomeworkSmartCollection/ai/chat-stream', '127.0.0.1', NULL, 1, 7, '2026-06-11 11:31:42');
INSERT INTO `audit_log` VALUES (109, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 21, '2026-06-11 11:44:32');
INSERT INTO `audit_log` VALUES (110, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 128, '2026-06-11 11:44:32');
INSERT INTO `audit_log` VALUES (111, 'student', 301, '李豪', 'CREATE', 'agent', '/agent/explainer', 'POST', '/HomeworkSmartCollection/agent/explainer', '127.0.0.1', NULL, 1, 45631, '2026-06-11 11:45:31');
INSERT INTO `audit_log` VALUES (112, 'student', 301, '李豪', 'CREATE', 'ai', '/ai/chat-stream', 'POST', '/HomeworkSmartCollection/ai/chat-stream', '127.0.0.1', NULL, 1, 6, '2026-06-11 11:50:43');
INSERT INTO `audit_log` VALUES (113, 'student', 301, '李豪', 'CREATE', 'ai', '/ai/chat-stream', 'POST', '/HomeworkSmartCollection/ai/chat-stream', '127.0.0.1', NULL, 1, 0, '2026-06-11 11:53:51');
INSERT INTO `audit_log` VALUES (114, 'student', 301, '李豪', 'CREATE', 'ai', '/ai/chat-stream', 'POST', '/HomeworkSmartCollection/ai/chat-stream', '127.0.0.1', NULL, 1, 0, '2026-06-11 11:55:39');
INSERT INTO `audit_log` VALUES (115, 'student', 301, '李豪', 'UPDATE', 'message', '/message/readAll', 'PUT', '/HomeworkSmartCollection/message/readAll', '127.0.0.1', NULL, 1, 33, '2026-06-11 12:06:13');
INSERT INTO `audit_log` VALUES (116, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 19, '2026-06-11 12:33:21');
INSERT INTO `audit_log` VALUES (117, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 72, '2026-06-11 12:33:21');
INSERT INTO `audit_log` VALUES (118, 'anon', NULL, NULL, 'UPDATE', 'message', '/message/readAll', 'PUT', '/HomeworkSmartCollection/message/readAll', '0:0:0:0:0:0:0:1', NULL, 1, 6, '2026-06-11 12:36:21');
INSERT INTO `audit_log` VALUES (119, 'student', 301, '李豪', 'CREATE', 'ai-insight', '/ai-insight/smart-practice', 'POST', '/HomeworkSmartCollection/ai-insight/smart-practice', '127.0.0.1', NULL, 1, 10704, '2026-06-11 12:36:51');
INSERT INTO `audit_log` VALUES (120, 'student', 301, '李豪', 'CREATE', 'gamification', '/gamification/checkin', 'POST', '/HomeworkSmartCollection/gamification/checkin', '127.0.0.1', NULL, 1, 52, '2026-06-11 12:51:46');
INSERT INTO `audit_log` VALUES (121, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 2, '2026-06-11 12:52:04');
INSERT INTO `audit_log` VALUES (122, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 1, '2026-06-11 12:52:19');
INSERT INTO `audit_log` VALUES (123, 'teacher', 401, '陈建国', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 7, '2026-06-11 12:52:19');
INSERT INTO `audit_log` VALUES (124, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 13, '2026-06-11 13:03:22');
INSERT INTO `audit_log` VALUES (125, 'teacher', 401, '陈建国', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 7, '2026-06-11 13:03:22');
INSERT INTO `audit_log` VALUES (126, 'anon', NULL, NULL, 'LOGOUT', 'teacher', '/teacher/logout', 'POST', '/HomeworkSmartCollection/teacher/logout', '127.0.0.1', NULL, 1, 1, '2026-06-11 13:15:12');
INSERT INTO `audit_log` VALUES (127, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 1, '2026-06-11 13:15:19');
INSERT INTO `audit_log` VALUES (128, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 12, '2026-06-11 13:15:19');
INSERT INTO `audit_log` VALUES (129, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 13, '2026-06-11 13:21:47');
INSERT INTO `audit_log` VALUES (130, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 24, '2026-06-11 13:21:47');
INSERT INTO `audit_log` VALUES (131, 'student', 301, '李豪', 'CREATE', 'agent', '/agent/explainer', 'POST', '/HomeworkSmartCollection/agent/explainer', '127.0.0.1', NULL, 1, 63385, '2026-06-11 13:23:30');
INSERT INTO `audit_log` VALUES (132, 'anon', NULL, NULL, 'CREATE', 'enroll', '/enroll/apply', 'POST', '/HomeworkSmartCollection/enroll/apply', '127.0.0.1', NULL, 1, 59, '2026-06-11 13:39:19');
INSERT INTO `audit_log` VALUES (133, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 29, '2026-07-11 08:09:36');
INSERT INTO `audit_log` VALUES (134, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 71, '2026-07-11 08:09:36');
INSERT INTO `audit_log` VALUES (135, 'student', 301, '李豪', 'CREATE', 'noticeRead', '/noticeRead/homework', 'POST', '/HomeworkSmartCollection/noticeRead/homework', '127.0.0.1', NULL, 1, 22, '2026-07-11 08:09:54');
INSERT INTO `audit_log` VALUES (136, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 9, '2026-07-11 08:12:59');
INSERT INTO `audit_log` VALUES (137, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 9, '2026-07-11 08:12:59');
INSERT INTO `audit_log` VALUES (138, 'student', 301, '李豪', 'CREATE', 'noticeRead', '/noticeRead/homework', 'POST', '/HomeworkSmartCollection/noticeRead/homework', '127.0.0.1', NULL, 1, 4, '2026-07-11 08:13:11');
INSERT INTO `audit_log` VALUES (139, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 10, '2026-07-11 08:15:36');
INSERT INTO `audit_log` VALUES (140, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 15, '2026-07-11 08:15:36');
INSERT INTO `audit_log` VALUES (141, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 10, '2026-07-11 08:19:03');
INSERT INTO `audit_log` VALUES (142, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 11, '2026-07-11 08:19:03');
INSERT INTO `audit_log` VALUES (143, 'student', 301, '李豪', 'CREATE', 'noticeRead', '/noticeRead/homework', 'POST', '/HomeworkSmartCollection/noticeRead/homework', '127.0.0.1', NULL, 1, 5, '2026-07-11 08:19:07');
INSERT INTO `audit_log` VALUES (144, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 14, '2026-07-11 09:03:39');
INSERT INTO `audit_log` VALUES (145, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 147, '2026-07-11 09:03:39');
INSERT INTO `audit_log` VALUES (146, 'student', 301, '李豪', 'CREATE', 'agent', '/agent/explainer', 'POST', '/HomeworkSmartCollection/agent/explainer', '127.0.0.1', NULL, 1, 55561, '2026-07-11 09:04:52');
INSERT INTO `audit_log` VALUES (147, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 18, '2026-07-11 10:24:39');
INSERT INTO `audit_log` VALUES (148, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 428, '2026-07-11 10:24:39');
INSERT INTO `audit_log` VALUES (149, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 19, '2026-07-18 18:27:55');
INSERT INTO `audit_log` VALUES (150, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 470, '2026-07-18 18:27:55');
INSERT INTO `audit_log` VALUES (151, 'student', 301, '李豪', 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 2, '2026-07-18 18:28:06');
INSERT INTO `audit_log` VALUES (152, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 362, '2026-07-18 18:28:07');
INSERT INTO `audit_log` VALUES (153, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-07-18 18:29:56');
INSERT INTO `audit_log` VALUES (154, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 2, '2026-07-18 18:30:04');
INSERT INTO `audit_log` VALUES (155, 'admin', 1, 'admin', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 390, '2026-07-18 18:30:05');
INSERT INTO `audit_log` VALUES (156, 'anon', NULL, NULL, 'LOGOUT', 'admin', '/admin/logout', 'POST', '/HomeworkSmartCollection/admin/logout', '127.0.0.1', NULL, 1, 0, '2026-07-18 18:51:19');
INSERT INTO `audit_log` VALUES (157, 'anon', NULL, '(登录尝试)', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 70, '2026-07-18 19:40:44');
INSERT INTO `audit_log` VALUES (158, 'anon', NULL, NULL, 'CREATE', 'wechat', '/wechat/auth/bind', 'POST', '/HomeworkSmartCollection/wechat/auth/bind', '127.0.0.1', NULL, 1, 11, '2026-07-18 19:42:38');
INSERT INTO `audit_log` VALUES (159, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 75, '2026-07-18 19:42:38');
INSERT INTO `audit_log` VALUES (160, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 700, '2026-07-18 19:49:08');
INSERT INTO `audit_log` VALUES (161, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 221, '2026-07-18 19:49:27');
INSERT INTO `audit_log` VALUES (162, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 208, '2026-07-18 19:49:30');
INSERT INTO `audit_log` VALUES (163, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 339, '2026-07-18 19:53:53');
INSERT INTO `audit_log` VALUES (164, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 4, '2026-07-18 19:56:06');
INSERT INTO `audit_log` VALUES (165, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 367, '2026-07-18 19:56:07');
INSERT INTO `audit_log` VALUES (166, 'anon', NULL, NULL, 'LOGOUT', 'student', '/student/logout', 'POST', '/HomeworkSmartCollection/student/logout', '127.0.0.1', NULL, 1, 1, '2026-07-18 19:57:35');
INSERT INTO `audit_log` VALUES (167, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 377, '2026-07-18 19:57:38');
INSERT INTO `audit_log` VALUES (168, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 390, '2026-07-18 20:03:08');
INSERT INTO `audit_log` VALUES (169, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 1199, '2026-07-18 20:23:04');
INSERT INTO `audit_log` VALUES (170, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 162, '2026-07-18 20:23:05');
INSERT INTO `audit_log` VALUES (171, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 37, '2026-07-18 20:23:53');
INSERT INTO `audit_log` VALUES (172, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 1, '2026-07-18 20:23:58');
INSERT INTO `audit_log` VALUES (173, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 186, '2026-07-18 20:24:31');
INSERT INTO `audit_log` VALUES (174, 'anon', NULL, NULL, 'CREATE', 'captcha', '/captcha/verify', 'POST', '/HomeworkSmartCollection/captcha/verify', '127.0.0.1', NULL, 1, 6, '2026-07-18 20:24:36');
INSERT INTO `audit_log` VALUES (175, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 703, '2026-07-18 20:24:36');
INSERT INTO `audit_log` VALUES (176, 'student', 301, '李豪', 'LOGIN', 'auth', '/auth/login', 'POST', '/HomeworkSmartCollection/auth/login', '127.0.0.1', NULL, 1, 384, '2026-07-18 20:24:36');
INSERT INTO `audit_log` VALUES (177, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 1210, '2026-07-18 20:27:06');
INSERT INTO `audit_log` VALUES (178, 'student', 301, '李豪', 'CREATE', 'wechat', '/wechat/auth/bind', 'POST', '/HomeworkSmartCollection/wechat/auth/bind', '127.0.0.1', NULL, 1, 539, '2026-07-18 20:27:13');
INSERT INTO `audit_log` VALUES (179, 'anon', NULL, '(登录尝试)', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 201, '2026-07-18 20:27:20');
INSERT INTO `audit_log` VALUES (180, 'student', 301, '李豪', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 247, '2026-07-18 20:27:41');
INSERT INTO `audit_log` VALUES (181, 'student', 301, '李豪', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 737, '2026-07-18 20:29:22');
INSERT INTO `audit_log` VALUES (182, 'student', 301, '李豪', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 258, '2026-07-18 20:29:26');
INSERT INTO `audit_log` VALUES (183, 'student', 301, '李豪', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 694, '2026-07-18 20:38:08');
INSERT INTO `audit_log` VALUES (184, 'student', 301, '李豪', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 231, '2026-07-18 20:38:13');
INSERT INTO `audit_log` VALUES (185, 'student', 301, '李豪', 'LOGIN', 'wechat', '/wechat/auth/login', 'POST', '/HomeworkSmartCollection/wechat/auth/login', '127.0.0.1', NULL, 1, 723, '2026-07-18 20:43:18');
INSERT INTO `audit_log` VALUES (186, 'student', 301, '李豪', 'CREATE', 'ai', '/ai/chat', 'POST', '/HomeworkSmartCollection/ai/chat', '127.0.0.1', NULL, 1, 2329, '2026-07-18 20:43:43');
INSERT INTO `audit_log` VALUES (187, 'student', 301, '李豪', 'CREATE', 'ai', '/ai/chat', 'POST', '/HomeworkSmartCollection/ai/chat', '127.0.0.1', NULL, 1, 3753, '2026-07-18 20:43:53');

-- ----------------------------
-- Table structure for badge_def
-- ----------------------------
DROP TABLE IF EXISTS `badge_def`;
CREATE TABLE `badge_def`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '徽章ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '徽章编码',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '徽章名称',
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '获得说明',
  `icon` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `condition_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '条件类型：POINTS/HOMEWORK/EXAM/CHECKIN/MASTER',
  `threshold` int NULL DEFAULT 0 COMMENT '达成阈值',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '徽章定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of badge_def
-- ----------------------------
INSERT INTO `badge_def` VALUES (1, 'FIRST_SUBMIT', '初露锋芒', '完成第一次作业提交', 'ph:medal-duotone', 'HOMEWORK', 1);
INSERT INTO `badge_def` VALUES (2, 'HOMEWORK_10', '勤学不辍', '累计提交10次作业', 'ph:books-duotone', 'HOMEWORK', 10);
INSERT INTO `badge_def` VALUES (3, 'POINTS_100', '积分新星', '累计积分达到100', 'ph:star-duotone', 'POINTS', 100);
INSERT INTO `badge_def` VALUES (4, 'POINTS_500', '积分达人', '累计积分达到500', 'ph:sparkle-duotone', 'POINTS', 500);
INSERT INTO `badge_def` VALUES (5, 'CHECKIN_7', '坚持一周', '连续打卡7天', 'ph:fire-duotone', 'CHECKIN', 7);
INSERT INTO `badge_def` VALUES (6, 'CHECKIN_30', '月度学霸', '连续打卡30天', 'ph:crown-duotone', 'CHECKIN', 30);
INSERT INTO `badge_def` VALUES (7, 'EXAM_FULL', '满分王者', '单场考试获得满分', 'ph:trophy-duotone', 'EXAM', 100);
INSERT INTO `badge_def` VALUES (8, 'MASTER_20', '纠错能手', '错题本掌握20题', 'ph:target-duotone', 'MASTER', 20);

-- ----------------------------
-- Table structure for classinfo
-- ----------------------------
DROP TABLE IF EXISTS `classinfo`;
CREATE TABLE `classinfo`  (
  `classid` int NOT NULL AUTO_INCREMENT COMMENT '班级ID',
  `classname` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '班级名称',
  `majorid` int NOT NULL COMMENT '所属专业ID',
  PRIMARY KEY (`classid`) USING BTREE,
  INDEX `idx_majorid`(`majorid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 219 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '班级表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of classinfo
-- ----------------------------
INSERT INTO `classinfo` VALUES (201, '计科2101班', 101);
INSERT INTO `classinfo` VALUES (202, '计科2102班', 101);
INSERT INTO `classinfo` VALUES (203, '软工2101班', 102);
INSERT INTO `classinfo` VALUES (204, '软工2102班', 102);
INSERT INTO `classinfo` VALUES (205, '信安2101班', 103);
INSERT INTO `classinfo` VALUES (206, '人工智能2101班', 104);
INSERT INTO `classinfo` VALUES (207, '大数据2101班', 105);
INSERT INTO `classinfo` VALUES (208, '大数据2102班', 105);
INSERT INTO `classinfo` VALUES (209, '信安2102班', 103);
INSERT INTO `classinfo` VALUES (210, '人工智能2102班', 104);
INSERT INTO `classinfo` VALUES (211, '网络2101班', 106);
INSERT INTO `classinfo` VALUES (212, '网络2102班', 106);
INSERT INTO `classinfo` VALUES (213, '物联网2101班', 107);
INSERT INTO `classinfo` VALUES (214, '数媒2101班', 108);
INSERT INTO `classinfo` VALUES (215, '智科2101班', 109);
INSERT INTO `classinfo` VALUES (216, '电信2101班', 110);
INSERT INTO `classinfo` VALUES (217, '计科2201班', 101);
INSERT INTO `classinfo` VALUES (218, '软工2201班', 102);

-- ----------------------------
-- Table structure for course
-- ----------------------------
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course`  (
  `courseid` int NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `coursename` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '课程名称',
  `teacherid` int NOT NULL COMMENT '任课教师ID',
  `hours` int NULL DEFAULT 0 COMMENT '课时',
  `description` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '课程描述',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `coursecode` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '课程码',
  `semester` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '学期',
  PRIMARY KEY (`courseid`) USING BTREE,
  UNIQUE INDEX `uk_coursecode`(`coursecode` ASC) USING BTREE,
  INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE,
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacherid`) REFERENCES `teacher` (`teacherid`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 691 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '课程表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of course
-- ----------------------------
INSERT INTO `course` VALUES (601, 'Java EE企业级应用开发教程', 401, 64, 'Java语言基础、面向对象编程、集合框架、IO流、多线程等', '2025-12-23 08:00:00', '67325573', '2025-2026-2');
INSERT INTO `course` VALUES (602, '数据结构与算法', 407, 48, '线性表、栈、队列、树、图、排序算法、查找算法等', '2025-12-23 08:00:00', '11742029', '2025-2026-2');
INSERT INTO `course` VALUES (603, '数据库原理', 402, 48, '关系数据库、SQL语言、数据库设计、事务处理等', '2025-12-23 08:00:00', '38241616', '2025-2026-1');
INSERT INTO `course` VALUES (604, '软件工程', 403, 32, '软件开发过程、需求分析、系统设计、测试与维护等', '2025-12-23 08:00:00', '51349706', '2025-2026-2');
INSERT INTO `course` VALUES (605, 'Web前端开发', 404, 48, 'HTML、CSS、JavaScript、Vue.js框架等', '2025-12-23 08:00:00', '55786961', '2025-2026-2');
INSERT INTO `course` VALUES (606, '网络安全基础', 405, 32, '网络安全概述、加密技术、防火墙、入侵检测等', '2025-12-23 08:00:00', '49815530', '2025-2026-2');
INSERT INTO `course` VALUES (607, 'Python程序设计', 401, 32, '学习Python', '2026-01-05 12:29:26', '65833286', '2025-2026-2');
INSERT INTO `course` VALUES (608, '数据结构-2022', 401, 48, 'History Data 2022', '2026-01-05 13:11:29', 'DS2022', '2022-2023-1');
INSERT INTO `course` VALUES (609, '算法导论-2023', 401, 48, 'History Data 2023', '2026-01-05 13:11:29', 'ALGO2023', '2023-2024-1');
INSERT INTO `course` VALUES (610, '操作系统-2024', 401, 48, 'History Data 2024', '2026-01-05 13:11:29', 'OS2024', '2024-2025-1');
INSERT INTO `course` VALUES (611, 'AI大模型-2026', 401, 48, 'Future Plan 2026', '2026-01-05 13:11:29', 'AI2026', '2026-2027-1');
INSERT INTO `course` VALUES (659, 'C语言程序设计', 401, 64, '2022-2023学年第一学期课程', '2026-01-05 13:33:51', '22231001', '2022-2023-1');
INSERT INTO `course` VALUES (660, '计算机导论', 401, 32, '2022-2023学年第一学期课程', '2026-01-05 13:33:51', '22231002', '2022-2023-1');
INSERT INTO `course` VALUES (661, 'Java程序设计基础', 401, 48, '2022-2023学年第二学期课程', '2026-01-05 13:33:51', '22232001', '2022-2023-2');
INSERT INTO `course` VALUES (662, '离散数学', 401, 48, '2022-2023学年第二学期课程', '2026-01-05 13:33:51', '22232002', '2022-2023-2');
INSERT INTO `course` VALUES (663, '数据结构', 401, 64, '2023-2024学年第一学期课程', '2026-01-05 13:33:51', '23241001', '2023-2024-1');
INSERT INTO `course` VALUES (664, '计算机组成原理', 408, 48, '2023-2024学年第一学期课程', '2026-01-05 13:33:51', '23241002', '2023-2024-1');
INSERT INTO `course` VALUES (665, '数据库系统概论', 401, 48, '2023-2024学年第二学期课程', '2026-01-05 13:33:51', '23242001', '2023-2024-2');
INSERT INTO `course` VALUES (666, '操作系统', 401, 64, '2023-2024学年第二学期课程', '2026-01-05 13:33:51', '23242002', '2023-2024-2');
INSERT INTO `course` VALUES (667, '计算机网络', 401, 48, '2024-2025学年第一学期课程', '2026-01-05 13:33:51', '24251001', '2024-2025-1');
INSERT INTO `course` VALUES (668, '软件工程', 401, 48, '2024-2025学年第一学期课程', '2026-01-05 13:33:51', '24251002', '2024-2025-1');
INSERT INTO `course` VALUES (669, 'Web前端开发', 401, 64, '2024-2025学年第二学期课程', '2026-01-05 13:33:51', '24252001', '2024-2025-2');
INSERT INTO `course` VALUES (670, 'Python数据分析', 401, 48, '2024-2025学年第二学期课程', '2026-01-05 13:33:51', '24252002', '2024-2025-2');
INSERT INTO `course` VALUES (671, '机器学习', 405, 48, '2025-2026学年第二学期课程', '2026-01-05 13:33:51', '25262001', '2025-2026-1');
INSERT INTO `course` VALUES (672, '深度学习基础', 401, 48, '2025-2026学年第二学期课程', '2026-01-05 13:33:51', '25262002', '2025-2026-1');
INSERT INTO `course` VALUES (673, '大数据处理技术', 401, 64, '2026-2027学年第一学期课程', '2026-01-05 13:33:51', '26271001', '2026-2027-1');
INSERT INTO `course` VALUES (674, '云计算技术', 401, 48, '2026-2027学年第一学期课程', '2026-01-05 13:33:51', '26271002', '2026-2027-1');
INSERT INTO `course` VALUES (675, '区块链导论', 401, 32, '2026-2027学年第二学期课程', '2026-01-05 13:33:51', '26272001', '2026-2027-2');
INSERT INTO `course` VALUES (676, '智能合约开发', 401, 48, '2026-2027学年第二学期课程', '2026-01-05 13:33:51', '26272002', '2026-2027-2');
INSERT INTO `course` VALUES (677, 'python程序设计', 406, 32, '1.掌握Python语法：理解变量、数据类型、控制结构、函数、面向对象等核心概念\n2.具备编程思维：学会用计算思维分析问题，设计算法并编写程序解决实际问题\n3.应用实践能力：熟练使用Python进行文件处理、数据分析、网络爬虫或简单Web开发\n4.了解生态体系：熟悉常用标准库和第三方库（如NumPy、Pandas、Matplotlib等）\n三、教学内容与学时分配', '2026-01-31 22:33:26', '42509182', '2025-2026-2');
INSERT INTO `course` VALUES (678, '计算机组成原理', 408, 80, '课程性质：专业核心必修课（大类基础课）\n学时学分：72-80学时（理论56+实验16-24），4.5学分\n先修课程：数字逻辑、程序设计基础\n后续课程：操作系统、计算机体系结构、嵌入式系统、编译原理\n课程地位：考研统考408科目核心组成，工程教育认证关键支撑课', '2026-01-31 22:42:01', '03644699', '2025-2026-2');
INSERT INTO `course` VALUES (679, '操作系统', 409, 96, '课程性质：专业核心必修课（系统性能力培养核心）\n学时学分：80-96学时（理论48-64+实验32-48），5-6学分\n先修课程：计算机组成原理、程序设计（C语言）、数据结构\n后续课程：分布式系统、编译原理、嵌入式系统、云计算与虚拟化技术\n课程地位：考研统考408分值最高科目（35分），工程教育认证核心支撑课', '2026-01-31 22:44:19', '68868715', '2025-2026-2');
INSERT INTO `course` VALUES (680, '单片机原理及接口技术课程设计', 405, 32, '本课程设计以单片机（微控制器MCU）为核心，立足单片机系统的设计、开发与调试全流程，兼顾理论深度与实践可行性，适配本科及高职高专相关专业的人才培养目标，强调“学用结合、知行合一”。课程设计依托51系列单片机（如AT89C51、STC89C52等）为核心教学载体，覆盖单片机基本结构、指令系统、接口技术、程序开发及系统集成等核心内容，打破理论课堂的抽象化局限，让学生在实际项目中理解单片机的工作机制与应用逻辑，逐步掌握嵌入式系统开发的基本流程与工程方法。', '2026-01-31 22:46:26', '66173474', '2025-2026-2');
INSERT INTO `course` VALUES (681, '移动应用开发', 404, 32, '本课程紧扣移动互联网行业发展趋势，兼顾理论系统性与实践实用性，适配本科及高职高专相关专业的人才培养目标，强调“学以致用、知行合一”。课程以主流移动应用开发技术（iOS/Android/跨平台开发）为核心，聚焦移动应用全流程开发，覆盖移动应用基础理论、开发环境搭建、界面设计、业务逻辑实现、数据交互、测试优化及发布部署等核心内容，打破传统编程教学的抽象局限，让学生在案例演练与项目实战中理解移动应用的开发逻辑与运行机制，逐步掌握符合行业规范的移动应用开发流程与技术规范。', '2026-01-31 22:48:13', '84856329', '2025-2026-2');
INSERT INTO `course` VALUES (682, 'test', 407, 32, 'dddd', '2026-02-01 16:36:14', '38736566', '2025-2026-1');
INSERT INTO `course` VALUES (683, 'test', 407, 32, 'esfdf', '2026-02-01 16:36:29', '03353298', '2025-2026-1');
INSERT INTO `course` VALUES (684, '网络爬虫技术', 410, 32, '   《网络爬虫技术》包含的主要内容为：初识爬虫、爬虫的实现原理和技术、网页请求原理、网页内容抓取、数据解析、抓取动态内容、存储爬虫数据、初识爬虫框架Scrapy、Scrapy终端与和核心组件。结合网络爬虫的基本流程，对教材进行了二次开发，主要知识点为60个左右。', '2026-02-09 12:49:59', '87881685', '2025-2026-2');
INSERT INTO `course` VALUES (685, '计算机网络原理', 411, 64, '系统讲解 OSI 七层模型、TCP/IP 协议栈、路由与交换技术。', '2026-02-25 09:00:00', 'NET2101', '2025-2026-2');
INSERT INTO `course` VALUES (686, '物联网技术导论', 412, 48, '物联网体系结构、传感器技术、嵌入式开发与边缘计算入门。', '2026-02-25 09:05:00', 'IOT2101', '2025-2026-2');
INSERT INTO `course` VALUES (687, '数字图像处理', 413, 48, '图像增强、边缘检测、形态学处理与 OpenCV 实践。', '2026-02-25 09:10:00', 'DIP2101', '2025-2026-2');
INSERT INTO `course` VALUES (688, '自然语言处理', 414, 56, '分词、词向量、文本分类到大语言模型基础原理。', '2026-02-25 09:15:00', 'NLP2101', '2025-2026-2');
INSERT INTO `course` VALUES (689, '信号与系统', 415, 64, '连续与离散信号分析、傅里叶变换、拉普拉斯变换。', '2026-02-25 09:20:00', 'SIG2101', '2025-2026-2');
INSERT INTO `course` VALUES (690, '编译原理', 416, 56, '词法分析、语法分析、语义分析与代码生成。', '2026-02-25 09:25:00', 'CPL2101', '2025-2026-2');

-- ----------------------------
-- Table structure for course_evaluation
-- ----------------------------
DROP TABLE IF EXISTS `course_evaluation`;
CREATE TABLE `course_evaluation`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `courseid` int NOT NULL COMMENT '课程ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `teachingscore` int NULL DEFAULT 0 COMMENT '教学态度评分（1-5）',
  `contentscore` int NULL DEFAULT 0 COMMENT '内容质量评分（1-5）',
  `difficultyscore` int NULL DEFAULT 0 COMMENT '难度适中评分（1-5）',
  `overallscore` int NULL DEFAULT 0 COMMENT '综合评分（1-5）',
  `comment` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '文字意见',
  `anonymous` int NULL DEFAULT 1 COMMENT '是否匿名：1是 0否',
  `createtime` datetime NULL DEFAULT NULL COMMENT '评价时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_student`(`courseid` ASC, `studentid` ASC) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1905 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '课程评价表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of course_evaluation
-- ----------------------------
INSERT INTO `course_evaluation` VALUES (1, 601, 301, 5, 5, 4, 5, '陈老师讲课非常认真负责，课程内容丰富实用，通过这门课学到了很多企业级开发的知识。Spring框架的讲解特别清晰，实践项目也很有帮助。', 0, '2026-01-13 11:47:35');
INSERT INTO `course_evaluation` VALUES (2, 602, 301, 4, 5, 5, 5, '数据结构课程内容充实，算法讲解详细。老师上课节奏适中，课后习题很有针对性。建议增加更多的动画演示，帮助理解复杂算法。', 1, '2026-01-15 11:47:35');
INSERT INTO `course_evaluation` VALUES (3, 603, 301, 5, 4, 4, 4, '数据库课程理论与实践结合得很好，SQL语法讲解清晰。实验环节设计合理，通过实际操作加深了对数据库的理解。', 0, '2026-01-16 11:47:35');
INSERT INTO `course_evaluation` VALUES (4, 601, 302, 5, 5, 4, 5, '课程质量很高，老师经验丰富，讲解深入浅出。项目实战环节让我们真正掌握了Java EE开发技能。', 1, '2026-01-14 11:47:35');
INSERT INTO `course_evaluation` VALUES (5, 602, 302, 4, 4, 5, 4, '数据结构是计算机专业的核心课程，老师讲得很好。课程难度适中，通过学习提升了编程能力和算法思维。', 1, '2026-01-12 11:47:35');
INSERT INTO `course_evaluation` VALUES (6, 603, 303, 5, 5, 3, 5, '王老师的数据库课程非常棒！从基础到高级，循序渐进。特别喜欢数据库设计部分的讲解，对实际项目很有帮助。', 0, '2026-01-11 11:47:35');
INSERT INTO `course_evaluation` VALUES (7, 674, 301, 5, 5, 5, 5, '老师教的非常好', 1, '2026-01-18 18:25:37');
INSERT INTO `course_evaluation` VALUES (8, 604, 303, 5, 5, 5, 4, '讲课还可以讲课还可以讲课还可以讲课还可以', 1, '2026-01-18 18:40:21');
INSERT INTO `course_evaluation` VALUES (9, 604, 301, 3, 5, 5, 5, '还行吧', 1, '2026-01-26 13:20:53');
INSERT INTO `course_evaluation` VALUES (1901, 602, 313, 5, 5, 4, 5, '郑老师讲解数据结构深入浅出，配套练习设计得很好。', 0, '2026-06-09 20:00:00');
INSERT INTO `course_evaluation` VALUES (1902, 602, 314, 4, 5, 4, 4, '课程内容充实，希望增加一些算法可视化演示。', 1, '2026-06-09 21:30:00');
INSERT INTO `course_evaluation` VALUES (1903, 607, 315, 5, 4, 5, 5, 'Python 课实践环节多，AI 智能练习对复习帮助很大。', 0, '2026-06-10 19:00:00');
INSERT INTO `course_evaluation` VALUES (1904, 607, 316, 4, 4, 4, 4, '作业难度适中，建议提供更多课后拓展资料。', 1, '2026-06-10 20:15:00');

-- ----------------------------
-- Table structure for course_knowledge_chunk
-- ----------------------------
DROP TABLE IF EXISTS `course_knowledge_chunk`;
CREATE TABLE `course_knowledge_chunk`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` int NOT NULL,
  `resource_id` int NOT NULL,
  `chunk_index` int NOT NULL,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `content_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `embedding_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_resource_chunk`(`resource_id` ASC, `chunk_index` ASC) USING BTREE,
  INDEX `idx_course`(`course_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程知识向量切片' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of course_knowledge_chunk
-- ----------------------------

-- ----------------------------
-- Table structure for course_resource
-- ----------------------------
DROP TABLE IF EXISTS `course_resource`;
CREATE TABLE `course_resource`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '资源ID',
  `courseid` int NOT NULL COMMENT '课程ID',
  `teacherid` int NOT NULL COMMENT '上传教师ID',
  `title` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '资源标题',
  `description` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '资源说明',
  `filetype` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件类型（PPT/PDF/CODE/OTHER）',
  `filepath` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件路径',
  `filename` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '原始文件名',
  `filesize` bigint NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `downloadcount` int NULL DEFAULT 0 COMMENT '下载次数',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
  INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE,
  CONSTRAINT `fk_resource_course` FOREIGN KEY (`courseid`) REFERENCES `course` (`courseid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '课程资源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of course_resource
-- ----------------------------
INSERT INTO `course_resource` VALUES (1, 601, 401, 'Java EE课程大纲', 'Java EE企业级应用开发课程教学大纲，包含课程目标、教学内容、考核方式等', 'PDF', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'java_ee_syllabus.pdf', 2048000, 15, '2025-12-19 11:47:35');
INSERT INTO `course_resource` VALUES (2, 601, 401, 'Spring框架核心概念PPT', 'Spring框架核心概念讲解，包括IoC容器、依赖注入、AOP等内容', 'PPT', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'spring_framework.pptx', 5120000, 23, '2025-12-24 11:47:35');
INSERT INTO `course_resource` VALUES (3, 601, 401, 'MVC设计模式示例代码', 'MVC设计模式完整示例项目，包含Model、View、Controller三层结构', 'CODE', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'mvc_demo.zip', 1024000, 8, '2025-12-29 11:47:35');
INSERT INTO `course_resource` VALUES (4, 601, 401, 'Hibernate ORM框架教程', 'Hibernate对象关系映射框架详细教程，包含配置和使用示例', 'PDF', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'hibernate_tutorial.pdf', 3584000, 12, '2026-01-03 11:47:35');
INSERT INTO `course_resource` VALUES (5, 602, 407, '数据结构课件-第一章', '数据结构与算法课程PPT第一章：绪论', 'PPT', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'data_structure_ch1.pptx', 3072000, 18, '2025-12-21 11:47:35');
INSERT INTO `course_resource` VALUES (6, 602, 407, '数据结构课件-第二章', '数据结构与算法课程PPT第二章：线性表', 'PPT', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'data_structure_ch2.pptx', 4096000, 16, '2025-12-28 11:47:35');
INSERT INTO `course_resource` VALUES (7, 602, 407, '排序算法动画演示', '常见排序算法的动画演示文件，帮助理解算法过程', 'OTHER', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'sort_animation.html', 512000, 26, '2026-01-04 11:47:35');
INSERT INTO `course_resource` VALUES (9, 603, 402, '数据库设计规范文档', '数据库设计规范和最佳实践，包含范式理论和实际案例', 'PDF', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'db_design_guide.pdf', 2560000, 15, '2025-12-27 11:47:35');
INSERT INTO `course_resource` VALUES (10, 603, 402, 'MySQL优化技巧', 'MySQL数据库性能优化技巧和索引使用指南', 'PDF', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', 'mysql_optimization.pdf', 1843200, 11, '2026-01-08 11:47:35');
INSERT INTO `course_resource` VALUES (13, 603, 402, 'SQL语法参考手册', 'SQL语法完整参考文档，包含DDL、DML、DCL等所有语句', 'PDF', 'uploads/resources/6755ddd8-2d60-4b4d-bf44-48087459927d.doc', '新建 DOC 文档.doc', 9216, 0, '2026-01-18 18:33:21');

-- ----------------------------
-- Table structure for courseclass
-- ----------------------------
DROP TABLE IF EXISTS `courseclass`;
CREATE TABLE `courseclass`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `courseid` int NOT NULL COMMENT '课程ID',
  `classid` int NOT NULL COMMENT '班级ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_courseclass`(`courseid` ASC, `classid` ASC) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
  INDEX `idx_classid`(`classid` ASC) USING BTREE,
  CONSTRAINT `fk_courseclass_class` FOREIGN KEY (`classid`) REFERENCES `classinfo` (`classid`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_courseclass_course` FOREIGN KEY (`courseid`) REFERENCES `course` (`courseid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 841 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '课程班级关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of courseclass
-- ----------------------------
INSERT INTO `courseclass` VALUES (724, 601, 201);
INSERT INTO `courseclass` VALUES (725, 601, 202);
INSERT INTO `courseclass` VALUES (726, 601, 203);
INSERT INTO `courseclass` VALUES (727, 601, 204);
INSERT INTO `courseclass` VALUES (728, 601, 206);
INSERT INTO `courseclass` VALUES (836, 601, 217);
INSERT INTO `courseclass` VALUES (703, 602, 201);
INSERT INTO `courseclass` VALUES (704, 602, 202);
INSERT INTO `courseclass` VALUES (837, 602, 217);
INSERT INTO `courseclass` VALUES (838, 602, 218);
INSERT INTO `courseclass` VALUES (816, 603, 201);
INSERT INTO `courseclass` VALUES (818, 603, 202);
INSERT INTO `courseclass` VALUES (817, 603, 203);
INSERT INTO `courseclass` VALUES (719, 604, 201);
INSERT INTO `courseclass` VALUES (718, 604, 202);
INSERT INTO `courseclass` VALUES (716, 604, 203);
INSERT INTO `courseclass` VALUES (717, 604, 204);
INSERT INTO `courseclass` VALUES (709, 605, 203);
INSERT INTO `courseclass` VALUES (710, 605, 204);
INSERT INTO `courseclass` VALUES (711, 606, 205);
INSERT INTO `courseclass` VALUES (799, 607, 202);
INSERT INTO `courseclass` VALUES (729, 607, 203);
INSERT INTO `courseclass` VALUES (840, 607, 211);
INSERT INTO `courseclass` VALUES (839, 607, 218);
INSERT INTO `courseclass` VALUES (730, 608, 201);
INSERT INTO `courseclass` VALUES (731, 609, 201);
INSERT INTO `courseclass` VALUES (732, 610, 201);
INSERT INTO `courseclass` VALUES (733, 611, 201);
INSERT INTO `courseclass` VALUES (780, 659, 201);
INSERT INTO `courseclass` VALUES (781, 660, 201);
INSERT INTO `courseclass` VALUES (782, 661, 201);
INSERT INTO `courseclass` VALUES (783, 662, 201);
INSERT INTO `courseclass` VALUES (784, 663, 201);
INSERT INTO `courseclass` VALUES (785, 664, 201);
INSERT INTO `courseclass` VALUES (786, 665, 201);
INSERT INTO `courseclass` VALUES (787, 666, 201);
INSERT INTO `courseclass` VALUES (788, 667, 201);
INSERT INTO `courseclass` VALUES (789, 668, 201);
INSERT INTO `courseclass` VALUES (790, 669, 201);
INSERT INTO `courseclass` VALUES (791, 670, 201);
INSERT INTO `courseclass` VALUES (792, 671, 201);
INSERT INTO `courseclass` VALUES (793, 672, 201);
INSERT INTO `courseclass` VALUES (794, 673, 201);
INSERT INTO `courseclass` VALUES (798, 673, 202);
INSERT INTO `courseclass` VALUES (795, 674, 201);
INSERT INTO `courseclass` VALUES (796, 675, 201);
INSERT INTO `courseclass` VALUES (797, 676, 201);
INSERT INTO `courseclass` VALUES (801, 677, 201);
INSERT INTO `courseclass` VALUES (802, 677, 203);
INSERT INTO `courseclass` VALUES (803, 678, 201);
INSERT INTO `courseclass` VALUES (805, 678, 203);
INSERT INTO `courseclass` VALUES (804, 678, 204);
INSERT INTO `courseclass` VALUES (806, 679, 201);
INSERT INTO `courseclass` VALUES (808, 679, 202);
INSERT INTO `courseclass` VALUES (807, 679, 203);
INSERT INTO `courseclass` VALUES (809, 680, 201);
INSERT INTO `courseclass` VALUES (810, 680, 203);
INSERT INTO `courseclass` VALUES (811, 680, 206);
INSERT INTO `courseclass` VALUES (812, 681, 201);
INSERT INTO `courseclass` VALUES (813, 681, 202);
INSERT INTO `courseclass` VALUES (814, 681, 203);
INSERT INTO `courseclass` VALUES (815, 681, 204);
INSERT INTO `courseclass` VALUES (819, 682, 201);
INSERT INTO `courseclass` VALUES (820, 682, 203);
INSERT INTO `courseclass` VALUES (821, 683, 202);
INSERT INTO `courseclass` VALUES (823, 684, 207);
INSERT INTO `courseclass` VALUES (824, 684, 208);
INSERT INTO `courseclass` VALUES (825, 685, 211);
INSERT INTO `courseclass` VALUES (826, 685, 212);
INSERT INTO `courseclass` VALUES (827, 686, 213);
INSERT INTO `courseclass` VALUES (828, 687, 214);
INSERT INTO `courseclass` VALUES (829, 688, 206);
INSERT INTO `courseclass` VALUES (830, 688, 210);
INSERT INTO `courseclass` VALUES (831, 688, 215);
INSERT INTO `courseclass` VALUES (832, 689, 216);
INSERT INTO `courseclass` VALUES (834, 690, 201);
INSERT INTO `courseclass` VALUES (835, 690, 202);
INSERT INTO `courseclass` VALUES (833, 690, 217);

-- ----------------------------
-- Table structure for enroll_application
-- ----------------------------
DROP TABLE IF EXISTS `enroll_application`;
CREATE TABLE `enroll_application`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `school` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `contact_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `contact_role` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `remark` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` int NULL DEFAULT 0,
  `create_time` datetime NULL DEFAULT NULL,
  `attachment` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `attachment_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1505 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '高校入驻申请' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of enroll_application
-- ----------------------------
INSERT INTO `enroll_application` VALUES (1501, '山东科技职业学院', '王建军', '教务处主任', '13700003001', '希望在软件技术专业 6 个班级试点使用智能作业批改功能。', 1, '2026-06-02 10:00:00', NULL, NULL);
INSERT INTO `enroll_application` VALUES (1502, '杭州电子信息职业学校', '李雪芹', '信息中心主任', '13700003002', '关注多智能体学习资源生成能力，期望先安排线上演示。', 0, '2026-06-08 15:30:00', NULL, NULL);
INSERT INTO `enroll_application` VALUES (1503, '武汉软件工程职业学院', '张鹏飞', '计算机系副主任', '13700003003', '计划下学期整系引入，需要了解私有化部署方案与报价。', 0, '2026-06-10 11:20:00', NULL, NULL);
INSERT INTO `enroll_application` VALUES (1504, '重庆大学', '李豪', '教师', '15320321640', '合作', 0, '2026-06-11 13:39:19', NULL, NULL);

-- ----------------------------
-- Table structure for exam
-- ----------------------------
DROP TABLE IF EXISTS `exam`;
CREATE TABLE `exam`  (
  `examid` int NOT NULL AUTO_INCREMENT COMMENT '考试ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '考试标题',
  `courseid` int NOT NULL COMMENT '课程ID',
  `teacherid` int NOT NULL COMMENT '创建教师ID',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '考试说明',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '试题JSON（与作业题目同构）',
  `duration` int NULL DEFAULT 60 COMMENT '答题时长(分钟)',
  `total_score` int NULL DEFAULT 100 COMMENT '总分',
  `starttime` datetime NULL DEFAULT NULL COMMENT '开考时间',
  `endtime` datetime NULL DEFAULT NULL COMMENT '截止时间',
  `status` int NULL DEFAULT 1 COMMENT '状态：0草稿 1已发布 2已结束',
  `anti_cheat` int NULL DEFAULT 1 COMMENT '是否启用防作弊：1是 0否',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`examid`) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
  INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE,
  CONSTRAINT `fk_exam_course` FOREIGN KEY (`courseid`) REFERENCES `course` (`courseid`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 853 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '在线考试表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of exam
-- ----------------------------
INSERT INTO `exam` VALUES (1, 'dddd', 601, 401, 'dddd', '{\"questions\":[{\"id\":\"q1780797715629921\",\"type\":\"single\",\"title\":\"test\",\"score\":5,\"options\":[\"ss\",\"ssed\",\"ds\",\"sd\"],\"answer\":3,\"gradingPolicy\":\"normal\"}]}', 60, 5, '2026-06-07 10:01:47', '2026-06-25 00:00:00', 1, 1, '2026-06-07 10:02:13');
INSERT INTO `exam` VALUES (851, 'Java EE 期中测试', 601, 401, '覆盖 Java 基础语法、面向对象与集合框架，请独立完成。', '{\"questions\":[{\"id\":\"e1q1\",\"type\":\"single\",\"title\":\"下列关于 Java 接口的说法正确的是？\",\"score\":25,\"options\":[\"接口中可以定义构造方法\",\"一个类只能实现一个接口\",\"接口中的方法默认是 public abstract\",\"接口不能继承其他接口\"],\"answer\":2},{\"id\":\"e1q2\",\"type\":\"single\",\"title\":\"HashMap 在 JDK8 中，链表转红黑树的默认阈值是？\",\"score\":25,\"options\":[\"6\",\"8\",\"16\",\"32\"],\"answer\":1},{\"id\":\"e1q3\",\"type\":\"judge\",\"title\":\"String 是不可变类，每次拼接都会产生新对象。\",\"score\":25,\"options\":[],\"answer\":true},{\"id\":\"e1q4\",\"type\":\"judge\",\"title\":\"被 static 修饰的方法可以直接访问本类的实例变量。\",\"score\":25,\"options\":[],\"answer\":false}]}', 90, 100, '2026-06-15 09:00:00', '2026-06-15 23:59:59', 1, 1, '2026-06-08 10:00:00');
INSERT INTO `exam` VALUES (852, 'Python 程序设计期末考试', 607, 401, '覆盖基础语法、函数、文件操作与面向对象，开启防切屏。', '{\"questions\":[{\"id\":\"e2q1\",\"type\":\"single\",\"title\":\"下列哪个是 Python 中合法的列表推导式？\",\"score\":25,\"options\":[\"[x for x in range(10)]\",\"(x for x in range(10)]\",\"{x : range(10)}\",\"list(x in range(10))\"],\"answer\":0},{\"id\":\"e2q2\",\"type\":\"single\",\"title\":\"open(\\\"a.txt\\\",\\\"a\\\") 中模式 a 的含义是？\",\"score\":25,\"options\":[\"只读\",\"覆盖写入\",\"追加写入\",\"二进制读\"],\"answer\":2},{\"id\":\"e2q3\",\"type\":\"judge\",\"title\":\"Python 中的元组（tuple）是可变序列。\",\"score\":25,\"options\":[],\"answer\":false},{\"id\":\"e2q4\",\"type\":\"judge\",\"title\":\"装饰器本质上是一个接收函数并返回函数的可调用对象。\",\"score\":25,\"options\":[],\"answer\":true}]}', 120, 100, '2026-06-28 09:00:00', '2026-06-28 23:59:59', 1, 1, '2026-06-10 14:00:00');

-- ----------------------------
-- Table structure for exam_record
-- ----------------------------
DROP TABLE IF EXISTS `exam_record`;
CREATE TABLE `exam_record`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `examid` int NOT NULL COMMENT '考试ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `answers` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '学生作答JSON',
  `score` decimal(6, 1) NULL DEFAULT NULL COMMENT '总得分',
  `objective_score` decimal(6, 1) NULL DEFAULT NULL COMMENT '客观题得分',
  `status` int NULL DEFAULT 0 COMMENT '状态：0进行中 1已交卷 2已批改',
  `starttime` datetime NULL DEFAULT NULL COMMENT '开始作答时间',
  `submittime` datetime NULL DEFAULT NULL COMMENT '交卷时间',
  `used_seconds` int NULL DEFAULT 0 COMMENT '用时(秒)',
  `switch_count` int NULL DEFAULT 0 COMMENT '切屏/失焦次数(防作弊)',
  `auto_submit` int NULL DEFAULT 0 COMMENT '是否超时自动交卷：1是',
  `ai_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI评语',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_exam_student`(`examid` ASC, `studentid` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
  CONSTRAINT `fk_exam_record_exam` FOREIGN KEY (`examid`) REFERENCES `exam` (`examid`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_exam_record_student` FOREIGN KEY (`studentid`) REFERENCES `student` (`studentid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 957 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '考试作答记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of exam_record
-- ----------------------------
INSERT INTO `exam_record` VALUES (1, 1, 310, '{\"q1780797715629921\":2}', 0.0, 0.0, 2, '2026-06-07 10:03:14', '2026-06-07 10:04:41', 85, 4, 0, '客观题：答对 0/1，得分 0');
INSERT INTO `exam_record` VALUES (951, 851, 301, '{\"e1q1\":2,\"e1q2\":1,\"e1q3\":true,\"e1q4\":false}', 100.0, 100.0, 2, '2026-06-15 09:02:00', '2026-06-15 09:48:00', 2760, 0, 0, '四道题全部答对，Java 基础概念掌握非常扎实，建议继续深入学习 JVM 与并发编程。');
INSERT INTO `exam_record` VALUES (952, 851, 302, '{\"e1q1\":2,\"e1q2\":3,\"e1q3\":true,\"e1q4\":false}', 75.0, 75.0, 2, '2026-06-15 09:05:00', '2026-06-15 10:12:00', 4020, 1, 0, '集合框架细节（HashMap 树化阈值）需要加强，建议复习源码层面的常量定义。');
INSERT INTO `exam_record` VALUES (953, 851, 312, '{\"e1q1\":0,\"e1q2\":1,\"e1q3\":true,\"e1q4\":true}', 50.0, 50.0, 2, '2026-06-15 09:10:00', '2026-06-15 10:35:00', 5100, 2, 0, '接口与 static 语义理解有偏差，建议重做教材第 3、5 章课后练习。');
INSERT INTO `exam_record` VALUES (954, 851, 333, '{\"e1q1\":2,\"e1q2\":1,\"e1q3\":true,\"e1q4\":false}', 100.0, 100.0, 2, '2026-06-15 09:00:30', '2026-06-15 09:40:00', 2370, 0, 0, '满分通过，知识面完整，可尝试参加 ACM 校内选拔。');
INSERT INTO `exam_record` VALUES (955, 852, 304, '{\"e2q1\":0,\"e2q2\":2,\"e2q3\":false,\"e2q4\":true}', 100.0, 100.0, 2, '2026-06-28 09:01:00', '2026-06-28 10:02:00', 3660, 0, 0, 'Python 基础全面过关，文件操作与装饰器概念清晰。');
INSERT INTO `exam_record` VALUES (956, 852, 313, '{\"e2q1\":0,\"e2q2\":1,\"e2q3\":false,\"e2q4\":true}', 75.0, 75.0, 2, '2026-06-28 09:03:00', '2026-06-28 10:30:00', 5220, 0, 0, '文件打开模式记忆混淆（w 与 a），建议动手实验各模式差异。');

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
INSERT INTO `flyway_schema_history` VALUES (1, '0', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'root', '2026-07-18 17:49:01', 0, 1);
INSERT INTO `flyway_schema_history` VALUES (2, '1', 'EnterpriseSchemaBaseline', 'JDBC', 'db.migration.V1__EnterpriseSchemaBaseline', NULL, 'root', '2026-07-18 17:49:04', 2619, 1);
INSERT INTO `flyway_schema_history` VALUES (3, '2', 'LearningPlanProgressColumn', 'JDBC', 'db.migration.V2__LearningPlanProgressColumn', NULL, 'root', '2026-07-18 17:54:58', 17, 1);
INSERT INTO `flyway_schema_history` VALUES (4, '3', 'RepairUtf8SeedData', 'JDBC', 'db.migration.V3__RepairUtf8SeedData', NULL, 'root', '2026-07-18 18:46:52', 44, 1);
INSERT INTO `flyway_schema_history` VALUES (5, '4', 'create student wechat binding', 'SQL', 'V4__create_student_wechat_binding.sql', -1608763952, 'root', '2026-07-18 19:36:06', 79, 1);

-- ----------------------------
-- Table structure for homework
-- ----------------------------
DROP TABLE IF EXISTS `homework`;
CREATE TABLE `homework`  (
  `homeworkid` int NOT NULL AUTO_INCREMENT COMMENT '作业ID',
  `title` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '作业标题',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '作业内容/要求',
  `courseid` int NOT NULL COMMENT '所属课程ID',
  `teacherid` int NOT NULL COMMENT '发布教师ID',
  `type` int NULL DEFAULT 1 COMMENT '作业类型：1在线答题 2文档上传',
  `deadline` datetime NULL DEFAULT NULL COMMENT '截止时间',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `attachment` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '附件路径（Word/PDF文件）',
  PRIMARY KEY (`homeworkid`) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
  INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE,
  CONSTRAINT `fk_homework_course` FOREIGN KEY (`courseid`) REFERENCES `course` (`courseid`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_homework_teacher` FOREIGN KEY (`teacherid`) REFERENCES `teacher` (`teacherid`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 833 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '作业表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of homework
-- ----------------------------
INSERT INTO `homework` VALUES (801, 'Java基础练习', '<p>请完成以下Java基础练习题：</p><p>1. 编写一个程序，输出1到100之间的所有素数</p><p>2. 实现一个简单的计算器类</p>', 601, 401, 1, '2026-06-15 23:59:59', '2025-12-23 10:00:00', NULL);
INSERT INTO `homework` VALUES (802, '面向对象编程实验报告', '<p>请提交面向对象编程实验报告（Word文档）：</p><p>1. 设计一个学生管理系统的类图</p><p>2. 实现核心类并测试</p>', 601, 401, 2, '2026-06-20 23:59:59', '2025-12-23 10:00:00', NULL);
INSERT INTO `homework` VALUES (804, 'SQL查询练习', '<p>请完成以下SQL查询练习：</p><p>1. 编写多表连接查询</p><p>2. 编写子查询和聚合函数查询</p>', 603, 402, 1, '2026-06-10 23:59:59', '2025-12-23 10:00:00', NULL);
INSERT INTO `homework` VALUES (805, '需求分析文档', '<p>请提交软件需求分析文档（Word文档）：</p><p>选择一个实际项目，完成需求分析</p>', 604, 403, 2, '2026-06-25 23:59:59', '2025-12-23 10:00:00', NULL);
INSERT INTO `homework` VALUES (806, 'Vue组件开发', '<p>请完成Vue组件开发练习：</p><p>1. 创建一个可复用的表格组件</p><p>2. 实现父子组件通信</p>', 605, 404, 1, '2026-05-20 23:59:59', '2025-12-23 10:00:00', NULL);
INSERT INTO `homework` VALUES (809, '第20周作业', '{\"version\":1,\"questions\":[{\"type\":\"judge\",\"title\":\"在 Java 中，final关键字修饰的变量只能被赋值一次，且赋值后不可修改。\",\"score\":5,\"options\":[],\"answer\":true,\"id\":\"q_1766841465392_9wfbsb\"},{\"type\":\"judge\",\"title\":\"Java 中的ArrayList底层是基于链表实现的，而LinkedList底层是基于数组实现的。\",\"score\":5,\"options\":[],\"answer\":false,\"id\":\"q_1766841465392_bwub5m\"},{\"type\":\"single\",\"title\":\"以下哪个关键字用于定义 Java 中的抽象方法？\",\"score\":5,\"options\":[\"static\",\"final\",\"abstract\",\"interface\"],\"answer\":2,\"id\":\"q_1766841465392_o1s7ff\"}]}', 601, 401, 1, '2026-06-22 00:00:00', '2025-12-27 21:17:54', NULL);
INSERT INTO `homework` VALUES (810, '第7课时作业', '{\"version\":1,\"questions\":[{\"type\":\"single\",\"title\":\"在Java中，以下哪个关键字用来声明一个变量？\",\"options\":[\"int\",\"float\",\"double\",\"var\"],\"answer\":3,\"score\":5,\"id\":\"q_1766937464172_dvnkv4\"},{\"type\":\"single\",\"title\":\"以下哪个方法可以用来获取字符串的长度？\",\"options\":[\"length()\",\"size()\",\"getLength()\",\"length\"],\"answer\":0,\"score\":5,\"id\":\"q_1766937464172_x2ijck\"},{\"type\":\"multi\",\"title\":\"在Java中，以下哪些是基本数据类型？\",\"options\":[\"int\",\"String\",\"boolean\",\"Object\"],\"answer\":[0,2],\"score\":5,\"id\":\"q_1766937464172_kacisg\"},{\"type\":\"multi\",\"title\":\"以下哪些是Java中的关键字？\",\"options\":[\"class\",\"main\",\"true\",\"integer\"],\"answer\":[0],\"score\":5,\"id\":\"q_1766937464172_3uaoml\"},{\"type\":\"judge\",\"title\":\"Java中的所有类都继承自Object类。\",\"answer\":true,\"score\":5,\"id\":\"q_1766937464172_m4wn2u\"},{\"type\":\"judge\",\"title\":\"Java中的String是不可变的。\",\"answer\":true,\"score\":5,\"id\":\"q_1766937464172_bpzde2\"},{\"type\":\"short\",\"title\":\"简述Java中的面向对象编程的基本概念。\",\"options\":[],\"answer\":null,\"score\":5,\"id\":\"q_1766937464172_6mepv4\"}]}', 601, 401, 1, '2026-06-12 00:00:00', '2025-12-28 23:57:50', NULL);
INSERT INTO `homework` VALUES (811, 'Final Project 2022', 'Please submit 2022 final project', 608, 401, 1, '2022-06-21 13:11:29', '2026-01-05 13:11:29', NULL);
INSERT INTO `homework` VALUES (812, 'Algorithm Exercises 2023', 'Please complete exercises', 609, 401, 1, '2023-09-29 13:11:29', '2026-01-05 13:11:29', NULL);
INSERT INTO `homework` VALUES (813, 'Lab Report 2024', 'Please submit lab report', 610, 401, 1, '2024-09-26 13:11:29', '2026-01-05 13:11:29', NULL);
INSERT INTO `homework` VALUES (814, 'Pre-study 2026', 'Read chapter 1', 611, 401, 1, '2026-06-30 13:11:29', '2026-01-05 13:11:29', NULL);
INSERT INTO `homework` VALUES (815, 'C语言基础练习', '请完成课后习题1-5', 659, 401, 2, '2026-06-15 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `homework` VALUES (816, 'Java OOP练习', '设计一个学生类', 661, 401, 2, '2026-06-20 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `homework` VALUES (817, '链表实现', '实现单链表的逆置', 663, 401, 2, '2026-06-15 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `homework` VALUES (818, 'TCP/IP协议分析', '分析三次握手过程', 667, 401, 2, '2026-06-10 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `homework` VALUES (819, 'Python第一次作业', '{\"version\":1,\"questions\":[{\"type\":\"blank\",\"title\":\"在Python中，如何定义一个列表？\",\"options\":[],\"answer\":\"a) list = [1, 2, 3]\",\"score\":5,\"id\":\"q_1767595570574_1oha8a\",\"gradingPolicy\":\"normal\"},{\"type\":\"blank\",\"title\":\"以下哪个是Python中的条件语句？\",\"options\":[],\"answer\":\"a) if\",\"score\":5,\"id\":\"q_1767595570574_bfyw12\",\"gradingPolicy\":\"normal\"},{\"type\":\"single\",\"title\":\"在Python中，如何遍历一个字典的键值对？\",\"options\":[\"a) for key in dict:\",\"b) for key, value in dict.items():\",\"c) for key, value in dict:\",\"d) for key in dict.items()\"],\"answer\":1,\"score\":5,\"id\":\"q_1767595570574_umaqrh\"},{\"type\":\"multi\",\"title\":\"以下哪些是Python中的数据类型？\",\"options\":[\"a) int\",\"b) float\",\"c) string\",\"d) list\",\"e) dict\",\"f) bool\"],\"answer\":[0,1,2,3,4,5],\"score\":5,\"id\":\"q_1767595570574_rpcfwq\"},{\"type\":\"multi\",\"title\":\"在Python中，以下哪些操作符用于比较？\",\"options\":[\"a) ==\",\"b) !=\",\"c) >\",\"d) <\",\"e) >=\",\"f) <=\"],\"answer\":[0,1,2,3,4,5],\"score\":5,\"id\":\"q_1767595570574_ig9zpu\"},{\"type\":\"judge\",\"title\":\"在Python中，字符串是不可变的。\",\"answer\":true,\"score\":5,\"id\":\"q_1767595570574_ocx277\"},{\"type\":\"judge\",\"title\":\"Python中的列表是可变的数据类型。\",\"answer\":true,\"score\":5,\"id\":\"q_1767595570574_ygzzz5\"}]}', 607, 401, 1, '2026-05-30 00:00:00', '2026-01-05 14:46:14', NULL);
INSERT INTO `homework` VALUES (821, '数据结构第二章练习题', '{\"version\":1,\"questions\":[{\"type\":\"blank\",\"title\":\"在数据库中，数据结构是指什么？\",\"options\":[],\"answer\":\"数据的逻辑结构\",\"score\":5,\"id\":\"q_1768988430519_7e7otg\",\"gradingPolicy\":\"normal\"},{\"type\":\"blank\",\"title\":\"以下哪个不是数据库中常用的数据结构？\",\"options\":[],\"answer\":\"数组\",\"score\":5,\"id\":\"q_1768988430520_whuunl\",\"gradingPolicy\":\"normal\"},{\"type\":\"single\",\"title\":\"在数据库中，索引的作用是什么？\",\"options\":[\"存储数据\",\"提高数据检索速度\",\"保证数据的完整性\",\"数据备份\"],\"answer\":1,\"score\":5,\"id\":\"q_1768988430520_ol46p3\"},{\"type\":\"multi\",\"title\":\"以下哪些是数据库中常用的数据结构？\",\"options\":[\"数组\",\"树\",\"图\",\"链表\",\"集合\"],\"answer\":[0,1,2,3],\"score\":5,\"id\":\"q_1768988430520_jmt748\"},{\"type\":\"multi\",\"title\":\"以下哪些操作可能会破坏数据库的完整性？\",\"options\":[\"删除数据\",\"插入数据\",\"更新数据\",\"查询数据\"],\"answer\":[0,1,2],\"score\":5,\"id\":\"q_1768988430520_yb5s8j\"},{\"type\":\"judge\",\"title\":\"数据库中的数据结构只能是一维的。\",\"answer\":false,\"score\":5,\"id\":\"q_1768988430520_ug6kuh\"},{\"type\":\"judge\",\"title\":\"在数据库中，数据结构的设计对数据库的性能没有影响。\",\"answer\":false,\"score\":5,\"id\":\"q_1768988430520_18pc67\"}]}', 665, 401, 1, '2026-06-18 00:00:00', '2026-01-21 17:40:35', NULL);
INSERT INTO `homework` VALUES (822, '移动应用开发第一次作业', '{\"version\":1,\"questions\":[{\"type\":\"blank\",\"title\":\"在移动应用开发中，以下哪个不是常用的UI布局方式？\",\"options\":[],\"answer\":\"RecyclerView\",\"score\":5,\"id\":\"q_1769871076377_d0t4ce\",\"gradingPolicy\":\"normal\"},{\"type\":\"blank\",\"title\":\"以下哪个不是Android开发中的四大组件？\",\"options\":[],\"answer\":\"SQLiteOpenHelper\",\"score\":5,\"id\":\"q_1769871076377_6reh7z\",\"gradingPolicy\":\"normal\"},{\"type\":\"single\",\"title\":\"在iOS开发中，用来处理用户交互事件的主要类是？\",\"options\":[\"UIView\",\"UILabel\",\"UIButton\",\"UITextField\"],\"answer\":2,\"score\":5,\"id\":\"q_1769871076377_64a9r4\"},{\"type\":\"multi\",\"title\":\"以下哪些是Android开发中常用的数据存储方式？\",\"options\":[\"SharedPreferences\",\"SQLite\",\"Room\",\"Network\"],\"answer\":[0,1,2],\"score\":5,\"id\":\"q_1769871076377_co9z4k\"},{\"type\":\"multi\",\"title\":\"在移动应用开发中，以下哪些技术用于提高应用性能？\",\"options\":[\"代码优化\",\"懒加载\",\"缓存机制\",\"多线程\"],\"answer\":[0,2,3],\"score\":5,\"id\":\"q_1769871076377_z6li6e\"},{\"type\":\"judge\",\"title\":\"在Android开发中，Activity的生命周期方法onCreate()总是在onStart()之前调用。\",\"answer\":true,\"score\":5,\"id\":\"q_1769871076377_28t70e\"},{\"type\":\"judge\",\"title\":\"iOS应用中的每个视图都可以直接响应触摸事件。\",\"answer\":false,\"score\":5,\"id\":\"q_1769871076377_q3kjbh\"}]}', 681, 404, 1, '2026-05-20 00:00:00', '2026-01-31 22:51:58', NULL);
INSERT INTO `homework` VALUES (824, '两数之和', '<h4 style=\"text-align: start;\">一、两数之和</h4><p style=\"text-align: start;\"><strong>题目描述</strong><br> 给定一个整数数组 <span style=\"color: rgb(10, 191, 91); background-color: rgb(243, 245, 249);\"><code>nums</code></span> 和一个目标值 <span style=\"color: rgb(10, 191, 91); background-color: rgb(243, 245, 249);\"><code>target</code></span>，请你在该数组中找出 <strong>和为目标值</strong> 的那 <strong>两个</strong> 整数，并返回它们的数组下标。假设每种输入只对应一个答案。</p><p style=\"text-align: start;\"><strong>思路概要</strong><br> 使用哈希表在一次遍历中完成查找：遍历时记录当前数值及其下标，查找 <span style=\"color: rgb(10, 191, 91); background-color: rgb(243, 245, 249);\"><code>target - nums[i]</code></span> 是否存在。</p>', 602, 407, 2, '2026-06-20 00:00:00', '2026-02-01 14:45:09', NULL);
INSERT INTO `homework` VALUES (826, '初识数据结构', '{\"version\":1,\"questions\":[{\"type\":\"single\",\"title\":\"以下哪个是线性表的一种？\",\"options\":[\"栈\",\"树\",\"队列\",\"散列表\"],\"answer\":0,\"score\":5,\"id\":\"q_1770609916719_b1tg4l\"},{\"type\":\"single\",\"title\":\"在链表中，删除一个节点需要做以下哪项操作？\",\"options\":[\"查找节点的前一个节点\",\"查找节点本身\",\"查找节点的后一个节点\",\"查找节点的所有后代\"],\"answer\":0,\"score\":5,\"id\":\"q_1770609916719_wljacu\"},{\"type\":\"single\",\"title\":\"以下哪个数据结构是非线性的？\",\"options\":[\"数组\",\"链表\",\"树\",\"哈希表\"],\"answer\":2,\"score\":5,\"id\":\"q_1770609916719_0kdv6s\"},{\"type\":\"multi\",\"title\":\"以下哪些是二叉树的特点？\",\"options\":[\"每个节点最多有两个子节点\",\"左子树和右子树的节点顺序固定\",\"可以递归地定义\",\"所有节点的度数都是2\"],\"answer\":[0,1,2],\"score\":5,\"id\":\"q_1770609916719_s15klf\"},{\"type\":\"multi\",\"title\":\"以下哪些是队列操作？\",\"options\":[\"入队\",\"出队\",\"遍历\",\"删除\"],\"answer\":[0,1],\"score\":5,\"id\":\"q_1770609916719_qcs292\"},{\"type\":\"judge\",\"title\":\"在链表中，插入一个节点的时间复杂度是O(n)。\",\"answer\":true,\"score\":5,\"id\":\"q_1770609916719_fvlhtf\"},{\"type\":\"judge\",\"title\":\"栈是一种特殊的线性表，其插入和删除操作都在一端进行。\",\"answer\":true,\"score\":5,\"id\":\"q_1770609916719_dxgnw6\"},{\"type\":\"blank\",\"title\":\"数组是一种____数据结构，它通过数组的下标直接访问元素。\",\"answer\":\"顺序\",\"score\":5,\"id\":\"q_1770609916719_j8g03f\",\"gradingPolicy\":\"normal\",\"options\":[]},{\"type\":\"blank\",\"title\":\"在链表中，查找一个节点的时间复杂度是____。\",\"answer\":\"O(n)\",\"score\":5,\"id\":\"q_1770609916719_45dxyg\",\"gradingPolicy\":\"normal\",\"options\":[]}]}', 602, 407, 1, '2026-06-15 00:00:00', '2026-02-09 12:05:20', '');
INSERT INTO `homework` VALUES (827, '第一章-认识网络爬虫', '{\"version\":1,\"questions\":[{\"type\":\"single\",\"title\":\"以下哪个不属于网络爬虫的常见类型？\",\"options\":[\"网页爬虫\",\"数据库爬虫\",\"文件爬虫\",\"邮件爬虫\"],\"answer\":3,\"score\":5,\"id\":\"q_1770613166197_3ln3ba\"},{\"type\":\"single\",\"title\":\"网络爬虫的主要目的是什么？\",\"options\":[\"收集邮件\",\"下载图片\",\"获取信息\",\"发送广告\"],\"answer\":2,\"score\":5,\"id\":\"q_1770613166197_dmhk3m\"},{\"type\":\"single\",\"title\":\"以下哪种协议是网络爬虫常用的协议？\",\"options\":[\"HTTP\",\"FTP\",\"SMTP\",\"POP3\"],\"answer\":0,\"score\":5,\"id\":\"q_1770613166197_lp73my\"},{\"type\":\"multi\",\"title\":\"网络爬虫遵循的原则包括哪些？\",\"options\":[\"尊重robots.txt文件\",\"避免过度抓取\",\"遵循网站服务条款\",\"使用代理IP提高抓取效率\"],\"answer\":[0,1,2],\"score\":5,\"id\":\"q_1770613166197_3etopn\"},{\"type\":\"multi\",\"title\":\"网络爬虫常见的错误处理包括哪些？\",\"options\":[\"处理网络连接异常\",\"处理页面解析错误\",\"处理数据存储异常\",\"处理用户输入错误\"],\"answer\":[0,1,2],\"score\":5,\"id\":\"q_1770613166197_jf7bo8\"},{\"type\":\"judge\",\"title\":\"网络爬虫只能抓取公开的网页。\",\"answer\":true,\"score\":5,\"id\":\"q_1770613166197_u3uf5x\"},{\"type\":\"judge\",\"title\":\"网络爬虫在抓取数据时不需要考虑数据版权问题。\",\"answer\":false,\"score\":5,\"id\":\"q_1770613166197_k93phw\"},{\"type\":\"blank\",\"title\":\"网络爬虫中的robots.txt文件主要用于_______。\",\"answer\":\"定义网站允许或禁止爬虫访问的路径\",\"score\":5,\"id\":\"q_1770613166197_895yh3\",\"gradingPolicy\":\"normal\",\"options\":[]},{\"type\":\"blank\",\"title\":\"网络爬虫中，常用的页面解析库有_______和_______。\",\"answer\":\"BeautifulSoup, lxml\",\"score\":5,\"id\":\"q_1770613166197_nbktmy\",\"gradingPolicy\":\"normal\",\"options\":[]}]}', 684, 410, 1, '2026-06-10 00:00:00', '2026-02-09 13:00:04', '');
INSERT INTO `homework` VALUES (828, '顺序表', '<p>1.已知长度为 n 的有序递增数组，请设计一个尽可能高效的算法，使数组中只包含不重复的元素，返回处理后数组的长度。例如，数组 {1, 3, 3, 6, 8, 10, 10, 11} 变成 {1, 3, 6, 8, 10, 11}。要求：<br></p><p>给出算法的基本设计思想。<br></p><p>采用 C/C++ 语言描述算法，关键之处给出注释。<br></p><p>说明你所设计算法的时间复杂度和空间复杂度。</p>', 602, 407, 2, '2026-06-20 00:00:00', '2026-02-09 18:44:25', '2026/02/20260209_5cf419d106ec49709ba90e3e1413ca6c.doc');
INSERT INTO `homework` VALUES (829, 'IP地址规划与子网划分', '{\"version\":1,\"questions\":[{\"type\":\"single\",\"title\":\"将网络 192.168.10.0/24 划分为 4 个等大小的子网，每个子网的可用主机数是多少？\",\"score\":25,\"options\":[\"62\",\"64\",\"126\",\"30\"],\"answer\":0,\"id\":\"q_net_001\"},{\"type\":\"single\",\"title\":\"下列哪个地址属于私有 IP 地址段？\",\"score\":25,\"options\":[\"8.8.8.8\",\"172.20.1.5\",\"11.0.0.1\",\"223.5.5.5\"],\"answer\":1,\"id\":\"q_net_002\"},{\"type\":\"judge\",\"title\":\"子网掩码 255.255.255.192 对应的 CIDR 前缀长度为 /26。\",\"score\":25,\"options\":[],\"answer\":true,\"id\":\"q_net_003\"},{\"type\":\"judge\",\"title\":\"TCP 三次握手的目的是协商加密算法。\",\"score\":25,\"options\":[],\"answer\":false,\"id\":\"q_net_004\"}]}', 685, 411, 1, '2026-06-20 23:59:59', '2026-06-06 09:00:00', NULL);
INSERT INTO `homework` VALUES (830, '词向量与文本分类实验', '<p>实验要求：</p><ol><li>使用 jieba 对给定语料进行分词与停用词过滤；</li><li>分别用 TF-IDF 与 Word2Vec 构建文本特征；</li><li>训练朴素贝叶斯与逻辑回归分类器并对比准确率；</li><li>提交实验报告（PDF）与完整代码。</li></ol>', 688, 414, 2, '2026-06-25 23:59:59', '2026-06-07 10:00:00', NULL);
INSERT INTO `homework` VALUES (831, '词法分析器设计', '<p>使用 C/C++ 或 Java 实现一个简化版词法分析器：</p><ul><li>支持识别标识符、关键字、整数、运算符与界符；</li><li>输出 Token 序列与符号表；</li><li>附测试用例与运行截图。</li></ul>', 690, 416, 2, '2026-06-28 23:59:59', '2026-06-08 11:00:00', NULL);
INSERT INTO `homework` VALUES (832, '传感器数据采集实验', '<p>基于 ESP32 + DHT11 完成温湿度数据采集，并通过 MQTT 协议上报至云平台，提交：</p><ol><li>硬件连线图；</li><li>固件源码；</li><li>云端数据截图与实验小结。</li></ol>', 686, 412, 2, '2026-06-22 23:59:59', '2026-06-06 15:00:00', NULL);

-- ----------------------------
-- Table structure for homework_comment
-- ----------------------------
DROP TABLE IF EXISTS `homework_comment`;
CREATE TABLE `homework_comment`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `homeworkid` int NOT NULL COMMENT '作业ID',
  `studentid` int NULL DEFAULT NULL COMMENT '学生ID（学生提问时）',
  `teacherid` int NULL DEFAULT NULL COMMENT '教师ID（教师回复时）',
  `parentid` int NULL DEFAULT 0 COMMENT '父评论ID（0表示顶级评论）',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '评论内容',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_homeworkid`(`homeworkid` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
  INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE,
  INDEX `idx_parentid`(`parentid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '作业评论表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of homework_comment
-- ----------------------------
INSERT INTO `homework_comment` VALUES (1, 801, 301, NULL, 0, '老师您好，第3题的多态概念我不太理解，能否详细解释一下？', '2026-01-15 11:47:35');
INSERT INTO `homework_comment` VALUES (2, 801, NULL, 401, 1, '多态是面向对象编程的重要特性，简单来说就是同一个方法调用可以有不同的执行结果。建议你复习一下继承和方法重写的内容，多态是建立在这两个概念之上的。', '2026-01-16 11:47:35');
INSERT INTO `homework_comment` VALUES (3, 801, 301, NULL, 1, '谢谢老师！我明白了，多态确实需要结合继承来理解。我会再仔细看看教材。', '2026-01-17 11:47:35');
INSERT INTO `homework_comment` VALUES (4, 801, 302, NULL, 0, '请问这次作业的提交格式有什么要求吗？需要注意什么？', '2026-01-14 11:47:35');
INSERT INTO `homework_comment` VALUES (5, 801, NULL, 401, 4, '在线答题作业系统会自动保存，完成后点击提交即可，不需要特殊格式。注意检查代码的语法正确性。', '2026-01-14 11:47:35');
INSERT INTO `homework_comment` VALUES (6, 801, 303, NULL, 0, '第1题的素数判断程序，我的代码运行超时了，有什么优化方法吗？', '2026-01-16 11:47:35');
INSERT INTO `homework_comment` VALUES (7, 801, NULL, 401, 6, '素数判断可以优化：1) 只需判断到sqrt(n)即可；2) 排除偶数后只判断奇数。这样可以大大提高效率。', '2026-01-16 11:47:35');
INSERT INTO `homework_comment` VALUES (8, 801, 301, NULL, 0, '老师您好，第2题的计算器类实现中，如何处理除数为0的情况？', '2026-01-18 11:47:35');
INSERT INTO `homework_comment` VALUES (9, 801, 301, NULL, 0, '这次作业比较难呀', '2026-01-18 17:56:10');
INSERT INTO `homework_comment` VALUES (10, 801, 303, NULL, 0, '作业太难了作业太难了作业太难了', '2026-01-18 18:39:34');
INSERT INTO `homework_comment` VALUES (11, 801, NULL, 401, 10, '再难也得学呀', '2026-01-18 18:55:41');
INSERT INTO `homework_comment` VALUES (12, 805, 301, NULL, 0, '我感觉我能得100分，我需要得100分', '2026-01-26 13:18:30');

-- ----------------------------
-- Table structure for learning_effect_evaluation
-- ----------------------------
DROP TABLE IF EXISTS `learning_effect_evaluation`;
CREATE TABLE `learning_effect_evaluation`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `student_id` int NOT NULL,
  `course_id` int NULL DEFAULT NULL,
  `resource_id` int NULL DEFAULT NULL,
  `source_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `pre_mastery` decimal(5, 1) NULL DEFAULT NULL,
  `post_mastery` decimal(5, 1) NULL DEFAULT NULL,
  `correct_count` int NULL DEFAULT NULL,
  `total_count` int NULL DEFAULT NULL,
  `focus_points` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `result_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `recommendation_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_student_course`(`student_id` ASC, `course_id` ASC, `id` ASC) USING BTREE,
  INDEX `idx_resource`(`student_id` ASC, `resource_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学习效果评估历史' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of learning_effect_evaluation
-- ----------------------------

-- ----------------------------
-- Table structure for learning_plan
-- ----------------------------
DROP TABLE IF EXISTS `learning_plan`;
CREATE TABLE `learning_plan`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `courseid` int NULL DEFAULT NULL COMMENT '课程ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '计划标题',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学情概述',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '路径JSON（阶段/知识点/任务）',
  `status` int NULL DEFAULT 1 COMMENT '状态：1有效 0归档',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  `progress_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1004 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI学习路径计划表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of learning_plan
-- ----------------------------
INSERT INTO `learning_plan` VALUES (1001, 301, 601, 'Java EE 期末冲刺 14 天计划', '聚焦集合框架、多线程与 SSM 整合三大薄弱点，14 天分三阶段完成。', '{\"stages\":[{\"name\":\"基础巩固\",\"days\":5,\"tasks\":[\"重学集合框架源码要点\",\"每日完成10道选择题\",\"整理错题本\"],\"milestone\":\"集合相关错题正确率达90%\"},{\"name\":\"专项突破\",\"days\":5,\"tasks\":[\"多线程编程3个实验\",\"手写生产者消费者模型\",\"复盘线程池参数\"],\"milestone\":\"独立完成线程池调优实验\"},{\"name\":\"综合实战\",\"days\":4,\"tasks\":[\"完成SSM整合小项目\",\"模拟卷2套限时训练\"],\"milestone\":\"模拟卷85分以上\"}]}', 1, '2026-06-08 20:00:00', NULL);
INSERT INTO `learning_plan` VALUES (1002, 304, 604, '软件工程文档能力提升计划', '针对需求分析与 UML 建模薄弱环节制定的 10 天提升计划。', '{\"stages\":[{\"name\":\"方法学习\",\"days\":3,\"tasks\":[\"精读需求工程章节\",\"临摹2份优秀需求文档\"],\"milestone\":\"能独立列出完整需求条目\"},{\"name\":\"建模训练\",\"days\":4,\"tasks\":[\"每天绘制1张用例图/类图\",\"互评同学的UML图\"],\"milestone\":\"UML图通过老师评审\"},{\"name\":\"综合输出\",\"days\":3,\"tasks\":[\"完成课程设计需求规格说明书\"],\"milestone\":\"文档评分85+\"}]}', 0, '2026-06-09 21:30:00', NULL);
INSERT INTO `learning_plan` VALUES (1003, 309, 602, '数据结构刷题计划', '以线性表、树、图为主线的 21 天刷题计划，配合错题本复盘。', '{\"stages\":[{\"name\":\"线性结构\",\"days\":7,\"tasks\":[\"完成顺序表/链表20题\",\"手写LRU缓存\"],\"milestone\":\"链表题正确率85%\"},{\"name\":\"树与递归\",\"days\":7,\"tasks\":[\"二叉树遍历10题\",\"BST增删改查实现\"],\"milestone\":\"独立完成AVL旋转推导\"},{\"name\":\"图与综合\",\"days\":7,\"tasks\":[\"BFS/DFS模板题8道\",\"最短路径2题\"],\"milestone\":\"期末模拟卷80分以上\"}]}', 1, '2026-06-10 19:00:00', NULL);

-- ----------------------------
-- Table structure for learning_resource
-- ----------------------------
DROP TABLE IF EXISTS `learning_resource`;
CREATE TABLE `learning_resource`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `owner_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属类型：student / teacher',
  `owner_id` int NOT NULL COMMENT '所属用户ID',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '范围：student / course / class',
  `course_id` int NULL DEFAULT NULL COMMENT '关联课程ID',
  `course_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '课程名称（冗余）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源包标题',
  `pack_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '资源包内容JSON（诊断/规划/讲义/思维导图/练习/拓展阅读/代码实操等）',
  `source_id` int NULL DEFAULT NULL COMMENT '来源资源ID（教师推送给学生时记录原资源，便于去重）',
  `create_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_owner`(`owner_type` ASC, `owner_id` ASC) USING BTREE,
  INDEX `idx_scope`(`scope_type` ASC, `course_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1103 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学习多智能体生成的个性化资源包' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of learning_resource
-- ----------------------------
INSERT INTO `learning_resource` VALUES (1101, 'student', 301, 'course', 601, 'Java EE企业级应用开发教程', 'Java 集合框架个性化学习包', '{\"plan\": {\"stages\": [{\"days\": 2, \"name\": \"原理精读\", \"milestone\": \"能画出HashMap put流程图\"}, {\"days\": 2, \"name\": \"源码实验\", \"milestone\": \"完成扩容机制断点调试\"}, {\"days\": 2, \"name\": \"综合练习\", \"milestone\": \"专项练习正确率90%\"}]}, \"quiz\": [{\"type\": \"single\", \"title\": \"HashMap 初始容量与负载因子默认值是？\", \"answer\": 0, \"options\": [\"16 和 0.75\", \"32 和 0.5\", \"16 和 0.5\", \"8 和 0.75\"], \"analysis\": \"JDK8 中 DEFAULT_INITIAL_CAPACITY=16，DEFAULT_LOAD_FACTOR=0.75f。\", \"difficulty\": 1}], \"mindmap\": {\"data\": {\"name\": \"Java 集合框架\", \"children\": [{\"name\": \"Collection 体系\", \"children\": [{\"name\": \"List：ArrayList / LinkedList\"}, {\"name\": \"Set：HashSet / TreeSet\"}, {\"name\": \"Queue：ArrayDeque / PriorityQueue\"}]}, {\"name\": \"Map 体系\", \"children\": [{\"name\": \"HashMap：数组+链表+红黑树\"}, {\"name\": \"TreeMap：红黑树有序\"}, {\"name\": \"ConcurrentHashMap：分段锁→CAS\"}]}, {\"name\": \"核心机制\", \"children\": [{\"name\": \"扩容：HashMap 2倍 / ArrayList 1.5倍\"}, {\"name\": \"hash 扰动与索引计算\"}, {\"name\": \"fail-fast 与 modCount\"}]}, {\"name\": \"选型原则\", \"children\": [{\"name\": \"随机访问选 ArrayList\"}, {\"name\": \"频繁插删选 LinkedList\"}, {\"name\": \"并发场景选 Concurrent 容器\"}]}]}}, \"diagnose\": {\"summary\": \"整体掌握中等偏上，集合框架底层原理与并发容器是主要短板。依据：近5次作业中 HashMap/ConcurrentHashMap 相关题目正确率仅 55%。\", \"strengths\": [\"面向对象设计\", \"异常处理\"], \"weakPoints\": [{\"level\": \"中\", \"point\": \"HashMap底层结构\", \"suggestion\": \"结合源码理解数组+链表+红黑树的演化过程\"}, {\"level\": \"弱\", \"point\": \"并发容器选型\", \"suggestion\": \"对比 ConcurrentHashMap 与 Collections.synchronizedMap 的锁粒度\"}]}, \"overview\": \"本学习包针对你在集合框架与多线程上的薄弱点定制，预计学习时长 6 小时，建议按「讲义-导图-练习-阅读」顺序完成。\"}', NULL, '2026-06-09 15:30:00');
INSERT INTO `learning_resource` VALUES (1102, 'teacher', 401, 'class', 601, 'Java EE企业级应用开发教程', '集合框架专题资源包（班级推送版）', '{\"quiz\": [{\"type\": \"single\", \"title\": \"ArrayList 扩容为原容量的多少倍？\", \"answer\": 0, \"options\": [\"1.5\", \"2\", \"1.25\", \"3\"], \"analysis\": \"grow() 中 newCapacity = oldCapacity + (oldCapacity >> 1)。\", \"difficulty\": 1}], \"mindmap\": {\"data\": {\"name\": \"Java 集合框架\", \"children\": [{\"name\": \"Collection 体系\", \"children\": [{\"name\": \"List：ArrayList / LinkedList\"}, {\"name\": \"Set：HashSet / TreeSet\"}, {\"name\": \"Queue：ArrayDeque / PriorityQueue\"}]}, {\"name\": \"Map 体系\", \"children\": [{\"name\": \"HashMap：数组+链表+红黑树\"}, {\"name\": \"TreeMap：红黑树有序\"}, {\"name\": \"ConcurrentHashMap：分段锁→CAS\"}]}, {\"name\": \"核心机制\", \"children\": [{\"name\": \"扩容：HashMap 2倍 / ArrayList 1.5倍\"}, {\"name\": \"hash 扰动与索引计算\"}, {\"name\": \"fail-fast 与 modCount\"}]}, {\"name\": \"选型原则\", \"children\": [{\"name\": \"随机访问选 ArrayList\"}, {\"name\": \"频繁插删选 LinkedList\"}, {\"name\": \"并发场景选 Concurrent 容器\"}]}]}}, \"overview\": \"面向计科2101班的集合框架专题包，覆盖讲义、思维导图与分层练习，适合期中后的查漏补缺。\", \"resources\": [{\"type\": \"lecture\", \"title\": \"集合框架体系总览\", \"content\": \"核心要点：Collection 与 Map 两大体系……\"}]}', NULL, '2026-06-10 10:20:00');

-- ----------------------------
-- Table structure for major
-- ----------------------------
DROP TABLE IF EXISTS `major`;
CREATE TABLE `major`  (
  `majorid` int NOT NULL AUTO_INCREMENT COMMENT '专业ID',
  `majorname` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '专业名称',
  PRIMARY KEY (`majorid`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 111 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '专业表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of major
-- ----------------------------
INSERT INTO `major` VALUES (101, '计算机科学与技术');
INSERT INTO `major` VALUES (102, '软件工程');
INSERT INTO `major` VALUES (103, '信息安全');
INSERT INTO `major` VALUES (104, '人工智能');
INSERT INTO `major` VALUES (105, '数据科学与大数据技术');
INSERT INTO `major` VALUES (106, '网络工程');
INSERT INTO `major` VALUES (107, '物联网工程');
INSERT INTO `major` VALUES (108, '数字媒体技术');
INSERT INTO `major` VALUES (109, '智能科学与技术');
INSERT INTO `major` VALUES (110, '电子信息工程');

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `title` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '消息标题',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '消息内容',
  `sendertype` int NOT NULL COMMENT '发送者类型：1管理员 2教师',
  `senderid` int NOT NULL COMMENT '发送者ID',
  `receivertype` int NOT NULL COMMENT '接收者类型：1学生 2教师 3班级',
  `receiverid` int NOT NULL COMMENT '接收者ID（学生ID/教师ID/班级ID）',
  `isread` int NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  `readtime` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  `createtime` datetime NULL DEFAULT NULL COMMENT '发送时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sendertype_senderid`(`sendertype` ASC, `senderid` ASC) USING BTREE,
  INDEX `idx_receivertype_receiverid`(`receivertype` ASC, `receiverid` ASC) USING BTREE,
  INDEX `idx_isread`(`isread` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 57 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '站内消息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of message
-- ----------------------------
INSERT INTO `message` VALUES (1, '欢迎使用智慧作业收集系统', '亲爱的同学，欢迎使用智慧作业收集系统！请及时查看作业通知，按时完成作业提交。如有问题可以通过系统消息或作业评论功能与老师沟通。祝学习进步！', 1, 1, 1, 301, 1, '2025-12-19 11:47:35', '2025-12-19 11:47:35');
INSERT INTO `message` VALUES (2, '作业提醒：Java基础练习即将截止', 'Java基础练习作业将于本周五（1月20日）23:59截止，请尚未提交的同学抓紧时间完成。作业要求：完成素数判断和计算器类的实现，注意代码规范和注释。', 2, 401, 1, 301, 1, '2026-01-13 11:47:35', '2026-01-11 11:47:35');
INSERT INTO `message` VALUES (3, '课程资源更新通知', 'Java EE企业级应用开发教程已上传新的课程资源，包括Spring框架PPT、MVC示例代码和Hibernate教程。请及时下载学习，这些资料对期末考试很有帮助。', 2, 401, 1, 301, 1, '2026-01-15 11:47:35', '2026-01-14 11:47:35');
INSERT INTO `message` VALUES (4, '期末考试安排通知', '本学期期末考试将于2月初进行，请同学们做好复习准备。考试范围：本学期所学全部内容。考试形式：闭卷笔试+上机实践。具体考试时间和地点将另行通知。', 1, 1, 1, 301, 1, '2026-01-08 11:47:35', '2026-01-06 11:47:35');
INSERT INTO `message` VALUES (5, '成绩公布通知', '您的Java基础练习作业已批改完成，成绩为85分。评语：代码逻辑清晰，但缺少注释。请登录系统查看详细批改意见，如有疑问可在作业详情中提问。', 2, 401, 1, 301, 1, '2026-06-11 12:06:13', '2026-01-17 11:47:35');
INSERT INTO `message` VALUES (6, '数据结构课程答疑通知', '本周四（1月19日）下午3:00-5:00在实验楼305进行数据结构课程答疑，欢迎同学们前来咨询。主要内容：排序算法、树和图的遍历。', 2, 401, 1, 301, 1, '2026-06-11 12:06:13', '2026-01-18 09:47:35');
INSERT INTO `message` VALUES (7, '课程评价邀请', '亲爱的同学，本学期课程即将结束，诚邀您对所修课程进行评价。您的宝贵意见将帮助我们改进教学质量。评价采用匿名方式，请放心填写真实想法。', 1, 1, 1, 301, 1, '2026-06-11 12:06:13', '2026-01-18 10:47:35');
INSERT INTO `message` VALUES (8, '考勤情况提醒', '您在数据结构课程中有2次缺勤记录（1次病假、1次事假），请注意出勤情况。根据课程要求，缺勤超过3次将影响平时成绩。如有特殊情况请及时与老师沟通。', 2, 401, 1, 301, 1, '2026-01-18 18:19:54', '2026-01-18 11:47:35');
INSERT INTO `message` VALUES (9, '班级通知：期末复习资料已上传', '各位同学，期末复习资料已上传至课程资源模块，包括各科目的复习提纲、历年试题和重点知识总结。请及时下载复习，预祝大家取得好成绩！', 2, 401, 3, 201, 0, NULL, '2026-01-15 11:47:35');
INSERT INTO `message` VALUES (10, '班级活动通知：元旦晚会', '计科2101班元旦晚会将于12月30日晚7:00在大礼堂举行，欢迎全班同学参加。请各位同学准备节目，报名请联系班长。', 1, 1, 3, 201, 1, '2025-12-29 11:47:35', '2025-12-24 11:47:35');
INSERT INTO `message` VALUES (11, '33232', '223323223', 2, 401, 1, 303, 1, '2026-01-18 18:40:32', '2026-01-18 18:10:47');
INSERT INTO `message` VALUES (12, '快做作业', '快做作业快做作业', 2, 401, 1, 303, 0, NULL, '2026-01-18 18:56:40');
INSERT INTO `message` VALUES (13, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年06月15日 00:00，请及时完成。', 2, 407, 1, 301, 1, '2026-06-11 12:06:13', '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (14, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年06月15日 00:00，请及时完成。', 2, 407, 1, 302, 0, NULL, '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (15, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年06月15日 00:00，请及时完成。', 2, 407, 1, 312, 0, NULL, '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (16, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年06月15日 00:00，请及时完成。', 2, 407, 1, 303, 0, NULL, '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (17, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年06月15日 00:00，请及时完成。', 2, 407, 1, 311, 0, NULL, '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (18, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年04月30日 00:00，请及时完成。', 2, 407, 1, 309, 1, '2026-02-09 12:05:54', '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (19, '新作业通知：初识数据结构', '郑伟光 老师发布了新作业「初识数据结构」（课程：数据结构与算法），截止时间：2026年04月30日 00:00，请及时完成。', 2, 407, 1, 310, 1, '2026-02-09 13:55:17', '2026-02-09 12:05:20');
INSERT INTO `message` VALUES (20, '新作业通知：第一章-认识网络爬虫', '黄文斌 老师发布了新作业「第一章-认识网络爬虫」（课程：网络爬虫技术），截止时间：2026年04月25日 00:00，请及时完成。', 2, 410, 1, 308, 0, NULL, '2026-02-09 13:00:04');
INSERT INTO `message` VALUES (21, '新作业通知：第一章-认识网络爬虫', '黄文斌 老师发布了新作业「第一章-认识网络爬虫」（课程：网络爬虫技术），截止时间：2026年04月25日 00:00，请及时完成。', 2, 410, 1, 316, 0, NULL, '2026-02-09 13:00:04');
INSERT INTO `message` VALUES (22, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 301, 1, '2026-06-11 12:06:13', '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (23, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 302, 0, NULL, '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (24, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 312, 0, NULL, '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (25, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 303, 0, NULL, '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (26, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 311, 0, NULL, '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (27, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 309, 0, NULL, '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (28, '新作业通知：顺序表', '郑伟光 老师发布了新作业「顺序表」（课程：数据结构与算法），截止时间：2026年04月28日 00:00，请及时完成。', 2, 407, 1, 310, 1, '2026-02-09 19:15:31', '2026-02-09 18:44:25');
INSERT INTO `message` VALUES (29, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。', 2, 401, 1, 303, 0, NULL, '2026-02-09 19:12:28');
INSERT INTO `message` VALUES (30, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。', 2, 401, 1, 311, 0, NULL, '2026-02-09 19:12:29');
INSERT INTO `message` VALUES (31, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。', 2, 401, 1, 309, 0, NULL, '2026-02-09 19:12:29');
INSERT INTO `message` VALUES (32, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。', 2, 401, 1, 310, 1, '2026-02-09 19:12:50', '2026-02-09 19:12:29');
INSERT INTO `message` VALUES (33, '作业提交提醒', '你有一份作业【数据结构第一章作业】尚未提交，请尽快完成提交。\n[homeworkid:820]', 2, 401, 1, 301, 1, '2026-06-11 12:06:13', '2026-02-09 19:16:15');
INSERT INTO `message` VALUES (34, '作业提交提醒', '你有一份作业【数据结构第一章作业】尚未提交，请尽快完成提交。\n[homeworkid:820]', 2, 401, 1, 302, 0, NULL, '2026-02-09 19:16:15');
INSERT INTO `message` VALUES (35, '作业提交提醒', '你有一份作业【数据结构第一章作业】尚未提交，请尽快完成提交。\n[homeworkid:820]', 2, 401, 1, 303, 0, NULL, '2026-02-09 19:16:15');
INSERT INTO `message` VALUES (36, '作业提交提醒', '你有一份作业【数据结构第一章作业】尚未提交，请尽快完成提交。\n[homeworkid:820]', 2, 401, 1, 311, 0, NULL, '2026-02-09 19:16:15');
INSERT INTO `message` VALUES (37, '作业提交提醒', '你有一份作业【数据结构第一章作业】尚未提交，请尽快完成提交。\n[homeworkid:820]', 2, 401, 1, 309, 0, NULL, '2026-02-09 19:16:15');
INSERT INTO `message` VALUES (38, '作业提交提醒', '你有一份作业【数据结构第一章作业】尚未提交，请尽快完成提交。\n[homeworkid:820]', 2, 401, 1, 310, 1, '2026-02-09 19:20:37', '2026-02-09 19:16:15');
INSERT INTO `message` VALUES (39, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。\n[homeworkid:819]', 2, 401, 1, 303, 0, NULL, '2026-02-09 19:16:38');
INSERT INTO `message` VALUES (40, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。\n[homeworkid:819]', 2, 401, 1, 311, 0, NULL, '2026-02-09 19:16:38');
INSERT INTO `message` VALUES (41, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。\n[homeworkid:819]', 2, 401, 1, 309, 0, NULL, '2026-02-09 19:16:38');
INSERT INTO `message` VALUES (42, '作业提交提醒', '你有一份作业【Python第一次作业】尚未提交，请尽快完成提交。\n[homeworkid:819]', 2, 401, 1, 310, 1, '2026-02-09 19:17:01', '2026-02-09 19:16:38');
INSERT INTO `message` VALUES (43, '课堂签到提醒', '老师发起了《Python程序设计》的签到（手势签到），请在 10 分钟内完成签到。', 2, 401, 1, 303, 0, NULL, '2026-06-02 16:05:13');
INSERT INTO `message` VALUES (44, '课堂签到提醒', '老师发起了《Python程序设计》的签到（手势签到），请在 10 分钟内完成签到。', 2, 401, 1, 311, 0, NULL, '2026-06-02 16:05:13');
INSERT INTO `message` VALUES (45, '课堂签到提醒', '老师发起了《Python程序设计》的签到（手势签到），请在 10 分钟内完成签到。', 2, 401, 1, 309, 0, NULL, '2026-06-02 16:05:13');
INSERT INTO `message` VALUES (46, '课堂签到提醒', '老师发起了《Python程序设计》的签到（手势签到），请在 10 分钟内完成签到。', 2, 401, 1, 310, 0, NULL, '2026-06-02 16:05:13');
INSERT INTO `message` VALUES (47, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 303, 0, NULL, '2026-06-02 16:20:04');
INSERT INTO `message` VALUES (48, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 311, 0, NULL, '2026-06-02 16:20:04');
INSERT INTO `message` VALUES (49, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 309, 0, NULL, '2026-06-02 16:20:04');
INSERT INTO `message` VALUES (50, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 310, 0, NULL, '2026-06-02 16:20:04');
INSERT INTO `message` VALUES (51, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 301, 1, '2026-06-11 12:06:13', '2026-06-02 16:41:26');
INSERT INTO `message` VALUES (52, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 302, 0, NULL, '2026-06-02 16:41:26');
INSERT INTO `message` VALUES (53, '课堂签到提醒', '老师发起了《Java EE企业级应用开发教程》的签到（签到码签到），请在 10 分钟内完成签到。', 2, 401, 1, 312, 0, NULL, '2026-06-02 16:41:26');
INSERT INTO `message` VALUES (54, '新作业通知：IP地址规划与子网划分', '高建华老师发布了新作业《IP地址规划与子网划分》，截止时间 2026-06-20 23:59，请按时完成。\n[homeworkid:829]', 2, 411, 1, 323, 0, NULL, '2026-06-06 09:05:00');
INSERT INTO `message` VALUES (55, '作业批改完成：传感器数据采集实验', '你的作业《传感器数据采集实验》已批改，得分 90 分，点击查看教师评语。', 2, 412, 1, 325, 0, NULL, '2026-06-10 11:05:00');
INSERT INTO `message` VALUES (56, '考试提醒：Java EE 期中测试', '《Java EE 期中测试》将于 2026-06-15 09:00 开始，时长 90 分钟，开启防切屏监测，请提前进入考试页面。', 2, 401, 1, 333, 0, NULL, '2026-06-14 16:00:00');

-- ----------------------------
-- Table structure for notice
-- ----------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice`  (
  `noticeid` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `title` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '标题',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '内容',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`noticeid`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 509 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '通知公告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of notice
-- ----------------------------
INSERT INTO `notice` VALUES (503, '关于作业提交系统升级的通知', '<p>各位师生：</p><p>作业提交系统将于本周末进行升级维护，届时系统将暂停服务。</p><p>信息中心</p>', '2025-12-22 14:00:00');
INSERT INTO `notice` VALUES (504, '关于校园网络维护的通知', '<p>各位师生：</p><p>为提升网络服务质量，校园网络将于周日凌晨进行维护。</p><p>信息中心</p>', '2025-12-22 16:00:00');
INSERT INTO `notice` VALUES (505, '2025-2026学年第二学期期末考试安排通知', '<p>各位同学：</p><p>本学期期末考试定于 <b>2026年6月22日至7月3日</b> 进行，请登录智衍学堂查看个人考试安排，按时参加在线考试。考试期间系统将开启防切屏监测，请提前调试好设备。</p><p>祝大家取得好成绩！</p>', '2026-06-05 09:00:00');
INSERT INTO `notice` VALUES (506, '暑期企业实训项目报名开始', '<p>2026 年暑期企业实训项目现已开放报名，涵盖 <b>Java 后端开发、前端工程化、大数据分析、AI 应用开发</b> 四个方向，报名截止 6 月 25 日，名额有限，请有意向的同学尽快在个人中心提交申请。</p>', '2026-06-08 10:30:00');
INSERT INTO `notice` VALUES (507, '图书馆暑期开放时间调整通知', '<p>自 7 月 1 日起，图书馆开放时间调整为 <b>8:30-17:30</b>（周一至周五），周末闭馆。电子资源 24 小时开放，请同学们合理安排学习时间。</p>', '2026-06-09 14:00:00');
INSERT INTO `notice` VALUES (508, '智衍学堂 AI 学习功能升级公告', '<p>平台已上线 <b>多智能体学习资源生成、AI 讲解微课、语音问答</b> 等全新功能，欢迎同学们在「AI 学习」菜单中体验，并通过消息中心反馈使用意见。</p>', '2026-06-10 16:00:00');

-- ----------------------------
-- Table structure for notice_read
-- ----------------------------
DROP TABLE IF EXISTS `notice_read`;
CREATE TABLE `notice_read`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `noticeid` int NULL DEFAULT NULL COMMENT '公告ID',
  `homeworkid` int NULL DEFAULT NULL COMMENT '作业ID',
  `studentid` int NULL DEFAULT NULL COMMENT '学生ID',
  `teacherid` int NULL DEFAULT NULL COMMENT '教师ID',
  `readtime` datetime NULL DEFAULT NULL COMMENT '阅读时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_noticeid`(`noticeid` ASC) USING BTREE,
  INDEX `idx_homeworkid`(`homeworkid` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
  INDEX `idx_teacherid`(`teacherid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 34 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '阅读状态表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of notice_read
-- ----------------------------
INSERT INTO `notice_read` VALUES (1, NULL, 801, 301, NULL, '2026-01-08 11:47:35');
INSERT INTO `notice_read` VALUES (2, NULL, 802, 301, NULL, '2026-01-10 11:47:35');
INSERT INTO `notice_read` VALUES (3, NULL, 803, 301, NULL, '2026-01-12 11:47:35');
INSERT INTO `notice_read` VALUES (4, NULL, 804, 301, NULL, '2026-01-14 11:47:35');
INSERT INTO `notice_read` VALUES (5, NULL, 805, 301, NULL, '2026-01-16 11:47:35');
INSERT INTO `notice_read` VALUES (6, 504, NULL, NULL, 401, '2026-01-18 18:09:43');
INSERT INTO `notice_read` VALUES (7, 504, NULL, 303, NULL, '2026-01-18 18:38:39');
INSERT INTO `notice_read` VALUES (8, NULL, 801, 303, NULL, '2026-01-18 18:39:24');
INSERT INTO `notice_read` VALUES (9, NULL, 802, 303, NULL, '2026-01-18 18:39:43');
INSERT INTO `notice_read` VALUES (10, NULL, 820, 301, NULL, '2026-01-21 14:31:00');
INSERT INTO `notice_read` VALUES (11, 504, NULL, 310, NULL, '2026-01-22 08:32:55');
INSERT INTO `notice_read` VALUES (12, NULL, 804, 310, NULL, '2026-01-23 23:31:53');
INSERT INTO `notice_read` VALUES (13, NULL, 825, 301, NULL, '2026-02-01 14:48:52');
INSERT INTO `notice_read` VALUES (14, NULL, 824, 301, NULL, '2026-02-01 15:08:47');
INSERT INTO `notice_read` VALUES (15, NULL, 809, 301, NULL, '2026-02-01 15:10:59');
INSERT INTO `notice_read` VALUES (16, NULL, 824, 302, NULL, '2026-02-01 15:32:08');
INSERT INTO `notice_read` VALUES (17, 504, NULL, NULL, 407, '2026-02-03 14:22:31');
INSERT INTO `notice_read` VALUES (18, 504, NULL, 312, NULL, '2026-02-08 20:04:38');
INSERT INTO `notice_read` VALUES (19, 503, NULL, 312, NULL, '2026-02-08 20:04:45');
INSERT INTO `notice_read` VALUES (20, NULL, 806, 313, NULL, '2026-02-08 20:05:15');
INSERT INTO `notice_read` VALUES (21, NULL, 820, 312, NULL, '2026-02-08 22:40:59');
INSERT INTO `notice_read` VALUES (22, NULL, 801, 310, NULL, '2026-02-08 22:49:24');
INSERT INTO `notice_read` VALUES (23, NULL, 809, 310, NULL, '2026-02-08 22:49:43');
INSERT INTO `notice_read` VALUES (24, NULL, 809, 311, NULL, '2026-02-08 23:41:51');
INSERT INTO `notice_read` VALUES (25, NULL, 826, 309, NULL, '2026-02-09 12:06:24');
INSERT INTO `notice_read` VALUES (26, NULL, 828, 301, NULL, '2026-02-09 18:45:13');
INSERT INTO `notice_read` VALUES (27, NULL, 819, 310, NULL, '2026-02-09 19:17:17');
INSERT INTO `notice_read` VALUES (28, NULL, 828, 310, NULL, '2026-02-09 19:17:38');
INSERT INTO `notice_read` VALUES (29, NULL, 826, 310, NULL, '2026-02-09 19:17:44');
INSERT INTO `notice_read` VALUES (30, NULL, 817, 301, NULL, '2026-02-10 20:47:57');
INSERT INTO `notice_read` VALUES (31, NULL, 815, 301, NULL, '2026-02-10 21:14:04');
INSERT INTO `notice_read` VALUES (32, NULL, 816, 301, NULL, '2026-02-10 21:14:13');
INSERT INTO `notice_read` VALUES (33, NULL, 831, 301, NULL, '2026-07-11 08:09:54');

-- ----------------------------
-- Table structure for peer_review
-- ----------------------------
DROP TABLE IF EXISTS `peer_review`;
CREATE TABLE `peer_review`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `homeworkid` int NOT NULL,
  `reviewerid` int NOT NULL COMMENT '评阅人（学生）ID',
  `submitid` int NOT NULL COMMENT '被评作业提交ID',
  `revieweeid` int NOT NULL COMMENT '被评学生ID',
  `score` decimal(5, 1) NULL DEFAULT NULL COMMENT '互评分',
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '互评评语',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态：0待评 1已评',
  `assign_time` datetime NULL DEFAULT NULL,
  `submit_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_reviewer_submit`(`reviewerid` ASC, `submitid` ASC) USING BTREE,
  INDEX `idx_homeworkid`(`homeworkid` ASC) USING BTREE,
  INDEX `idx_reviewerid`(`reviewerid` ASC) USING BTREE,
  INDEX `idx_revieweeid`(`revieweeid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1206 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '作业互评任务/结果' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of peer_review
-- ----------------------------
INSERT INTO `peer_review` VALUES (1201, 801, 302, 901, 301, 88.0, '代码结构清晰，命名规范，建议给核心方法补充注释。', 1, '2026-06-08 09:00:00', '2026-06-08 20:15:00');
INSERT INTO `peer_review` VALUES (1202, 801, 303, 901, 301, 85.0, '实现完整，边界条件处理到位，输出格式可以再美观一些。', 1, '2026-06-08 09:00:00', '2026-06-09 10:30:00');
INSERT INTO `peer_review` VALUES (1203, 801, 301, 902, 302, 80.0, '思路正确，但循环里存在重复计算，可以提取公共变量优化。', 1, '2026-06-08 09:00:00', '2026-06-08 21:40:00');
INSERT INTO `peer_review` VALUES (1204, 801, 303, 902, 302, NULL, NULL, 0, '2026-06-08 09:00:00', NULL);
INSERT INTO `peer_review` VALUES (1205, 801, 301, 903, 303, 92.0, '使用了泛型与流式 API，代码很简洁，值得学习。', 1, '2026-06-08 09:00:00', '2026-06-09 08:50:00');

-- ----------------------------
-- Table structure for peer_review_task
-- ----------------------------
DROP TABLE IF EXISTS `peer_review_task`;
CREATE TABLE `peer_review_task`  (
  `homeworkid` int NOT NULL COMMENT '作业ID',
  `teacherid` int NULL DEFAULT NULL,
  `reviewers_per` int NULL DEFAULT 2 COMMENT '每份作业被评阅人数',
  `max_score` int NULL DEFAULT 100 COMMENT '互评满分',
  `anonymous` tinyint NULL DEFAULT 1 COMMENT '是否匿名：1是 0否',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：1开启 0关闭',
  `create_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`homeworkid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '作业互评配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of peer_review_task
-- ----------------------------
INSERT INTO `peer_review_task` VALUES (801, 401, 2, 100, 1, 1, '2026-06-08 09:00:00');

-- ----------------------------
-- Table structure for pipeline_run
-- ----------------------------
DROP TABLE IF EXISTS `pipeline_run`;
CREATE TABLE `pipeline_run`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `owner_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `owner_id` int NOT NULL,
  `course_id` int NULL DEFAULT NULL,
  `quiz_count` int NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `steps_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `resource_id` int NULL DEFAULT NULL,
  `error` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL,
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_owner`(`owner_type` ASC, `owner_id` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '多智能体流水线运行记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pipeline_run
-- ----------------------------

-- ----------------------------
-- Table structure for plagiarism_report
-- ----------------------------
DROP TABLE IF EXISTS `plagiarism_report`;
CREATE TABLE `plagiarism_report`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '报告ID',
  `homeworkid` int NOT NULL COMMENT '作业ID',
  `teacherid` int NULL DEFAULT NULL COMMENT '发起教师ID',
  `pair_count` int NULL DEFAULT 0 COMMENT '比对对数',
  `flagged_count` int NULL DEFAULT 0 COMMENT '超阈值疑似对数',
  `max_similarity` decimal(5, 2) NULL DEFAULT 0.00 COMMENT '最高相似度(%)',
  `threshold` decimal(5, 2) NULL DEFAULT 70.00 COMMENT '判定阈值(%)',
  `result_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '相似对明细JSON',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_homeworkid`(`homeworkid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1302 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '作业查重报告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of plagiarism_report
-- ----------------------------
INSERT INTO `plagiarism_report` VALUES (1301, 801, 401, 3, 1, 82.50, 70.00, '{\"pairs\":[{\"studentA\":\"李豪\",\"studentB\":\"赵明辉\",\"similarity\":82.5,\"flagged\":true,\"detail\":\"Prime类实现部分高度相似，变量命名一致\"},{\"studentA\":\"李豪\",\"studentB\":\"孙浩然\",\"similarity\":41.2,\"flagged\":false},{\"studentA\":\"赵明辉\",\"studentB\":\"孙浩然\",\"similarity\":38.6,\"flagged\":false}],\"aiDetect\":{\"suspected\":0,\"note\":\"未检测到明显AI生成痕迹\"}}', '2026-06-09 11:00:00');

-- ----------------------------
-- Table structure for points_log
-- ----------------------------
DROP TABLE IF EXISTS `points_log`;
CREATE TABLE `points_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `points` int NOT NULL COMMENT '变动积分(正负)',
  `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型：CHECKIN/HOMEWORK/EXAM/MASTER/OTHER',
  `reason` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '说明',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '积分流水表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of points_log
-- ----------------------------
INSERT INTO `points_log` VALUES (1, 310, 10, 'EXAM', '完成考试 +10', '2026-06-07 10:04:41');
INSERT INTO `points_log` VALUES (2, 301, 5, 'CHECKIN', '连续打卡第1天 +5', '2026-06-08 11:03:00');
INSERT INTO `points_log` VALUES (3, 301, 5, 'CHECKIN', '连续打卡第1天 +5', '2026-06-11 12:51:45');
INSERT INTO `points_log` VALUES (11, 302, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-08 20:30:00');
INSERT INTO `points_log` VALUES (12, 302, 5, 'CHECKIN', '连续打卡第1天 +5', '2026-06-09 08:10:00');
INSERT INTO `points_log` VALUES (13, 303, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-08 21:00:00');
INSERT INTO `points_log` VALUES (14, 304, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-09 18:20:00');
INSERT INTO `points_log` VALUES (15, 304, 20, 'EXAM', '考试成绩优秀 +20', '2026-06-28 11:00:00');
INSERT INTO `points_log` VALUES (16, 309, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-10 09:00:00');
INSERT INTO `points_log` VALUES (17, 321, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-09 19:35:00');
INSERT INTO `points_log` VALUES (18, 325, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-09 17:05:00');
INSERT INTO `points_log` VALUES (19, 333, 20, 'EXAM', '考试满分 +20', '2026-06-15 10:00:00');
INSERT INTO `points_log` VALUES (20, 301, 10, 'HOMEWORK', '按时提交作业 +10', '2026-06-09 20:00:00');

-- ----------------------------
-- Table structure for resource_practice_attempt
-- ----------------------------
DROP TABLE IF EXISTS `resource_practice_attempt`;
CREATE TABLE `resource_practice_attempt`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `student_id` int NOT NULL,
  `resource_id` int NOT NULL,
  `course_id` int NULL DEFAULT NULL,
  `answers_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `score` decimal(5, 1) NULL DEFAULT NULL,
  `correct_count` int NULL DEFAULT NULL,
  `total_count` int NULL DEFAULT NULL,
  `detail_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_student_resource`(`student_id` ASC, `resource_id` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '资源包练习作答记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resource_practice_attempt
-- ----------------------------

-- ----------------------------
-- Table structure for student
-- ----------------------------
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student`  (
  `studentid` int NOT NULL AUTO_INCREMENT COMMENT '学生ID',
  `studentno` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '学号',
  `studentname` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '学生姓名',
  `password` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '密码',
  `sex` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '性别',
  `phone` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `address` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '地址',
  `classid` int NULL DEFAULT NULL COMMENT '所属班级ID',
  `majorid` int NULL DEFAULT NULL COMMENT '所属专业ID',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `avatar` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '头像路径',
  PRIMARY KEY (`studentid`) USING BTREE,
  UNIQUE INDEX `uk_studentno`(`studentno` ASC) USING BTREE,
  INDEX `idx_classid`(`classid` ASC) USING BTREE,
  INDEX `idx_majorid`(`majorid` ASC) USING BTREE,
  CONSTRAINT `fk_student_class` FOREIGN KEY (`classid`) REFERENCES `classinfo` (`classid`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_student_major` FOREIGN KEY (`majorid`) REFERENCES `major` (`majorid`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 337 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '学生表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of student
-- ----------------------------
INSERT INTO `student` VALUES (301, '2023001001', '李豪', '$2a$12$J12t0FZySJ4j.EXtswuxxezywf3t8NJENJBjS.xL/dAE4usAo0RES', '男', '13800138001', '2852382302@qq.com', '北京市海淀区', 201, 101, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (302, '2023001002', '赵明辉', '123456', '女', '13800138002', '1492977861@qq.com', '北京市朝阳区', 201, 101, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (303, '2023001003', '孙浩然', '123456', '男', '13800138003', '2547670982@qq.com', '上海市浦东新区', 202, 101, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (304, '2023002001', '周思雨', '123456', '女', '13800138004', '2534653323@qq.com', '广州市天河区', 203, 102, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (305, '2023002002', '吴晨阳', '123456', '男', '13800138005', '287678623@qq.com', '深圳市南山区', 204, 102, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (306, '2023003001', '郑雅琪', '123456', '女', '13800138006', '2578328923@qq.com', '杭州市西湖区', 205, 103, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (307, '2023004001', '钱志远', '123456', '男', '13800138007', '2053432561@qq.com', '成都市武侯区', 206, 104, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (308, '2023005001', '孟晓燕', '123456', '女', '13800138008', '2345645672@qq.com', '武汉市洪山区', 207, 105, '2025-12-23 08:00:00', NULL);
INSERT INTO `student` VALUES (309, '2024303292', '林佳颖', '123456', '女', '15123901748', '3074395164@qq.com', '重庆市长寿区', 202, 101, '2026-01-11 20:47:53', '2026/02/20260209_f21bdb67cada4e6a8188087b05ac3fa0.jpg');
INSERT INTO `student` VALUES (310, '2024305743', '徐国栋', '123456', '男', '18580118283', '2584410718@qq.com', '重庆市合川区', 202, 101, '2026-01-11 20:50:10', '2026/02/20260209_03eb9e36fbe54d42a1d127a1484acd58.jpg');
INSERT INTO `student` VALUES (311, '2024301663', '黄梦瑶', '123456', '女', '18436568321', '1063174805@qq.com', '重庆市巴南区', 202, 101, '2026-01-24 09:45:16', '2026/02/20260209_6c108a468e15453f9b7eb0af35a4b9fd.jpg');
INSERT INTO `student` VALUES (312, '2024303774', '何旭', '123456', NULL, NULL, NULL, NULL, 201, 101, '2026-02-08 19:54:58', '2026/02/20260209_c2f4bd6a63fb4022bdd269b54bb1666a.jpg');
INSERT INTO `student` VALUES (313, '2024302754', '曹正义', '123456', NULL, NULL, NULL, NULL, 204, 102, '2026-02-08 19:58:49', NULL);
INSERT INTO `student` VALUES (314, '2024305723', '许宏伟', '123456', NULL, NULL, NULL, NULL, 204, 102, '2026-02-09 12:41:31', '2026/02/20260209_e761af68827d40b1ad86582ea9a3c6b4.jpg');
INSERT INTO `student` VALUES (315, '2024304746', '叶芳芳', '123456', NULL, NULL, NULL, NULL, 205, 103, '2026-02-09 12:42:14', '2026/02/20260209_4027a85c3c1341e2afccbb15fe1416e7.jpg');
INSERT INTO `student` VALUES (316, '2024401375', '方敏慧', '123456', '女', '18521897671', '2365252321@qq.com', NULL, 207, 105, '2026-02-09 12:44:50', '2026/02/20260209_c4ffe4ea278c4f9e92e54c92cb65afa4.jpg');
INSERT INTO `student` VALUES (317, '2024501001', '蔡文静', '123456', '女', '13900002001', 'caiwj@stu.zhiyan.edu.cn', '山东省济南市', 209, 103, '2026-03-01 10:00:00', NULL);
INSERT INTO `student` VALUES (318, '2024501002', '邓子豪', '123456', '男', '13900002002', 'dengzh@stu.zhiyan.edu.cn', '山东省青岛市', 209, 103, '2026-03-01 10:01:00', NULL);
INSERT INTO `student` VALUES (319, '2024502001', '任晓彤', '123456', '女', '13900002003', 'renxt@stu.zhiyan.edu.cn', '江苏省南京市', 210, 104, '2026-03-01 10:02:00', NULL);
INSERT INTO `student` VALUES (320, '2024502002', '石磊', '123456', '男', '13900002004', 'shilei@stu.zhiyan.edu.cn', '江苏省苏州市', 210, 104, '2026-03-01 10:03:00', NULL);
INSERT INTO `student` VALUES (321, '2024503001', '龚雪丽', '123456', '女', '13900002005', 'gongxl@stu.zhiyan.edu.cn', '浙江省杭州市', 211, 106, '2026-03-01 10:04:00', NULL);
INSERT INTO `student` VALUES (322, '2024503002', '贺志鹏', '123456', '男', '13900002006', 'hezp@stu.zhiyan.edu.cn', '浙江省宁波市', 211, 106, '2026-03-01 10:05:00', NULL);
INSERT INTO `student` VALUES (323, '2024503003', '邹雨桐', '123456', '女', '13900002007', 'zouyt@stu.zhiyan.edu.cn', '安徽省合肥市', 212, 106, '2026-03-01 10:06:00', NULL);
INSERT INTO `student` VALUES (324, '2024503004', '章浩宇', '123456', '男', '13900002008', 'zhanghy@stu.zhiyan.edu.cn', '安徽省芜湖市', 212, 106, '2026-03-01 10:07:00', NULL);
INSERT INTO `student` VALUES (325, '2024504001', '苏婉婷', '123456', '女', '13900002009', 'suwt@stu.zhiyan.edu.cn', '福建省福州市', 213, 107, '2026-03-01 10:08:00', NULL);
INSERT INTO `student` VALUES (326, '2024504002', '范志超', '123456', '男', '13900002010', 'fanzc@stu.zhiyan.edu.cn', '福建省厦门市', 213, 107, '2026-03-01 10:09:00', NULL);
INSERT INTO `student` VALUES (327, '2024505001', '金美玲', '123456', '女', '13900002011', 'jinml@stu.zhiyan.edu.cn', '广东省广州市', 214, 108, '2026-03-01 10:10:00', NULL);
INSERT INTO `student` VALUES (328, '2024505002', '段宇航', '123456', '男', '13900002012', 'duanyh@stu.zhiyan.edu.cn', '广东省深圳市', 214, 108, '2026-03-01 10:11:00', NULL);
INSERT INTO `student` VALUES (329, '2024506001', '侯佳音', '123456', '女', '13900002013', 'houjy@stu.zhiyan.edu.cn', '湖南省长沙市', 215, 109, '2026-03-01 10:12:00', NULL);
INSERT INTO `student` VALUES (330, '2024506002', '白志远', '123456', '男', '13900002014', 'baizy@stu.zhiyan.edu.cn', '湖南省株洲市', 215, 109, '2026-03-01 10:13:00', NULL);
INSERT INTO `student` VALUES (331, '2024507001', '秦悦', '123456', '女', '13900002015', 'qinyue@stu.zhiyan.edu.cn', '湖北省武汉市', 216, 110, '2026-03-01 10:14:00', NULL);
INSERT INTO `student` VALUES (332, '2024507002', '严俊杰', '123456', '男', '13900002016', 'yanjj@stu.zhiyan.edu.cn', '湖北省宜昌市', 216, 110, '2026-03-01 10:15:00', NULL);
INSERT INTO `student` VALUES (333, '2024101001', '谭丽华', '123456', '女', '13900002017', 'tanlh@stu.zhiyan.edu.cn', '四川省成都市', 217, 101, '2026-03-01 10:16:00', NULL);
INSERT INTO `student` VALUES (334, '2024101002', '卢浩天', '123456', '男', '13900002018', 'luht@stu.zhiyan.edu.cn', '四川省绵阳市', 217, 101, '2026-03-01 10:17:00', NULL);
INSERT INTO `student` VALUES (335, '2024201001', '庞雪儿', '123456', '女', '13900002019', 'pangxe@stu.zhiyan.edu.cn', '河南省郑州市', 218, 102, '2026-03-01 10:18:00', NULL);
INSERT INTO `student` VALUES (336, '2024201002', '雷霆宇', '123456', '男', '13900002020', 'leity@stu.zhiyan.edu.cn', '河南省洛阳市', 218, 102, '2026-03-01 10:19:00', NULL);

-- ----------------------------
-- Table structure for student_attendance
-- ----------------------------
DROP TABLE IF EXISTS `student_attendance`;
CREATE TABLE `student_attendance`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '考勤ID',
  `courseid` int NOT NULL COMMENT '课程ID',
  `classid` int NOT NULL COMMENT '班级ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `attenddate` date NOT NULL COMMENT '上课日期',
  `status` int NOT NULL DEFAULT 1 COMMENT '出勤状态：1出勤 2迟到 3早退 4缺勤 5请假',
  `remark` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '备注',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `teacherid` int NULL DEFAULT NULL COMMENT '记录教师ID',
  `session_id` int NULL DEFAULT NULL COMMENT '关联签到会话ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_attendance`(`courseid` ASC, `classid` ASC, `studentid` ASC, `attenddate` ASC) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE,
  INDEX `idx_classid`(`classid` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
  INDEX `idx_attenddate`(`attenddate` ASC) USING BTREE,
  CONSTRAINT `fk_attendance_course` FOREIGN KEY (`courseid`) REFERENCES `course` (`courseid`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_attendance_student` FOREIGN KEY (`studentid`) REFERENCES `student` (`studentid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 33 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '学生考勤表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of student_attendance
-- ----------------------------
INSERT INTO `student_attendance` VALUES (1, 601, 201, 301, '2026-01-04', 1, '正常出勤', '2026-01-04 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (2, 601, 201, 301, '2026-01-05', 1, '正常出勤', '2026-01-05 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (3, 601, 201, 301, '2026-01-06', 2, '迟到10分钟', '2026-01-06 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (4, 601, 201, 301, '2026-01-07', 1, '正常出勤', '2026-01-07 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (5, 601, 201, 301, '2026-01-08', 1, '正常出勤', '2026-01-08 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (6, 601, 201, 301, '2026-01-11', 1, '正常出勤', '2026-01-11 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (7, 601, 201, 301, '2026-01-12', 1, '正常出勤', '2026-01-12 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (8, 601, 201, 301, '2026-01-13', 1, '正常出勤', '2026-01-13 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (9, 601, 201, 301, '2026-01-14', 2, '迟到5分钟', '2026-01-14 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (10, 601, 201, 301, '2026-01-15', 1, '正常出勤', '2026-01-15 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (11, 602, 201, 301, '2026-01-04', 1, '正常出勤', '2026-01-04 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (12, 602, 201, 301, '2026-01-06', 1, '正常出勤', '2026-01-06 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (13, 602, 201, 301, '2026-01-08', 4, '病假', '2026-01-08 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (14, 602, 201, 301, '2026-01-10', 5, '事假', '2026-01-10 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (15, 602, 201, 301, '2026-01-12', 1, '正常出勤', '2026-01-12 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (16, 602, 201, 301, '2026-01-14', 1, '正常出勤', '2026-01-14 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (17, 602, 201, 301, '2026-01-16', 1, '正常出勤', '2026-01-16 11:47:35', 401, NULL);
INSERT INTO `student_attendance` VALUES (18, 603, 201, 301, '2026-01-05', 1, '正常出勤', '2026-01-05 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (19, 603, 201, 301, '2026-01-07', 1, '正常出勤', '2026-01-07 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (20, 603, 201, 301, '2026-01-09', 1, '正常出勤', '2026-01-09 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (21, 603, 201, 301, '2026-01-11', 3, '早退15分钟', '2026-01-11 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (22, 603, 201, 301, '2026-01-13', 1, '正常出勤', '2026-01-13 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (23, 603, 201, 301, '2026-01-15', 1, '正常出勤', '2026-01-15 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (24, 603, 201, 301, '2026-01-17', 1, '正常出勤', '2026-01-17 11:47:35', 402, NULL);
INSERT INTO `student_attendance` VALUES (25, 673, 201, 301, '2026-01-18', 2, '', '2026-01-18 18:56:17', 401, NULL);
INSERT INTO `student_attendance` VALUES (26, 673, 201, 302, '2026-01-18', 1, '', '2026-01-18 18:56:17', 401, NULL);
INSERT INTO `student_attendance` VALUES (27, 673, 201, 309, '2026-01-18', 1, '', '2026-01-18 18:56:17', 401, NULL);
INSERT INTO `student_attendance` VALUES (28, 601, 201, 301, '2026-06-02', 1, '课堂签到', '2026-06-02 15:36:34', 401, 2);
INSERT INTO `student_attendance` VALUES (29, 601, 201, 302, '2026-06-02', 4, '未签到', '2026-06-02 15:37:11', 401, 1);
INSERT INTO `student_attendance` VALUES (30, 601, 201, 312, '2026-06-02', 4, '未签到', '2026-06-02 15:37:11', 401, 1);
INSERT INTO `student_attendance` VALUES (31, 601, 202, 310, '2026-06-02', 1, '签到码签到', '2026-06-02 16:20:40', 401, 4);
INSERT INTO `student_attendance` VALUES (32, 601, 202, 303, '2026-06-02', 1, '教师代签', '2026-06-02 16:22:46', 401, 4);

-- ----------------------------
-- Table structure for student_badge
-- ----------------------------
DROP TABLE IF EXISTS `student_badge`;
CREATE TABLE `student_badge`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `badge_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '徽章编码',
  `obtain_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_stu_badge`(`studentid` ASC, `badge_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1608 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生徽章表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of student_badge
-- ----------------------------
INSERT INTO `student_badge` VALUES (1, 301, 'FIRST_SUBMIT', '2026-06-08 11:03:00');
INSERT INTO `student_badge` VALUES (1602, 301, 'POINTS_100', '2026-05-20 18:30:00');
INSERT INTO `student_badge` VALUES (1603, 302, 'FIRST_SUBMIT', '2026-03-05 11:20:00');
INSERT INTO `student_badge` VALUES (1604, 303, 'FIRST_SUBMIT', '2026-03-06 09:15:00');
INSERT INTO `student_badge` VALUES (1605, 304, 'FIRST_SUBMIT', '2026-03-06 14:40:00');
INSERT INTO `student_badge` VALUES (1606, 309, 'FIRST_SUBMIT', '2026-03-10 16:00:00');
INSERT INTO `student_badge` VALUES (1607, 301, 'CHECKIN_7', '2026-04-12 08:00:00');

-- ----------------------------
-- Table structure for student_favorite
-- ----------------------------
DROP TABLE IF EXISTS `student_favorite`;
CREATE TABLE `student_favorite`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_id` int NOT NULL COMMENT '学生ID',
  `homework_id` int NOT NULL COMMENT '作业ID',
  `question_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题目ID',
  `question_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '题目内容快照',
  `explain_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '讲解内容快照',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_student`(`student_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1703 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生收藏表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of student_favorite
-- ----------------------------
INSERT INTO `student_favorite` VALUES (1701, 301, 801, 'q_1766841465392_9wfbsb', '在 Java 中，final关键字修饰的变量只能被赋值一次，且赋值后不可修改。', 'final 修饰基本类型变量时值不可变；修饰引用类型变量时引用不可变，但对象内部状态仍可修改，这是常见易错点。', '2026-06-08 21:00:00');
INSERT INTO `student_favorite` VALUES (1702, 302, 801, 'q_1766841465392_bwub5m', 'Java 中的ArrayList底层是基于链表实现的，而LinkedList底层是基于数组实现的。', '两者说反了：ArrayList 基于动态数组，随机访问 O(1)；LinkedList 基于双向链表，头尾插删 O(1)。选型时按访问模式决定。', '2026-06-09 19:30:00');

-- ----------------------------
-- Table structure for student_points
-- ----------------------------
DROP TABLE IF EXISTS `student_points`;
CREATE TABLE `student_points`  (
  `studentid` int NOT NULL COMMENT '学生ID',
  `total_points` int NULL DEFAULT 0 COMMENT '累计积分',
  `level` int NULL DEFAULT 1 COMMENT '等级',
  `continuous_days` int NULL DEFAULT 0 COMMENT '连续打卡天数',
  `last_checkin` date NULL DEFAULT NULL COMMENT '最近打卡日期',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`studentid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生积分汇总表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of student_points
-- ----------------------------
INSERT INTO `student_points` VALUES (301, 10, 1, 1, '2026-06-11', '2026-06-11 12:51:45');
INSERT INTO `student_points` VALUES (302, 15, 1, 1, '2026-06-09', '2026-06-09 08:10:00');
INSERT INTO `student_points` VALUES (303, 10, 1, 0, NULL, '2026-06-08 21:00:00');
INSERT INTO `student_points` VALUES (304, 30, 1, 0, NULL, '2026-06-28 11:00:00');
INSERT INTO `student_points` VALUES (309, 10, 1, 0, NULL, '2026-06-10 09:00:00');
INSERT INTO `student_points` VALUES (310, 10, 1, 0, NULL, '2026-06-07 10:04:41');
INSERT INTO `student_points` VALUES (321, 10, 1, 0, NULL, '2026-06-09 19:35:00');
INSERT INTO `student_points` VALUES (325, 10, 1, 0, NULL, '2026-06-09 17:05:00');
INSERT INTO `student_points` VALUES (333, 20, 1, 0, NULL, '2026-06-15 10:00:00');

-- ----------------------------
-- Table structure for student_profile
-- ----------------------------
DROP TABLE IF EXISTS `student_profile`;
CREATE TABLE `student_profile`  (
  `student_id` int NOT NULL COMMENT '学生ID',
  `profile_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '6维画像JSON（知识基础/认知风格/易错点偏好/学习目标/学习节奏/兴趣方向）',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '画像一句话概述',
  `update_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`student_id`) USING BTREE,
  CONSTRAINT `fk_profile_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`studentid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '对话式动态学习画像' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of student_profile
-- ----------------------------
-- 注意 JSON 形状：必须是 {"dimensions":{"<六维key>":{"name","score","label","note"}}}
-- 六维 key 与 LearningProfileService.DIMENSIONS / LearningProfile.vue 严格一致，
-- 少一层 dimensions 或 key 写错都不会报错——前端会静默落到「score=0 / 待完善」。
-- 下列 score 为演示种子值，由原数据的定性描述映射而来（视觉型/后端方向/并发偏弱 等）。
INSERT INTO `student_profile` VALUES (301, '{\"dimensions\":{\"knowledgeBase\":{\"name\":\"知识基础\",\"score\":72,\"label\":\"基础扎实\",\"note\":\"面向对象设计与 SQL 掌握较好\"},\"cognitiveStyle\":{\"name\":\"认知风格\",\"score\":68,\"label\":\"视觉型\",\"note\":\"偏好通过图解与源码理解原理\"},\"errorPreference\":{\"name\":\"易错点偏好\",\"score\":45,\"label\":\"并发偏弱\",\"note\":\"多线程与 JVM 调优易失分\"},\"learningGoal\":{\"name\":\"学习目标\",\"score\":75,\"label\":\"后端方向\",\"note\":\"目标为 Java 后端实习\"},\"learningPace\":{\"name\":\"学习节奏\",\"score\":70,\"label\":\"中等偏快\",\"note\":\"节奏中等偏快，习惯晚上学习\"},\"interest\":{\"name\":\"兴趣方向\",\"score\":80,\"label\":\"后端与算法\",\"note\":\"兴趣集中在后端开发与算法竞赛\"}}}', '视觉型学习者，偏好通过图解和源码理解原理；目标是 Java 后端方向，需重点补强并发编程。', '2026-06-09 22:00:00');
INSERT INTO `student_profile` VALUES (304, '{\"dimensions\":{\"knowledgeBase\":{\"name\":\"知识基础\",\"score\":66,\"label\":\"基础尚可\",\"note\":\"文档写作与团队协作较为突出\"},\"cognitiveStyle\":{\"name\":\"认知风格\",\"score\":70,\"label\":\"实践型\",\"note\":\"偏好动手实践与项目驱动\"},\"errorPreference\":{\"name\":\"易错点偏好\",\"score\":50,\"label\":\"建模偏弱\",\"note\":\"UML 建模与需求分析易失分\"},\"learningGoal\":{\"name\":\"学习目标\",\"score\":72,\"label\":\"项目主导\",\"note\":\"目标为主导一次课程设计项目\"},\"learningPace\":{\"name\":\"学习节奏\",\"score\":62,\"label\":\"中等\",\"note\":\"节奏中等，习惯下午学习\"},\"interest\":{\"name\":\"兴趣方向\",\"score\":74,\"label\":\"产品与协作\",\"note\":\"兴趣在产品设计与项目管理\"}}}', '实践型学习者，擅长文档与协作，软件工程方法论是当前提升重点。', '2026-06-10 17:30:00');

-- ----------------------------
-- Table structure for student_wechat_binding
-- ----------------------------
DROP TABLE IF EXISTS `student_wechat_binding`;
CREATE TABLE `student_wechat_binding`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `studentid` int NOT NULL,
  `appid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `openid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `unionid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `bindtime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `lastlogintime` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wechat_app_openid`(`appid` ASC, `openid` ASC) USING BTREE,
  UNIQUE INDEX `uk_wechat_app_student`(`appid` ASC, `studentid` ASC) USING BTREE,
  INDEX `idx_wechat_studentid`(`studentid` ASC) USING BTREE,
  CONSTRAINT `fk_wechat_binding_student` FOREIGN KEY (`studentid`) REFERENCES `student` (`studentid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of student_wechat_binding
-- ----------------------------
INSERT INTO `student_wechat_binding` VALUES (1, 301, 'wx04743e25872ee4ae', 'oNZ0L6zLzYvJ8OLuV8ns82WsrD8s', NULL, '2026-07-18 20:27:13', '2026-07-18 20:43:18');

-- ----------------------------
-- Table structure for student_wrong_question
-- ----------------------------
DROP TABLE IF EXISTS `student_wrong_question`;
CREATE TABLE `student_wrong_question`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `courseid` int NULL DEFAULT NULL COMMENT '课程ID',
  `sourceid` int NULL DEFAULT NULL COMMENT '来源作业/考试ID',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'homework' COMMENT '来源：homework/exam',
  `question_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题目ID',
  `question_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '题型',
  `question_title` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '题干',
  `options_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '选项JSON',
  `correct_answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '正确答案',
  `student_answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '学生答案',
  `wrong_count` int NULL DEFAULT 1 COMMENT '累计错误次数',
  `mastered` int NULL DEFAULT 0 COMMENT '是否已掌握：0否 1是',
  `last_wrong_time` datetime NULL DEFAULT NULL COMMENT '最近错误时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收录时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_stu_q_src`(`studentid` ASC, `question_id` ASC, `source` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
  INDEX `idx_courseid`(`courseid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1807 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生错题本表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of student_wrong_question
-- ----------------------------
INSERT INTO `student_wrong_question` VALUES (1801, 301, 601, 801, 'homework', 'q_1766841465392_bwub5m', 'judge', 'Java 中的ArrayList底层是基于链表实现的，而LinkedList底层是基于数组实现的。', '[]', 'false', 'true', 2, 0, '2026-06-08 20:00:00', '2026-06-01 19:00:00');
INSERT INTO `student_wrong_question` VALUES (1802, 301, 601, 801, 'homework', 'q_hashmap_01', 'single', 'HashMap 在 JDK8 中链表转红黑树的阈值是？', '[\"6\",\"8\",\"16\",\"32\"]', '1', '2', 1, 1, '2026-06-05 21:00:00', '2026-06-05 21:00:00');
INSERT INTO `student_wrong_question` VALUES (1803, 302, 601, 801, 'homework', 'q_1766841465392_9wfbsb', 'judge', '在 Java 中，final关键字修饰的变量只能被赋值一次，且赋值后不可修改。', '[]', 'true', 'false', 1, 0, '2026-06-08 20:30:00', '2026-06-08 20:30:00');
INSERT INTO `student_wrong_question` VALUES (1804, 309, 602, 826, 'homework', 'q_ds_001', 'single', '在长度为 n 的顺序表中插入一个元素，平均需要移动多少个元素？', '[\"n\",\"n/2\",\"n-1\",\"1\"]', '1', '0', 2, 0, '2026-06-10 15:00:00', '2026-06-07 14:00:00');
INSERT INTO `student_wrong_question` VALUES (1805, 309, 602, 826, 'homework', 'q_ds_002', 'judge', '单链表删除节点的时间复杂度始终为 O(1)。', '[]', 'false', 'true', 1, 1, '2026-06-09 16:00:00', '2026-06-09 16:00:00');
INSERT INTO `student_wrong_question` VALUES (1806, 321, 685, 829, 'homework', 'q_net_001', 'single', '将网络 192.168.10.0/24 划分为 4 个等大小的子网，每个子网的可用主机数是多少？', '[\"62\",\"64\",\"126\",\"30\"]', '0', '1', 1, 0, '2026-06-09 19:30:00', '2026-06-09 19:30:00');

-- ----------------------------
-- Table structure for studenthomework
-- ----------------------------
DROP TABLE IF EXISTS `studenthomework`;
CREATE TABLE `studenthomework`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '学生作业ID',
  `homeworkid` int NOT NULL COMMENT '作业ID',
  `studentid` int NOT NULL COMMENT '学生ID',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '作业内容（在线答题）',
  `filepath` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件路径（文档上传）',
  `score` decimal(5, 2) NULL DEFAULT NULL COMMENT '成绩',
  `comment` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '评语',
  `status` int NULL DEFAULT 0 COMMENT '状态：0未提交 1已提交 2已批改',
  `submittime` datetime NULL DEFAULT NULL COMMENT '提交时间',
  `gradetime` datetime NULL DEFAULT NULL COMMENT '批改时间',
  `grading_status` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'AI评分状态：PENDING/GRADING/COMPLETED/FAILED',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_homeworkid`(`homeworkid` ASC) USING BTREE,
  INDEX `idx_studentid`(`studentid` ASC) USING BTREE,
  CONSTRAINT `fk_submission_homework` FOREIGN KEY (`homeworkid`) REFERENCES `homework` (`homeworkid`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_submission_student` FOREIGN KEY (`studentid`) REFERENCES `student` (`studentid`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1039 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '学生作业表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of studenthomework
-- ----------------------------
INSERT INTO `studenthomework` VALUES (901, 801, 301, '<p>1. 素数程序代码如下：</p><p>public class Prime { ... }</p><p>2. 计算器类实现：</p><p>public class Calculator { ... }</p>', NULL, 85.00, '代码逻辑清晰，但缺少注释', 2, '2025-12-24 14:00:00', '2025-12-24 16:00:00', NULL);
INSERT INTO `studenthomework` VALUES (902, 801, 302, '<p>1. 素数判断程序：</p><p>for(int i=2; i<=100; i++) { ... }</p>', NULL, 78.00, '基本完成，但效率可以优化', 2, '2025-12-24 15:00:00', '2025-12-24 17:00:00', NULL);
INSERT INTO `studenthomework` VALUES (903, 801, 303, '<p>正在完成中...</p>', NULL, 90.00, '完成良好，继续努力', 2, '2025-12-24 18:00:00', '2025-12-25 16:09:17', NULL);
INSERT INTO `studenthomework` VALUES (906, 804, 304, '<p>SQL查询答案：</p><p>SELECT * FROM student s JOIN classinfo c ON s.classid = c.classid</p>', NULL, 88.00, '查询语句正确，理解深入', 2, '2025-12-24 16:00:00', '2025-12-24 18:00:00', NULL);
INSERT INTO `studenthomework` VALUES (911, 804, 302, '<p>22222222223</p><p><br></p><p>4333333333333333</p>', NULL, NULL, NULL, 1, '2025-12-25 21:39:25', NULL, NULL);
INSERT INTO `studenthomework` VALUES (916, 802, 303, '/**\r\n * 学生实体类，封装学生基本信息\r\n */\r\npublic class Student {\r\n    // 私有属性\r\n    private String id;\r\n    private String name;\r\n    private int age;\r\n    private String gender;\r\n    private String major;\r\n\r\n    // 构造方法\r\n    public Student(String id, String name, int age, String gender, String major) {\r\n        this.id = id;\r\n        this.name = name;\r\n        // 简单数据校验\r\n        this.age = age > 0 ? age : 0;\r\n        this.gender = (\"男\".equals(gender) || \"女\".equals(gender)) ? gender : \"未知\";\r\n        this.major = major;\r\n    }\r\n\r\n    // getter和setter方法\r\n    public String getId() {\r\n        return id;\r\n    }\r\n\r\n    public String getName() {\r\n        return name;\r\n    }\r\n\r\n    public void setName(String name) {\r\n        this.name = name;\r\n    }\r\n\r\n    public int getAge() {\r\n        return age;\r\n    }\r\n\r\n    public void setAge(int age) {\r\n        if (age > 0) {\r\n            this.age = age;\r\n        }\r\n    }\r\n\r\n    public String getGender() {\r\n        return gender;\r\n    }\r\n\r\n    public void setGender(String gender) {\r\n        if (\"男\".equals(gender) || \"女\".equals(gender)) {\r\n            this.gender = gender;\r\n        }\r\n    }\r\n\r\n    public String getMajor() {\r\n        return major;\r\n    }\r\n\r\n    public void setMajor(String major) {\r\n        this.major = major;\r\n    }\r\n\r\n    // 重写toString，方便打印\r\n    @Override\r\n    public String toString() {\r\n        return \"学生ID：\" + id + \" | 姓名：\" + name + \" | 年龄：\" + age + \" | 性别：\" + gender + \" | 专业：\" + major;\r\n    }\r\n}', '2025/12/20251227_57d86e2c24754650b8cb34465026e8d5.doc', 85.00, '[AI自动评分] 学生实体类的设计较为完整，包括基本的属性、构造方法、getter和setter方法以及重写的toString方法。数据校验部分体现了对数据完整性的考虑。但是，未提供类图设计和核心类的测试代码，因此内容完整性和准确性有所欠缺。建议补充类图和测试代码，以提高作业质量。', 2, '2025-12-27 20:54:11', '2025-12-27 20:54:15', NULL);
INSERT INTO `studenthomework` VALUES (919, 809, 302, '{\"version\":1,\"questions\":[{\"id\":\"q_1766841465392_9wfbsb\",\"type\":\"judge\",\"title\":\"在 Java 中，final关键字修饰的变量只能被赋值一次，且赋值后不可修改。\",\"score\":5,\"options\":[]},{\"id\":\"q_1766841465392_bwub5m\",\"type\":\"judge\",\"title\":\"Java 中的ArrayList底层是基于链表实现的，而LinkedList底层是基于数组实现的。\",\"score\":5,\"options\":[]},{\"id\":\"q_1766841465392_o1s7ff\",\"type\":\"single\",\"title\":\"以下哪个关键字用于定义 Java 中的抽象方法？\",\"score\":5,\"options\":[\"static\",\"final\",\"abstract\",\"interface\"]}],\"answers\":{\"q_1766841465392_9wfbsb\":false,\"q_1766841465392_bwub5m\":true,\"q_1766841465392_o1s7ff\":3}}', NULL, 0.00, '[AI自动评分] 得分: 0.0/100\n客观题: 0.0/100.0 (对0错3)\n你的努力值得肯定，但这次的成绩还有提升空间。对于Java基础知识的掌握，你已展现出一定的理解，但细节方面还需加强。建议多复习基础概念，特别是关于关键字和集合类的实现方式。继续加油，你一定可以做得更好！', 1, '2025-12-27 23:10:16', '2025-12-27 23:10:16', NULL);
INSERT INTO `studenthomework` VALUES (920, 810, 302, '{\"version\":1,\"questions\":[{\"id\":\"q_1766937464172_dvnkv4\",\"type\":\"single\",\"title\":\"在Java中，以下哪个关键字用来声明一个变量？\",\"score\":5,\"options\":[\"int\",\"float\",\"double\",\"var\"]},{\"id\":\"q_1766937464172_x2ijck\",\"type\":\"single\",\"title\":\"以下哪个方法可以用来获取字符串的长度？\",\"score\":5,\"options\":[\"length()\",\"size()\",\"getLength()\",\"length\"]},{\"id\":\"q_1766937464172_kacisg\",\"type\":\"multi\",\"title\":\"在Java中，以下哪些是基本数据类型？\",\"score\":5,\"options\":[\"int\",\"String\",\"boolean\",\"Object\"]},{\"id\":\"q_1766937464172_3uaoml\",\"type\":\"multi\",\"title\":\"以下哪些是Java中的关键字？\",\"score\":5,\"options\":[\"class\",\"main\",\"true\",\"integer\"]},{\"id\":\"q_1766937464172_m4wn2u\",\"type\":\"judge\",\"title\":\"Java中的所有类都继承自Object类。\",\"score\":5,\"options\":[]},{\"id\":\"q_1766937464172_bpzde2\",\"type\":\"judge\",\"title\":\"Java中的String是不可变的。\",\"score\":5,\"options\":[]},{\"id\":\"q_1766937464172_6mepv4\",\"type\":\"short\",\"title\":\"简述Java中的面向对象编程的基本概念。\",\"score\":5,\"options\":[]}],\"answers\":{\"q_1766937464172_dvnkv4\":2,\"q_1766937464172_x2ijck\":2,\"q_1766937464172_kacisg\":[1,2],\"q_1766937464172_3uaoml\":[1,3],\"q_1766937464172_m4wn2u\":false,\"q_1766937464172_bpzde2\":false,\"q_1766937464172_6mepv4\":\"111111111111\"}}', NULL, 0.00, '[AI自动评分] 得分: 0.0/100\n客观题: 0.0/85.7 (对0错6)\n简答题: 0.0/14.3\n你今天的努力值得肯定，虽然成绩不尽如人意，但你的进步空间很大。在Java基础知识的掌握上，建议你多关注关键字和基本数据类型的区分，错题部分要重点复习。继续加油，相信你会越来越棒！', 1, '2025-12-28 23:58:29', '2025-12-28 23:58:29', NULL);
INSERT INTO `studenthomework` VALUES (921, 810, 304, '{\"version\":1,\"questions\":[{\"id\":\"q_1766937464172_dvnkv4\",\"type\":\"single\",\"title\":\"在Java中，以下哪个关键字用来声明一个变量？\",\"score\":5,\"options\":[\"int\",\"float\",\"double\",\"var\"]},{\"id\":\"q_1766937464172_x2ijck\",\"type\":\"single\",\"title\":\"以下哪个方法可以用来获取字符串的长度？\",\"score\":5,\"options\":[\"length()\",\"size()\",\"getLength()\",\"length\"]},{\"id\":\"q_1766937464172_kacisg\",\"type\":\"multi\",\"title\":\"在Java中，以下哪些是基本数据类型？\",\"score\":5,\"options\":[\"int\",\"String\",\"boolean\",\"Object\"]},{\"id\":\"q_1766937464172_3uaoml\",\"type\":\"multi\",\"title\":\"以下哪些是Java中的关键字？\",\"score\":5,\"options\":[\"class\",\"main\",\"true\",\"integer\"]},{\"id\":\"q_1766937464172_m4wn2u\",\"type\":\"judge\",\"title\":\"Java中的所有类都继承自Object类。\",\"score\":5,\"options\":[]},{\"id\":\"q_1766937464172_bpzde2\",\"type\":\"judge\",\"title\":\"Java中的String是不可变的。\",\"score\":5,\"options\":[]},{\"id\":\"q_1766937464172_6mepv4\",\"type\":\"short\",\"title\":\"简述Java中的面向对象编程的基本概念。\",\"score\":5,\"options\":[]}],\"answers\":{\"q_1766937464172_dvnkv4\":0,\"q_1766937464172_x2ijck\":0,\"q_1766937464172_kacisg\":[2],\"q_1766937464172_3uaoml\":[0],\"q_1766937464172_m4wn2u\":true,\"q_1766937464172_bpzde2\":true,\"q_1766937464172_6mepv4\":\"232352\"}}', NULL, 57.16, '[AI自动评分] 得分: 57.2/100\n客观题: 57.2/85.7 (对4错2)\n简答题: 0.0/14.3\n你的努力值得肯定，继续保持！虽然有些概念理解上有偏差，比如对Java关键字的掌握，但你的多选题做得不错。加强数据类型和关键字的学习，相信你会取得更好的成绩。加油！', 1, '2025-12-29 11:28:12', '2025-12-29 11:28:12', NULL);
INSERT INTO `studenthomework` VALUES (922, 815, 301, 'My C answer', NULL, 70.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (923, 815, 302, 'My C answer', NULL, 75.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (925, 816, 301, 'My Java answer', NULL, 76.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (926, 816, 302, 'My Java answer', NULL, 95.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (928, 817, 301, 'Linked List Code', NULL, 84.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (929, 817, 302, 'Linked List Code', NULL, 73.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (931, 818, 301, 'TCP Handshake', NULL, 89.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (932, 818, 302, 'TCP Handshake', NULL, 78.00, NULL, 2, '2026-01-05 13:33:51', '2026-01-05 13:33:51', NULL);
INSERT INTO `studenthomework` VALUES (934, 819, 304, '{\"version\":1,\"questions\":[{\"id\":\"q_1767595570574_1oha8a\",\"type\":\"blank\",\"title\":\"在Python中，如何定义一个列表？\",\"score\":5,\"options\":[]},{\"id\":\"q_1767595570574_bfyw12\",\"type\":\"blank\",\"title\":\"以下哪个是Python中的条件语句？\",\"score\":5,\"options\":[]},{\"id\":\"q_1767595570574_umaqrh\",\"type\":\"single\",\"title\":\"在Python中，如何遍历一个字典的键值对？\",\"score\":5,\"options\":[\"a) for key in dict:\",\"b) for key, value in dict.items():\",\"c) for key, value in dict:\",\"d) for key in dict.items()\"]},{\"id\":\"q_1767595570574_rpcfwq\",\"type\":\"multi\",\"title\":\"以下哪些是Python中的数据类型？\",\"score\":5,\"options\":[\"a) int\",\"b) float\",\"c) string\",\"d) list\",\"e) dict\",\"f) bool\"]},{\"id\":\"q_1767595570574_ig9zpu\",\"type\":\"multi\",\"title\":\"在Python中，以下哪些操作符用于比较？\",\"score\":5,\"options\":[\"a) ==\",\"b) !=\",\"c) >\",\"d) <\",\"e) >=\",\"f) <=\"]},{\"id\":\"q_1767595570574_ocx277\",\"type\":\"judge\",\"title\":\"在Python中，字符串是不可变的。\",\"score\":5,\"options\":[]},{\"id\":\"q_1767595570574_ygzzz5\",\"type\":\"judge\",\"title\":\"Python中的列表是可变的数据类型。\",\"score\":5,\"options\":[]}],\"answers\":{\"q_1767595570574_1oha8a\":\"list\",\"q_1767595570574_bfyw12\":\"if\",\"q_1767595570574_umaqrh\":1,\"q_1767595570574_rpcfwq\":[1,2,3],\"q_1767595570574_ig9zpu\":[2,1],\"q_1767595570574_ocx277\":true,\"q_1767595570574_ygzzz5\":false}}', NULL, 28.58, '[AI自动评分] 得分: 28.6/100\n客观题: 28.6/100.0 (对2错5)\n你的基础知识掌握得不错，但在细节上还需要更加严谨。对于列表的定义和条件语句的选择，记得要区分语法和选项。建议加强练习，特别是对于多选题和判断题，确保理解题意后再作答。继续努力，你一定能取得更好的成绩！', 2, '2026-01-05 14:46:54', '2026-01-05 14:47:23', NULL);
INSERT INTO `studenthomework` VALUES (935, 805, 301, '原创性声明\n本人呈交的毕业论文，是在导师的指导下，独立进行研究工作所取得的成果，所有数据、图片资料真实可靠。尽我所知，除文中已经注明引用的内容外，本毕业论文的研究成果不包含他人享有著作权的内容。对本论文所涉及的研究工作做出贡献的其他个人和集体，均已在文中以明确的方式标明。本毕业论文的知识产权归属于培养单位。\n本人签名：                     日期：                    \n\n摘  要\n非物质文化遗产是民族文化的重要组成部分，非物质文化遗产的保护和传承对文化多样性和社会发展具有重要意义。随着互联网的兴起，数字化包含文化资产已成为当前的热点研究领域。同时，非遗文化的数字化管理方法也存在诸多问题，非遗数据管理分散、统计数据不完整、数据存储缺乏规范性等,导致非遗数字化效果对非遗文化的保护和选优方面发挥作用有限，对国内数量分散的非遗数据难以满足国内非遗数据管理的需求。针对此问题，本研究设计了非遗民族文化资源管理系统，旨在为非遗文化的管理和传承提供一个高效便捷的Web平台。该系统采用Spring Boot、Vue.js、MySQL等技术对非遗数据进行采集存储、检索和展示。通过对非遗资源分类管理、资源在线展示、非遗资源消息管理、传承人信息管理、用户评论等功能进行实现，\n本系统实现不仅实现了非遗文化资源的在线管理方式，还提高了非遗文化的在线检索的效率，同时也为政府、研究机构以及社会群众提供了一个高效的在线非遗资源浏览平台，从而进一步的推动非遗文化在社会当中的的数字化保护与传播。\n关键词：非遗数字化；文化保护；Spring Boot；Vue.js\nABSTRACT\n With the development of the Internet, the way of digitizing cultural relics has become a hot spot for research. Intangible cultural heritage is an important component of national culture, and its protection and inheritance has a profound influence on cultural diversity and social development. So we developed and implemented a web-based management system for intangible cultural heritage, aiming to provide a convenient and efficient platform for the management and sharing of intangible cultural heritage information. The digital effect of the intangible cultural heritage was not satisfactory, and the intangible cultural heritage data were widely scattered, which was not easy to meet the management needs of intangible cultural heritage data in China. The main features of the system are that it collects, stores, searches and displays the data of intangible cultural heritage, using the technology of Spring Boot, Vue.js and MySQL. By implementing functions such as classified management of intangible cultural heritage resources, online display of resources, message management of intangible cultural heritage resources, information management of inheritors, and user comments,\nThis system not only realizes the online management of intangible cultural heritage resources, but also improves the efficiency of online retrieval of intangible cultural heritage. At the same time, it provides an efficient online platform for governments, research institutions, and the general public to browse intangible cultural heritage resources, further promoting the digital protection and dissemination of intangible cultural heritage in society.\nKey words:Digitalization of Intangible Cultural Heritage；Cultural Protection；Spring Boot； Vue.js\n目  录\n摘  要	I\nABSTRACT	II\n1 绪论	1\n1.1 研究的目的及意义	1\n1.2 国内外研究现状	1\n1.2.1 国内研究现状	1\n1.2.2 国外研究现状	2\n1.3 论文主要工作及章节安排	2\n1.4 本章小结	3\n2 系统需求分析	4\n2.1 系统整体分析	4\n2.2 功能需求分析	4\n2.2.1 管理端需求	4\n2.2.2 用户端需求	5\n2.3 可行性分析	6\n2.3.1 技术可行性分析	6\n2.3.2 经济可行性分析	6\n2.3.3 操作可行性分析	6\n2.4 本章小结	7\n3 系统开发技术介绍	8\n3.1 开发技术	8\n3.1.1 Spring Boot	8\n3.1.2 Java语言	8\n3.1.3 MySQL	8\n3.1.4 Vue.js	8\n3.2 开发工具	9\n3.2.1 Visual Studio Code	9\n3.2.2 IntelliJ IDEA	9\n3.2.3 Navicat Premium	9\n3.3 开发环境	9\n3.4 本章小结	10\n4 系统总体设计	11\n4.1 概要设计	11\n4.2 系统功能设计	11\n4.2.1 管理员功能架构设计	11\n4.2.2 用户功能架构设计	12\n4.3 数据库设计	12\n4.3.1 E-R图	12\n4.3.2 数据库表结构设计	15\n4.4 本章小结	19\n5 系统详细设计与实现	20\n5.1 前端系统设计	20\n5.1.1 个人中心	20\n5.1.2 文化资源浏览	21\n5.1.3 非物质文化浏览功能	22\n5.1.4 评价功能	23\n5.1.5 购物车	23\n5.2 后端系统设计	24\n5.2.1 用户管理模块	24\n5.2.2 资源分类管理模块	25\n5.2.3 资源信息管理模块	26\n5.2.4 申请管理模块	27\n5.2.5 评价管理模块	28\n5.2.6 订单管理模块	29\n5.3 本章小结	29\n6 系统测试	30\n6.1 测试内容	30\n6.2 测试用例	30\n6.2.1 登录功能测试	30\n6.2.2 添加类别功能测试	30\n6.2.3 添加非遗展示资源功能测试	31\n6.3 测试结果	31\n6.4 本章小结	31\n结  论	32\n参考文献	33\n致  谢	34\n1 绪论\n1.1 研究的目的及意义\n非物质文化遗产作为民族传统文化的重要构成，不仅包含着丰厚的历史、文化和社会价值，是民族精神的象征，也是人类历史文明中的重要标识。然而，随着现代化进程的加速，传统的民族文化传承，尤其是非物质文化遗产的保护、传承与传播面临着极大的挑战[1]。大部分传统文化形式存在着逐渐消失的危机，尤其是随着全球化和城市化的不断加深，很多非遗文化资源面临着逐步消失的危机，传统的保护与管理措施已经无法有效应对越来越高的需求与复杂的局面。传统非遗文化管理形式较多依靠纸质与人工记录，存在着信息更新不及时、查询繁琐、容易损坏等缺点，难以适应日益复杂的实际需求。\n本文设计了一个非遗民族文化资源管理系统，将利用信息技术为非遗文化建立一个更方便、快捷和智能化的资源管理平台，为非遗资源的保存管理、传播和利用提供更好的数字化支持。通过该系统将非遗资源信息整合管理起来，并以数字化的形式展示出来，包括非遗项目的基本信息、传承人信息、相关资源信息等多个方面的内容，形成一个更加完整且系统的非遗文化数据库，在线实现对非遗项目及相关资源的查询、浏览、管理和传承，使非遗文化得到更全面、方便和快捷的保护和传播，突破非遗项目传承与管理方式的局限性，使非遗文化得到更加长期的保存与流传。\n本系统为非遗文化的保护、管理和传播提供了重要的数字化支持，极大提升了其社会价值、学术意义和实践意义。希望通过本系统不仅能够高效的帮助非遗文化的保存与传承，同时还能够在全球的范围推动传播文化的多样性交流与理解，能够让群众能够更好地了解和接触到非遗文化，提高对非遗文化的认知与兴趣，从而推动文化的传承和发展，为全球非物质文化遗产的保护工作贡献力量[2]。\n1.2 国内外研究现状\n1.2.1 国内研究现状\n随着非物质文化遗产保护意识的加强，国内也逐渐展开了非遗资源的数字化保护研究，部分文化机构、学者纷纷利用信息技术，尤其是数据库与多媒体技术对非遗资源进行数字化存档和展示。近年来，国内某些地区的非遗数据库已经初步建立，涵盖民间艺术、传统技艺等。其中对一些具体的非遗档案的数字化研究，有郑奥成等对体育类非遗数字化传播与发展探析，分析了疫情背景下体育类非遗数字化的发展情况[3]。张楠、余辰浩等从数字化建设角度对非遗保护提出建议[4][5]。孙传明等通过对广西少数民族非遗数字化现状的详细数据分析和案例总结提出了一系列结论和建议，为少数民族非遗数字化建设提供了一定的参考依据[6]。满雪薇分别基于马特兰德政策执行的模糊-冲突模型、麦克拉夫林互动模型建立不同的理论模型，系统阐明抚顺市非物质文化遗产政策执行实际情况，分析问题及成因，总结国内外非物质文化遗产政策执行的成功经验，并依据模型分类提出不同的政策执行建议[7]。\n1.2.2 国外研究现状\n世界范围来看，我国采取相同措施开展非物质文化遗产保护工作的国家有很多，即通过设置各级文化行政管理部门，由政府及其组成部门按照其制定的政策开展非物质文化遗产有关工作。国外学术界关于非物质文化遗产政策方面的研究成果主要有以下两个方面：一是非物质文化遗产研究。例如，美国学者Gregory Hansen提出在美国的非遗文化与美国的民俗学研究紧密相关，认为非遗文化和民俗文化是不可分割的[8]。澳大利亚学者LaurajaneSmith等讨论了价值的有形与无形，加深了对非遗概念的认知[9]。二是非遗保护研究。美国学者Michaela DeSoucey等研究了非遗国际公约的意义，提出通过对非遗的合理表达或理解方式缓解非遗的普遍意义与价值的紧张关系[10]。\n1.3 论文主要工作及章节安排\n在现代的发展中，全球化和数字化进程加速的背景下，非遗文化面临传承人缺失、传承方式狭窄以及传统的非遗记录方式的存储成本高、共享性差等困难。为此，本研究设计并开发了一套基于Web的非遗民族文化资源管理系统，该系统通过数字化非遗资源管理、在线展示、用户互动评论等功能模块，实现了非遗资源的整合管理与跨地域共享。技术上采用前后端分离架构，前端基于Vue.js构建响应式交互界面，后端依托Spring Boot框架，数据管理使用MySQL数据库。最终进行测试验证，保障系统运行稳定，有效提升了非遗文化的在线资源展示和对非遗文化进行数字化保护，通过资源的数据管理为非遗传承创新和政策制定提供了数字化支撑。\n为了在进行论文编写的过程中有清晰的结构框架，更好的了解和理解程序的开发流程与相关内容，将通过以下内容来对论文相关章节内容进行简要阐述：\n第一章 绪论。在本章节中将对系统开发的目的及意义相关内容进行描述，深入研究该系统在国内外环境中的研发现状，结合相关知识来对本论文的研究内容做出整体框架，并总结本论文的研究内容和整体章节安排。\n第二章 系统需求分析。在本章节将通过经济、技术、操作三个方面来对本系统进行可行性分析，分析在对系统的开发上是否具备良好的可行性，并在该章节对系统的功能需求进行基础分析，总结该系统需要实现的功能模块。\n第三章 系统开发技术介绍。在本章节中将描述系统开发过程中的开发环境，以及开发过程中所涉及到的相关技术、开发工具等信息的详细介绍。\n第四章 系统总体设计。对系统大功能模块下的功能细分信息，以及对存储程序数据的数据库表文件结构进行相关的设计等；\n第五章 系统详细设计与实现。在本章节将详细描述系统的主要功能实现界面与功能介绍的内容，也对程序操作人员操作的部分功能进行了描述；\n第六章 系统测试。描述了程序功能的测试内容，并介绍了系统测试的概念与方法，并通过相关功能测试用例来测试系统的完整性。\n结论。该章节主要对系统与论文的完成度和在开发过程当中的不足之处进行总结。\n1.4 本章小结\n非物质文化遗产身为民族精神的关键载体，在现代化进程中遭遇传承受阻以及保护手段滞后等诸多挑战，因此需要开发一个基于Web的非遗民族文化资源管理系统。本章对开发非遗文化资源管理系统的研究背景和意义进行了详细的分析，并对国内外的研究现状进行了总结。研究现状显示，国内聚焦非遗文化的数字化保护技术、国外主要依靠法律政策来保护。最后对本文的主要工作和章节安排进行了阐述。\n\n2 系统需求分析\n2.1 系统整体分析\n本系统对非遗民族文化资源进行数字化，将现有的非遗民族文化进行分类、存储展示、多条件检索，实现了非遗资源的集中管理与动态传播。系统设计了多级角色权限管理包含普通用户和管理员，提供了互动评论、非遗资源展示、用户个人中心等功能。通过非遗资源在线平台的开放与分享，为文化部门提供更加科学的数据支持，更好的提升对非遗资源的管理效率，推动非遗活态传承与全球化的传播。\n2.2 功能需求分析\n2.2.1 管理员需求分析\n管理员需要具备高效、灵活的管理能力，能够在后台管理中全面高效的处理平台内各类用户、资源、订单和评价等内容。同时为了保障文化资源的传承、展示与保护[11]，应给予管理员能够有效组织和管理用户信息的相关功能，提供方便的资源分类、信息管理、申请处理等功能。需要保证系统资源的质量和用户体验，支持系统管理中的多项功能，保障平台的良好运行，满足非遗文化资源的保护与推广。管理员用户用例图如图2.1所示，具体的功能如下：\n（1）用户管理:用户账户信息管理、用户功能权限管理、系统平台的安全稳定。\n（2）资源分类管理：对数据库中的文化资源进行分类，以方便用户浏览和选择。\n（3）资源信息管理：对上传至管理系统平台中的文化资源信息进行管理，保证资源的准确性与完整性。\n（4）申请管理：对用户上传资源的申请进行管理，上传资源必须符合平台的相关上传要求。\n（5）资源评价管理：实施对用户评价过的资源进行有效管理。同时，严格执行对用户评论内容的核实，确保所有评论的客观公正。\n（6）订单管理：对跟踪管理平台的订单信息进行管理。\n（7）系统管理：设置平台整体设置、权限控制、备份恢复等，确保平台安全稳定。\n图2.1 管理员用例图\n2.2.2 用户需求分析\n用户功能主要是为了让用户在浏览非遗文化中又更好的体验，为用户提供便捷、快速的功能，以帮助用户能够更加轻松地浏览、学习和购买各类文化资源。系统用户能够在平台上便捷的管理、查看个人信息，并能够在平台的评论中留下自己的感受。除此之外，还需要平台能够向用户提供清晰、简洁的资源展示，方便用户找到自己感兴趣的非物质文化遗产项目。平台在用户端的功能应该以用户体验为主，保障使用平台时体验流畅、便捷且有价值[12]。用户端用例图如图2.2所示，其具体功能如下：\n（1）个人中心：可以查看及管理个人信息，进行资料修改和密码变更，设置隐私，查看活动记录和订单记录。\n（2）文化资源浏览：为用户提供购买的各种文化资源商品的浏览界面，支持购买商品时按类别、价格等条件筛选查看文化资源详情。\n（3）非物质文化浏览：提供非物质文化遗产相关资源的浏览，使受众认识到每个项目的背景、传承和文化意义。\n（4）评价功能：用户可以对资源进行评价和评论，分享自己的使用体验，同时帮助其他用户做出选择。\n（5）购物车：集中管理用户选择的商品，便于修改数量、删除商品，并查看总价，提供流畅的购物结算体验。\n图2.2 用户用例图\n2.3 可行性分析\n2.3.1 技术可行性分析\n在本次系统的开发中，采用了基于Java的主流Web开发技术栈，在后端的构建上采用了Spring Boot框架进行搭建；前端则采用了Vue.js构建了响应式的功能界面；在数据库上则选用了MySQL来对系统数据进行结构化的存储和管理。系统在设计上的架构整体稳定、开发的效率高、易于维护和功能扩展，在技术方面具有良好的可行性。\n2.3.2 经济可行性分析\n本系统开发成本较低，主要成本在于开发的电脑和网络资源。开发的电脑可以使用配置一般的笔记本就可以完成。网络资源主要由学校提供，因此也不需要太多成本。在硬件和网络的投入成本近乎为零，开发主要集中于人力及时间成本，人力与时间成本可控；长期运维可通过开源技术框架降低支出。因此，在经济上可行。\n2.3.3 操作可行性分析\n在系统的操作层面上，目前市面上的电脑软件和相应配套硬件的升级和完善，现在的计算机环境是非常完善，而在该系统用户界面设计中也采用了简洁直观的设计方式，简洁的操作能让各类用户都能轻松上手。对用户的体验上也有较好的完善，确保用户能够满意是使用该系统，从而提升用户满意度和系统的使用频率。\n因此，从操作角度来看，通过对用户操作界面的简洁设计以及对系统运行环境的优化下，该系统在操作相关性上是具有较好的可行性的。\n2.4 本章小结\n本系统由管理端和用户端构成，管理端通过分类审核、权限控制保障非遗资源规范管理；用户端以多条件检索、互动评价及个性化服务优化体验，实现非遗资源的数字化保护、活态传承与全球化传播支持。系统采用Spring Boot+Vue.js+MySQL成熟技术栈，架构稳定且扩展性强；开发成本低，依托开源框架运维可控；界面简洁适配主流环境，用户操作友好。综合技术、经济与操作层面均具备可行性。\n3 系统开发技术介绍\n3.1 开发技术\n3.1.1 Spring Boot\n本文的后端架构选择Spring Boot框架。该框架的主要功能是用于加快Spring应用程序的开发效率。而作为一个开源的框架，Spring Boot集成了大量标准库及工具包，能够更加有效的帮助开发者迅速部署完备、生产就绪的Spring应用环境。\nSpring Boot无缝对接多样化数据库、消息中间件及缓存解决方案，针对非遗文化资源管理系统中用户数据的存储需求，通过借助其对关系型数据库的卓越集成能力，实现了对相关数据管理功能的高效且简便集成，提升了系统的开发效率与灵活性。\n3.1.2 Java语言\nJava则作为系统开发过程中选择的。众所周知，Java程序语言在计算机软件开发中起着非常重要的作用。在Web开发、移动应用开发、人工智能、游戏开发等多个领域中，都有着非常重要的应用，具有跨平台性、强大的类库支持和丰富的社区资源，为系统的开发提供了一个稳定而高效的开发环境。\n通过面向对象的Java编程语言特性轻松创建非遗资源管理系统的相关功能模块，例如用户、文化资源管理等，同时在Java安全、可拓展等特性的保障下，确保系统运行的稳定性以及后续的功能拓展，满足未来访问量日渐增加的文化资源管理系统功能需求，给予访问者优质的服务体验。\n3.1.3 MySQL\nMySQL是一个开源并功能强大的关系型数据库，以其表格形式的数据结构，对数据进行管理，并通过表间的关联、分表等实现复杂的数据管理与查询。它保证了数据的逻辑性和安全性，适合于对数据安全有较高要求的系统。MySQL在处理最重要的部分数据逻辑时具有显著优势，同时它小巧、功能齐全，并且遵循严格的SQL标准语法。\n3.1.4 Vue.js\nVue.js，作为一款流行的JavaScript框架，被广泛用于构建用户界面。它采用HTML、CSS以及JavaScript等标准化技术进行构建。Vue.js提供了一套基于组件的声明式编程模型，旨在帮助开发者更高效地开发用户界面。该框架不仅在生产环境中得到了广泛应用，还以其高效能和强大的扩展性，可以轻松应对大多数Web应用开发场景，且几乎不需要手动进行性能优化。Vue.js不仅能够满足中小型项目开发的需求，还能够胜任大规模应用的开发。\n在非遗文化平台的开发当中，通过Vue的响应式设计和数据绑定技术使得界面能够与后端数据实时同步，提升用户的使用体验。\n3.2 开发工具\n3.2.1 Visual Studio Code\nVisual Studio Code，简称VS Code，是Microsoft开发的轻量级代码编辑器。该编辑器免费开源，功能全面，涵盖主流程序语言的语法高亮、自定义快捷键、预定义代码片段、代码差异比较功能，且支持插件扩展。作为目前最受欢迎的代码编辑器之一，VS Code适用于多种编程语言，并拥有丰富的扩展库。VS Code不仅适合编程初学者，也适用于专业的软件开发者，因为它提供了一个高度灵活且可定制的工作环境。\n3.2.2 IntelliJ IDEA\n在系统开发中对于后端架构的设计中，使用了IntelliJ IDEA作为后端开发的平台。在开发非遗文化资源管理系统时，IntelliJ IDEA通过强大的功能和便捷的操作方式让开发系统的过程中能够更加轻松是进行功能开发。这款强大的Java集成开发环境（IDE）不仅提供了丰富的功能和工具，还以其出色的性能和用户友好的界面极大地提升了系统的开发效率。\n3.2.3 Navicat Premium\n在对MySQL的管理工具的选择上使用了Navicat Premium。Navicat Premium是一款高度综合的数据库开发工具。在构建非遗文化资源管理系统这一项目中，Navicat Premium提供了涵盖全面的数据库管理技术支持。Navicat Premium拥有众多内置快捷功能，极大地方便了开发者在相关管理逻辑方面的开发工作。\n3.3 开发环境\n处理器：Intel Core i7；\n内存：16GB；\n操作系统：Microsoft Windows 10；\n开发语言：Java；\n数据库：MySQL 8.0.26。\n3.4 本章小结\n在这一章节当中，对非遗民族文化资源管理系统的技术架构以及开发工具的选择展开了细致的说明，系统的后端运用Spring Boot框架，并且结合Java语言来达成高效开发，前端基于Vue.js框架构建出响应式界面，数据库选用MySQL，在开发工具方面，Visual Studio Code可支持前端开发与调试，IntelliJ IDEA可以提供智能后端编码支持，Navicat Premium则可优化数据库管理流程，为后续的测试与部署奠定了坚实的基础。\n\n4 系统总体设计\n4.1 概要设计\n非遗民族文化资源管理系统的总体功能架构图如图4.1所示。在设计过程中将系统的功能主要按照角色进行划分，分为两个主要部分，用户和管理员。用户模块，针对用户需求设计的功能分为：个人中心、文化资源浏览、非物质文化浏览、评价功能以及购物车功能，满足用户使用需求。管理员模块则涵盖了用户管理、资源管理、订单管理及系统管理等功能模块，保障了管理员对系统信息的全面管理。整个系统的结构清晰，功能模块划分明确，能够高效的满足非物质文化网站的使用和管理需求。\n图4.1 系统功能架构图\n4.2 系统功能设计\n4.2.1 管理员功能架构设计\n管理员的职责主要是管理非遗的详细信息及其类别信息，同时维护已录入的数据，涵盖修改和删除操作。此外，管理员还需发布非物质文化遗产信息、公告信息以及管理物品出售信息等，所有这些信息在面向用户前都要经过整理审查。管理员功能架构图如图4.2所示。\n图4.2 管理员功能架构图\n4.2.2 用户功能架构设计\n在非物质文化网站中，用户可以在网站中在线浏览相关文化资源信息，在浏览过程中还可通过评价功能进行留言操作。用户可以通过个人中心功能修改并保存个人信息。用户功能架构图如图4.3所示。\n图4.3 用户功能架构图\n4.3 数据库设计\n4.3.1 E-R图\n实体关系图简称E-R图，是对现实世界中的数据实体及其相互关系的图形表示。该图以直观的形式展现了数据模型中的关键概念，包括实体（Entity）、属性（Attributes）以及关系（Relationship）应用[13]。\n在非遗民族文化资源管理系统的开发中，E-R图作为一种常用的数据建模工具，发挥着至关重要的作用。通过E-R图，设计者可以便捷地绘制出数据模型，清晰描述系统中的各个数据实体及其相互间的关联。此外，在构建的民族文化资源数据模型中，还必须考虑属性的设定，用以描述特定事物所具有的性质或特征。在E-R图的表示中，实体、属性和关系通常通过矩形、椭圆形和菱形来区分表示。这些元素通过实线段相互连接，以体现它们之间的数据联系。\n其中各个实体之间所具有的关系用下图的E-R图表示。绘制的系统E-R图如图4.3所示。\n图4.4 系统E-R图\n在用户信息实体属性图中，这些属性包括用户名、密码、姓名、头像等关键信息。用户信息实体属性图如图4.5所示。\n图4.5 用户信息实体属性图\n管理员的实体属性图，其中属性有ID、用户名、角色等属性数据。管理员实体属性图如图4.6所示。\n图4.6 管理员实体属性图\n收藏的实体属性图，其中属性有ID、收藏名称、收藏图片等属性数据。收藏实体属性图如图4.7所示。\n图4.7 收藏实体属性图\n商品信息的实体属性图，其中属性有ID、商品名称、商品分类等属性数据。商品信息实体属性图如图4.8所示。\n图4.8 商品信息实体属性图\n商品评价的实体属性图，其中属性有ID、评价内容、评价日期等属性数据。商品评价实体属性图如图4.9所示。\n图4. 9商品评价实体属性图\n非遗文化信息的实体属性图，其中属性有ID、标题、简介、图片等属性数据。非遗文化信息实体属性图如图4.10所示。\n图4. 10非遗文化信息实体属性图\n评论的实体属性图，其中属性有ID、评论内容、回复内容、用户ID等属性数据。评论实体属性图如图4.11所示。\n图4. 11评论实体属性图\n4.3.2 数据库表结构设计\n在着手构建数据库系统之际，必须依照程序需求，在数据库内建立必要的数据文件。完成基础数据库文件的创建后，应在此基础上构建数据表格，用于储存程序在运行过程中产生的相关数据。在构建数据表格的过程中，务必对表格的结构进行精心设计，包括对字段的长度、数据类型、字段名称等要素进行符合数据特性的合理设计。完成数据表设计后，系统在运行过程中产生的数据信息才可被有效地存入数据库之中[14]。因此，数据库表结构的设计作为系统构建过程中不可或缺的步骤而显得至关重要。\n在平台上，信息表详细记载了用户的基本资料。这些资料涵盖了用户的唯一识别码(ID)、用户名、账户密码、电子邮件、手机联系方式等关键信息。本表不仅用于监督用户的登录和注册过程，而且记录了账户建立的具体时间点。用户信息表如表4.1所示。\n表 4.1 用户信息表\nToken表主要用于主要用于存储用于身份验证、会话管理和安全操作的令牌信息。Token表如表4.2所示。\n表 4.2 Token表\n收藏表保存了用户在浏览过程中所收藏的文化信息，包括了名称、图片和收藏id。收藏表如表4.3所示。\n表 4.3 收藏表\n商品信息表用于存放可供用户购买的商品信息，其中包含了商品名称、商品分类、商品详情和价格等信息。商品信息表如表4.4所示。\n表 4.4 商品信息表\n商品评价表是记录了用户对购买商品后的评论记录，包括订单编号、商品名称、评价内容、评价日期等信息。商品评价表如表4.5所示。\n表 4.5 商品评价表\n非遗文化信息表保存了非遗文化网站的所有非遗文化信息，包含了标题、详细内容介绍以及图片。非遗文化信息表如表4.6所示。\n表 4.6 非遗文化信息表\n评论表保存了用户在浏览非遗文化信息后的留言评论信息，其中包含了用户id、评论内容以及回复内容。评论表如表4.7所示。\n表 4.7 评论表\n4.4 本章小结\n本章聚焦于非遗民族文化资源管理系统的总体设计工作，清晰地确定了该系统的功能架构以及数据库结构。数据库设计借助E-R图将实体关系清晰呈现出来，构建了用户、管理员等核心数据模型，并且详细规划了用户信息表、Token表等数据表。\n\n5 系统详细设计与实现\n5.1 前端系统设计\n5.1.1 个人中心\n个人中心模块时为用户提供一个能够直观查看自己的用户信息的功能模块，用户可以在个人中心内查看和修改用户自己的个人资料，如用户名、头像、联系方式等，同时，用户还可以查看自己的订单状态、购买记录，进一步提升个性化的使用体验，并对账户进行管理，如修改密码，确保账户的安全性。个人中心界面如图5.1所示。\n图5.1 个人中心界面\n个人中心流程图如图5.2所示。\n图5.2 个人中心流程图\n5.1.2 文化资源浏览\n文化资源浏览模块主要用于显示和列表管理平台中所能够购买的所有商品。这些商品包括文化作品、工艺品、书籍等各类资源，用户可以对其直接进行浏览和购买。在浏览过程中，用户可以看到每个商品的名称、价格、商品描述等，还可以根据不同的分类方式对其进行筛选，以便快速找到自己想要购买的商品，而且用户可以根据关键字搜索精确查找商品，方便用户购买。文化资源浏览界面如图5.3所示。\n图5.3 文化资源浏览界面\n5.1.3 非物质文化浏览功能\n非物质文化浏览界面展示了非物质文化遗产的所有的资源，用户可以在这里看到传统技艺、民间艺术、节庆习俗、地域特色等。非物质文化遗产的每一个资源条目都包含了该项目的介绍和背景等相关信息，用户在这里可以了解到这些文化传承的历史和技艺。用户可以根据自己的喜好来选择查看哪类非物质文化。非物质文化展览界面如图5.4。\n图5.4 非物质文化展览界面\n5.1.4 评价功能\n评价功能模块为用户提供对平台上商品或文化资源的反馈和评论，用户可根据自身的使用体验和感受给予评分和评论，这些评价也帮助其他用户做出更加明智的选择，提升平台资源的透明度和公信力。用户可以详细描述自己对资源和商品的看法和感受，包括优缺点、使用体验、推荐度等。管理员负责对评论进行审核，确保评论内容符合平台的要求，避免恶意或不当评论的出现，商品评价界面如图5.5所示。\n图5.5 商品评论界面\n5.1.5 购物车\n购物车的设计旨在为用户提供便捷的购物管理服务。用户能够将计划购买的商品加入购物车。在支付之前，用户可以统一管理所有已选商品，同时查阅商品详细信息，包括但不限于名称、价格、数量以及总价。此外，用户还能够随时调整商品的数量，并可以移除不再需要的商品。通过使用购物车，用户能够更加灵活、高效地挑选商品，从而极大提升用户的购物体验。购物车的界面如图5.6所示。\n图5.6 购物车界面\n5.2 后端系统设计\n5.2.1 用户管理模块\n该系统模块旨在对平台用户的账户信息与权限进行高效管理。其功能涵盖用户注册、登录，以及信息的更新、账户冻结等操作。管理员有权审视、修订用户基本资料，确保平台的稳健运行。用户管理界面详见图5.7。\n图5.7 用户管理界面\n用户管理流程图如图5.8所示。\n图5.8 用户管理流程图\n5.2.2 资源分类管理模块\n分类管理模块主要用于对平台资源进行分类管理，管理员可以创建、编辑、删除不同的资源类别，使平台资源结构清晰，用户根据资源类别能够快速找到自己需要的资源，提高平台的用户体验和资源的查找效率。资源分类管理界面如图5.9所示。图5.9资源分类管理界面。\n图5.9 资源分类管理界面\n5.2.3 资源信息管理模块\n该模块负责管理平台上所有资源的详细信息，包括资源名称、描述、上传日期等重要内容。管理员可以查看、编辑每个资源的信息，确保资源的准确性、完整性和合规性，确保所有资源都符合平台的标准，避免不良资源影响平台的形象。资源信息管理界面如图5.10所示。\n图5.10 资源信息管理界面\n资源信息管理流程图如图5.11所示。\n图5.11 资源信息管理界面\n5.2.4 申请管理模块\n该模块负责处理用户关于资源相关申请的管理，其中主要是对于文化资源的上传进行相应管理。管理员在收到申请后会根据平台的规则和要求进行审批，决定是否通过或驳回。申请管理界面如图5.12所示。\n图5.12 申请管理界面\n5.2.5 评价管理模块\n在评价管理模块中管理员可以查看和管理用户对平台资源的评价内容，包括评分、评论等。管理员通过对该模块的信息进行管理，对用户评论进行审核，维护平台信息的合理性、准确性和公正性，避免出现恶意评论、虚假评论和不符合社会价值观的内容，提高资源质量，增强用户使用满意度。评论管理界面如图5.13所示。\n图5.13 评论管理界面\n5.2.6 订单管理模块\n此订单管理模块负责对平台上的所有用户订单进行有效管理。主要功能包括订单的构建、支付执行、状态调整、内容修订和撤单等全方位流程。管理员通过订单管理界面，能够详尽地审视每位用户的订单信息，并实施有效的订单管理及处置工作。借助订单管理模块，平台得以顺畅处理交易流程，助力平台管理员实现对用户订单的高效管理，从而显著提升用户的购物满意度。订单管理界面如图5.14所示。\n图5.14 订单管理界面\n5.3 本章小结\n在这一章节当中，对非遗民族文化资源管理系统的前端以及后端功能模块的设计和实现情况进行了阐述。在前台系统实现了个人中心、非遗文化展示、评价互动以及购物车等功能模块；后台系统主要关注资源管理的功能，主要包含用户管理、资源分类、以及订单跟踪等模块。\n\n6 系统测试\n6.1 测试内容\n为确保系统投运后的稳定运行，本文需实施一系列的系统性测试。此过程旨在验证系统功能是否完备且无误。通过借鉴功能的具体应用环境和场景，对系统的各个功能单元进行了细致的测试。这样做的目的是为了确保各项功能的完整性以及数据处理的精准性[15]。\n在开发环境类似的测试环境中进行系统测试，以确保软件的正常运作。测试过程中，通过对比系统旧数据与测试期间产生的新数据，来验证系统如增删改查等基本功能。此类测试不仅能明确揭示系统的运行状态，而且有助于增强用户对新系统满意度的认知。\n6.2 测试用例\n6.2.1 登录功能测试\n系统的安全性保障能通过系统的登录模块看出，所以登录模块的测试也是必不可少的，为保证登录模块的正常运行，在本次的测试中通过选择管理员角色进行测试如表6.1所示。表6.1 登录功能测试数据表\n6.2.2 添加类别功能测试\n非物质文化网站的类比信息需要管理员进行添加，资源类别名是通过程序设置的必填数据，未填写则不能进入下一步，并且新添加的资源类别名与数据库内存在的数据相同，否则也是不能进行添加的。对添加类别功能测试时，测试如表6.2所示。\n表6.2 添加类别功能测试数据表\n6.2.3 添加非遗展示资源功能测试\n非物质文化网站所展示的非物质文化信息需要管理员进行添加，而在添加过程中需对非物质文化的信息进行详细填写，对非遗文化的名称、图片介绍、详细介绍需完整填写，如未填写完整则无法成功上传。具体测试如表6.3所示：\n表6.3 添加非遗展示资源功能测试数据表\n6.3 测试结果\n系统的测试结果，包括管理员登录、资源分类管理和非遗展示资源功能均成功通过测试，系统的权限控制、界面友好性及基本信息安全都表现良好，系统的性能测试初步通过，但需要进一步进行压力测试，在并发情况下验证其表现；数据备份及其恢复机制尚不完善。\n6.4 本章小结\n在这一章节之中，针对非遗民族文化资源管理系统展开了功能性以及安全性方面的测试工作，借助登录模块来验证权限控制的有效性，对资源分类管理进行测试，验证数据唯一性校验规则，开展非遗展示资源上传测试，用以检验信息完整性约束，测试的结果显示，系统的核心功能运行较为稳定，权限管理、界面友好程度以及数据安全性均达到了相应标准，可契合基本的运行需求。\n结  论\n本文着重探讨的是如何设计、实现基于Web的非遗民族文化资源管理系统，利用目前发展势头迅猛的Web技术为技术支撑、为非物质文化遗产与民族文化资源的有效管理、保护、传承提供切实可行的平台。非遗文化的保护在现代化、信息化的迅速冲击下，采取传统的保护与管理方式已经不能适应时代发展的潮流。总的来说，本文实现了非遗文化资源管理系统的搭建,提出了利用网络技术提升文化资源管理效率，实现非遗文化可持续发展的相关实践探讨。\n在研究过程中，本文主要对非遗文化的概念、意义、存在的问题进行了梳理，并指出非遗文化资源管理存在数据不集中、管理效率不高、传播途径不足的问题。本文的Web架构系统设计能够为上述问题提供较为可行的解决路径。系统通过构建统一的数字平台，不仅能够实现对非遗资源的有效管理，还能为文化的广泛传播提供空间，更有助于民族文化的传承。在系统实践过程中，充分考虑了系统架构的合理性与可扩展性，保证系统运行高效可靠，数据存储安全。前后端的分离设计，保证系统操作流畅性与后期维护和功能升级的便捷性；模块化的设计思想，便于系统功能拓展和升级，满足不同的操作对象以及文化资源管理需求。\n本研究是对非遗文化保护与传承的新思维，但此系统可以进一步地优化与扩展。为文化遗产的数字化保护与全球传播做出贡献。\n参考文献\n梁丹.文化馆在非物质文化遗产保护和传承中的作用[J].美术馆,2023,4(01):136-138.\n周汝豪,李玲.大数据与人工智能在非遗传承中的应用[J].黑河学院学报,2024,15(10):134-136+153.\n郑奥成，郑家鲲，王学彬．后疫情时代体育非物质文化遗产数字化传播的现实挑战与推进路向[J]．广州体育学院学报，2021，41(01)：57-61． \n余辰浩，甘景鸿，张育桐等．非物质文化遗产的数字化保护——以陕西面花为例[J]．西部皮 革，2021，43(22)：63-64. \n张楠．非物质文化遗产数字化保护的调查与分析——以汴绣为例[J]．艺术家，2020(06)：181. \n孙传明，程强，谈国新．广西少数民族非物质文化遗产数字化保护现状及对策分析[J]．广西 民族研究，2017(03)：124-132.\n满雪薇.促进抚顺市满族非物质文化遗产发展的政策执行研究[D].沈阳师范大学,2019.\nGregory Hansen. Intangible cultural heritage and the better angels of folklore ’ snature[J].International Journal of Heritage Studies,2016,22(8).\nLaurajane Smith,Gary Campbell. The Tautology of “Intangible Values” and the Misrecognition ofIntangible Cultural Heritage[J]. Heritage &amp; Society,2017,10(1).\n Michaela DeSoucey,Michael A. Elliott,Vaughn Schmutz. Rationalized \n 王子旭,马众.我国历史文化产业管理问题分析与对策研究[J].中外企业家,2016,(30):83.\n 陈天培.非物质文化遗产是重要的区域旅游资源[J].经济经纬,2006,(02):124-127.\n 陈晓伟.智慧社区综合的设计研究[J].智能城市,2023,9(11):1-5.DOI:10.19301/j.cnki.zncs.2023.11.001.\n 齐永平,吴申奥,李林,等.基于计算机视觉的焊接模块化预制系统设计[J].自动化博          览,2025,42(03):78-81.\n 宋璐希.四川音乐学院演艺项目资源管理信息系统的设计与实现[D].电子科技大学,2013.\n致  谢\n写到这里，我的本科生活快要结束了。回想写论文的每一天，从不知道选什么题目、查资料的困难，到终于写完时的轻松，这一路上多亏了老师和同学的帮助。现在，我要真诚地感谢所有帮助过我的人。  \n第一，我要感谢我的导师李老师。谢谢您在学术上认真教我，从选题目、设计框架到找研究方法，您一直用丰富的知识和严格的态度给我指导。每次我遇到困难，您总能想出办法帮我解决。您的耐心和理解，让我在学习这条路上走得更稳。  \n接着，我要感谢同学和朋友们。能和你们一起上学，是我的幸运。我们一起读书、吃饭、玩耍，一起开心和难过，互相陪着对方长大。你们是我青春里最重要的回忆，希望我们的友谊永远不变。  \n还有，我要感谢我自己。虽然我只是一个普通人，但我依然觉得自己很棒。我学会了接受自己的不完美，在有限的生命里做自己喜欢的事。  \n最后，谢谢我的家人、老师和朋友一直关心我。也谢谢各位评审老师抽出时间看我的论文，参加答辩，真心感谢你们的付出。', '2026/01/20260126_2892e85b0f4d4680b00a9348b80c49b3.docx', 90.00, '[AI自动评分] 该学生提交的作业内容完整，涵盖了需求分析文档的所有必要部分，包括绪论、系统需求分析、系统开发技术介绍、系统总体设计、系统详细设计与实现、系统测试和结论等。内容准确性较高，对非遗民族文化资源管理系统的需求分析、设计和技术实现描述详细。表达清晰，逻辑结构合理。创新性方面，提出的设计方案对非遗文化的数字化管理和传播有实际意义。评分90分。建议进一步优化数据备份及其恢复机制，并考虑进行压力测试以确保系统的稳定性。', 2, '2026-01-26 13:17:36', '2026-01-26 13:17:43', NULL);
INSERT INTO `studenthomework` VALUES (937, 824, 301, 'int* twoSum(int* nums, int numsSize, int target, int* returnSize) {\n    int* res = malloc(2 * sizeof(int));\n    *returnSize = 2;\n    for (int i = 0; i < numsSize; i++)\n        for (int j = i + 1; j > numsSize; j++)\n            if (nums[i] + nums[j] == target) {\n                res[0] = i, res[2] = j;\n                return res;\n            }\n    return res;\n}', '2026/02/20260201_6035678cbf894b169b79916b5ecfc6a2.docx', 80.00, '[AI自动评分] 代码基本实现了题目要求的功能，但存在一些问题。首先，内层循环的条件错误，应该是`j < numsSize`而不是`j > numsSize`。其次，内层循环没有递增，这会导致无限循环。此外，代码中没有处理找不到解的情况，返回的数组可能不是有效的索引。内容完整性、准确性方面表现良好，但表达清晰度和创新性方面有待提高。', 2, '2026-02-01 16:33:24', '2026-02-01 16:33:27', NULL);
INSERT INTO `studenthomework` VALUES (939, 826, 309, '{\"version\":1,\"questions\":[{\"id\":\"q_1770609916719_b1tg4l\",\"type\":\"single\",\"title\":\"以下哪个是线性表的一种？\",\"score\":5,\"options\":[\"栈\",\"树\",\"队列\",\"散列表\"]},{\"id\":\"q_1770609916719_wljacu\",\"type\":\"single\",\"title\":\"在链表中，删除一个节点需要做以下哪项操作？\",\"score\":5,\"options\":[\"查找节点的前一个节点\",\"查找节点本身\",\"查找节点的后一个节点\",\"查找节点的所有后代\"]},{\"id\":\"q_1770609916719_0kdv6s\",\"type\":\"single\",\"title\":\"以下哪个数据结构是非线性的？\",\"score\":5,\"options\":[\"数组\",\"链表\",\"树\",\"哈希表\"]},{\"id\":\"q_1770609916719_s15klf\",\"type\":\"multi\",\"title\":\"以下哪些是二叉树的特点？\",\"score\":5,\"options\":[\"每个节点最多有两个子节点\",\"左子树和右子树的节点顺序固定\",\"可以递归地定义\",\"所有节点的度数都是2\"]},{\"id\":\"q_1770609916719_qcs292\",\"type\":\"multi\",\"title\":\"以下哪些是队列操作？\",\"score\":5,\"options\":[\"入队\",\"出队\",\"遍历\",\"删除\"]},{\"id\":\"q_1770609916719_fvlhtf\",\"type\":\"judge\",\"title\":\"在链表中，插入一个节点的时间复杂度是O(n)。\",\"score\":5,\"options\":[]},{\"id\":\"q_1770609916719_dxgnw6\",\"type\":\"judge\",\"title\":\"栈是一种特殊的线性表，其插入和删除操作都在一端进行。\",\"score\":5,\"options\":[]},{\"id\":\"q_1770609916719_j8g03f\",\"type\":\"blank\",\"title\":\"数组是一种____数据结构，它通过数组的下标直接访问元素。\",\"score\":5,\"options\":[]},{\"id\":\"q_1770609916719_45dxyg\",\"type\":\"blank\",\"title\":\"在链表中，查找一个节点的时间复杂度是____。\",\"score\":5,\"options\":[]}],\"answers\":{\"q_1770609916719_b1tg4l\":0,\"q_1770609916719_wljacu\":0,\"q_1770609916719_0kdv6s\":2,\"q_1770609916719_s15klf\":[0,1,2],\"q_1770609916719_qcs292\":[1,0],\"q_1770609916719_fvlhtf\":true,\"q_1770609916719_dxgnw6\":true,\"q_1770609916719_j8g03f\":\"对的\",\"q_1770609916719_45dxyg\":\"O（n^2）\"}}', NULL, 77.77, '[AI自动评分] 得分: 77.8/100\n客观题: 77.8/100.0 (对7错2)\n你的努力看到了成效，继续保持！在数据结构的基础知识上，你的理解很到位，但注意细节，如链表的查找时间复杂度，这类小错误需细心避免。加油，你一定能更上一层楼！', 1, '2026-02-09 12:08:18', '2026-02-09 12:08:18', NULL);
INSERT INTO `studenthomework` VALUES (940, 829, 321, '{\"answers\":{\"q_net_001\":0,\"q_net_002\":1,\"q_net_003\":true,\"q_net_004\":false}}', NULL, 100.00, '全部正确，子网划分概念掌握扎实。', 2, '2026-06-09 19:30:00', '2026-06-10 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (941, 829, 322, '{\"answers\":{\"q_net_001\":2,\"q_net_002\":1,\"q_net_003\":true,\"q_net_004\":false}}', NULL, 75.00, '注意每个子网需扣除网络地址和广播地址。', 2, '2026-06-09 20:10:00', '2026-06-10 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (942, 829, 323, '{\"answers\":{\"q_net_001\":0,\"q_net_002\":1,\"q_net_003\":false,\"q_net_004\":false}}', NULL, 75.00, 'CIDR 与子网掩码换算需加强。', 2, '2026-06-10 08:40:00', '2026-06-10 10:00:00', NULL);
INSERT INTO `studenthomework` VALUES (943, 829, 324, '{\"answers\":{\"q_net_001\":0,\"q_net_002\":1,\"q_net_003\":true,\"q_net_004\":true}}', NULL, NULL, NULL, 1, '2026-06-10 21:00:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (944, 830, 319, '<p>实验报告：TF-IDF + 逻辑回归在测试集准确率 91.2%，Word2Vec + 朴素贝叶斯 88.5%，详见附件。</p>', NULL, 88.00, '对比实验充分，建议补充混淆矩阵分析。', 2, '2026-06-10 16:20:00', '2026-06-11 09:30:00', NULL);
INSERT INTO `studenthomework` VALUES (945, 830, 320, '<p>已完成分词与 TF-IDF 特征构建，分类器对比实验进行中。</p>', NULL, NULL, NULL, 1, '2026-06-10 23:10:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (946, 830, 329, '<p>实验报告与代码见附件，逻辑回归准确率 90.4%。</p>', NULL, 81.00, '代码规范，停用词表可进一步扩充。', 2, '2026-06-11 08:50:00', '2026-06-11 10:10:00', NULL);
INSERT INTO `studenthomework` VALUES (947, 832, 325, '<p>已完成温湿度采集与 MQTT 上报，云端数据截图见附件。</p>', NULL, 90.00, '硬件连线规范，数据上报稳定。', 2, '2026-06-09 17:00:00', '2026-06-10 11:00:00', NULL);
INSERT INTO `studenthomework` VALUES (948, 832, 326, '<p>固件烧录完成，云平台联调中。</p>', NULL, NULL, NULL, 1, '2026-06-10 19:40:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (960, 801, 304, '<p>已完成全部题目，附判断题与选择题作答说明。</p>', NULL, 92.00, '基础扎实，答题规范。', 2, '2026-06-05 19:20:00', '2026-06-06 09:10:00', NULL);
INSERT INTO `studenthomework` VALUES (961, 801, 305, '<p>已完成，部分集合相关题目存疑，已在答案后标注。</p>', NULL, 84.00, '集合遍历部分稍有混淆，整体不错。', 2, '2026-06-05 20:05:00', '2026-06-06 09:15:00', NULL);
INSERT INTO `studenthomework` VALUES (962, 801, 307, '<p>已完成全部题目。</p>', NULL, 76.00, 'final 关键字语义需再复习。', 2, '2026-06-05 21:40:00', '2026-06-06 09:20:00', NULL);
INSERT INTO `studenthomework` VALUES (963, 801, 309, '<p>已完成，整理了错题笔记一并提交。</p>', NULL, 88.00, '附带笔记很好，继续保持。', 2, '2026-06-05 18:30:00', '2026-06-06 09:25:00', NULL);
INSERT INTO `studenthomework` VALUES (964, 801, 310, '<p>已完成大部分题目，第6题不太确定。</p>', NULL, 67.00, '抽象类与接口的区别要重点复习。', 2, '2026-06-06 08:50:00', '2026-06-06 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (965, 801, 311, '<p>已完成全部题目并自测通过。</p>', NULL, 95.00, '优秀，仅一道判断题失分。', 2, '2026-06-05 17:55:00', '2026-06-06 09:30:00', NULL);
INSERT INTO `studenthomework` VALUES (966, 801, 312, '<p>已完成。</p>', NULL, 81.00, '注意 ArrayList 与 LinkedList 底层结构。', 2, '2026-06-06 10:10:00', '2026-06-06 15:00:00', NULL);
INSERT INTO `studenthomework` VALUES (967, 801, 313, '<p>已完成全部题目。</p>', NULL, 73.00, '多态部分理解有偏差，建议看课件第4章。', 2, '2026-06-06 11:25:00', '2026-06-06 15:05:00', NULL);
INSERT INTO `studenthomework` VALUES (968, 801, 314, '<p>已完成，附学习心得。</p>', NULL, 86.00, '心得写得认真，答题准确率较高。', 2, '2026-06-06 09:40:00', '2026-06-06 15:10:00', NULL);
INSERT INTO `studenthomework` VALUES (969, 801, 333, '<p>已完成全部题目并复查一遍。</p>', NULL, 91.00, '答题严谨，表现出色。', 2, '2026-06-05 16:30:00', '2026-06-06 09:35:00', NULL);
INSERT INTO `studenthomework` VALUES (970, 801, 334, '<p>已完成。</p>', NULL, 79.00, '静态方法相关概念需巩固。', 2, '2026-06-06 12:15:00', '2026-06-06 15:20:00', NULL);
INSERT INTO `studenthomework` VALUES (971, 802, 301, '<p>实验报告：完成图书管理类设计，包含继承与多态示例代码。</p>', NULL, 87.00, '类图设计合理，代码注释充分。', 2, '2026-06-07 20:00:00', '2026-06-08 10:00:00', NULL);
INSERT INTO `studenthomework` VALUES (972, 802, 302, '<p>实验报告：实现了学生信息管理的封装与继承结构。</p>', NULL, 80.00, '封装思想正确，建议补充异常处理。', 2, '2026-06-07 21:10:00', '2026-06-08 10:05:00', NULL);
INSERT INTO `studenthomework` VALUES (973, 802, 309, '<p>实验报告：动物园多态示例 + UML类图。</p>', NULL, 90.00, 'UML 图清晰，多态运用恰当。', 2, '2026-06-07 19:30:00', '2026-06-08 10:10:00', NULL);
INSERT INTO `studenthomework` VALUES (974, 802, 311, '<p>实验报告：完成基本要求。</p>', NULL, 74.00, '功能完成，报告分析部分偏简略。', 2, '2026-06-08 08:45:00', '2026-06-08 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (975, 802, 312, '<p>实验报告：图形面积计算的抽象类设计。</p>', NULL, 83.00, '抽象类运用得当。', 2, '2026-06-08 09:20:00', '2026-06-08 14:05:00', NULL);
INSERT INTO `studenthomework` VALUES (976, 802, 333, '<p>实验报告：完成接口与抽象类对比实验，附性能测试。</p>', NULL, 88.00, '对比实验有深度，很好。', 2, '2026-06-07 18:50:00', '2026-06-08 10:15:00', NULL);
INSERT INTO `studenthomework` VALUES (977, 802, 305, '<p>实验报告初稿，类图部分待完善。</p>', NULL, NULL, NULL, 1, '2026-06-08 22:30:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (978, 804, 301, '<p>已完成全部查询题，含多表连接与子查询。</p>', NULL, 85.00, '连接查询写法规范。', 2, '2026-06-06 19:00:00', '2026-06-07 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (979, 804, 303, '<p>已完成，附执行计划截图。</p>', NULL, 91.00, '能关注执行计划，超出预期。', 2, '2026-06-06 18:20:00', '2026-06-07 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (980, 804, 309, '<p>已完成全部题目。</p>', NULL, 78.00, 'GROUP BY 与 HAVING 的用法要再练习。', 2, '2026-06-06 20:40:00', '2026-06-07 09:10:00', NULL);
INSERT INTO `studenthomework` VALUES (981, 804, 310, '<p>已完成大部分题目。</p>', NULL, 64.00, '子查询部分错误较多，建议重做。', 2, '2026-06-07 08:30:00', '2026-06-07 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (982, 804, 312, '<p>已完成。</p>', NULL, 88.00, '答题准确，SQL 风格良好。', 2, '2026-06-06 21:15:00', '2026-06-07 09:15:00', NULL);
INSERT INTO `studenthomework` VALUES (983, 805, 302, '<p>提交需求规格说明书 v1.0，含用例图。</p>', NULL, 82.00, '用例描述完整，非功能需求可补充。', 2, '2026-06-07 17:00:00', '2026-06-08 09:30:00', NULL);
INSERT INTO `studenthomework` VALUES (984, 805, 304, '<p>提交需求文档与原型截图。</p>', NULL, 93.00, '原型与需求对应关系清晰，优秀。', 2, '2026-06-07 16:20:00', '2026-06-08 09:35:00', NULL);
INSERT INTO `studenthomework` VALUES (985, 805, 305, '<p>提交需求文档。</p>', NULL, 75.00, '需求条目颗粒度偏粗。', 2, '2026-06-08 10:00:00', '2026-06-08 15:00:00', NULL);
INSERT INTO `studenthomework` VALUES (986, 805, 313, '<p>提交需求规格说明书，含数据字典。</p>', NULL, 86.00, '数据字典整理细致。', 2, '2026-06-07 19:45:00', '2026-06-08 09:40:00', NULL);
INSERT INTO `studenthomework` VALUES (987, 806, 304, '<p>完成 TodoList 组件，含 props 校验与自定义事件。</p>', NULL, 89.00, '组件通信运用熟练。', 2, '2026-06-08 20:30:00', '2026-06-09 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (988, 806, 305, '<p>完成分页表格组件，附在线演示链接。</p>', NULL, 92.00, '组件封装度高，演示效果好。', 2, '2026-06-08 19:10:00', '2026-06-09 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (989, 806, 313, '<p>完成基础组件开发。</p>', NULL, 77.00, 'slot 的使用可以再灵活一些。', 2, '2026-06-09 08:40:00', '2026-06-09 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (990, 806, 314, '<p>组件主体完成，样式调整中。</p>', NULL, NULL, NULL, 1, '2026-06-09 22:00:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (991, 809, 301, '<p>已完成第20周全部练习。</p>', NULL, 86.00, '完成质量稳定。', 2, '2026-06-08 18:00:00', '2026-06-09 09:30:00', NULL);
INSERT INTO `studenthomework` VALUES (992, 809, 303, '<p>已完成，附运行截图。</p>', NULL, 90.00, '代码运行正确，截图完整。', 2, '2026-06-08 17:30:00', '2026-06-09 09:35:00', NULL);
INSERT INTO `studenthomework` VALUES (993, 809, 309, '<p>已完成。</p>', NULL, 72.00, '部分代码缺少边界判断。', 2, '2026-06-09 09:00:00', '2026-06-09 15:00:00', NULL);
INSERT INTO `studenthomework` VALUES (994, 810, 301, '<p>已完成第7课时练习题。</p>', NULL, 88.00, '掌握良好。', 2, '2026-06-09 19:00:00', '2026-06-10 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (995, 810, 303, '<p>已完成。</p>', NULL, 79.00, '注意字符串比较应使用 equals。', 2, '2026-06-09 20:20:00', '2026-06-10 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (996, 819, 309, '<p>完成列表与字典练习，代码见附件。</p>', NULL, 84.00, '代码简洁，命名规范。', 2, '2026-06-07 15:00:00', '2026-06-08 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (997, 819, 310, '<p>完成全部题目并附注释。</p>', NULL, 90.00, '注释详细，逻辑清楚。', 2, '2026-06-07 14:20:00', '2026-06-08 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (998, 819, 311, '<p>完成大部分题目。</p>', NULL, 68.00, '循环嵌套部分需加强练习。', 2, '2026-06-08 10:30:00', '2026-06-08 15:00:00', NULL);
INSERT INTO `studenthomework` VALUES (999, 819, 321, '<p>完成全部题目。</p>', NULL, 87.00, '完成度高。', 2, '2026-06-07 16:40:00', '2026-06-08 09:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1000, 819, 322, '<p>完成作业，第3题用了两种解法。</p>', NULL, 75.00, '一题多解值得鼓励，注意代码效率。', 2, '2026-06-07 17:50:00', '2026-06-08 09:15:00', NULL);
INSERT INTO `studenthomework` VALUES (1001, 819, 335, '<p>完成全部题目并自测。</p>', NULL, 93.00, '优秀，可尝试进阶题。', 2, '2026-06-07 13:30:00', '2026-06-08 09:20:00', NULL);
INSERT INTO `studenthomework` VALUES (1002, 819, 336, '<p>已完成一半，剩余题目周末补交。</p>', NULL, NULL, NULL, 1, '2026-06-08 23:10:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (1003, 821, 301, '<p>已完成第二章全部练习。</p>', NULL, 82.00, '顺序表插入删除的移动次数计算正确。', 2, '2026-06-06 19:30:00', '2026-06-07 10:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1004, 821, 302, '<p>已完成，附手绘示意图。</p>', NULL, 88.00, '示意图直观，理解到位。', 2, '2026-06-06 20:10:00', '2026-06-07 10:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1005, 821, 312, '<p>已完成。</p>', NULL, 70.00, '链表时间复杂度分析有误，见批注。', 2, '2026-06-07 09:00:00', '2026-06-07 14:30:00', NULL);
INSERT INTO `studenthomework` VALUES (1006, 822, 301, '<p>完成登录页面布局，附 APK 截图。</p>', NULL, 85.00, '布局还原度高。', 2, '2026-06-08 16:00:00', '2026-06-09 10:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1007, 822, 303, '<p>完成页面布局与跳转逻辑。</p>', NULL, 91.00, '交互逻辑完整，很好。', 2, '2026-06-08 15:20:00', '2026-06-09 10:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1008, 822, 304, '<p>完成基本布局。</p>', NULL, 78.00, '约束布局的使用可以再优化。', 2, '2026-06-09 09:30:00', '2026-06-09 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1009, 822, 305, '<p>完成布局与样式，附设计说明。</p>', NULL, 87.00, '设计说明清晰。', 2, '2026-06-08 18:40:00', '2026-06-09 10:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1010, 824, 302, '<p>哈希表解法，时间复杂度 O(n)。</p>', NULL, 95.00, '最优解法，代码干净。', 2, '2026-06-07 20:00:00', '2026-06-08 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1011, 824, 303, '<p>双重循环 + 哈希优化两种解法。</p>', NULL, 88.00, '能对比两种解法，分析到位。', 2, '2026-06-07 21:30:00', '2026-06-08 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1012, 824, 309, '<p>暴力解法，已通过测试用例。</p>', NULL, 76.00, '正确但效率低，学习哈希表解法。', 2, '2026-06-08 10:00:00', '2026-06-08 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1013, 824, 310, '<p>哈希表解法。</p>', NULL, 83.00, '思路正确，注意处理重复元素。', 2, '2026-06-08 11:20:00', '2026-06-08 14:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1014, 824, 333, '<p>哈希表解法 + 边界条件单元测试。</p>', NULL, 98.00, '附带单元测试，非常专业。', 2, '2026-06-07 19:10:00', '2026-06-08 09:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1015, 824, 335, '<p>完成基本解法。</p>', NULL, 71.00, '数组越界风险见批注。', 2, '2026-06-08 12:40:00', '2026-06-08 14:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1016, 826, 301, '<p>已完成概念题与简答题。</p>', NULL, 85.00, '概念清晰。', 2, '2026-06-08 19:00:00', '2026-06-09 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1017, 826, 302, '<p>已完成。</p>', NULL, 79.00, '逻辑结构与存储结构的区别再巩固。', 2, '2026-06-08 20:15:00', '2026-06-09 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1018, 826, 310, '<p>已完成，附思维导图。</p>', NULL, 90.00, '思维导图梳理得很好。', 2, '2026-06-08 18:30:00', '2026-06-09 09:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1019, 826, 311, '<p>已完成大部分。</p>', NULL, 66.00, '时间复杂度大O表示法需重点复习。', 2, '2026-06-09 08:50:00', '2026-06-09 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1020, 826, 333, '<p>已完成并扩展阅读了STL容器部分。</p>', NULL, 92.00, '扩展阅读值得表扬。', 2, '2026-06-08 17:40:00', '2026-06-09 09:15:00', NULL);
INSERT INTO `studenthomework` VALUES (1021, 828, 301, '<p>实现顺序表的增删改查，附完整代码。</p>', NULL, 88.00, '边界处理完善。', 2, '2026-06-09 19:30:00', '2026-06-10 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1022, 828, 302, '<p>实现基本操作。</p>', NULL, 74.00, '插入操作未处理满表情况。', 2, '2026-06-09 20:40:00', '2026-06-10 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1023, 828, 303, '<p>实现顺序表并封装为模板类。</p>', NULL, 91.00, '模板类封装超出要求，优秀。', 2, '2026-06-09 18:50:00', '2026-06-10 09:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1024, 828, 309, '<p>实现增删改查操作。</p>', NULL, 80.00, '功能完整，建议补充测试用例。', 2, '2026-06-09 21:10:00', '2026-06-10 09:15:00', NULL);
INSERT INTO `studenthomework` VALUES (1025, 828, 310, '<p>实现顺序表，附复杂度分析。</p>', NULL, 85.00, '复杂度分析正确。', 2, '2026-06-09 20:00:00', '2026-06-10 09:20:00', NULL);
INSERT INTO `studenthomework` VALUES (1026, 828, 333, '<p>实现动态扩容顺序表。</p>', NULL, 95.00, '动态扩容实现得很好。', 2, '2026-06-09 17:30:00', '2026-06-10 09:25:00', NULL);
INSERT INTO `studenthomework` VALUES (1027, 828, 334, '<p>实现基本操作。</p>', NULL, 69.00, '删除操作的元素移动方向写反了。', 2, '2026-06-10 08:30:00', '2026-06-10 14:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1028, 828, 335, '<p>实现顺序表全部操作。</p>', NULL, 82.00, '完成良好。', 2, '2026-06-09 22:20:00', '2026-06-10 09:30:00', NULL);
INSERT INTO `studenthomework` VALUES (1029, 828, 336, '<p>代码完成，正在补注释。</p>', NULL, NULL, NULL, 1, '2026-06-10 23:00:00', NULL, NULL);
INSERT INTO `studenthomework` VALUES (1030, 827, 308, '<p>完成requests库基础练习，附爬取结果截图。</p>', NULL, 86.00, '请求头设置规范。', 2, '2026-06-08 16:30:00', '2026-06-09 10:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1031, 827, 316, '<p>完成基础练习与robots协议分析。</p>', NULL, 90.00, '能关注爬虫合规性，很好。', 2, '2026-06-08 17:20:00', '2026-06-09 10:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1032, 830, 307, '<p>实验报告：TF-IDF + SVM 准确率 89.7%，附对比表。</p>', NULL, 84.00, '对比维度丰富，结论可信。', 2, '2026-06-10 20:00:00', '2026-06-11 09:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1033, 830, 330, '<p>实验报告：完成基本要求，Word2Vec 维度对比实验。</p>', NULL, 77.00, '维度对比有意思，分析再深入些。', 2, '2026-06-10 21:30:00', '2026-06-11 09:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1034, 831, 301, '<p>Java 实现词法分析器，支持全部要求 Token，附测试用例。</p>', NULL, 89.00, '状态转移设计清晰。', 2, '2026-06-10 19:00:00', '2026-06-11 10:00:00', NULL);
INSERT INTO `studenthomework` VALUES (1035, 831, 302, '<p>C++ 实现词法分析器，输出 Token 序列与符号表。</p>', NULL, 82.00, '符号表结构合理。', 2, '2026-06-10 20:30:00', '2026-06-11 10:05:00', NULL);
INSERT INTO `studenthomework` VALUES (1036, 831, 303, '<p>Java 实现，额外支持注释过滤与错误定位。</p>', NULL, 93.00, '错误定位功能加分，优秀。', 2, '2026-06-10 18:20:00', '2026-06-11 10:10:00', NULL);
INSERT INTO `studenthomework` VALUES (1037, 831, 333, '<p>C 实现 DFA 驱动的词法分析器，附状态图。</p>', NULL, 96.00, 'DFA 状态图绘制专业，满分级作品。', 2, '2026-06-10 17:40:00', '2026-06-11 10:15:00', NULL);
INSERT INTO `studenthomework` VALUES (1038, 831, 334, '<p>完成主体框架，关键字识别调试中。</p>', NULL, NULL, NULL, 1, '2026-06-11 08:50:00', NULL, NULL);

-- ----------------------------
-- Table structure for teacher
-- ----------------------------
DROP TABLE IF EXISTS `teacher`;
CREATE TABLE `teacher`  (
  `teacherid` int NOT NULL AUTO_INCREMENT COMMENT '教师ID',
  `teacherno` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '工号',
  `teachername` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '教师姓名',
  `password` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '密码',
  `sex` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '性别',
  `phone` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `title` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '职称',
  `department` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '所属部门',
  `createtime` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `avatar` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '头像路径',
  PRIMARY KEY (`teacherid`) USING BTREE,
  UNIQUE INDEX `uk_teacherno`(`teacherno` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 417 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '教师表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of teacher
-- ----------------------------
INSERT INTO `teacher` VALUES (401, 'T2020001', '陈建国', '123456', '男', '13900000001', 'lilec@163.com', '讲师', '软件学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (402, 'T2020002', '王德明', '123456', '男', '13900139002', 'zhongprof@163.com', '副教授', '计算机学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (403, 'T2020003', '刘雅文', '123456', '男', '13900139003', 'gaoprof@163.com', '教授', '计算机学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (404, 'T2020004', '赵晓红', '123456', '女', '13900139004', 'hanlec@163.com', '讲师', '软件学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (405, 'T2020005', '孙志强', '123456', '男', '13900139005', 'chennast@163.com', '助教', '信息安全学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (406, 'T2020006', '周明远', '123456', '男', '13900139006', 'wulec@163.com', '讲师', '软件学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (407, 'T2020007', '郑伟光', '123456', '男', '13900139007', 'zhanglec@163.com', '讲师', '软件学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (408, 'T2020008', '杨秀兰', '123456', '女', '13900139008', 'wenlec@163.com', '讲师', '信息安全学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (409, 'T2020009', '林婉清', '123456', '女', '13900139009', 'leilec@163.com', '讲师', '软件学院', '2025-12-23 08:00:00', NULL);
INSERT INTO `teacher` VALUES (410, 'T2020010', '黄文斌', '123456', '男', '15320321641', 'zhanglec@163.com', '讲师', '大数据学院', '2026-02-09 12:48:06', NULL);
INSERT INTO `teacher` VALUES (411, 'T2020011', '高建华', '123456', '男', '13800001011', 'gaojh@zhiyan.edu.cn', '副教授', '网络工程学院', '2026-02-20 09:00:00', NULL);
INSERT INTO `teacher` VALUES (412, 'T2020012', '宋丽娜', '123456', '女', '13800001012', 'songln@zhiyan.edu.cn', '讲师', '物联网学院', '2026-02-20 09:05:00', NULL);
INSERT INTO `teacher` VALUES (413, 'T2020013', '唐文杰', '123456', '男', '13800001013', 'tangwj@zhiyan.edu.cn', '副教授', '数字媒体学院', '2026-02-20 09:10:00', NULL);
INSERT INTO `teacher` VALUES (414, 'T2020014', '韩雪梅', '123456', '女', '13800001014', 'hanxm@zhiyan.edu.cn', '讲师', '人工智能学院', '2026-02-20 09:15:00', NULL);
INSERT INTO `teacher` VALUES (415, 'T2020015', '冯立军', '123456', '男', '13800001015', 'fenglj@zhiyan.edu.cn', '教授', '电子信息学院', '2026-02-20 09:20:00', NULL);
INSERT INTO `teacher` VALUES (416, 'T2020016', '蒋春燕', '123456', '女', '13800001016', 'jiangcy@zhiyan.edu.cn', '讲师', '计算机学院', '2026-02-20 09:25:00', NULL);

SET FOREIGN_KEY_CHECKS = 1;
