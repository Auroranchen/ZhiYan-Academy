# 智枢学堂 · RAG 评测报告

- 生成时间：2026-09-13T10:06:25.899852100Z
- 金标准集：golden（可答 9 / 负样本 4）
- 语料快照：corpus-v1（资料 43 / 切片 179）
- 模式：仅检索（不调用模型，指标均为确定性计算）
- 生成模型：-　评判模型：-

## 变体对比

| 变体 | 检索命中率 |
|---|---|
| v0-baseline | 88.9% |
| v1-lexical-only | 77.8% |
| v2-semantic-only | 44.4% |
| v3-topk20 | 88.9% |
| v4-semantic-heavy | 77.8% |
| v5-lexical-heavy | 88.9% |
| v6-zhipu-hybrid | 77.8% |
| v7-zhipu-semantic-only | 44.4% |
| v8-zhipu-lexical-only | 77.8% |
| v9-zhipu-threshold | 66.7% |
| v10-zhipu-lexical-heavy | 88.9% |
| v11-zhipu-tuned | 66.7% |

> 本次为仅检索模式：不调用任何模型，指标全部是确定性计算。它衡量的是**纯检索质量**，与生成模型无关。

> **检索命中率** = 题目标注的切片是否真的被检索出来（越接近 100% 越好，与模型无关，纯检索质量）。

## 逐题明细

### v0-baseline

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v1-lexical-only

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✓ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✗ |
| JAVA-ENCAP-01 | Java 封装 | ✗ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v2-semantic-only

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✗ |
| PY-LIST-02 | Python 列表 | ✗ |
| PY-LIST-03 | Python 列表 | ✗ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✗ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v3-topk20

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v4-semantic-heavy

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✗ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v5-lexical-heavy

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v6-zhipu-hybrid

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✗ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v7-zhipu-semantic-only

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✗ |
| PY-LIST-02 | Python 列表 | ✗ |
| PY-LIST-03 | Python 列表 | ✗ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✗ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v8-zhipu-lexical-only

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✓ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✗ |
| JAVA-ENCAP-01 | Java 封装 | ✗ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v9-zhipu-threshold

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✗ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✗ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v10-zhipu-lexical-heavy

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✓ |
| JAVA-ENCAP-01 | Java 封装 | ✓ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

### v11-zhipu-tuned

| 题号 | 主题 | 检索命中 |
|---|---|---|
| PY-LIST-01 | Python 列表 | ✓ |
| PY-LIST-02 | Python 列表 | ✓ |
| PY-LIST-03 | Python 列表 | ✓ |
| PY-COND-01 | Python 条件语句 | ✗ |
| JAVA-VAR-01 | Java 关键字 | ✓ |
| JAVA-STR-01 | Java 字符串 | ✓ |
| SPRING-FW-01 | Spring 框架 | ✓ |
| SPRING-ORM-01 | Spring 框架 | ✗ |
| JAVA-ENCAP-01 | Java 封装 | ✗ |
| NEG-MYSQL-01 | 负样本 | ✗ |
| NEG-TX-01 | 负样本 | ✗ |
| NEG-CONTAINER-01 | 负样本 | ✗ |
| NEG-REDIS-01 | 负样本 | ✗ |

