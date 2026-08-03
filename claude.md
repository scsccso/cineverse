# CLAUDE.md — CineVerse 电影院订票系统

> 本文件是本项目的唯一真相来源(single source of truth)。每次开新的 Claude Code session,先读这份文件。
> 更新时间:2026-08(随项目迭代持续更新)
> 当前进度:Phase 0~6 已完成(含 Phase 6 Stripe Checkout 支付),Phase 7(订单/电子票)未开始 —— 详见第 3 节。
> 详细的API调试步骤见 docs/DEVELOPMENT.md,面向招聘官的项目介绍见 README.md,本文件是面向Claude Code的项目记忆。

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

### 前端(已确认,2026-08-01 定案)
| 组件 | 选型 | 备注 |
|---|---|---|
| 框架 | Next.js 16.x | App Router + Turbopack;`middleware` 改名 `proxy.ts` |
| UI 库 | React 19.2 | |
| 语言 | TypeScript(严格模式) | |
| 样式 | Tailwind CSS 4.x | CSS-first 配置(`@theme`),深色主题为默认且唯一主题 |
| 组件基础 | shadcn/ui(base-ui 变体) | 自定义暖金色(`#F4C430`)强调色覆盖默认样式 |
| 动效 | framer-motion | 页面转场、按钮反馈、表单校验进出场动效 |
| 表单校验 | react-hook-form + zod | |
- 理由:要做 Apple 风高交互体验,这套组合在动效、组件质感上最省力
- 设计 token、字体分工、组件复用与无障碍标准的完整记录见 1.5 节。

---

## 1.5 前端设计系统(Liquid Glass)

> 下面几条原本分散记在本节(第 1 节)的技术栈表格备注,以及第 3 节 Phase 5
> 前端补充里,这里整合成一节统一记录;那两处原文保留了简短提及 + 指向本节的
> 链接,不做整段搬迁删除,避免 Phase 记录里的上下文断裂。

- **设计语言定位**:参照 Apple 2025~2026 的 Liquid Glass 设计语言,核心是"高光
  跟随指针动态移动",不是把静态渐变边框往卡片上一贴就算数的 glassmorphism
  抄袭——`GlassCard`(`frontend/src/components/glass/glass-card.tsx`)的高光位置
  通过 `--glow-x`/`--glow-y` 两个 CSS 自定义属性在 `pointermove` 时实时更新,
  用 `radial-gradient(circle at var(--glow-x) var(--glow-y), ...)` 渲染,这是和
  固定角度渐变的静态玻璃拟态本质上的区别。
- **设计 token**(定义在 `frontend/src/app/globals.css` 的 `.dark` 选择器下——
  暗色是唯一主题,这些 token 没有浅色变体):
  - `--glass-surface: rgb(255 255 255 / 6%)` —— 卡片底色,极低不透明度白,
    营造"透光而非发光"的玻璃质感
  - `--glass-border: rgb(255 255 255 / 15%)` —— 卡片描边
  - `--glass-highlight: rgb(255 255 255 / 35%)` —— 高光本体颜色,配合上面的
    `radial-gradient` 在指针位置渲染出跟随移动的光斑
  - 配套的 `--blur-glass: 20px`(`backdrop-blur-glass` 工具类)做的是真正的
    背景模糊,不是叠一层半透明色蒙混视觉
- **字体分工**(定义在 `frontend/src/app/layout.tsx`):
  - **Clash Display**(标题,`--font-display`):Fontshare 出品,但它不在
    `next/font/google` 目录、也没有发 npm 包——把 woff2 文件下载到
    `src/app/fonts/` 自托管,用 `next/font/local` 加载,而不是在 `<head>` 里加
    CDN `<link>`。这不是随手的性能优化,是修一个真 bug:`<link>` 不允许直接挂
    在 `<html>` 下,浏览器会在 React hydrate 之前静默把它重新挂载到合法位置,
    导致 server/client 渲染树对不上、抛 hydration 错误。自托管方式和
    `next/font/google` 走同一套字体优化管线(子集化、避免 FOIT/FOUT),顺带
    绕开了这个问题。
  - **Inter**(正文,`--font-sans`):`next/font/google`,标准无衬线正文字体。
  - **JetBrains Mono**(数据类文字——座位号、价格、token 展示,`--font-mono`):
    `next/font/google`,等宽字体让数字对齐、易扫读。
- **`GlassCard` 是全应用的 signature 组件**:以后新增卡片式 UI(电影卡、场次卡
  等)默认基于它构建,不要另起一套玻璃拟态样式。**唯一的例外是座位图**
  (`components/booking/seat-map.tsx`):一个影厅动辄上百个座位按钮,如果每个都
  实例化完整的 `GlassCard`(每张卡自带一个 `pointermove` 监听 + framer-motion
  光标跟随高光),等于同时挂上百个 `pointermove` 监听器,会拖慢交互——所以座位
  按钮只复用 `--glass-*` 这套 CSS token 做视觉语言统一,不复用 `GlassCard`
  组件本体,按钮上只留一个轻量的 `whileTap` 缩放反馈。这是密度驱动的性能例外,
  不是"忘了用组件库"。
