# CineVerse — 电影院订票系统

CineVerse 是一个电影院在线选座订票系统:用户可以浏览正在上映/即将上映的电影、挑选场次、在座位图上实时选座、在线支付,支付成功后即时拿到电子票;管理员可以管理电影、分店影厅、场次排期。前端 Next.js,后端 Spring Boot,数据库 PostgreSQL,座位锁与幂等控制走 Redis。

## 核心功能

- **实时座位锁定**——选座时,座位在持有窗口内会对其他用户实时显示为"锁定",避免同一座位被多人同时抢订。底层用 Redis 做原子加锁(单条 `SET NX EX` 命令,不是"先查后写"两步操作),保证并发下真正只有一个人能锁定同一座位。
- **完整的在线支付闭环**——接入 Stripe Checkout:选座、创建订单、跳转支付、Stripe webhook 回调确认订单,一条完整走得通的支付流程,而不是停在"模拟支付成功"那一步;webhook 处理做了幂等保护,同一支付事件重复到达不会产生重复订单或重复扣费记录。
- **Liquid Glass 视觉设计语言**——参照 Apple 最新的 Liquid Glass 设计语言,高光跟随指针动态移动的玻璃拟态卡片,配合流畅的页面转场与微交互动效,高交互性的现代化界面。
- **JWT 认证 + 角色权限管理**——Customer / Admin 两种角色;access token 只存在前端内存中、refresh token 走 httpOnly cookie,兼顾安全性与使用体验。
- **电子票 + 入场核销**——支付成功后即拥有一张电子票(签名过的二维码,不是能被猜测/伪造的自增编号),现场扫码/输入编码即可核验入场;同一张票被重复核销会被拒绝,避免一票多用。
- **管理后台报表**——销售报表(按日/周/月粒度统计营收,Postgres `GROUP BY` + `date_trunc` + `generate_series` 真实聚合,不是拉全表到内存里算)与上座率分析,支持 CSV/PDF 导出;营收口径明确区分"已确认收入"与"待人工核对金额",不静默丢弃异常支付。

## 作为作品集(Portfolio)项目

CineVerse 同时是一个全栈工程能力的作品集项目:架构设计、高并发下的数据一致性处理、安全性、测试(Testcontainers 真实数据库/缓存集成测试)与 CI/CD 等工程实践贯穿整个项目,而不只是 CRUD 堆砌。

**当前状态:Phase 0~8 全部完成,MVP 路线图收尾。** User Management(注册/登录/刷新/登出)、Movie Management(电影 CRUD + 海报上传)、Cinema & Hall Management(分店/影厅/座位自动布局)、Showtime Scheduling(场次排期)、Seat Booking(选座锁座 + 订单)、Payment(Stripe Checkout + webhook 幂等确认)、Order & E-ticket(电子票 + 入场核销)、Admin Dashboard & Reporting(销售报表 + 上座率分析,CSV/PDF 导出)。

## 技术栈

### 后端(`cineverse-backend/`)
- Java 25 (LTS) + Spring Boot 4.1.x + Spring Security 7
- PostgreSQL 16 + Flyway
- Redis 7(座位锁,Phase 5 接入;refresh token 撤销走的是数据库 `revoked` 字段,不经过 Redis)
- Spring Data JPA + Hibernate + MapStruct
- JWT(jjwt),BCrypt 密码加密;电子票编码(Phase 7)复用同一套 jjwt 签名机制
- Stripe Checkout(测试模式,Phase 6 支付;webhook 签名验证 + 幂等确认)
- OpenPDF(LGPL/MPL,Phase 8 报表 PDF 导出)
- springdoc-openapi(Swagger UI)
- JUnit 5 + Mockito + Testcontainers(真实 Postgres 集成测试)
- Maven

### 前端(`frontend/`)
- Next.js 16(App Router + Turbopack)+ React 19.2
- TypeScript(严格模式)
- Tailwind CSS 4 + shadcn/ui(深色主题,暖金色强调色)
- framer-motion(页面转场 / 微交互动效)
- react-hook-form + zod(表单与校验)
- qrcode.react(电子票二维码,客户端渲染,Phase 7)
- recharts(管理后台报表图表,Phase 8)

## 项目结构

```
CineVerse/
├── CLAUDE.md                 # 项目记忆:架构、路线图、当前冲刺范围(面向 Claude Code)
├── docs/DEVELOPMENT.md       # 开发者参考:API curl 示例、环境配置、手动验证步骤
├── docs/DATABASE.md          # 数据库 schema 参考:表结构、外键策略、字段级说明
├── docker-compose.yml        # 本地依赖:Postgres 16 + Redis 7
├── .env.example               # docker-compose 变量 + 后端进程直接读取的 Stripe key(Phase 6)
├── cineverse-backend/        # Spring Boot 后端(Maven 项目)
│   └── src/main/resources/db/migration/  # Flyway 迁移脚本
└── frontend/                 # Next.js 前端
    ├── .env.example           # NEXT_PUBLIC_API_BASE_URL 样例
    └── src/
        ├── app/               # 路由:/, /login, /register, /profile, /showtimes/[id](/seats), /bookings/[id]/confirmed
        ├── components/        # ui(shadcn) / auth / layout / motion / booking(选座 + 支付确认)
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

## 快速体验

本地跑起来后,用种子管理员账号(`admin@cineverse.local` / `Admin@12345`)登录可以
直接体验电影/影院/场次的 CRUD 管理功能;或者自行注册一个新账号体验 Customer 的
选座订票流程。

---

详细的API调试命令和环境配置见 [`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md)。

完整的技术栈选型、架构原则与模块路线图见 [`CLAUDE.md`](./CLAUDE.md)。
