# CLAUDE.md — CineVerse 电影院订票系统

> 本文件是本项目的唯一真相来源(single source of truth)。每次开新的 Claude Code session,先读这份文件。
> 更新时间:2026-08(随项目迭代持续更新)
> 当前进度:Phase 0~5 已完成(含 Phase 5 前端选座页),Phase 6(支付模块)未开始 —— 详见第 3 节。

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
详见第 4 节。CI 曾因为一个真实的 flaky test bug 红过一次(access token 缺 `jti`,
同一秒内签发的两个 token 会完全相同)——已修复并补了回归测试,细节见
`JwtService`/`JwtServiceTest`。

关键决定:
- **Token 存储方式**:access token 15min,纯内存(前端 React Context,不落地
  localStorage/sessionStorage,防 XSS);refresh token 7 天,**httpOnly cookie**
  (`Path=/`,`SameSite=Strict`,`Secure` 视 profile 而定)。第 5 节的开放问题 #2
  已按此定案。
- **Token rotation**:每次 `/refresh` 旧 refresh token 立即标记 `revoked`,下发新
  的一对 token,不是简单延期。
- **CORS**:后端显式配置 `allowedOrigins`(默认 `http://localhost:3000`)+
  `allowCredentials(true)`,因为 refresh cookie 要靠 `credentials:'include'` 跨
  端口(3000 → 8081)携带。SameSite=Strict 在这里仍然生效,因为"site"按
  scheme+eTLD+1 算,不看端口——`localhost:3000` 和 `localhost:8081` 是跨源但
  同站。
- **前端框架**:确认用 Next.js 16(App Router + Turbopack),不是 Vue/纯HTML。
  第 5 节开放问题 #1 已按此定案。

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
- **前端轮询,不上 WebSocket**:第 5 节开放问题 #3 已按此定案。MVP 阶段轮询
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
- **座位视觉状态的区分逻辑**(`components/booking/seat-map.tsx`):四种状态
  用"边框样式 + 图标"双重编码,不是只靠颜色区分(色弱也能分辨)——
  可选:玻璃卡片描边;已选:主题金色描边 + 浅色填充;LOCKED(别人正在选,
  暂时锁定):虚线边框 + 灰色半透明 + 一个小锁图标(替换掉座位号数字);
  BOOKED(已成交):实心灰色填充、无边框 + 一个勾选图标。特意没有直接复用
  `GlassCard`(它给每张卡片都挂了 pointermove 监听 + framer-motion 光标跟随
  高光,一个影厅动辄上百个座位按钮,每个都挂一份等于上百个 pointermove
  监听器,会拖慢交互),而是用了同一套 `--glass-*` CSS token 做视觉语言复用,
  只在按钮上留了一个轻量的 `whileTap` 缩放反馈。情侣座额外叠加一个心形图标
  常驻显示(不受状态影响),和"占 2 格宽"一起提示这是情侣座。
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

### 4.5 完成定义(Definition of Done)✅ 全部完成
- [x] 所有 endpoint 有 Swagger 文档
- [x] 密码错误、邮箱重复注册等异常情况有明确错误码
- [x] 单元测试覆盖 Service 层核心逻辑(密码校验、token生成/校验)
- [x] 集成测试(Testcontainers)覆盖 register → login → 访问受保护接口 全流程
- [x] README 更新:如何本地启动、如何跑测试

---

## 5. 待你确认的开放问题(Open Decisions)

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

## 6. 已知的环境注意事项

- **8080 端口冲突**:本机(开发机)8080 被 Oracle 的 `TNSLSNR.EXE`(TNS Listener,
  和本项目无关的系统服务)占用。后端本地开发固定改用 **8081**
  (`SERVER_PORT=8081 mvn spring-boot:run`),前端 `frontend/.env.local` 里的
  `NEXT_PUBLIC_API_BASE_URL` 相应指向 `http://localhost:8081`。这个 `.env.local`
  不进 git,换一台没有这个冲突的机器直接用 `.env.example` 里的 8080 默认值即可。
- **本机 shell 里裸跑 `mvn spring-boot:run` 可能不会用 JDK 25**:本机装了不止
  一个 JDK,新开的 shell 里 `mvn` 解析到的 `java` 不一定是 Eclipse Temurin 25——
  症状是启动时报 `UnsupportedClassVersionError`(class file version 69,当前
  JRE 只认到 61,也就是 Java 17)。跑之前先 `export JAVA_HOME="C:\Program
  Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"` 再 `export
  PATH="$JAVA_HOME/bin:$PATH"`。另外,如果本地已经有一个跑了很久的后端进程,
  它是用启动那一刻的代码跑的——中途拉了新 migration(比如 Phase 5 的
  `V9__bookings.sql`)不会自动生效,`flyway_schema_history` 表停在旧版本,
  `GET .../seats` 这类新接口会直接 500;重启一次后端进程让 Flyway 重新跑一遍
  就好。

---

## 7. 开发规范

- **分支命名**:`feature/user-management-login`、`fix/xxx`
- **Commit规范**:Conventional Commits(`feat:`, `fix:`, `test:`, `docs:`, `refactor:`)
- **每个Phase完成后**:打一个 tag(如 `v0.1-user-management`),方便简历里写"迭代式交付"