- **已确立的无障碍原则**(以下几条是所有新页面的默认标准,不是三个页面各自
  处理一次的一次性工作):
  - **状态区分不只靠颜色**:边框样式 + 图标双重编码。座位图四态为例——可选:
    实线玻璃描边;已选:主题金色描边 + 浅色填充;`LOCKED`:虚线描边 + 灰色
    半透明 + 锁形图标;`BOOKED`:实心灰底、无描边 + 勾选图标。色弱用户也能靠
    形状/图标分辨状态,不依赖颜色对比。
  - **`prefers-reduced-motion` 两层降级(JS + CSS)**:`GlassCard` 用
    `useReducedMotion()`(framer-motion 提供的 JS hook)在系统开启该设置时直接
    跳过 `whileHover` 缩放和 `pointermove` 高光更新;同时高光图层本身还叠加了
    Tailwind 的 `motion-reduce:hidden`(纯 CSS)作为第二层保险——即使某个代码
    路径漏掉了 JS 判断,CSS 这层也会兜底隐藏动效,不依赖单一判断点。
  - **触屏设备的 hover 效果降级**:`GlassCard` 用
    `window.matchMedia("(hover: hover) and (pointer: fine)")` 判断当前输入
    设备是否支持真正的悬停(排除触屏),不支持则高光固定显示在卡片左上角一个
    默认位置,不强行模拟一个触屏上不存在的"指针跟随"效果。
  - **最小点击热区 44×44px**(WCAG 2.5.5):2026-08-03 之前这条只在座位图和
    部分表单/CTA 按钮上靠手动加 `h-11` 类名局部达标,不是设计系统的默认值,
    验证时发现共享 `Button` 组件(`components/ui/button.tsx`)自身的
    `default`/`lg` 尺寸变体其实还是 `h-8`(32px)/`h-9`(36px)——已修正:
    两个变体的高度统一提到 `h-11`(44px),这样以后新写一个不带高度覆盖的
    `<Button>` 也会自动达标;座位图按钮(不走共享 `Button` 组件,是独立的
    `motion.button`)也从 `h-8` 一并提到 `h-11`,配合已有的横向滚动容器
    (座位多的行在窄屏上滚动查看,而不是把按钮压缩到 44px 以下)。
    `xs`/`sm`/`icon-xs`/`icon-sm` 这几个更小的尺寸变体保持原样——它们是有意
    做小的次要工具型控件(导航栏的登出按钮、次要 nav 链接),不是这条标准的
    疏漏。已跑过 `npm run build` + `npm run lint` 确认这次调整没有影响其他
    页面(所有主要 CTA 按钮本来就带显式的 `h-11`/`h-12` 覆盖,不依赖这个默认值)。

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

### Phase 0 — 项目基建(在写任何业务代码前)✅ 完成于 2026-08-01
- [x] Spring Boot 4.1.x 项目骨架 + Docker Compose(Postgres + Redis)
- [x] Flyway 初始化,建立 `V1__init.sql`
- [x] 全局异常处理 + 统一响应格式
- [x] GitHub Actions:push 时跑 build + test
- [x] Swagger/OpenAPI 接入

关键决定:
- Spring Boot 4.1 比预想中变动更大(模块化拆分)—— `springdoc-openapi` 要 3.0.x
  才兼容 Spring Framework 7;Flyway 的 Spring 胶水代码独立成
  `spring-boot-starter-flyway`;MockMvc 测试支持挪到
  `spring-boot-starter-webmvc-test`;默认 Jackson 版本变成 Jackson 3
  (`tools.jackson.*`,不是 `com.fasterxml.jackson.*`)。踩坑记录写进了对应模块的代码注释里。

### Phase 1 — 用户管理(User Management)✅ 完成于 2026-08-01
CI 曾因为一个真实的 flaky test bug 红过一次(access token 缺 `jti`,
同一秒内签发的两个 token 会完全相同)——已修复并补了回归测试,细节见
`JwtService`/`JwtServiceTest`。

关键决定:
- **Token 存储方式**:access token 15min,纯内存(前端 React Context,不落地
  localStorage/sessionStorage,防 XSS);refresh token 7 天,**httpOnly cookie**
  (`Path=/`,`SameSite=Strict`,`Secure` 视 profile 而定)。第 4 节的开放问题 #2
  已按此定案。
- **Token rotation**:每次 `/refresh` 旧 refresh token 立即标记 `revoked`,下发新
  的一对 token,不是简单延期。
- **CORS**:后端显式配置 `allowedOrigins`(默认 `http://localhost:3000`)+
  `allowCredentials(true)`,因为 refresh cookie 要靠 `credentials:'include'` 跨
  端口(3000 → 8081)携带。SameSite=Strict 在这里仍然生效,因为"site"按
  scheme+eTLD+1 算,不看端口——`localhost:3000` 和 `localhost:8081` 是跨源但
  同站。
