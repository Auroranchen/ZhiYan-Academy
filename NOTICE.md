# 第三方开源组件与许可说明

本项目为「智枢学堂」参赛作品，团队原创代码版权归团队成员所有。项目在开发中使用了以下开源软件/组件，均遵循其各自的开源许可证；本项目未将任何开源项目的代码直接包装为参赛作品。

## 后端

| 组件 | 用途 | 许可证（以官方发布为准） |
|---|---|---|
| Spring Boot / Spring Framework | 应用框架、Web、Security | Apache-2.0 |
| Spring Security | 会话鉴权、CSRF、CORS、安全响应头 | Apache-2.0 |
| MyBatis / MyBatis-Spring-Boot-Starter | 教务主数据 ORM | Apache-2.0 |
| Flyway | 数据库版本迁移 | Apache-2.0 |
| MySQL Connector/J | MySQL 驱动 | GPL-2.0 with FOSS Exception |
| Apache POI | Word / Excel / PPT 解析与生成 | Apache-2.0 |
| Apache PDFBox | PDF 文本提取 | Apache-2.0 |
| OkHttp | 外部 AI / 语音服务 HTTP 客户端 | Apache-2.0 |
| Lombok | 编译期样板代码生成 | MIT |
| Jackson | JSON 序列化 | Apache-2.0 |
| Caffeine | 本地缓存 | Apache-2.0 |
| JUnit 5 / Mockito / Testcontainers | 测试 | EPL-2.0 / MIT / MIT |

## 前端

| 组件 | 用途 | 许可证（以官方发布为准） |
|---|---|---|
| Vue 3 / Vue Router / Vuex | 前端框架、路由、状态管理 | MIT |
| Element Plus | 企业级 UI 组件库 | MIT |
| ECharts | 图表与雷达图 | Apache-2.0 |
| Axios | HTTP 客户端 | MIT |
| DOMPurify | HTML 白名单消毒 | Apache-2.0 OR MPL-2.0 |
| Iconify | 图标 | MIT / 各图标集许可证 |
| @wangeditor/editor | 富文本编辑器 | MIT |
| html2pdf.js | 前端 PDF 导出 | MIT |
| Vitest / fast-check / jsdom | 单元测试与属性测试 | MIT |

## 外部 AI / 语音服务

- DeepSeek API：对话与内容生成（商用 API，按官方条款使用）。
- 智谱 AI embedding-3：文本向量化（商用 API，按官方条款使用）。
- 科大讯飞：语音听写与在线语音合成（商用 API，按官方条款使用）。

> 上述服务仅通过环境变量/本地密钥文件注入凭据，仓库不包含任何真实密钥。
