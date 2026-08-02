# CineVerse — 电影院订票系统

CineVerse 是一个作品集(Portfolio)项目:一个电影院订票系统的全栈实现(Spring Boot 后端 + Next.js 前端),用来展示架构设计、并发处理、安全性(JWT认证)、测试(Testcontainers 集成测试)与 CI/CD 等工程能力。

**当前状态:Phase 0~5 已完成。** User Management(注册/登录/刷新/登出)、Movie Management(电影 CRUD + 海报上传)、Cinema & Hall Management(分店/影厅/座位自动布局)、Showtime Scheduling(场次排期)、Seat Booking(选座锁座 + 订单)。

## 技术栈

### 后端(`cineverse-backend/`)
- Java 25 (LTS) + Spring Boot 4.1.x + Spring Security 7
- PostgreSQL 16 + Flyway
- Redis 7(座位锁,Phase 5 接入;refresh token 撤销走的是数据库 `revoked` 字段,不经过 Redis)
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
├── CLAUDE.md                 # 项目记忆:架构、路线图、当前冲刺范围(面向 Claude Code)
├── docs/DEVELOPMENT.md       # 开发者参考:API curl 示例、环境配置、手动验证步骤
├── docker-compose.yml        # 本地依赖:Postgres 16 + Redis 7
├── .env.example               # docker-compose 使用的环境变量样例
├── cineverse-backend/        # Spring Boot 后端(Maven 项目)
│   └── src/main/resources/db/migration/  # Flyway 迁移脚本
└── frontend/                 # Next.js 前端
    ├── .env.example           # NEXT_PUBLIC_API_BASE_URL 样例
    └── src/
        ├── app/               # 路由:/, /login, /register, /profile, /showtimes/[id](/seats)
        ├── components/        # ui(shadcn) / auth / layout / motion / booking(选座)
        ├── lib/                # api 客户端、auth context、zod schema
        └── proxy.ts           # 路由保护(Next 16:middleware 改名 proxy)
```

## 本地开发

### 前置条件

- JDK 25(推荐 [Eclipse Temurin](https://adoptium.net/))+ Maven 3.9+
- Node.js 20.9+ + npm
- Docker + Docker Compose

### 1. 启动依赖(Postgres + Redis)

```bash
cp .env.example .env
docker compose up -d
```

### 2. 启动后端

```bash
cd cineverse-backend
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

打开 http://localhost:3000 。

### 4. 跑测试

```bash
# 后端:单元测试 + Testcontainers 集成测试(需要 Docker 在运行)
cd cineverse-backend
mvn clean install

# 前端
cd frontend
npm run build
npm run lint
```

---

详细的API调试命令和环境配置见 [`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md)。

完整的技术栈选型、架构原则与模块路线图见 [`CLAUDE.md`](./CLAUDE.md)。