- **前端框架**:确认用 Next.js 16(App Router + Turbopack),不是 Vue/纯HTML。
  第 4 节开放问题 #1 已按此定案。

### Phase 2 — 影片管理(Movie Management)✅ 完成于 2026-08-02
- 电影 CRUD(仅 ADMIN)、genre、trailer 链接、rating、poster 图片存储(先本地/后 S3)
- 公开 API:分页查询、按 genre 筛选、按状态筛选(Now Playing / Coming Soon)

关键决定:
- `content_rating`(如 "PG-13",分级)和 `user_rating`(admin 手填的数值评分)是
  两个独立字段,命名上刻意区分,不要混用。
- poster/backdrop 的 URL **永不返回 null** —— 没上传时后端直接给一个占位图 URL
  (`/images/no-poster.svg` 等),前端不用自己处理裂图兜底逻辑。
- `StorageService` 接口 + `LocalStorageService` 实现(存本地 `uploads/` 目录,
  已加入 `.gitignore`),换 S3 只需新增一个实现类,调用方不用改。
- 匿名用户打 ADMIN-only 接口拿到的是 **401**,不是 403——这是 Spring Security
  的标准语义(匿名主体触发 `AccessDeniedException` 会被 `ExceptionTranslationFilter`
  转译成"先认证"的 401,403 只留给"已登录但角色不对"的场景)。这一条在集成测试
  里踩过一次坑(以为该测 403,实际断言失败才发现是 401),之后 Phase 3 直接照
  这个语义写测试,没有再踩。

### Phase 3 — 影院/影厅管理(Cinema & Hall Management)✅ 完成于 2026-08-02
- 分店(Cinema)→ 影厅(Hall)→ 座位布局(Seat Layout,行列 + 座位类型:标准/情侣座)
- MVP 范围:1 个分店、3 个影厅,座位布局用简单的行列网格,不做花哨的非规则布局

关键决定:
- **座位类型只做 2 种**:`STANDARD`、`COUPLE`。不加 VIP——以后 VIP 用价格系数
  解决,不需要新的座位类型,避免过早引入一套价格 x 类型的组合复杂度。
- **行列网格,不做非规则形状**:每个座位只有 `row_label` + `column_number`
  两个坐标。COUPLE 座位物理上占 2 个 column 的宽度,但在数据库里只有 1 行记录
  (`column_number` 存左边那一列),订票逻辑里也只算 1 个可预订单元。这个"一个
  座位可能占多格"的语义在 `SeatLayoutGenerator` 里通过构造过程保证不重叠(不是
  生成完再检查),因为这是本模块最容易埋 bug 的地方,专门写了单元测试覆盖。
- **座位布局不可局部修改**:影厅一旦创建、座位随之自动生成,没有"改座位类型"
  或"改布局"的 API。要换布局就删除整个 hall 重建。这是有意为之的 MVP 边界,不
  是遗漏。
- **`GET /api/v1/halls/{id}/seats` 的响应格式是为 Phase 5 预留的**:每个座位
  除了 `rowLabel`/`columnNumber`/`seatType`,还带一个派生字段 `columnSpan`
  (STANDARD=1,COUPLE=2),这样前端渲染座位图时不需要自己硬编码"哪些类型该占
  几格"的业务规则,直接读这个数字就行。响应体顶层还带 `totalRows`/
  `totalColumns`,前端不用再单独请求一次 hall 详情才能确定网格尺寸。现在把格式
  定好,是为了 Phase 5 真正做选座渲染时不用回头改 API 形状。

### Phase 4 — 场次排期(Showtime Scheduling)✅ 完成于 2026-08-02
- 电影 + 影厅 + 时间段绑定,校验同一影厅时间段不冲突(含清场缓冲时间)

关键决定:
- **20 分钟清场缓冲**:同一影厅两个场次之间,前一场 `end_time` + 20 分钟必须
  `<=` 后一场 `start_time`,否则视为冲突拒绝创建。`end_time` 本身不含这个
  缓冲——它就是 `start_time + movie.duration_minutes`,纯粹由电影时长算出来,
  不接受 Admin 手动填写(避免人为输入错误导致数据不一致)。缓冲只在冲突校验
  时临时应用在区间末端(`[start, end+buffer)`),不落库、不污染 `end_time` 字段
  语义。边界值(正好间隔 20 分钟)判定为**不冲突**——校验用的是严格小于
  (`isBefore`),不是 `<=`。
- **冲突校验放应用层,不用数据库 exclusion constraint**:同一 hall 的所有既有
  场次先用 `findByHallId` 取出来,再在 Java 里逐条跑重叠判断
  (`ShowtimeOverlapChecker`,纯静态方法、无依赖,方便脱离 Testcontainers 单独
  测边界条件)。换来的是更灵活、错误信息可读性更好(能明确说出和哪一条已有
  场次冲突),代价是量级变大后需要换成更窄的时间窗口查询——MVP 阶段这个
  tradeoff 是划算的,先不做过早优化。
