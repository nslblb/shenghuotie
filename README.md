# 生活帖（ShenghuoTie）

一个面向生活分享场景的社交平台后端 + 前端。支持发帖、评论、点赞、收藏、关注、私信、通知、举报申诉、管理后台，以及基于 RAG 的 AI 问答（SSE 流式输出）。

本仓库为**最小可运行版本**，仅包含启动项目所需的源码与配置。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Vue Router · Element Plus · Axios |
| 后端 | Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security + JWT |
| 数据 | MySQL 8 · Redis 7 |
| 实时 | WebSocket（STOMP over SockJS）· SSE |
| AI | Spring AI · 智谱 GLM（对话）· NVIDIA nv-embed-v1（向量化）· Qdrant（向量库） |

---

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

---

## 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 21+ | 后端编译与运行 |
| Maven | 3.9+ | 可用仓库自带 `mvnw` 替代，无需单独安装 |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端构建（见 `frontend/package.json` 的 `engines`） |
| MySQL | 8.0 | 需先建库并导入 `backend/init.sql` |
| Redis | 7+ | 用于缓存、限流、验证码、在线状态 |
| Qdrant | 最新版 | **可选**，仅 AI 知识库检索需要 |

---

## 一、准备数据库

后端使用 `ddl-auto: validate`，**不会自动建表**，必须先把表结构导入：

```sql
-- 1. 创建数据库（库名固定为 shenghuotiesql，配置文件里写死的）
CREATE DATABASE shenghuotiesql
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 2. 导入表结构
--    mysql -u root -p shenghuotiesql < backend/init.sql
```

> 库名 `shenghuotiesql` 与端口 `3306` 在 `backend/src/main/resources/application.yml` 的 `spring.datasource.url` 中硬编码。
> 若你的 MySQL 不在本机或端口不同，需要直接修改该 URL（默认配置不读取 `DB_HOST`，详见第二节末尾说明）。

Redis 无需预处理，保证 `localhost:6379` 可连即可。

---

## 二、环境变量配置

### 2.1 变量清单

后端配置通过 `${变量名}` 占位符注入环境变量（见 `application.yml` 与 `application-prod.yml`）。

#### 必填项（缺失将无法启动）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` | 无 | MySQL 用户名 |
| `DB_PASSWORD` | 无 | MySQL 密码 |
| `JWT_SECRET` | 无 | JWT 签名密钥，**必须 ≥ 32 字节（UTF-8）**，否则 `Keys.hmacShaKeyFor` 会抛 `WeakKeyException` 导致启动失败 |
| `ZHIPU_KEY` | 无 | 智谱 GLM API Key（LLM 对话必需） |

#### 可选项（有默认值，按需覆盖）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `REDIS_PASSWORD` | 空 | Redis 密码；留空表示 Redis 未启用密码 |
| `LLM_BASE_URL` | `https://open.bigmodel.cn/api/paas/v4` | LLM 接口地址 |
| `EMBEDDING_BASE_URL` | `https://integrate.api.nvidia.com/v1` | Embedding 接口地址 |
| `EMBEDDING_API_KEY` | 空 | NVIDIA Embedding Key；留空则向量化请求会失败，知识库检索不可用 |
| `QDRANT_HOST` | `localhost` | Qdrant 主机 |
| `QDRANT_PORT` | `6334` | Qdrant gRPC 端口 |
| `UPLOAD_DIR` | `./uploads` | 上传文件存储目录（prod profile 默认为 `/data/uploads`） |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 允许跨域的来源，多个用逗号分隔 |
| `WS_ALLOWED_ORIGINS` | `http://localhost:*` | WebSocket 允许的来源 |
| `SMS_CONSOLE_PRINT` | `true` | 是否把短信验证码打印到控制台（本地开发时设为 `true` 方便调试，生产必须为 `false`） |
| `DB_HOST` | `mysql` | **仅 prod profile 生效**，默认 profile 的数据源地址写死为 `localhost` |
| `REDIS_HOST` | prod 为 `redis` / 默认 profile 为 `localhost` | 同上，仅 prod profile 生效 |
| `REDIS_PORT` | `6379` | 仅 prod profile 生效 |

### 2.2 如何设置

> **重要前提**：Spring Boot **不会自动读取 `.env` 文件**。`.env` 只对 `docker compose` 的 `${VAR}` 插值有效。
> 本地直接启动时，必须通过「系统环境变量」或「IDE 运行配置」注入。

#### 方式一：PowerShell（临时，仅当前终端有效）

