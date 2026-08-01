# CLAUDE.md — CineVerse 电影院订票系统

> 本文件是本项目的唯一真相来源(single source of truth)。每次开新的 Claude Code session,先读这份文件。
> 更新时间:2026-08(随项目迭代持续更新)

---

## 0. 项目定位

- **目标**:作为求职作品集(Portfolio)项目,展示完整的后端工程能力(架构设计、并发处理、安全性、测试、CI/CD),而不只是CRUD堆砌。
- **开发方式**:Agile / 迭代式。不追求一次性做完11个模块,一个模块打磨到"能上线的质量"再进下一个。
- **参考视觉风格**:Apple 风格的高交互暗色主题(见 Cineverse 参考图:大幅Hero banner、清晰的卡片评分、简洁的CTA按钮)。

---

## 1. 技术栈(已确认版本,2026-08)

### 后端
| 组件 | 选型 | 备注 |
|---|---|---|
| 语言 | Java 25 (LTS) | 2025年9月发布的LTS,支持窗口最长 |
| 框架 | Spring Boot 4.1.x | 基于 Spring Framework 7;**不要用 3.x 教程**,3.5 已于 2026-06-30 EOL |
| 安全 | Spring Security 7 | 配合 Spring Framework 7 |
| 数据库 | PostgreSQL 16+ | |
| 迁移工具 | Flyway | Schema 版本化,面试时能讲清楚数据库变更管理 |
| 缓存/分布式锁 | Redis 7.x | 座位锁 + refresh token 黑名单 |
| ORM | Spring Data JPA + Hibernate | |
| DTO映射 | MapStruct | 禁止 Entity 直接暴露给 Controller |
| API文档 | springdoc-openapi | 自动生成 Swagger UI |
| 测试 | JUnit 5 + Testcontainers (Postgres, Redis) | 集成测试用真实容器,不用H2糊弄 |
| 构建工具 | Maven | |
| 容器化 | Docker + docker-compose(本地开发) | |
| CI/CD | GitHub Actions | build + test + (后续)部署 |

### 前端(⚠️ 假设,待你确认)
- React + Next.js + Tailwind CSS + shadcn/ui
- 理由:要做 Apple 风高交互体验,这套组合在动效、组件质感上最省力
- **如果你已经有别的想法(Vue/纯HTML+JS等),告诉我,这部分要改**

---

## 2. 架构原则

- 分层:Controller → Service → Repository,禁止 Controller 直接操作 Repository
- Entity 与 DTO 严格分离,用 MapStruct 转换
- 全局异常处理:`@RestControllerAdvice` 统一错误响应格式(错误码 + message,不要裸露 stacktrace)
- 权限模型:`ROLE_CUSTOMER` / `ROLE_ADMIN`,后续如有影院经理角色再扩展
- **公开路由 vs 登录路由要在路由设计阶段就分开**:浏览电影、场次列表属于公开只读 API,不应该依赖登录态
- 每个模块交付时必须有:API文档(Swagger)+ 至少核心逻辑的单元测试 + README更新

---

## 3. 模块路线图(Phase-based,迭代式)

> 顺序按"依赖关系 + 对作品集的边际价值"排列。促销积分/评价/通知三个模块价值低,放 Backlog,MVP不做。

### Phase 0 — 项目基建(在写任何业务代码前)
- [ ] Spring Boot 4.1.x 项目骨架 + Docker Compose(Postgres + Redis)
- [ ] Flyway 初始化,建立 `V1__init.sql`
- [ ] 全局异常处理 + 统一响应格式
- [ ] GitHub Actions:push 时跑 build + test
- [ ] Swagger/OpenAPI 接入

### Phase 1 — 用户管理(当前冲刺,User Management)
详见第 4 节。

### Phase 2 — 影片管理(Movie Management)
- 电影 CRUD(仅 ADMIN)、genre、trailer 链接、rating、poster 图片存储(先本地/后 S3)
- 公开 API:分页查询、按 genre 筛选、按状态筛选(Now Playing / Coming Soon)

### Phase 3 — 影院/影厅管理(Cinema & Hall Management)
- 分店(Cinema)→ 影厅(Hall)→ 座位布局(Seat Layout,行列 + 座位类型:普通/VIP/情侣座)
- MVP 范围建议:先做 1 个分店、2-3 个影厅,座位布局用简单的行列网格,不做花哨的非规则布局