- **没有更新场次的 API**:排期填错了只能删除重建,不支持局部改
  `start_time`——这类"改一个字段,连带需要重新校验冲突、重算 end_time"的操作
  容易埋数据不一致的 bug,是有意收窄的 MVP 边界,和 Phase 3 座位布局"不可局部
  修改"的边界是同一个设计思路。
- **电影/影厅 - 场次交界处:删除电影/影厅必须走 RESTRICT,不能级联**(2026-08-02
  补充,属于事后发现的交界处漏洞,不是当时就想到的):V7 建表时 `movie_id`/
  `hall_id` 两个外键随手写成了 `ON DELETE CASCADE`,没有认真想过——这意味着
  Admin 删除一部电影会连带把它所有的排期场次一起悄悄删掉,这对订票系统来说
  代价太大(如果场次已经产生订单,级联删除等于销毁交易记录)。已通过 V8
  migration 改成显式的 `ON DELETE RESTRICT`,并且**不是只依赖数据库报错**——
  `MovieService.delete()` 会先查 `ShowtimeRepository.existsByMovieId`,如果电影
  还有排期中的场次,直接返回 409("Cannot delete this movie: it still has
  scheduled showtimes. Delete those showtimes first."),FK 上的 RESTRICT 只是
  兜底(防止绕过 Service 层的直接 SQL/未来其他代码路径)。Hall 目前还没有
  delete API(Phase 3 边界),所以 `hall_id` 这个交界处理论上还触发不到,但
  V8 已经把 FK 一并改成了 RESTRICT,先做到"失败即安全";以后如果给 Hall 加
  delete 功能,`HallService` 必须照抄 `MovieService` 这个"先查 showtime 存在性
  再删"的模式,不能只靠 FK 报错兜底(否则前端拿到的是裸 DB 异常,不是干净的
  409)。

### Phase 5 — 选座/订票(Seat Booking)⚠️ 核心难点 ✅ 完成于 2026-08-02
- 座位状态机:`AVAILABLE → LOCKED(Redis, TTL 5min) → PENDING(创建 booking 记录)→ EXPIRED/CANCELLED`
  (`CONFIRMED` 的列值已预留,但没有任何代码路径会设置它——那是 Phase 6 支付
  成功之后才会触发的事,这个 Phase 的边界明确到"锁座 + 创建待支付 booking"为止)
- 新增 `bookings`/`booking_seats` 两张表(V9),`GET /api/v1/showtimes/{id}/seats`
  (公开)+ `POST/DELETE/GET /api/v1/bookings`(需登录,不限角色)

关键决定:
- **前端轮询,不上 WebSocket**:第 4 节开放问题 #3 已按此定案。MVP 阶段轮询
  `GET /api/v1/showtimes/{id}/seats` 足够,避免过早引入长连接的复杂度(连接管理、
  广播、断线重连)。以后真要做实时推送,轮询接口的响应格式不用改,只是多一条
  推送通道。
- **Redis 锁的 key 设计**:`seat-lock:{showtimeId}:{seatId}`,value 存的是加锁者
  的 userId(不是空标记位),方便日后排查"这个座位到底是被谁锁住的"。加锁用
  `StringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl)`——这是单条
  Redis `SET key value NX EX ttl` 命令,不是"先 GET 判断再 SET"的两步操作,
  从根本上排除了两个并发请求都读到"未锁定"然后都执行 SET 的竞态窗口。
- **TTL 用 Redis 原生 EXPIRE,不做应用层定时轮询**:锁 5 分钟到期后 Redis 自己
  清掉这个 key,即使服务重启/崩溃,锁也不会泄漏成永久占用——不需要一个额外的
  后台线程去检查"这个锁是不是该过期了"。
- **懒惰过期(lazy expiration),不写定时扫描任务**:booking 表有 `expires_at`
  字段,但没有任何 cron/scheduled job 去扫描"哪些 PENDING 记录已经过期"。
  取而代之的是:**任何读取 booking 状态的路径**(`GET /showtimes/{id}/seats`
  聚合座位状态、`GET/DELETE /bookings/{id}` 读取单条 booking)在读到一条
  `expires_at` 已过但状态仍是 `PENDING` 的记录时,当场把它标记为 `EXPIRED`
  并落库,再返回结果——见 `BookingStateMachine.shouldLazilyExpire` +
  `BookingService.loadWithLazyExpiry`/`activeSeatStatuses`。这是 MVP 阶段刻意
  选择的边界,不是漏掉了定时任务:好处是不需要额外的调度基础设施,代价是
  "没人读"的场次里,已过期的 PENDING 记录会一直挂在数据库里直到下次被读取
  ——对订票场景完全可以接受(座位状态本来就是靠这条读取路径对外呈现的)。