```powershell
# 必填项
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "你的MySQL密码"
$env:JWT_SECRET  = "至少32字节的随机字符串"
$env:ZHIPU_KEY   = "你的智谱APIKey"

# 可选项（按需覆盖）
$env:REDIS_PASSWORD     = ""
$env:EMBEDDING_API_KEY  = "你的NVIDIA Key"
$env:QDRANT_HOST        = "localhost"

cd backend
.\mvnw.cmd spring-boot:run
```

新开一个终端后变量会失效，需要重新设置。

生成一个满足长度要求的 JWT 密钥（两个 GUID 拼接，共 72 字符）：

```powershell
$env:JWT_SECRET = (New-Guid).ToString() + (New-Guid).ToString()
```

#### 方式二：IntelliJ IDEA 运行配置（推荐，一次配置长期有效）

`Run/Debug Configurations` → 选中 `BackendApplication` → `Environment variables` 填入：

```
DB_USERNAME=root;DB_PASSWORD=你的密码;JWT_SECRET=至少32字节;ZHIPU_KEY=你的Key
```

#### 方式三：Windows 持久化（写入用户环境变量）

```powershell
setx DB_USERNAME "root"
setx DB_PASSWORD "你的MySQL密码"
setx JWT_SECRET  "至少32字节的随机字符串"
setx ZHIPU_KEY   "你的智谱APIKey"
```

`setx` 写入的是**用户级永久环境变量**，对后续所有进程生效。执行后必须**关闭并重新打开终端**才会读取到新值。

---

## 三、启动后端

```powershell
cd backend

# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

默认监听 `8080`，默认 profile 下可用性检查：

- 接口文档（仅非 prod 环境开启）：`http://localhost:8080/swagger-ui.html`
- 健康探测：请求任意需鉴权接口应返回 401 而非连接失败

---

## 四、启动前端

```powershell
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:5173`。

`vite.config.ts` 已配置三个代理，均指向 `http://localhost:8080`：

| 路径 | 用途 |
|---|---|
| `/api` | 后端 REST 接口 |
| `/uploads` | 用户上传的图片 |
| `/ws` | WebSocket（含 `ws: true`） |

**若后端端口不是 8080**，修改 `frontend/vite.config.ts` 中 `server.proxy` 的 `target` 即可。

其他脚本：

```powershell
npm run build       # 类型检查 + 生产构建（输出到 dist/）
npm run preview     # 预览构建产物
npm run lint        # oxlint + eslint 检查并自动修复
```

---

## 五、AI 功能说明（可选依赖）

AI 模块对第三方服务有依赖，但**不影响平台其他功能**使用：

| 能力 | 依赖 | 缺失时的表现 |
|---|---|---|
| AI 对话 | `ZHIPU_KEY` | 启动即失败（该变量为必填占位符） |
| 知识库检索 | Qdrant + `EMBEDDING_API_KEY` | 退化为纯对话，检索结果为空 |
| 短信验证码 | `SMS_CONSOLE_PRINT=true` | 验证码打印在控制台，需自行查看日志获取 |

本地启动 Qdrant（可选）：

```powershell
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

---

## 六、常见问题

**Q：启动报 `Could not resolve placeholder 'DB_USERNAME'`**
没有设置环境变量。按 2.2 任选一种方式注入。注意 `.env` 文件对 Spring Boot 无效。

**Q：启动报 `WeakKeyException: The specified key byte array is ... bits which is not secure enough`**
`JWT_SECRET` 长度不足 32 字节，换成更长的随机串。

**Q：启动报 `Table 'shenghuotiesql.user' doesn't exist`**
未导入表结构。先执行 `backend/init.sql`（`ddl-auto` 为 `validate`，不会自动建表）。

**Q：前端页面能打开但所有请求 401 / 跨域报错**
检查后端是否已启动、`CORS_ALLOWED_ORIGINS` 是否包含前端实际地址。

**Q：登录时收不到短信验证码**
本地开发把 `SMS_CONSOLE_PRINT` 设为 `true`，验证码会打印在后端控制台。

---

## 已知配置说明（注意与实际代码的差异）

- `EMBEDDING_ENABLED` 这个变量在本仓库的配置中**未被引用**：`application.yml` 里 `spring.ai.openai.embedding.enabled` 是硬编码的 `true`。设置该环境变量不会产生效果；如需要开关控制，需改造为 `${EMBEDDING_ENABLED:true}`。
- 默认 profile 的 `spring.datasource.url` 与 `spring.data.redis.host` 为硬编码 `localhost`，`DB_HOST` / `REDIS_HOST` 仅在 `prod` profile 下生效。