### Phase 4 — 场次排期(Showtime Scheduling)
- 电影 + 影厅 + 时间段绑定,校验同一影厅时间段不冲突(含清场缓冲时间)

### Phase 5 — 选座/订票(Seat Booking)⚠️ 核心难点
- 座位状态机:`AVAILABLE → LOCKED(Redis, TTL 5min) → CONFIRMED / EXPIRED / CANCELLED`
- Redis 分布式锁只解决"同一时刻不能两人同时选中",**必须配合 TTL 自动过期 + 数据库订单状态同步**,否则锁泄漏会导致座位永久不可用
- 前端座位图实时更新:WebSocket 广播座位状态变化(可选加分项,MVP先用轮询也行,别一开始就上WebSocket卡住进度)

### Phase 6 — 支付模块(Payment)
- Stripe 测试模式(国际通用,面试官认得)+ 可选本地 FPX(如果想强调本地化)
- **Webhook 幂等性处理**:同一支付回调可能重复到达,必须用订单号做幂等校验,这是很多人漏掉的点

### Phase 7 — 订单/电子票(Order & E-ticket)
- 订单记录、QR code 生成、入场核销 API(扫码校验 + 防止重复入场)

### Phase 8 — 管理后台/报表(Admin Dashboard & Reporting)
- 销售报表、上座率分析(SQL聚合查询,能展示你的SQL能力)

### Backlog(MVP不做,时间充裕再加)
- 促销/会员积分
- 评价评分
- 邮件/短信通知

---

## 4. 当前冲刺详情:User Management(Login 优先)

### 4.1 范围边界(这次冲刺只做这些)
- 用户注册(邮箱 + 密码)
- 用户登录(JWT:access token 15min + refresh token 7天,存 httpOnly cookie 或前端安全存储待定)
- Refresh token 轮换(每次刷新旧的失效,防止token被盗用后一直有效)
- 角色区分:CUSTOMER / ADMIN(种子数据里手动插入一个admin)
- 密码用 BCrypt 加密存储
- 登出(refresh token 加入 Redis 黑名单)

### 4.2 暂不做(明确排除,避免范围蔓延)
- 邮箱验证(email verification)——先不做,注册即可用,标注为"生产环境需要"
- 忘记密码/重置密码流程——放到本模块的 v2 迭代
- 第三方登录(Google/Facebook OAuth)——如果时间充裕再加,面试加分但非必须
- 会员等级(Membership tier)——这是Phase 8之后的事,现在只做基础角色

### 4.3 数据表设计(草案)
```
users
  id (UUID, PK)
  email (unique, not null)
  password_hash (not null)
  role (enum: CUSTOMER, ADMIN)
  full_name
  created_at
  updated_at

refresh_tokens
  id (UUID, PK)
  user_id (FK)
  token_hash
  expires_at
  revoked (boolean)
```

### 4.4 API 草案
```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/users/me     (需要认证)
```

### 4.5 完成定义(Definition of Done)
- [ ] 所有 endpoint 有 Swagger 文档
- [ ] 密码错误、邮箱重复注册等异常情况有明确错误码
- [ ] 单元测试覆盖 Service 层核心逻辑(密码校验、token生成/校验)
- [ ] 集成测试(Testcontainers)覆盖 register → login → 访问受保护接口 全流程
- [ ] README 更新:如何本地启动、如何跑测试

---

## 5. 待你确认的开放问题(Open Decisions)

1. 前端框架:React+Next.js 是我的假设,你要用别的吗?
2. JWT token 存储方式:httpOnly cookie(更安全,防XSS)还是前端localStorage(更简单但有XSS风险)?
3. 座位实时更新:MVP阶段用轮询还是直接上WebSocket?(建议轮询,先跑通流程)
4. 部署目标:本地Docker就够,还是要部署到云端给面试官一个可访问的demo链接?(如果要,现在就该定Railway/Render/AWS,影响后面的配置)
5. 影院规模:MVP做几个分店、几个影厅比较合适?(建议1个分店、2-3个影厅,别一开始就上多分店复杂度)

---

## 6. 开发规范

- **分支命名**:`feature/user-management-login`、`fix/xxx`
- **Commit规范**:Conventional Commits(`feat:`, `fix:`, `test:`, `docs:`, `refactor:`)
- **每个Phase完成后**:打一个 tag(如 `v0.1-user-management`),方便简历里写"迭代式交付"