- **两层并发防护,不是只靠 Redis**:`POST /bookings` 先查数据库(懒惰过期之后
  再查)排除掉"确定已经被占"的座位——这一层单独存在是因为 Redis 没有配置持久化
  (见 `docker-compose.yml`),万一 Redis 重启丢数据,数据库这层查询仍然是
  兜底真相来源。但这一层**单独不足以防并发**:两个几乎同时到达的事务都可能在
  对方提交前读到"没人占用"。真正解决竞态的是随后对每个座位做的 Redis 原子加锁
  ——任何一个座位加锁失败,这次请求已经拿到的锁全部释放,返回 409 并明确指出
  是哪个座位冲突,不留下任何数据库记录。并发集成测试
  (`BookingConcurrencyIntegrationTest`)专门用两个线程真实并发提交同一个座位,
  断言"恰好一个成功、一个失败,失败的那次数据库里零残留"——本地跑了 5 遍确认
  不 flaky。

### Phase 5 交界处补充:showtime - booking 的删除防护
新增 booking 之后,`showtimes` 第一次有了"被别的表引用"的情况——照抄 Phase 4
那次的教训(见上面 Phase 4 的"电影/影厅"补充条目),`bookings.showtime_id`
从一开始就是 `ON DELETE RESTRICT`(不是先写 CASCADE 再回头改),并且
`ShowtimeService.delete()` 也在应用层显式检查 `BookingRepository.existsByShowtimeId`,
有 booking 记录的场次不允许删除,返回干净的 409。这次没有再犯 V7 那次"随手写
CASCADE"的错误。

### Phase 5 前端补充:选座页 `/showtimes/{id}/seats`(前端已完成,2026-08-02)
替换掉了原来的"Phase 5 开发中"占位页,`GET .../seats` 公开轮询 +
`POST/DELETE /bookings` 走完整交互流程。关键实现决定:

- **轮询间隔固定 4 秒**,且只在座位图真正显示时轮询——一旦提交成功进入
  "请在 5 分钟内支付" 倒计时页,或者倒计时到期进入"已过期"页,轮询立刻停止
  (没必要在这两个屏幕上还请求座位状态);返回选座页时才恢复轮询,同时立刻
  触发一次手动刷新。
- **座位视觉状态的区分逻辑**(`components/booking/seat-map.tsx`):四种状态用
  "边框样式 + 图标"双重编码——可选:玻璃卡片描边;已选:主题金色描边 + 浅色
  填充;LOCKED(别人正在选,暂时锁定):虚线边框 + 灰色半透明 + 一个小锁图标
  (替换掉座位号数字);BOOKED(已成交):实心灰色填充、无边框 + 一个勾选图标。
  情侣座额外叠加一个心形图标常驻显示(不受状态影响),和"占 2 格宽"一起提示
  这是情侣座。这条编码原则、座位按钮不复用 `GlassCard` 的性能考量、以及
  44×44px 点击热区的达标情况,统一记录在 1.5 节,这里不重复。
- **座位选择冲突(409)处理:不解析错误信息里的座位 UUID,而是重新拉取一次
  座位状态并跟当前选择做 diff**——`SeatUnavailableException` 的 message 只在
  Redis 加锁失败时精确到 1 个座位 id,但在数据库预检查阶段命中时可能一次列出
  多个;与其依赖这个字符串格式(以后后端改了措辞前端就得跟着改),不如让前端
  自己重新请求一次权威数据源、对比"我选中的座位里哪些现在不是 AVAILABLE
  了",这样无论后端这次报了 1 个还是全部冲突座位,前端都能完整地摘掉它们并
  提示具体是哪几个。
- **倒计时到期后不主动调用 `DELETE /bookings/{id}`,而是完全依赖后端的懒惰
  过期**:如果客户端在计时到 0 时抢着调用 DELETE,可能会跟后端自己的懒惰过期
  判定竞态——如果后端先把这条 PENDING 标记成了 EXPIRED,DELETE 会因为
  "booking 状态已不是 PENDING" 返回 409。倒计时到 0 只是把本地 UI 切到"已
  过期"提示屏,真正让座位状态变回 AVAILABLE 的是用户点"返回重新选座"时那次
  `GET .../seats` 请求本身(它会顺带懒惰过期这条 booking)。用户主动点"取消
  选座"(倒计时还没到)则正常调用 DELETE。
- **未登录用户点"确认选座"**:不会直接调用 API 拿 401,而是先在前端用
  `useAuth().status` 判断,提示"请先登录后再确认选座"并在约 900ms 后跳转到
  `/login?from=/showtimes/{id}/seats`。采用了简化版方案——登录成功后回到选座
  页,但不记忆之前选的座位,需要用户重新点一次(工作量对比"登录后自动带着
  座位选择重新提交"要小很多,而且重新点选的心智负担很低,MVP 阶段没必要为
  这个做状态持久化)。为此把 `LoginForm` 原来写死的 `router.push("/profile")`
  改成了可传入的 `redirectTo`,`/login` 页面读取 `?from=` 查询参数并做了
  same-origin 校验(只接受 `/` 开头且不是 `//` 开头的路径,防 open redirect)。
