# CineVerse — 电影院订票系统

CineVerse 是一个作品集(Portfolio)项目:一个电影院订票系统的全栈实现(Spring Boot 后端 + Next.js 前端),用来展示架构设计、并发处理、安全性(JWT认证)、测试(Testcontainers 集成测试)与 CI/CD 等工程能力。

项目按 Phase 迭代式交付,详细的技术栈选型、架构原则与模块路线图见 [`CLAUDE.md`](./CLAUDE.md)。

**当前状态:Phase 0/1 已完成,Phase 2(影片管理)进行中。** 已完成 User Management:注册 / 登录 / refresh token 轮换 / 登出 / 当前用户信息。

## 技术栈

### 后端(`cineverse-backend/`)
- Java 25 (LTS) + Spring Boot 4.1.x + Spring Security 7
- PostgreSQL 16 + Flyway
- Redis 7(座位锁 / refresh token 黑名单,Phase 5+ 接入)
- Spring Data JPA + Hibernate + MapStruct
- JWT(jjwt),BCrypt 密码加密
- springdoc-openapi(Swagger UI)
- JUnit 5 + Mockito + Testcontainers(真实 Postgres 集成测试)
- Maven

### 前端(`frontend/`)
- Next.js 16(App Router + Turbopack)+ React 19.2
- TypeScript(严格模式)
- Tailwind CSS 4 + shadcn/ui(深色主题,暖金色强调色)
- framer-motion(页面转场 / 微交互动效)
- react-hook-form + zod(表单与校验)

## 项目结构

```
CineVerse/
├── CLAUDE.md                 # 项目唯一真相来源:架构、路线图、当前冲刺范围
├── docker-compose.yml        # 本地依赖:Postgres 16 + Redis 7
├── .env.example               # docker-compose 使用的环境变量样例
├── cineverse-backend/        # Spring Boot 后端(Maven 项目)
│   └── src/main/resources/db/migration/  # Flyway 迁移脚本
└── frontend/                 # Next.js 前端
    ├── .env.example           # NEXT_PUBLIC_API_BASE_URL 样例
    └── src/
        ├── app/               # 路由:/, /login, /register, /profile
        ├── components/        # ui(shadcn) / auth / layout / motion
        ├── lib/                # api 客户端、auth context、zod schema
        └── proxy.ts           # 路由保护(Next 16:middleware 改名 proxy)
```

## 本地开发

### 前置条件

