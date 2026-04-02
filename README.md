# TCM-Intelligent-Inquiry
An AI-powered Traditional Chinese Medicine (TCM) intelligent inquiry system for symptom analysis, constitution identification, and personalized health recommendations

## Development

**本地必需中间件（可不使用 Docker）**：本机安装并启动 **MySQL 8**（建库 `tcm_inquiry` 或依赖 URL 中 `createDatabaseIfNotExist`）、**Redis Stack**（带 RediSearch，用于向量索引；纯 `redis` OSS 镜像不足以支撑 Spring AI `RedisVectorStore`）。再启动 **Ollama**（默认 `http://localhost:11434`）。后端会把上传文件放在 `backend/data/kb-files` 等目录（启动时创建）。

- **Backend (Spring Boot 3.3+, Java 17+)**: `cd backend && ./mvnw -q -DskipTests compile`. Run: `./mvnw spring-boot:run`（默认端口 **8080**）。通过环境变量配置 MySQL / Redis（见下表）；不要使用 SQLite。
- **Spring AI / Ollama**: 使用 `spring-ai-starter-model-ollama` + `spring-ai-starter-vector-store-redis`（由 `spring-ai-bom` 管版本）。
- **Frontend (Vue 3 + Vite)**: `cd frontend && npm install && npm run dev`。开发态将 `/api` 代理到 `http://127.0.0.1:8080`。质量检查：`npm run lint`、`npm test`、`npm run build`。
- **并行开发（多 Composer / fast）**：`backend/pom.xml` 与主 `application.yml` 仅由 **WS1（backend-platform）** 修改；业务仅限各自包：`modules/consultation|knowledge|literature|agent`、`frontend/`。各模块若新增 JPA 实体，须在本包内增加 `*JpaConfig`（`@EntityScan` + `@EnableJpaRepositories`），与咨询/知识/文献现状一致。

### 阶段一：中医问诊流式对话（已打通）

- **后端**：`POST /api/v1/consultation/chat`（`Content-Type: application/json`，`Accept: text/event-stream`）请求体字段：`sessionId`、`message`、可选 `temperature`、`maxHistoryTurns`。响应为 SSE：`data:` 为增量文本，结束前发送 `data:[DONE]`；流结束后异步写入 `chat_messages`。系统提示词见 `ConsultationPrompts.SYSTEM`。历史：`GET /api/v1/consultation/sessions/{id}/messages`。
- **前端**：问诊页使用 `useChat` + `openSseStream`；**历史会话**列表（`GET /sessions`）与切换后拉取消息（`loadHistory`）；`localStorage` 恢复上次会话；可选 **关联知识库** 在本轮流式请求中注入 RAG 摘录（请求体 `knowledgeBaseId` / `ragTopK` 等）。需本机 **Ollama** 已启动且配置模型可用。

### 阶段二：中医药知识库与全局 RAG（已打通 MVP）

- **依赖**：`spring-ai-tika-document-reader`（PDF/Word/TXT 等经 Tika 抽取文本）、**Redis Stack** 上的 `RedisVectorStore` + Ollama `EmbeddingModel`（默认 `bge-m3:latest`）。单元测试 / `ci` profile 仍可 fallback 到内存 `SimpleVectorStore`。
- **元数据**：向量文档带 `kb_id`、`file_id`、`source`（文件名），检索与删除按 `kb_id` / `file_id` 过滤。
- **接口**（均前缀 `/api/v1/knowledge`）：
  - `POST /bases` 创建知识库；`GET /bases` 列表；
  - `POST /bases/{kbId}/documents`：`multipart/form-data`，字段 `file`，可选 `chunkSize`；
  - `GET /bases/{kbId}/documents`：已上传文件列表；`DELETE /bases/{kbId}/documents/{fileUuid}`：删文件并删向量；
  - `POST /bases/{kbId}/query`：JSON `{ message, topK?, similarityThreshold? }` → RAG 非流式回答。
- **配置**：`application.yml` 中 `tcm.knowledge.*`（分块与检索默认参数）、`spring.servlet.multipart` 大小限制；文件落盘目录默认 `data/kb-files/{kbId}/`（在 `backend/data/` 下，已被 `.gitignore` 覆盖）。
- **前端**：`frontend` 知识库页可选择/创建库、上传、删除与提问。

## 部署与环境变量

### 后端（Spring Boot）

| 变量 / 配置 | 说明 |
|-------------|------|
| `spring.profiles.active` | 生产建议 `prod`，加载 [backend/src/main/resources/application-prod.yml](backend/src/main/resources/application-prod.yml) 示例 |
| `tcm.api.expose-error-details` | `false` 时向客户端隐藏未处理异常的内部详情（对应环境变量 `TCM_API_EXPOSE_ERROR_DETAILS`） |
| `tcm.api.cors-allowed-origin-patterns` | CORS 来源列表；生产勿长期使用 `*` |
| `spring.ai.ollama.base-url` | Ollama 地址；环境变量 `SPRING_AI_OLLAMA_BASE_URL` |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | JDBC 由 `application.yml` 组装为 MySQL 连接（默认库名 `tcm_inquiry`） |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_USERNAME` / `REDIS_PASSWORD` | Redis Stack 连接，供向量检索 |
| `spring.ai.vectorstore.redis.*` | 索引名、前缀、`initialize-schema` 等，见 `application.yml` |

### 前端（Vite 构建 / 开发）

| 变量 | 说明 |
|------|------|
| `VITE_API_PROXY_TARGET` | 仅开发：`vite` 将 `/api` 代理到该地址（默认 `http://127.0.0.1:8080`） |

生产环境通常由 Nginx/Caddy 将 `/api` 反向代理到后端，静态资源指向 `frontend` 的 `dist/`。

### 可选：Docker 编排

若不希望通过本机包管理安装 MySQL/Redis，可使用仓库根目录 [docker-compose.yml](docker-compose.yml)（`docker compose up`）。**日常开发不强制 Docker**：同样可在本机直接跑中间件。

镜像构建参考：[backend/Dockerfile](backend/Dockerfile)。

## 安全与贡献

- [SECURITY.md](SECURITY.md) — API 暴露选型、网关限流示例、漏洞报告方式。
- [CONTRIBUTING.md](CONTRIBUTING.md) — 构建命令与 PR 约定。