- **接口契约核对结果:没有发现不一致**。用一个测试账号跑通了完整链路
  (`GET .../seats` → `POST /bookings` → 轮询看到 `LOCKED` → `DELETE
  /bookings/{id}` → 轮询看到座位回到 `AVAILABLE`,以及两个账号抢同一个座位
  触发 409),`ShowtimeSeatsResponse`/`BookingResponse` 的字段名、类型、
  `SeatStatus`/`BookingStatus` 枚举取值都和前端 TS 类型完全对得上,没有需要
  额外转换或兜底的地方。

### Phase 6 — 支付模块(Payment)✅ 完成于 2026-08-03
- Stripe **Checkout**(不是 Payment Intents + 自建表单):新增 `payments` 表
  (V10)、`POST /api/v1/bookings/{id}/checkout`(需登录,仅 booking 本人)、
  `POST /api/v1/webhooks/stripe`(公开,校验签名)。`bookings.status` 的
  `CONFIRMED` 终于第一次被真正设置——由 webhook 处理成功后触发,不是靠
  前端乐观更新。

关键决定:
- **Checkout 而不是 Payment Intents**:这是一个作品集项目,不需要自定义支付
  表单 UI 带来的加分(那更多是为了产品体验/品牌一致性),Stripe 托管页面
  免费省掉整个"自己实现符合 PCI 要求的支付表单"的工作量,把有限的时间留给
  幂等性这个真正体现工程能力的难点。
- **Stripe 测试模式 key 不能自己凭空构造**:和测试卡号(`4242 4242 4242
  4242`)不同,test key 是跟具体 Stripe 账号绑定生成的,没有官方公开的
  可复用示例值。因此测试策略上选择了 mock 掉 `StripeCheckoutGateway`(见
  下面的接口拆分),自动化测试全程不需要真实 Stripe 账号,真实 key 只走
  `.env` 配置,留给本地手动用 Stripe CLI 联调。
- **`StripeCheckoutGateway` 只包了一个方法**(`createSession`)——真正需要
  联网、需要真实 Stripe 账号的只有"创建 Checkout Session"这一步;Webhook
  签名验证(`Webhook.constructEvent`)是纯本地 HMAC 计算,不联网、不需要
  真实账号,所以**没有**包进这个接口,而是直接在 `PaymentService` 里调用
  真正的 Stripe SDK。这样测试时只 mock 网络调用那一小块
  (`PaymentFlowIntegrationTest` 用 `@MockitoBean` 替换
  `StripeCheckoutGateway`),签名验证和幂等性用的是真实 SDK 代码路径 +
  自己按 Stripe 公开文档的算法(`t=<timestamp>,v1=hex(HMAC-SHA256(secret,
  "<timestamp>.<payload>"))`)签出的测试 payload,而不是一个绕过真实校验
  逻辑的假实现——覆盖的是这个模块真正的难点,不是在自我复述"if 签名对就
  放行"这行代码本身。
- **幂等键选的是 `payments.stripe_session_id`(唯一约束),不是单独维护一张
  processed-event-id 表**:`checkout.session.completed` 这个事件类型在一个
  session 的生命周期里只有意义地触发一次(重复投递 = 同一个 session id 的
  同一个事件),所以"这个 session id 是否已经处理过"这一个判断就足够构成
  幂等性,不需要额外一层"事件 id 是否见过"的记录表。真正防止竞态的是
  `PaymentRepository.findByStripeSessionIdForUpdate`——`SELECT ... FOR
  UPDATE` 的行锁,不是"先查再判断"的两步操作:两个几乎同时到达的 webhook
  投递,第二个会等第一个事务提交后才读到"已经是 SUCCEEDED",直接 no-op,
  不会重复创建/更新任何记录。`PaymentFlowIntegrationTest` 里对同一个签名
  正确的 payload 连续 POST 两次,断言 `payments` 表始终只有一行、booking
  只被确认一次。
- **Stripe Checkout Session 的 `expires_at` 硬性下限是创建后 30
  分钟**(Stripe API 的强制约束,查证于官方文档,不是猜测),远长于 Phase 5
  的 5 分钟选座持有窗口。第一版实现选择了"把 booking 的持有窗口从 5 分钟
  延长到 35 分钟去迁就 Stripe"——这个方案被推翻了,原因和最终方案见下面
  单独一条,这是本 Phase 最重要的权衡,值得完整记录。

