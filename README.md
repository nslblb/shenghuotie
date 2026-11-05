# 生活帖（ShenghuoTie）

一个面向生活分享场景的社交平台后端 + 前端。支持发帖、评论、点赞、收藏、关注、私信、通知、举报申诉、管理后台，以及基于 RAG 的 AI 问答（SSE 流式输出）。

本仓库为**最小可运行版本**，仅包含启动项目所需的源码与配置。

***

## 技术栈

| 层  | 技术                                                                    |
| -- | --------------------------------------------------------------------- |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Vue Router · Element Plus · Axios |
| 后端 | Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security + JWT   |
| 数据 | MySQL 8 · Redis 7                                                     |
| 实时 | WebSocket（STOMP over SockJS）· SSE                                     |
| AI | Spring AI · 智谱 GLM（对话）· NVIDIA nv-embed-v1（向量化）· Qdrant（向量库）          |

***

## 仓库结构

```
.
├── backend
│   ├── src/main/java/com/csust/backend   后端源码（含 ai 模块）
│   ├── src/main/resources                配置文件（application.yml / application-prod.yml）
│   ├── init.sql                          数据库建表脚本
│   ├── pom.xml
│   └── mvnw / mvnw.cmd                   Maven Wrapper（无需本机安装 Maven）
├── frontend
│   ├── src                               前端源码
│   ├── public                            静态资源
│   ├── index.html / vite.config.ts
│   └── package.json / package-lock.json
├── README.md
└── .gitignore
```

***

## 环境要求

| 依赖      | 版本                       | 说明                                          |
| ------- | ------------------------ | ------------------------------------------- |
| JDK     | 21+                      | 后端编译与运行                                     |
| Maven   | 3.9+                     | 可用仓库自带 `mvnw` 替代，无需单独安装                     |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端构建（见 `frontend/package.json` 的 `engines`） |
| MySQL   | 8.0                      | 需先建库并导入 `backend/init.sql`                  |
| Redis   | 7+                       | 用于缓存、限流、验证码、在线状态                            |
| Qdrant  | 最新版                      | **可选**，仅 AI 知识库检索需要                         |

***

## 一、环境变量配置

后端配置通过 `${变量名}` 占位符注入环境变量（见 `application.yml` 与 `application-prod.yml`）。

#### 必填项（缺失将无法启动）

| 变量            | 默认值 | 说明                                                                                  |
| ------------- | --- | ----------------------------------------------------------------------------------- |
| `DB_USERNAME` | 无   | MySQL 用户名                                                                           |
| `DB_PASSWORD` | 无   | MySQL 密码                                                                            |
| `JWT_SECRET`  | 无   | JWT 签名密钥，**必须 ≥ 32 字节（UTF-8）**，否则 `Keys.hmacShaKeyFor` 会抛 `WeakKeyException` 导致启动失败 |
| `ZHIPU_KEY`   | 无   | 智谱 GLM API Key（LLM 对话必需）                                                            |

#### 可选项（有默认值，按需覆盖）

| 变量                     | 默认值                                       | 说明                                                     |
| ---------------------- | ----------------------------------------- | ------------------------------------------------------ |
| `REDIS_PASSWORD`       | 空                                         | Redis 密码；留空表示 Redis 未启用密码                              |
| `LLM_BASE_URL`         | `https://open.bigmodel.cn/api/paas/v4`    | LLM 接口地址                                               |
| `EMBEDDING_BASE_URL`   | `https://integrate.api.nvidia.com/v1`     | Embedding 接口地址                                         |
| `EMBEDDING_API_KEY`    | 空                                         | NVIDIA Embedding Key；留空则向量化请求会失败，知识库检索不可用              |
| `QDRANT_HOST`          | `localhost`                               | Qdrant 主机                                              |
| `QDRANT_PORT`          | `6334`                                    | Qdrant gRPC 端口                                         |
| `UPLOAD_DIR`           | `./uploads`                               | 上传文件存储目录（prod profile 默认为 `/data/uploads`）             |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`                   | 允许跨域的来源，多个用逗号分隔                                        |
| `WS_ALLOWED_ORIGINS`   | `http://localhost:*`                      | WebSocket 允许的来源                                        |
| `SMS_CONSOLE_PRINT`    | `true`                                    | 是否把短信验证码打印到控制台（本地开发时设为 `true` 方便调试，生产必须为 `false`）      |
| `DB_HOST`              | `mysql`                                   | **仅 prod profile 生效**，默认 profile 的数据源地址写死为 `localhost` |
| `REDIS_HOST`           | prod 为 `redis` / 默认 profile 为 `localhost` | 同上，仅 prod profile 生效                                   |
| `REDIS_PORT`           | `6379`                                    | 仅 prod profile 生效                                      |

***

## 二、启动后端

```powershell
cd backend

# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

默认监听 `8080`，默认 profile 下可用性检查：

- 接口文档（仅非 prod 环境开启）：`http://localhost:8080/swagger-ui.html`

***

## 三、启动前端

```powershell
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:5173`。

`vite.config.ts` 已配置三个代理，均指向 `http://localhost:8080`：

| 路径         | 用途                      |
| ---------- | ----------------------- |
| `/api`     | 后端 REST 接口              |
| `/uploads` | 用户上传的图片                 |
| `/ws`      | WebSocket（含 `ws: true`） |

**若后端端口不是 8080**，修改 `frontend/vite.config.ts` 中 `server.proxy` 的 `target` 。

其他脚本：

```powershell
npm run build       # 类型检查 + 生产构建（输出到 dist/）
npm run preview     # 预览构建产物
npm run lint        # oxlint + eslint 检查并自动修复
```

***

## 四、演示图片

<img src="C:\Users\HP\AppData\Roaming\Typora\typora-user-images\image-20261105094720395.png" alt="image-20261105094720395" style="zoom:80%;" />

![image-20261105094807020](C:\Users\HP\AppData\Roaming\Typora\typora-user-images\image-20261105094807020.png)



***

## 已知配置说明（注意与实际代码的差异）

- 默认 profile 的 `spring.datasource.url` 与 `spring.data.redis.host` 为硬编码 `localhost`，`DB_HOST` / `REDIS_HOST` 仅在 `prod` profile 下生效。