- JDK 25(推荐 [Eclipse Temurin](https://adoptium.net/))+ Maven 3.9+
- Node.js 20.9+ + npm
- Docker + Docker Compose

> **本机注意**:8080 端口被 Oracle 的 `TNSLSNR.EXE`(与本项目无关的系统服务)占用,
> 所以下面后端一律用 **8081**。换一台没有这个冲突的机器,直接用默认的 8080 即可
> (把下面命令里的 `SERVER_PORT=8081` 去掉,`frontend/.env.local` 也改回 8080)。

### 1. 启动依赖服务(Postgres + Redis)

```bash
cp .env.example .env   # 按需修改账号密码/端口
docker compose up -d
docker compose ps      # 两个服务都应为 healthy
```

### 2. 启动后端

```bash
cd cineverse-backend
SERVER_PORT=8081 mvn spring-boot:run
```

默认激活 `local` profile,连接 `docker-compose` 起的 Postgres(见 `application-local.yml`)。启动时 Flyway 会自动建表并插入一个本地测试用的 admin 账号(见下方"种子账号")。

验证:

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs

### 3. 启动前端

```bash
cd frontend
cp .env.example .env.local
# .env.example 默认写的是 8080;本机后端跑在 8081,把 .env.local 里的
# NEXT_PUBLIC_API_BASE_URL 改成 http://localhost:8081 再启动
npm install
npm run dev
```

打开 http://localhost:3000 。`/login`、`/register`、`/profile` 已经可以走完整的注册 → 登录 → 查看个人信息 → 登出流程;`/profile` 受 `proxy.ts` 保护,未登录会被重定向回 `/login`。

### 4. 跑测试 / 构建

后端:

```bash
cd cineverse-backend
mvn clean install
```

单元测试(`JwtServiceTest`、`AuthServiceTest` 等)不需要任何外部依赖。集成测试
(`AuthFlowIntegrationTest`)用 Testcontainers 拉起一个真实的临时 Postgres 容器,
所以本地跑 `mvn clean install` 需要 Docker 在运行;GitHub Actions runner 自带
Docker,`.github/workflows/ci.yml` 里的 `mvn test` 会原样执行到这些集成测试。

前端:

```bash
cd frontend
npm run build   # 生产构建
npm run lint    # ESLint
```

## 环境配置(Profiles)

| Profile | 用途 | 配置文件 |
|---|---|---|
| `local`(默认) | 本地开发,连接 docker-compose 里的 Postgres,`cookie-secure=false`(纯 HTTP 方便 curl/浏览器测试) | `application-local.yml` |
| `prod` | 生产部署,所有连接信息 + JWT 密钥必须由环境变量提供,无默认值;`cookie-secure=true` | `application-prod.yml` |

前端只有一个环境变量:`NEXT_PUBLIC_API_BASE_URL`(后端地址),通过 `.env.local` 配置,不在代码里硬编码。

## 种子账号(仅本地开发)

`V2__seed_admin.sql` 会插入一个固定的管理员账号,方便本地联调,**生产环境部署前必须删除这个 migration**:

| Email | Password | Role |
|---|---|---|
| `admin@cineverse.local` | `Admin@12345` | `ADMIN` |

## API 快速上手(curl)

以下命令假设后端跑在 `http://localhost:8081`(按上面的说明替换成你实际的端口)。
refresh token 走 httpOnly cookie,所以要用 curl 的 cookie jar(`-c`/`-b`)把它在
请求之间带上,不能像 access token 一样直接从响应体里复制。

### 1. 注册

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"moviefan@example.com","password":"Sup3rSecret!","fullName":"Jane Doe"}'
```

返回 `201` + 用户信息(不含密码)。邮箱重复注册会返回 `409`。

### 2. 登录(拿 access token,refresh token 存进 cookie jar)

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"moviefan@example.com","password":"Sup3rSecret!"}'
```

响应体里的 `accessToken` 手动复制出来备用(下面用 `$ACCESS_TOKEN` 代替);
`cookies.txt` 里会多一行 `refresh_token`,`HttpOnly`,`Path=/`(broad path是有意的——前端
`proxy.ts` 靠这个 cookie 的存在与否判断路由要不要放行,Path 若收窄到 `/api/v1/auth`,
前端页面路径根本收不到这个 cookie)。
密码错误或邮箱不存在都返回同样的 `401 Invalid email or password`,不会告诉你
到底是哪一种,防止被拿来枚举已注册邮箱。

### 3. 访问受保护接口

```bash
ACCESS_TOKEN="<上一步拿到的 accessToken>"

curl -i http://localhost:8081/api/v1/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

不带 token(或 token 过期/伪造)会返回 `401`。

### 4. 刷新 Token(Token Rotation)

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/refresh \
  -b cookies.txt -c cookies.txt
```

返回新的 `accessToken`,同时 `cookies.txt` 里的 `refresh_token` 也被替换成新的
——旧的那个在服务端已经标记 `revoked`,再拿旧 cookie 去 `/refresh` 会得到 `401`。

### 5. 登出

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/logout \
  -b cookies.txt -c cookies.txt
```

返回 `204`,`cookies.txt` 里的 `refresh_token` 被清空(`Max-Age=0`)。登出之后
再拿这个 token 去 `/refresh` 同样是 `401`。

## 路线图

完整的 Phase 0 ~ Phase 8 模块规划、每个 Phase 的完成定义(Definition of Done)见 [`CLAUDE.md`](./CLAUDE.md) 第 3、4 节。