- **权衡:座位持有窗口维持 5 分钟不变,反过来主动让 Stripe session 提前
  过期,而不是放宽内部持有窗口去迁就 Stripe**。
  - **被推翻的方案是什么、为什么当时会倾向于选它**:把 booking 的
    `expires_at`(以及对应的 Redis 座位锁 TTL)从 5 分钟延长到 35
    分钟——这样无论用户在 Stripe 托管页面填卡号、走 3D Secure 花多久,只要
    在 Stripe 自己 30 分钟的 session 窗口内完成,都还落在我们自己的持有
    窗口内,不会出现"钱刚付完但座位已经不是 PENDING"的落差。这个方案能让
    Stripe 顺利接入,但没有认真评估代价就采纳了。
  - **代价是什么**:Phase 5 把持有窗口定在 5 分钟,是"座位快速流转"这个
    设计意图的直接体现——选了座不付款,尽快把座位还给别人。只要用户点了
    "去支付"却没有真正走完支付流程(哪怕只是点开付款页看了眼价格就犹豫了、
    或者临时决定不买),座位就会被锁 35 分钟,是原设计的 7 倍。真实使用中
    "点了去支付但没付款"大概率比"点了去支付并在 5~35 分钟之间完成付款"更
    常见,所以这个方案实际上是用"绝大多数放弃支付的人都要多等 30 分钟"去
    换"极少数支付较慢的人不必面对一个边界情况"——这个 trade-off 在两个
    方向上都选错了权重,是为了让 Stripe 能用而顺势接受的妥协,不是经过权衡
    后的决定。
  - **最终方案**:座位锁/booking 的 5 分钟过期时间完全不变——不因为 Stripe
    的限制而改动 Phase 5 的核心设计意图。Checkout Session 仍然按 Stripe
    的硬性要求创建成 30 分钟(这个绕不过去)。落差由 booking 一旦被释放
    (无论是懒惰过期,还是用户主动取消)就反过来**主动调用 Stripe API 把
    对应的 Checkout Session 标记为 expired**(`Session.retrieve(id,
    opts).expire(opts)`,见 `StripeCheckoutGateway.expireSession` /
    `StripeCheckoutGatewayImpl`)来解决,而不是被动等 Stripe 自己 30
    分钟后过期。`BookingService`(lazy expiry 的两处调用点 +
    `cancel()`)在释放 booking 时发布 `BookingReleasedEvent`(纯 Spring
    应用事件,`booking` 包完全不知道 `payment` 包、Stripe 的存在),
    `PaymentService.onBookingReleased`(`@TransactionalEventListener
    (phase = AFTER_COMMIT)`,只在释放 booking 的事务真正提交后才触发,
    避免基于一个可能回滚的事务去调用 Stripe 这种不可逆操作)监听这个事件,
    查出该 booking 名下所有还是 `PENDING` 的 `Payment` 行,逐个调用
    Stripe 的 expire。这样"座位快速流转"这个 Phase 5 的核心设计意图完全
    保留,只是多了一步"尽快通知 Stripe 这边也别再等了"。
  - **Stripe 拒绝过期请求(通常因为 session 已经 complete)不是错误,是
    正常的竞态分支**:`onBookingReleased` 调用 `expireSession` 失败时只
    记录一条 warning 日志,`Payment` 行原样保留、不抛异常——这条竞态(我们
    发起"过期"请求的同时,用户恰好在 Stripe 那边完成了支付)由下面
    `ORPHANED_SUCCESS` 状态的分支去处理,不是这里要解决的问题。
  - **`ORPHANED_SUCCESS`:钱到账但 booking 已经不能安全确认时的第三种终态**
    (V11 migration 在原来的 `PENDING`/`SUCCEEDED`/`FAILED` 上新增)。
    `handleCheckoutSessionCompleted` 收到 webhook 时先调用
    `BookingService.confirmIfPending`——只有 booking 当前确实还是
    `PENDING` 才会被转 `CONFIRMED` 并把 `Payment` 标记 `SUCCEEDED`;如果
    booking 已经是 `EXPIRED`(懒惰过期 + 上面的主动 expire 双重收紧之后,
    这个窗口比第一版方案下窄得多,但 Stripe 支付到 webhook 到达之间仍有
    真实的网络延迟,不可能完全消除)、`CANCELLED`,或者已经被另一次
    checkout 尝试 confirm 过,这次 webhook 会把 `Payment` 标记
    `ORPHANED_SUCCESS`(而不是 `SUCCEEDED`)并打一条 warning 日志——钱确实
    收到了,记录下来留痕,但**不会**把 booking 状态改回 `CONFIRMED`(座位
    可能已经易主),也**不自动退款**(那是运营/客服层面的对账流程,不在这
    个 Phase 范围内,但必须是"可发现、有记录"的,不能悄无声息地把这笔钱的
    去向弄丢)。`PaymentFlowIntegrationTest
    .lateSuccessfulPaymentAfterBookingExpiredDoesNotStealTheSeatBackFromAnotherCustomer`
    完整覆盖这个场景:第一个客户的 booking 过期释放座位、第二个客户订走同
    一个座位、第一个客户的支付这时才姗姗来迟地成功,断言第一个 booking
    仍是 `EXPIRED`、第二个客户的 booking 完全不受影响、`Payment` 变成
    `ORPHANED_SUCCESS`,不会把座位从第二个客户手里抢回来。
  - **`checkout.session.expired` 只标记 `Payment.status = FAILED`,不碰
    booking**:booking 状态交给 Phase 5 已有的懒惰过期机制处理(见 Phase 5
    的"懒惰过期"决定),这里重复写一遍反而会有两套判断逻辑互相打架的风险。
