# 数据库脚本

## 迁移是怎么组织的

Flyway 的 `locations` 配成了 `classpath:db/migration`，它同时扫描两处：

- **Java 迁移** `src/main/java/db/migration/`
- **SQL 迁移** `src/main/resources/db/migration/`

两者按版本号统一排序执行，都记录在 `flyway_schema_history` 里。只看其中一处会误判迁移的完整性。

当前的迁移链：

| 版本 | 形式 | 作用 |
|------|------|------|
| V1 | Java | 企业基线：执行下方四个建表脚本、创建运行时表、补列、修历史数据、加核心外键 |
| V2 | Java | `learning_plan` 增加 `progress_json` |
| V3 | Java | 修复历史种子数据的 UTF-8 乱码 |
| V4 | SQL | 微信小程序绑定表 |
| V6 | SQL | 修正 `attend_session` 列名（见下） |

## 被 V1 执行的建表脚本

以下脚本**不是**遗留的手工脚本，而是 V1 的输入，由 `runScript` 按顺序执行，
因此都必须保持可重复执行：

- `core_feature_tables.sql` —— 作业评论、课程资源、考勤、通知已读、课程评价、站内消息
- `feature_expansion.sql` —— 审计日志、考试、错题本、积分徽章、学习计划、查重报告
- `learning_effect_rag.sql` —— 学习成效评估与课程知识切片
- `academic_term.sql` —— 学年学期主数据与历史学期回填

`core_feature_tables.sql` 取代了原先的 `new_tables.sql`。后者每张表前都有
`DROP TABLE IF EXISTS`，在有数据的库上执行会清空那六张业务表。

## 运行期这些表由谁读写

业务代码访问数据库有两条路径，改表结构时两边都要看：

- **MyBatis Mapper** `src/main/java/com/mapper/` + `resources/mapper/*.xml` —— 教务主数据（学生、
  教师、课程、作业、考试等）走这条。
- **仓储类** `src/main/java/com/repository/` —— AI 相关的表原先在 Service 里内联 SQL，
  已收敛到这里，是它们唯一的 SQL 出口：

  | 仓储 | 负责的表 |
  |------|----------|
  | `LearningResourceRepository` | `learning_resource` |
  | `LearningProfileRepository` | `student_profile`、`learning_effect_evaluation`、`resource_practice_attempt`、`learning_plan` |
  | `PipelineRunRepository` | `pipeline_run` |
  | `AiReportRepository` | `ai_report` |

Service 层仍保留少量只读的统计聚合查询（`AiInsightService`、`LearningAgentService`、
`CourseKnowledgeContextService`），它们与各自的分析逻辑耦合紧密，未做抽离。
写操作已全部经由仓储。

## 编写要求

迁移会运行在两种库上：全新空库，以及历史上用手工脚本建过表的存量库，因此必须幂等。

- 建表统一 `CREATE TABLE IF NOT EXISTS`
- 种子数据用 `INSERT IGNORE` 或 `ON DUPLICATE KEY UPDATE`
- **加列不能直接 `ALTER TABLE ... ADD COLUMN`**：MySQL 8 没有 `ADD COLUMN IF NOT EXISTS`，
  列已存在会报错，该版本被标记失败后会阻塞其后全部迁移。
  Java 迁移用 `addColumnIfMissing`，SQL 迁移参照 `V6` 先查 `information_schema` 再决定执行。
- 不要写 `DROP TABLE`。`V6` 是唯一的例外，且加了「表为空」的硬性前置判断并写明了理由。
- 已执行过的 SQL 迁移不得改内容，Flyway 会校验校验和；需要变更时新增更高版本。
  Java 迁移默认不参与校验和比对，但同样不应改动已发布的行为。

## attend_session 的列名问题

`AttendSessionMapper.xml` 读写的是 `checkin_code / title / type / late_minutes /
duration / create_time`。V1 早期版本却把表建成了 `session_code / session_name /
created_at`，导致按 V1 建库的环境一调用签到接口就报 Unknown column。

V1 已改为正确列名，`V6` 负责修复那些已经执行过旧版 V1 的库：仅当表存在、缺
`checkin_code`、且没有任何数据时才重建。列名对不上时应用根本插不进数据，
所以「无数据」等价于「这张表从没被用过」。

## 验证

`src/test/resources/db/test-base-schema.sql` 是集成测试用的最小基础表，不参与生产部署。
`EnterpriseBaselineIntegrationTest` 会起一个真实的 MySQL 8 容器把全部迁移跑一遍，
是验证迁移的主要手段。它标了 `disabledWithoutDocker`，**没装 Docker 时会静默跳过**——
上面那个列名不一致的问题能长期存在，正是因为这几个测试一直没真正执行过。
改动迁移后请在装有 Docker 的环境执行 `mvn test` 确认。