- **用户在 Stripe 页面点返回/关标签页放弃支付**:Stripe 不会给我们发任何
  通知,booking 保持 `PENDING` 直到懒惰过期机制在下一次读取时自然清理(这次
  释放同时会触发上面的主动 expire-session)——这是有意为之,不是遗漏
  (题目本身也是这么问的,这里正式记录下来)。
- **前端"取消支付返回选座页"需要一个额外机制才能真正可用**:cancel_url
  设成 `/showtimes/{showtimeId}/seats?bookingId={id}`,但光加这个 query
  参数不够——`SeatPicker` 原本的 `booking` state 只在"当前这次组件生命周期
  里刚创建成功的订单"时才有值,用户从 Stripe 页面返回时是全新的页面加载,
  这个 state 是空的,如果不处理,用户会看到自己选的座位显示成"被锁定"
  (灰色虚线+锁图标,和别人锁定的座位视觉上无法区分)却点不动。加了一个
  resume 机制:`SeatPicker` 收到 `initialBookingId` 时用 `callAuthorized`
  拉一次 `GET /bookings/{id}`,如果还是 `PENDING` 就直接展示支付确认页
  (可以重新点"去支付"),不是 `PENDING`(现在持有窗口维持 5 分钟不变,
  支付花了一会儿的话大概率已经是这种情况)就当作普通座位图处理;处理完用
  `router.replace` 把 query 参数从 URL 里去掉,避免刷新页面重复触发。
- **支付成功页 `/bookings/{id}/confirmed` 用轮询确认状态,不直接信任
  redirect 本身**:Stripe 的 `success_url` 跳转和 webhook 到达是两个独立的
  异步事件,谁先到不保证——落地页可能在 booking 真正变成 `CONFIRMED` 之前
  就先渲染出来了。沿用 Phase 5"轮询、不上 WebSocket"的思路,每 1.5 秒轮询
  一次 `GET /bookings/{id}`,最多 10 次(~15 秒),期间显示"正在确认支付
  结果";超时后不会一直转圈卡住,而是给一个"支付结果确认中,请稍后刷新"的
  兜底提示,不假装成功也不假装失败。
- **金额单位**:Stripe API 要求最小货币单位(分/仙,不是"元"),`payments`
  表和 `bookings.total_price` 一样存的是十进制的"元"(`NUMERIC(10,2)`,
  MYR);只有 `StripeCheckoutGatewayImpl` 调用 Stripe SDK 那一层做
  `amount * 100` 的换算,不让这个 Stripe API 特有的细节渗透到领域模型/
  数据库里。

### Phase 7 — 订单/电子票(Order & E-ticket)
- 订单记录、QR code 生成、入场核销 API(扫码校验 + 防止重复入场)

### Phase 8 — 管理后台/报表(Admin Dashboard & Reporting)
- 销售报表、上座率分析(SQL聚合查询,能展示你的SQL能力)

### Backlog(MVP不做,时间充裕再加)
- 促销/会员积分
- 评价评分
- 邮件/短信通知

---

## 4. 待你确认的开放问题(Open Decisions)

1. ~~前端框架:React+Next.js 是我的假设,你要用别的吗?~~ **已解决**:确认用
   Next.js 16(App Router + Turbopack + React 19.2)。见第 3 节 Phase 1。
2. ~~JWT token 存储方式:httpOnly cookie 还是前端 localStorage?~~ **已解决**:
   access token 内存 / refresh token httpOnly cookie。见第 3 节 Phase 1。
3. ~~座位实时更新:MVP阶段用轮询还是直接上WebSocket?~~ **已解决**:轮询。
   见第 3 节 Phase 5。
4. 部署目标:本地Docker就够,还是要部署到云端给面试官一个可访问的demo链接?(如果要,现在就该定Railway/Render/AWS,影响后面的配置)—— **待定**。
5. ~~影院规模:MVP做几个分店、几个影厅比较合适?~~ **已解决**:1 个分店、3 个
   影厅(6~10 排、10~14 列不等)。见第 3 节 Phase 3。

---

## 5. 已知的环境注意事项

环境踩坑记录见 [`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md)。

---

## 6. 开发规范

- **分支命名**:`feature/user-management-login`、`fix/xxx`
- **Commit规范**:Conventional Commits(`feat:`, `fix:`, `test:`, `docs:`, `refactor:`)
- **每个Phase完成后**:打一个 tag(如 `v0.1-user-management`),方便简历里写"迭代式交付"
