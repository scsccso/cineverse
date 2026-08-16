# CLAUDE.md — CineVerse 电影院订票系统

> 本文件是本项目的唯一真相来源(single source of truth)。每次开新的 Claude Code session,先读这份文件。
> 更新时间:2026-08(随项目迭代持续更新)
> 当前进度:Phase 0~8 全部完成(含 Phase 8 管理后台/报表——销售报表 + 上座率分析,
> CSV/PDF 导出),MVP 路线图到此收尾 —— 详见第 3 节。
> 详细的API调试步骤见 docs/DEVELOPMENT.md,数据库 schema 详情见 docs/DATABASE.md,
> 面向招聘官的项目介绍见 README.md,本文件是面向Claude Code的项目记忆。

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
| 语言 | Java 21 (LTS) | 2026-08-11 从 Java 25 降级,理由见第 3 节"技术栈降级"条目 |
| 框架 | Spring Boot 3.5.15 | 基于 Spring Framework 6.2;3.x 线的最后一个次版本,已于 2026-06-30 OSS EOL——明知已 EOL 仍选它,原因见第 3 节"技术栈降级"条目 |
| 安全 | Spring Security 6.x | 配合 Spring Framework 6.2 |
| 数据库 | PostgreSQL 16+ | |
| 迁移工具 | Flyway | Schema 版本化,面试时能讲清楚数据库变更管理 |
| 缓存/分布式锁 | Redis 7.x | 座位锁 + refresh token 黑名单 |
| ORM | Spring Data JPA + Hibernate 6.6 | |
| DTO映射 | MapStruct | 禁止 Entity 直接暴露给 Controller |
| API文档 | springdoc-openapi 2.x | 自动生成 Swagger UI;2.x 线对应 Boot 3.x/Framework 6,3.x 线才是 Boot 4/Framework 7 |
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

### 1.5.1 Admin 后台的设计系统例外(Phase 8,2026-08-06)

`/admin/**` 下的页面(目前是 `/admin/dashboard`)**不使用 Liquid Glass 暗色
玻璃拟态**——这是继座位图的性能例外之后,第二个有意偏离"全站默认基于
`GlassCard`"这条规则的地方,原因和座位图不同(那是密度驱动的性能例外),
这里是内容形态驱动的可读性例外:

- **为什么例外**:管理后台是数据密集型界面(图表 + 表格 + 筛选器),核心
  诉求是信息密度和扫描效率,不是沉浸感。玻璃拟态的指针跟随高光在浏览
  电影海报这种视觉主导的页面上是加分项,但在一屏塞满数字和柱状图的报表
  页面上反而分散注意力;同时这类页面往往同时渲染多张卡片(筛选器卡片 +
  两个报表卡片,以后可能更多),`GlassCard` 每张卡自带的 `pointermove`
  监听器叠加起来,和座位图当初放弃 `GlassCard` 的性能顾虑是同一类问题。
- **怎么做到的:一个作用域内的 CSS 自定义属性覆盖(`.admin-light` 类,挂在
  `app/admin/layout.tsx` 最外层 `<div>` 上),不是新写一套组件库**——
  `Card`/`Badge`/`Button`/`Input` 等已有组件在这个子树内自动读到浅色 token,
  不用各写一份 admin 专属变体。完整的 CSS 继承机制推导见
  `docs/DECISIONS.md`「1.5.1 Admin 后台的设计系统例外」。
- **卡片视觉规范**:细边框 + 低强度阴影(复用已有 `Card` 组件自带的
  `ring-1 ring-foreground/10`,外加 `shadow-sm` 类名),不是 `GlassCard`
  的模糊背景 + 跟随光标高光。
- **无障碍标准不因为换主题而降级**:44×44 最小点击热区、状态区分不能只靠
  颜色、`prefers-reduced-motion` 支持——这几条在 1.5 开头已经确立为全站
  默认标准,admin 页面同样遵守,具体应用见 Phase 8 前端补充里图表/筛选器/
  导出按钮各自的说明,这里不重复。

**补充(2026-08-14,同 1.5.2 那条一样是补前提、不重写本节)**:开头"`/admin/**`
下的页面(目前是 `/admin/dashboard`)"这个前提已经过时——现在是
`/admin/dashboard`、`/admin/movies`(含 `/new`、`/[id]/edit`)、`/admin/users`。
本节的结论(admin 不用 Liquid Glass、用 `Card` 的浅色平面视觉)对这些后加的
页面同样适用,而且确实被援引过:`/admin/movies/new` 的 TMDB 搜索结果网格
(见下面 2026-08-14 那条)就是按这条边界做的——海报网格用 `Card` 的视觉
token,没有引入 `GlassCard` 或指针跟随高光。

### 1.5.2 Admin 拥有完全独立的导航 shell,不复用顾客端 Navbar(2026-08-06)

顶部导航栏 `AdminHeader`(`components/admin/admin-header.tsx`)是和顾客端
`Navbar` 完全独立的组件,不是同一个组件里加 if 分支——顾客端所有路由挪进
`app/(customer)/` 路由组,和 `app/admin/**` 是**兄弟关系,不是父子嵌套**,
Next.js 按文件系统渲染组件树,两者互不出现在对方的渲染结果里(访问
`/admin/dashboard` 时顾客端 `Navbar` 根本不在这次渲染路径上,不是"渲染了
再被样式盖住")。

- **`AdminHeader` 的内容**:logo/wordmark(跳回 `/admin/dashboard`)、
  Dashboard/Movies/Users 三个顶部导航链接(`usePathname` +
  `pathname.startsWith(href)` 判定当前项高亮)、"返回前台"链接(跳回 `/`)、
  当前用户名(复用 `AuthProvider` 会话状态)、退出登录(复用已有的
  `LogoutButton` 组件)。
- **admin 唯一的入口仍然放在顾客端 Navbar 里**:一条不起眼的"管理后台"文字
  链接(只在 `user?.role === "ADMIN"` 时渲染)——顾客端 Navbar 负责"进入
  admin 语境的入口",`AdminHeader` 负责"admin 语境内部的导航",分工明确。
- **没有做成侧边栏**:三个页面(Dashboard/Movies/Users)的量级还不需要
  多级导航,`AdminHeader` 这一条顶部导航够用。

完整论述(为什么不用 if 分支而是拆成独立组件、路由组重构细节、无障碍
复核)见 `docs/DECISIONS.md`「1.5.2 Admin 独立导航 shell」。

### 1.5.3 登录/注册表单卡片保留 Card,不用 GlassCard(2026-08-08)

`LoginForm`/`RegisterForm` 外层容器用普通 `Card`,不用 `GlassCard`——这是
设计系统第三个有意例外(继座位图的性能例外、admin 后台的可读性例外之后),
**专注度驱动**:`GlassCard` 跟随指针移动的高光(见 1.5 节开头)会和"专心
看清自己在填什么"这个任务抢注意力,浏览型 UI 里同样的高光是加分项,表单
容器里反而是减分项。无障碍标准不受影响(`Input`/`Button` 默认高度已是
`h-11`,表单校验动效均支持 `prefers-reduced-motion`)。完整论述见
`docs/DECISIONS.md`「1.5.3 登录/注册表单卡片保留 Card」。

---

## 2. 架构原则

- 分层:Controller → Service → Repository,禁止 Controller 直接操作 Repository
- Entity 与 DTO 严格分离,用 MapStruct 转换
- 全局异常处理:`@RestControllerAdvice` 统一错误响应格式(错误码 + message,不要裸露 stacktrace)
- 权限模型:`ROLE_CUSTOMER` / `ROLE_ADMIN`,后续如有影院经理角色再扩展
- **公开路由 vs 登录路由要在路由设计阶段就分开**:浏览电影、场次列表属于公开只读 API,不应该依赖登录态
- 每个模块交付时必须有:API文档(Swagger)+ 至少核心逻辑的单元测试 + README更新
- **新增/修改 Flyway migration,交付前必须同步更新 `docs/DATABASE.md`**:新表的
  字段/主键/外键(尤其是 `ON DELETE` 策略)、关系图、引入的 Phase,都要补充
  进去,和更新 CLAUDE.md/README.md 一样是每个 Phase 完成的强制项,不是可选项
  ——这份文档存在的意义就是不用每次都去翻 migration 原文件拼凑 schema 全貌。

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
- 项目起步时用的是 Spring Boot 4.1/Java 25,2026-08-11 降级到当前的
  Spring Boot 3.5.15/Java 21(见本节"技术栈降级"条目)。起步阶段 Boot 4
  专属的模块化踩坑记录已经不适用于当前代码,完整原文见
  `docs/DECISIONS.md`「Phase 0」。

### Phase 1 — 用户管理(User Management)✅ 完成于 2026-08-01
(CI 曾因一次真实的 flaky test bug 红过——细节见 `docs/DECISIONS.md`
「Phase 1」。)

关键决定:
- **Token 存储方式**:access token 15min,纯内存(前端 React Context,不落地
  localStorage/sessionStorage,防 XSS);refresh token 7 天,**httpOnly cookie**
  (`Path=/`,`SameSite=Lax`——最初定的是 `Strict`,Phase 6 接入 Stripe Checkout
  后发现的问题、以及为什么改成 `Lax` 见 Phase 6 补充,`Secure` 视 profile 而定)。
  第 4 节的开放问题 #2 已按此定案。
- **Token rotation**:每次 `/refresh` 旧 refresh token 立即标记 `revoked`,下发新
  的一对 token,不是简单延期。
- **CORS**:后端显式配置 `allowedOrigins`(默认 `http://localhost:3000`)+
  `allowCredentials(true)`,因为 refresh cookie 要靠 `credentials:'include'` 跨
  端口(3000 → 8081)携带。SameSite=Lax 在这里仍然生效,因为"site"按
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
  是遗漏。**更正(2026-08-15)**:"删除整个 hall 重建"这句原文不准确——开发
  「影院/影厅管理 `/admin/cinemas`」admin 页面时核实过,`CinemaController`/
  `HallController` 目前完全没有 delete 端点,"删除重建"这个操作在 API 层面
  根本不存在;真实情况是座位布局一旦创建就不可撤销,要重来只能在数据库层面
  手动处理,不是这个系统内任何 admin 操作能做到的。这次交付时新页面上给
  admin 看的提示文案已经按准确说法写,不是照抄这句"删除重建"。
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
- **接口契约核对结果:没有发现不一致**(测试记录见
  `docs/DECISIONS.md`「Phase 5 前端补充」)。

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
  过期,而不是放宽内部持有窗口去迁就 Stripe**(Stripe Checkout Session 的
  `expires_at` 硬性下限是创建后 30 分钟,远长于 Phase 5 的 5 分钟持有窗口;
  第一版实现曾把持有窗口延长到 35 分钟去迁就 Stripe,评估后否决——完整的
  代价分析见 `docs/DECISIONS.md`「Phase 6」)。booking 一旦被释放(懒惰过期
  或用户主动取消)就反过来**主动调用 Stripe API 把对应的 Checkout Session
  标记为 expired**(`Session.retrieve(id, opts).expire(opts)`,见
  `StripeCheckoutGateway.expireSession`),而不是被动等 Stripe 自己 30
  分钟后过期——`BookingService` 释放 booking 时发布
  `BookingReleasedEvent`(纯 Spring 应用事件,`booking` 包不知道
  `payment`/Stripe 的存在),`PaymentService.onBookingReleased`
  (`@TransactionalEventListener(phase = AFTER_COMMIT)`,只在释放事务真正
  提交后才触发)监听并调用 Stripe expire。Stripe 拒绝过期请求(通常因为
  session 已 complete)不是错误,是正常竞态分支,只记录 warning 日志,
  交给下面 `ORPHANED_SUCCESS` 处理。
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

### Phase 6 事后修复:refresh cookie 从 `SameSite=Strict` 改成 `Lax`(2026-08-04)
根因是 Stripe `success_url`/`cancel_url` 跳转回来是跨站顶级导航,
`SameSite=Strict` 的 cookie 在这次"落地请求"上不会被带上,导致
`proxy.ts` 误判成未登录。完整根因排查见 `docs/DECISIONS.md`「Phase 6 事后
修复」;结论已同步进 Phase 1 的"Token 存储方式"决定。

### Phase 7 — 订单/电子票(Order & E-ticket)✅ 完成于 2026-08-04
- Booking 支付成功(`CONFIRMED`)之后即拥有一张电子票;新增
  `POST /api/v1/tickets/redeem`(仅 ADMIN,入场核销)。数据库只新增了
  `bookings.redeemed_at` 一个字段(V12),没有新建 `tickets` 表——见下面的
  关键决定。

关键决定:
- **票据编码复用 jjwt,不是自己手写 HMAC + base64url 拼接**:票据编码本质上
  就是"一段签名过、防篡改的数据",这正是 JWT 已经标准化解决的问题,项目里
  `JwtService`(access/refresh token)已经在用同一个库、同一种模式
  (constructor 接收 secret 配置、`Jwts.builder()`/`Jwts.parser()`)。新增
  `TicketCodeService` 完整照抄这个模式:`sign(bookingId)` 生成一个以
  booking id 为 subject、带 `"type": "ticket"` claim 的签名 JWT;
  `verify(ticketCode)` 校验签名 + type claim,失败统一抛
  `InvalidTicketCodeException`(400)。没有必要重新发明签名验证/常量时间
  比较这些 jjwt 已经处理好的细节。
- **签名密钥和 JWT access/refresh secret 是三把独立的密钥**
  (`app.ticket.signing-secret`,本地开发有 dev-only 默认值,prod profile
  强制要求环境变量,和 JWT secret 的处理方式完全一致):轮换票据签名密钥
  不该连带让所有人登出,反之亦然,和 access/refresh 两个密钥分开的理由
  一样(见 `JwtProperties` 的注释)。
- **没有新建 `tickets` 表,只在 `bookings` 上加了一个 `redeemed_at TIMESTAMPTZ
  NULL` 字段**:一张"电子票"本质上就是一条已经 `CONFIRMED` 的 booking,
  从"入场核验"这个角度重新看待而已,不是一个有独立生命周期的新实体,没必要
  为此新建一张 1:1 关联的表。`redeemed_at` 用一个可空的时间戳同时表达
  "有没有被核销"和"什么时候核销的"两件事——`NULL` = 未核销,非 `NULL` = 
  核销时刻——不是"布尔值 + 时间戳"两个字段的组合(那种设计允许"已核销=true
  但时间戳是 null"这种不该存在的非法状态)。票据编码本身**不落库**:它是
  booking id + 签名密钥的确定性函数,每次 `GET /bookings/{id}` 需要时
  现算(`BookingMapper` 在 `status == CONFIRMED` 时调用
  `TicketCodeService.sign`),不需要也不应该持久化一份"官方版本"。
- **`BookingMapper`(在 `booking` 包)依赖 `TicketCodeService`(在 `ticket`
  包),但这不是循环依赖**:`TicketCodeService` 本身是一个纯签名/验证工具,
  零依赖 `booking` 包任何东西(只吃一个 `UUID`);真正会反过来依赖
  `booking` 包(`BookingRepository` 等)的是 `TicketService`(核销的业务
  编排逻辑),它是另一个类。所以依赖方向始终是单向的:`booking` 包依赖的是
  `TicketCodeService` 这个叶子工具类,不是 `TicketService`。
- **核销接口设计成"提交一段扫码/手输的编码字符串",不是
  `/bookings/{id}/redeem`**:工作人员扫码枪/手机扫到的是二维码里的原始
  字符串(签名过的 JWT),不是 booking id 本身——先让前端解析出 booking id
  再拼 URL 反而多一道没必要的转换。所以设计成独立的
  `POST /api/v1/tickets/redeem`,请求体直接带原始编码字符串,后端自己验签
  解析出 booking id。**这个 Phase 没有做扫码摄像头 UI**(前端只负责在
  支付成功页渲染 QR 图案给顾客看,没有做"管理员用摄像头扫码"的界面)——
  核销 API 本身已经就绪,以后加扫码 UI 只是多一个调用这个 API 的前端页面,
  不需要改后端。
- **核销校验顺序:先验签、再查 booking 状态是否 CONFIRMED、再查是否已核销
  过**,三项都通过才标记核销并落库,任何一项失败都不产生副作用。签名不对
  是 400(客户端提交的编码本身有问题),booking 不是 CONFIRMED 或者已经
  核销过都是 409(状态冲突,不是编码格式问题)——延续了这个项目一贯的
  "错误语义要精确"的风格(参考 Phase 2 那条 401 vs 403 的教训)。
  `TicketFlowIntegrationTest.redeemingATicketTwiceRejectsTheSecondAttempt`
  是这个 Phase 的核心验收场景:同一张票核销两次,第二次必须被拒绝。
- **没有做"核销时间窗口"限制**(比如"只能在场次开始前后 N 小时内核销"):
  这个 Phase 的范围就是"付过款、没被用过就能核销",不做基于场次时间的额外
  限制——如果以后要加,是在 `TicketService.redeem` 里再叠一层独立校验,
  不需要改票据编码本身的结构。
- **没有记录"是哪个 ADMIN 核销的"**:`redeemed_at` 只记录核销时刻,不记录
  操作者身份——审计追溯"具体是谁check-in的"目前不在范围内,是有意收窄的
  MVP 边界,不是遗漏。
- **前端二维码用 `qrcode.react`(`QRCodeSVG`)客户端渲染,后端只提供编码
  内容**:后端完全不涉及图片生成,`ticketCode` 就是一段普通字符串,`GET
  /bookings/{id}` 拿到之后前端直接传给 `<QRCodeSVG value={...} />` 现场画出
  二维码图案。二维码固定套一层白底(不管当前是不是暗色主题)——扫码枪/相机
  需要足够对比度,Liquid Glass 半透明的深色卡片背景本身做不到这一点。已
  核销的票据二维码保留显示但视觉上调暗(不是隐藏),兼作"支付凭证"用途。

### Phase 8 — 管理后台/报表(Admin Dashboard & Reporting)✅ 完成于 2026-08-06
- 两个核心报表,全部走真正的 SQL 聚合(`GROUP BY` + `date_trunc` + `generate_series`
  时间分桶),不是整表拉到 Java 里用 stream 算:
  - `GET /api/v1/admin/reports/sales`:按 day/week/month 粒度统计营收,支持预设
    时间范围(今日/近7天/近30天,前端计算)+ 自定义起止日期(后端只认 `from`/
    `to` 两个显式日期参数,预设只是前端把它们解析成具体值再调同一个接口,
    API 不需要为"预设"单独设计一套参数),可选按 `movieId`/`hallId` 筛选。
  - `GET /api/v1/admin/reports/occupancy`:按场次统计已订座位数(仅 `CONFIRMED`)
    / 总座位数,响应同时带每场次明细和 from/to 范围内的汇总,可选按
    `movieId`/`hallId` 筛选。
  - `GET /api/v1/admin/reports/{sales,occupancy}/export?format=csv|pdf`:CSV/
    PDF 导出,参数与对应的查询接口一致。
- ADMIN-only,复用项目已有的 401(未登录)/403(角色不对)语义,新增
  `/api/v1/admin/**` 路由匹配(`SecurityConfig`),不重新发明。
- 新增 `V13__report_indexes.sql`(见下面索引一节)。

关键决定:

- **营收口径:只统计 `CONFIRMED` booking 对应的 `SUCCEEDED` payment 金额;
  `ORPHANED_SUCCESS` 状态的支付不计入营收,但作为单独的
  `pendingReconciliationAmount` 字段返回,不静默丢弃**。这两个状态在 Phase 6
  就已经定义好语义(`ORPHANED_SUCCESS` = Stripe 报告支付成功但当时 booking
  已经不是 `PENDING`,钱真的收到了但不能安全地确认给这个 booking——见
  CLAUDE.md Phase 6),报表层只是忠实地把这个已有的区分暴露出来,不是这个
  Phase 新发明的规则。之所以不干脆把 `ORPHANED_SUCCESS` 也算进营收(它确实
  是收到的钱):这笔钱可能对应的是一个已经被别人订走的座位,把它算进"销售
  报表"会让管理员误以为这是一笔正常成交的订单,而它需要人工核对去向
  (退款还是补发)。反过来也不能直接丢弃不展示,那样这笔钱就彻底消失于
  报表体系之外——所以选择"不计入总营收,但单独展示"这个中间态。
- **营收按 `payments.updated_at`(支付真正转为 `SUCCEEDED` 的时刻)分桶,不是
  `payments.created_at`(Checkout Session 创建、也就是用户开始付款的时刻)**。
  `Payment` 实体的 `updated_at` 由 `@UpdateTimestamp` 在每次 `save` 时自动
  刷新,而 `markSucceeded()` 是这一行记录从 `PENDING` 到终态唯一会再次触发
  保存的地方,所以对一条 `SUCCEEDED` 记录而言 `updated_at` 就精确等于"支付
  成功"的那一刻;`created_at` 反而代表的是结账流程*开始*的时刻,用它分桶会
  把"今天下单、但可能拖到明天才在 Stripe 页面完成付款"的一笔钱记到下单那天,
  与"营收=实际入账时间"的直觉不符。这个区别在小额、当场完成支付的场景下
  通常不会跨天,但语义上 `updated_at` 才是对的锚点,所以从一开始就选它,
  没有依赖"反正很少跨天"这种侥幸。
- **报表的时间分桶/范围转换复用与前端相同的固定时区
  `Asia/Kuala_Lumpur`**(`ReportService.CINEMA_ZONE`,和
  `frontend/src/lib/format.ts` 的 `CINEMA_TIME_ZONE` 是同一个值,分别在两处
  硬编码,原因同 `format.ts` 的注释——MVP 只有一家分店,统一到它所在地的
  当地时间)。请求的 `from`/`to` 是这个时区下的日历日期,`ReportDateRange`
  把它转换成半开区间的 `Instant`(`[from 当地 00:00, to+1天 当地 00:00)`)
  再传给 SQL 层——这个转换单独拆成一个不依赖 Spring/DB 的纯静态工厂方法,
  专门为了能在没有 Testcontainers 的前提下写单元测试锁定这个最容易出
  off-by-one 错误的地方(`ReportDateRangeTest`)。
- **聚合查询用手写原生 SQL(`NamedParameterJdbcTemplate`),不是 JPA/Hibernate
  实体查询**——这是本 Phase 唯一引入 `spring-jdbc` 直接查询方式的模块,
  `spring-boot-starter-data-jpa` 本身就传递依赖了 `spring-jdbc`,且
  `NamedParameterJdbcTemplate` 由 Spring Boot 根据已有的 `DataSource`
  自动配置好,不需要新增依赖。选它是因为这个 Phase 的价值主张就是展示真正
  的 SQL 聚合能力(`GROUP BY`、`date_trunc`、`generate_series`、相关子查询),
  用 JPA 把整表实体拉进 Java 再用 stream 求和,既绕开了这个价值点,性能上
  也是明显的倒退。
- **销售报表的时间桶用 `generate_series` 补零,不是只返回有数据的桶**:
  `ReportRepository.salesBuckets` 用一个 CTE 先生成 `[from, to)` 范围内每个
  粒度(日/周/月)的完整桶序列,再 `LEFT JOIN` 实际的营收聚合结果,
  `COALESCE` 缺失值为 0——这样前端拿到的永远是一条连续的时间序列,不需要
  自己判断"哪天没数据要不要补一个 0 点",图表也不会因为稀疏数据出现断裂的
  柱子。`generate_series` 的步长用 `('1 ' || :granularity)::interval` 拼出
  (如 `'1 day'`/`'1 week'`/`'1 month'`),`:granularity` 本身是绑定参数
  (值域被 `ReportGranularity` 枚举收窄到 `day`/`week`/`month` 三个值,虽然
  参数化绑定本身已经排除了 SQL 注入风险,但输入侧仍然只信任枚举,不接受
  任意字符串拼接)。
- **上座率只统计 `CONFIRMED` 状态 booking 占用的座位,`PENDING`(5 分钟持有窗口
  内的临时锁定)不计入**——和营收口径"只算 `CONFIRMED`"是同一条原则的另一个
  应用,避免上座率数字随着用户"选座中但还没付款"的瞬时状态抖动,只反映
  真实卖出的座位。
- **可选的"按电影/影厅细分"实现成过滤器(narrow to 指定 movieId/hallId),不是
  一个额外的分组维度**:两个报表的响应形状都保持"一份扁平的时间桶列表"或
  "一份扁平的场次列表",`movieId`/`hallId` 参数只是收窄这份列表的范围,不会
  展开成"电影 × 时间"或"影厅 × 时间"的交叉表——避免为一个没有被要求做成
  表格聚合视图的需求引入组合爆炸的响应结构。真需要交叉分析时,前端可以对
  同一个接口用不同的 `movieId`/`hallId` 分别请求几次,不需要后端预先算好
  所有组合。
- **CSV/PDF 导出复用同一份 `TabularReport`(title + headers + rows)中间结构,
  两个报表 × 两种格式没有写四份互相独立的导出逻辑**:`ReportExportService`
  是唯一同时认识"报表 DTO 长什么样"和"表格长什么样"的地方,负责把
  `SalesReportResponse`/`OccupancyReportResponse` 转换成 `TabularReport`;
  `CsvWriter`/`PdfTableWriter` 只认 `TabularReport`,不知道销售报表和上座率
  报表的存在。CSV 手写了一个最小的 RFC 4180 实现(没有引入 opencsv 这种量级
  的依赖去做几行转义逻辑),额外带了 UTF-8 BOM 前缀方便 Excel 正确识别编码
  (电影标题可能包含非 ASCII 字符,不加 BOM 会被 Excel 当 Latin-1 读乱码)。
  PDF 用 **OpenPDF**(LGPL/MPL 协议的 iText 4 分支)而不是 iText 本身——iText
  当前主线版本是 AGPL/商业双授权,对一个希望能被自由 clone、构建的作品集
  项目来说协议不合适,OpenPDF 是这条代码线里协议干净的延续。
- **索引:只新增了一个 `payments (status, updated_at)` 复合索引
  (`V13__report_indexes.sql`)**,评估后没有再加别的——销售报表的 SQL 同时
  按 `status` 过滤、按 `updated_at` 做范围扫描/分桶,这两个查询模式合在一起
  才需要一个新索引;上座率报表用到的 `showtimes.start_time`/`hall_id`/
  `movie_id` 索引已经在 Phase 4(`V7`)建好,`bookings (showtime_id, status)`
  复合索引也已经在 Phase 5(`V9`)建好,足够覆盖这个 Phase 新增查询的过滤
  需求,不需要重复造一遍。加索引不是"每个 Phase 例行公事都要加",这里是
  确认了缺口之后才加,不是凑数。
- **测试:核心验收是 Testcontainers 集成测试对已知 fixture 断言精确的聚合
  数字,不是只断言 200**(`ReportFlowIntegrationTest`,用随机标题隔离测试
  数据,和 `TicketFlowIntegrationTest` 同一思路;完整测试策略见
  `docs/DECISIONS.md`「Phase 8」)。

### Phase 8 前端补充:管理后台 `/admin/dashboard`(前端已完成,2026-08-06)

- **`proxy.ts` 对 `/admin/:path*` 只做和 `/profile`、`/bookings` 一样的粗粒度
  网关(refresh_token cookie 存不存在),不能也没有尝试在这一层判断
  ADMIN 角色**:refresh token 的 JWT payload 里没有 `role` claim(只有 access
  token 才带,见 `JwtService.generateAccessToken` vs `generateRefreshToken`),
  而 access token 只存在浏览器内存里,Proxy 运行在服务端、请求真正到达
  客户端 React 代码之前,根本拿不到它。**真正的 ADMIN 角色校验在
  `app/admin/layout.tsx`,是这个 Phase 唯一新增的、真正决定"渲不渲染"的
  防线**:mount 时调用 `fetchCurrentUser()`,`status==="unauthenticated"`
  跳 `/login?from=/admin/dashboard`,拿到用户后 `role !== "ADMIN"` 跳回
  `/`——在这两种情况解决之前,`children` 完全不渲染(不是渲染完再补一个
  跳转,不会有一帧的"泄漏")。这个"Proxy 只做粗筛、真正判断在客户端
  definitive check"的分层,和 `/profile` 页面已有的模式完全一致,不是
  这个 Phase 独创的新模式。手动验证过:未登录直接访问
  `/admin/dashboard` 会被 Proxy 弹到登录页;登录了但角色是 `CUSTOMER`
  直接改地址栏输入 `/admin/dashboard` 会被 `AdminLayout` 弹回首页
  (不是"入口对非 ADMIN 隐藏"这种伪保护,输入 URL 直达同样拦得住)。
- **admin 侧不复用 GlassCard/暗色玻璃拟态,这是继座位图之后第二个有意的
  设计系统例外**——完整决定和理由见 1.5 节新增的对应小节,这里不重复。
- **图表库用 recharts**,两个图表都是单一数据系列(销售报表按时间的营收、
  上座率报表按场次的占比),按 dataviz 方法论的表单选型规则,单一系列
  用一个固定色相就够,不需要一整套多色分类色板,也不需要图例框(卡片
  标题已经说明画的是什么)——`bookingCount`(订单数)这类辅助数据放进
  tooltip 里一起展示,不做成第二根 Y 轴(项目里从不做双轴图表)。柱状图
  的填充色是新定义的 `--chart-amber`(`#B8860B`),**不是**直接复用按钮/
  强调色用的 `--primary`(`#F4C430`)——用 dataviz 方法论自带的
  `validate_palette.js` 校验过:`#F4C430` 在 admin 的白色卡片背景上作为
  图表描边/填充色的对比度只有 ~1.6:1(远低于图形元素要求的 3:1),因为它
  的设计亮度本来就是为"金色文字/图标在深色背景上"这个场景校准的,不是为
  "作为色块画在白底上"校准的;`#B8860B` 是同色相下压暗到亮度能过检的版本,
  两个变量在 `.admin-light` 里分别定义、分别使用,不是同一个值的两个名字。
  完整校验命令和结论记在 `globals.css` 里 `--chart-amber` 定义处的注释。
- **图表的 `Bar` 显式关掉了 recharts 默认的入场动画(`isAnimationActive=
  {false}`)**——`ResponsiveContainer` 的 `ResizeObserver` 会在容器尺寸
  变化时把入场动画从头重放,导致截图/首屏可能撞上"柱子从 0 开始长"的空白
  中间态;关掉动画顺带满足 `prefers-reduced-motion` 标准。发现过程见
  `docs/DECISIONS.md`「Phase 8 前端补充」。
- **筛选器切换用骨架屏过渡,不是保留上一帧 + 半透明**——是否骨架屏由
  `sales.from/to/granularity` 是否等于当前筛选器的值直接推导
  (`salesLoading`/`occupancyLoading`),不用手动维护的 `loading` boolean
  (React 19 的 `react-hooks/set-state-in-effect` 规则不允许在
  `useEffect` 里同步 setState)。这个"loading 从数据是否匹配当前请求
  参数推导"的模式后来被多处复用,见「Admin 用户管理」一节。完整论述见
  `docs/DECISIONS.md`「Phase 8 前端补充」。
- **无障碍标准继续沿用全站默认,不因为脱离 Liquid Glass 暗色主题就放松**:
  预设时间范围按钮除了填充色变化,同时用 `aria-pressed` + 一个勾选图标
  标出当前选中项(不是只变个颜色);两个图表各自配一个 `<details>`
  折叠的数据表格(dataviz 方法论"图表之外始终存在一份可读表格"的要求),
  表格本身没有任何数字是图表之外读不到的;导出按钮的加载态图标同时带
  `motion-reduce:animate-none`;筛选器按钮显式给了 `h-11`(44px)高度,
  自定义日期选择器的 `<input type="date">` 当时也手动加了 `h-11` 覆盖
  ——2026-08-08 的审计后修复把 `Input` 组件自身的默认高度改成了 `h-11`
  (见下面"审计后修复"一节),这两处手动覆盖已经跟着删掉,不是遗漏。
- **没有在筛选器里暴露 `movieId`/`hallId` 下拉框**——后端两个报表接口都
  支持这两个过滤参数(见上面的关键决定),但前端这次交付的筛选器只做了
  任务要求的"预设时间范围按钮 + 自定义起止日期",电影/影厅细分是后端能力
  的一部分,不是这次前端 UI 的范围,以后要加也只是多两个 `<select>`,不需要
  改接口形状。

### Phase 8 交付涉及的四份文档同步
和以往每个 Phase 一样,交付前同步更新了 CLAUDE.md(本节)、README.md("当前
状态"一行)、`docs/DEVELOPMENT.md`(报表/导出接口的 curl 示例)、
`docs/DATABASE.md`(`V13` 迁移 + 索引记录),不是只在 CLAUDE.md 里写"做完了"。

### 审计后修复(2026-08-08,不算新 Phase)
不是新功能,是一次针对既有代码的技术债审计,修掉三类问题:

- **`Input` 组件默认高度从 `h-8` 提到 `h-11`**(`components/ui/input.tsx`)
  ——照抄 `Button` 的修法,组件默认值本身达标,不用每处手动加 `h-11`。
- **`prefers-reduced-motion` 补全**到 `components/motion/` 下四个组件
  (`page-transition.tsx`、`submit-progress-bar.tsx`、
  `animated-form-banner.tsx`、`animated-field-error.tsx`)和
  `seat-map.tsx` 的 `SeatButton`;`EASE_APPLE` 缓动曲线统一提到
  `lib/motion.ts`;三处相似的入场动画合并成共享的
  `components/motion/fade-in.tsx`(`<FadeIn>`)。**这次审计当时漏了一个
  调用点**(`components/ui/skeleton.tsx` 共享 `Skeleton` 组件)——已于
  2026-08-14 补齐,见「skeleton.tsx 补 motion-reduce」一节。
- **新增 `app/(customer)/error.tsx` + `not-found.tsx`(暗色,复用
  `GlassCard`)、`app/admin/error.tsx` + `not-found.tsx`(浅色,复用
  `Card`)**——Next.js 的 `error.tsx`/`not-found.tsx` 语义是"包裹同 segment
  页面内容,但不包裹同 segment 自己的 `layout.tsx`",所以 `AdminHeader`/
  顾客端 `Navbar` 在这两个文件渲染时依然正常显示。`app/admin/
  not-found.tsx` 目前实际触发不到(admin 只有静态路由),是为以后动态
  路由预先埋好的。

完整发现过程和多轮补充修正记录见 `docs/DECISIONS.md`「审计后修复」。

### 种子数据的海报图来源(2026-08-08)
一次性数据操作,不是长期架构决定。图片来自 **OMDb API**(`t=` 精确标题
查询,key 存 `.env` 的 `OMDB_API_KEY`),直接热链 Amazon 图片 URL,不下载
转存。**OMDb 条款禁止商业用途**(CC BY-NC 4.0 + "个人/非商用"硬约束)——
CineVerse 是非商业作品集,符合限制;若项目商业化,这个数据源必须换掉。
完整的匹配置信度判断、migration 细节见 `docs/DECISIONS.md`「种子数据的
海报图来源」。

### 种子数据扩充:删除测试 fixture + 补充 10 部真实电影(2026-08-08)
删除了三部测试 fixture 电影(`Verify Fix`/`Verify Movie`/`E2E Test Movie`,
`V15` migration),种子数据扩充到 11 部真实电影(`V16` migration),覆盖
科幻/剧情/动作/动画/喜剧/恐怖/犯罪/奇幻/传记等类型。genre 映射不是 OMDb
标签的直译(项目固定 15 个 genre 值,OMDb 标签超出范围时需要人工挑最贴切
的替代,如 `Oppenheimer` 映射成 `Drama`+`War`)。完整的 migration 依赖
顺序、置信度判断见 `docs/DECISIONS.md`「种子数据扩充」。

### `poster_url`/`backdrop_url` 从此是两个不同数据源(2026-08-08)
`poster_url` 继续用 OMDb(卡片缩略图分辨率校准,~380px 宽);
`backdrop_url` 改用 **TMDB**(`backdrop_path`,`image.tmdb.org/t/p/w1280/`
——专为横版沉浸式背景图设计的真实剧照,不是海报硬拉伸)。`next.config.ts`
需要给两个数据源各自的域名加 `images.remotePatterns`(`m.media-
amazon.com`、`image.tmdb.org`),改完需要重启 `next dev` 才生效。TMDB 条款
同样禁止商用,且明确要求可见署名(logo + 指定文案)——**已于 2026-08-14
补上,见"TMDB 署名合规"一节**。

**已知缺口,尚未修复**:移动端(DPR 3 高分屏)上 Next.js 图片优化管线生成
的 WebP 没有按视网膜屏幕需求加倍取样(桌面端同样有,但移动端缺口更明显),
根因在 Sharp/Next 的 WebP 转码路径,不是 `sizes` 配置问题(`sizes` 已经
排查排除)。完整匹配规则、TMDB 条款原文、排查过程见 `docs/DECISIONS.md`
「poster_url/backdrop_url 从此是两个不同数据源」。

### Hero 背景图:裁切位置修正(2026-08-08)
`object-position` 从默认居中改成 **`50% 30%`**(`hero-carousel.tsx`)——
Hero 容器(70vh,≈2.29 宽高比)比 TMDB 16:9 剧照更"扁宽",默认居中裁切会
切掉关键构图、角色头部贴着画面顶边。清晰度问题的排查过程(排除了 `sizes`
配置、定位到 WebP 转码管线)见上一节"已知缺口"和 `docs/DECISIONS.md`
「Hero 背景图:裁切位置修正」。

### Hero 修复没有覆盖到电影详情页,提成共享组件补上(2026-08-08)
电影详情页顶部的 backdrop 横幅原本是独立的第二份实现,没有共享 Hero 的
裁切修复。提成了 `components/movies/movie-backdrop.tsx`(`<MovieBackdrop>`)
——只抽了 `Image` 的 `fill`/`sizes`/`object-cover`/`object-position` 组合
+ 渐变蒙层,外层尺寸容器/布局仍由调用方各自处理。顺手把两处误用的
`preload` prop(未知属性,无效果)改成了真正生效的 `priority`。完整验证
过程见 `docs/DECISIONS.md`「Hero 修复没有覆盖到电影详情页」。

### 设计债批次修复(2026-08-08,不算新 Phase)
排好但一直没做的四项技术债,一次性清掉,不涉及新的视觉方向判断:

- **登录/注册表单卡片保留 `Card`、不用 `GlassCard` 的设计决定补充记录进
  1.5.3 节**——这是既有事实的补记,不是这次新改的代码,见该节。
- **首页 Hero 去卡片化**(`hero-carousel.tsx`):去掉包裹文字的 `GlassCard`
  容器,标题从 `text-3xl sm:text-4xl` 提到 `text-4xl sm:text-5xl
  lg:text-6xl`,直接叠在背景图上,靠强化过的渐变 + 每个文字元素的
  `text-shadow` 保证可读性。移动端箭头改成 `hidden sm:flex`(小屏只留
  圆点指示器)——排查过程(Turbopack HMR 对任意值 class 的增量扫描漏判)
  见 `docs/DECISIONS.md`「设计债批次修复」。
- **`/profile` 页面垂直居中**:外层 `<section>` 改成和 `/login`、
  `/register` 一致的居中模式(`flex min-h-[calc(100vh-4rem)] flex-col
  justify-center`)。
- **移动端导航收纳**:`Navbar` 新增 `md:hidden` 汉堡按钮 + 下拉面板,断点
  统一成 `md`(768px),不再有两条不同步的断点线。面板关闭时机用"渲染期间
  比较 prop 变化"模式(`if (pathname !== prevPathname) {...}`),不用
  `useEffect` 里同步 `setState`(会撞上 `react-hooks/set-state-in-effect`
  规则)——**这个模式后来被多处复用**,见「Admin 用户管理」一节。
  `AdminHeader` 的"管理后台"副标题、"返回前台"文字都加了 `hidden
  sm:inline`(只留图标,补 `aria-label`)。
- **admin `StatTile` 警示色统一**:`tone="warning"` 分支改用
  `--chart-amber-border`/`--chart-amber-surface`(从已验证的
  `--chart-amber` 用 `color-mix(in oklch, ...)` 派生),不再用 Tailwind
  内置的 `amber-400`/`amber-50` 凑近似色。

验证方式(Playwright 截图对比、临时隔离后端实例)见 `docs/DECISIONS.md`
「设计债批次修复」。

### 顾客端交互反馈强化(2026-08-09,不算新 Phase)
方案文档见 `docs/design-proposal-customer-interaction.md`。范围只到顾客端
桌面视口,admin 和移动端专项都没碰。**硬性前提:任何新增的交互反馈都不能
让触屏/键盘用户失去等价的可达路径**。

- **`GlassCard` 补上 CSS 那一层 reduced-motion 兜底**:加了
  `motion-reduce:transform-none!`(必须带 `!`,因为 framer-motion 把
  scale 写成内联 style,普通类名优先级压不过)。
- **`SubmitProgressBar` 复用到订票流程风险最高的两个按钮**("确认选座"、
  "去支付")——此前只有按钮文字切换,是全站反馈最弱但风险最高的两次点击。
- **选座页三态切换(`booking`/`expired`/座位网格)补上 `AnimatePresence
  mode="wait"` 过渡**——**只做 opacity,刻意不做 y 位移**:座位网格里的
  `SelectionSummaryBar` 是 `position: fixed`,而带 transform 的祖先元素
  会成为其 fixed 子孙的包含块,一旦 wrapper 动 y,结算栏会在过渡期间改为
  相对 wrapper 定位而不是视口。
- **`GlassCard` 新增 `interactive` prop(`whileTap:{scale:0.98}`),
  opt-in 不是默认**——只有本身是链接/按钮的两处(`MovieCard`、
  `ShowtimeList` 场次胶囊)显式传 `interactive`,静态卡面(信息卡、电子票
  等)不套,避免制造假的可点击提示。

独立 bug 修复(`MovieCard` 双重缩放)、详细验证方式(Playwright 读
`getComputedStyle` 实测值,不只截图)见 `docs/DECISIONS.md`「顾客端交互
反馈强化」。

### 顾客端流程/新手友好度改造 第一批(2026-08-09)
方案文档见 `docs/design-proposal-final-polish.md`(顾客端三份方案文档的最后一份:
editorial 管"视觉分量"、interaction 管"操作反馈",这一份管"流程顺不顺、新手会不会
卡住")。这一批做了该文档的第一、二梯队共 4 项,其中 **F-2 是真正的新增功能模块**
(后端接口 + 前端页面),其余三项是既有页面的调整。LPF/MPAA 分级问题(文档 N-8
第二层)按要求**没有动**,留待单独决定。

#### F-2 订单列表:补上"电子票能找回来"这个此前完全缺失的环节

**为什么这是 bug 级而不是打磨级**:此前支付成功后落在
`/bookings/{id}/confirmed`,而这个 URL 是电子票的**唯一**入口——前端没有订单列表
页,后端也**没有列表接口**(`BookingController` 只有 `POST /`、`GET /{id}`、
`DELETE /{id}`、`POST /{id}/checkout`)。用户关掉标签页,或者第二天想在影院门口
调出二维码,就再也进不去自己的票。更糟的是确认页底部那个按钮写着"查看我的账号"
并指向 `/profile`,而 `/profile` 上只有姓名/邮箱/用户 ID/加入时间,跟刚买的票毫无
关系——系统主动把用户引向了一个死胡同。对订票系统来说票据是最终交付物,交付物
拿不到,前面所有流程优化都没有意义。

**接口设计决定**:

- **`GET /api/v1/bookings`(集合根),不是 `/users/me/bookings`**:`bookings` 这个
  资源根已经存在(POST 建单、GET/{id} 查详情都挂在这里),集合的"我的"语义由
  **认证主体**而不是 URL 路径表达,和项目里其余接口的风格一致,也不需要为此新增
  一个 `/users/me/**` 命名空间。
- **排序按 `created_at DESC`(下单时间倒序),不是场次开始时间**:方案文档里原本
  写的是按 `startTime`,实施时改成了 `created_at`——用户打开这一页的心智是"我最近
  买了什么",最近一次操作应该排在最前;按场次时间排会让一张刚买的、下周才放映的
  票沉到底部。排序写在 SQL 的 `order by` 里,不在 Java 里二次排序。
- **没有 ADMIN 越权分支——这是和 `getById` 有意不同的地方**。`getById` 保留
  ADMIN 可查任意订单(客服场景:"帮我查一下这个用户的订单"),但列表接口**对所有
  角色一律只返回调用者自己的订单**,ADMIN 也一样。理由:一个不过滤的列表会悄悄
  变成"全体用户订单导出",那是经营数据,属于 Phase 8 的
  `/api/v1/admin/reports/**`,不该从一个顾客端接口漏出去。**归属过滤写在查询的
  `where b.user.id = :userId` 里,不是查出来再在 Java 里 filter**——后者只要有人漏
  写一个分支就会越权,前者不存在"忘记过滤"的代码路径。集成测试
  `adminListingSeesOnlyItsOwnBookingsNotEveryCustomers` 专门锁住这条:ADMIN 的列表
  里查不到顾客的订单,但同一个 ADMIN 用 `GET /{id}` 仍然能查到那一单(证明这是
  有意的差异,不是把 ADMIN 权限一刀切掉了)。
- **懒惰过期同样适用,而且是批量做的**:延续 Phase 5"没有定时扫描任务、每条读取
  路径自己负责过期"的设计,列表接口在返回前把所有"已过 `expires_at` 但仍是
  `PENDING`"的订单一次性标记 `EXPIRED`、`saveAllAndFlush`、并逐个发布
  `BookingReleasedEvent`(Phase 6 靠这个事件反向去过期 Stripe session)。做法照抄
  `activeSeatStatuses` 的批量写法,不是每行调一次 `loadWithLazyExpiry`。**副作用是
  正向的**:用户打开订单列表这个动作,顺带把自己名下过期的挂单清理了。因此这个
  方法**不能标 `@Transactional(readOnly = true)`**,和 `getShowtimeSeats` 同理。
- **N+1 是这里唯一真正的性能陷阱,用显式 fetch join 解决**:`Booking.showtime`、
  `Showtime.movie`、`Showtime.hall`、`BookingSeat.seat` **全部是 `FetchType.LAZY`**,
  而 `BookingMapper` 每行都要读电影标题、影厅名,每个座位都要读排号/列号/座位类型
  ——不处理的话一份 20 条的订单历史会打出几十次额外查询。新增了两个带
  `join fetch` 的查询方法(`BookingRepository.findAllByUserIdNewestFirst`、
  `BookingSeatRepository.findByBookingIdInWithSeat`),**整份订单历史固定 2 次查询**。
  注意**没有**去改既有的 `findByBookingIdIn`:它的调用方(`activeSeatStatuses`)
  只读 `getSeat().getId()`,懒代理不查库就能返回外键,给它加 fetch join 反而是
  没必要的开销。
- **空结果短路**:用户没有任何订单时直接返回 `List.of()`,不去执行一次
  `in ()` 的座位查询(空 IN 列表既无意义、在部分驱动上还是非法 SQL)。

**前端**:

- 新增 `app/(customer)/bookings/page.tsx`,每行卡片链接到**已有的**
  `/bookings/{id}/confirmed`——电子票页一行没改,它本来就已经能处理
  CONFIRMED/已核销/未完成支付几种状态,不重复实现二维码。
- **路由鉴权不需要新增任何东西**:`proxy.ts` 的 matcher 里 `/bookings/:path*`
  **本来就覆盖了裸路径 `/bookings`**(`:path*` 是零或多段)。实测确认匿名访问
  `/bookings` 会 307 到 `/login?from=%2Fbookings`,和 `/profile` 行为一致;页面内
  再做一次 `authStatus` 的最终判断,沿用 `/profile` 已有的"Proxy 粗筛 + 客户端
  definitive check"分层。
- **订单状态用图标 + 文字 + 颜色三重编码**,不靠颜色单独区分(1.5 节标准):
  已确认(勾)/待支付(时钟)/已过期(日历叉)/已取消(圆叉);`redeemedAt` 非空时
  显示"已入场"并压过"已确认"——一张已核销的票不能再用,这是扫一眼更需要知道的事。
- **三个入口全部接上**:确认页底部按钮从"查看我的账号"改成"查看我的订单"并指向
  `/bookings`;`/profile` 加了一个"我的订单"按钮;Navbar 加了"我的订单"链接——
  这一条**放在共享的 `AuthSection` 里而不是桌面端那一行**,所以移动端汉堡面板里
  同样有,电子票入口不会只在某个断点下存在。

**测试**(单元 + 集成,都新增了):

- `BookingServiceListTest`(纯 Mockito,不依赖 Testcontainers):锁住三件纯装配
  逻辑——repository 的排序不被二次打乱、每个 booking 只拿到**自己**的座位(故意
  把座位查询结果打乱顺序返回,证明分组按 `bookingSeat.getBooking().getId()` 而不是
  返回顺序)、以及过期挂单在返回前被标记 + 落库 + 发事件。`BookingMapper` 用真的
  不用 mock,断言跑在控制器真正返回的响应形状上(和 `TicketServiceTest` 同一思路)。
- `BookingFlowIntegrationTest` 新增三个用例:匿名 401、
  `listReturnsOnlyTheCallersOwnBookingsNewestFirst`(两个顾客各自只看到自己的、
  且新的在前)、以及上面提到的 ADMIN 不越权那条。跨用户隔离这条**必须**放集成
  测试——它验证的是 SQL 的 where 子句,mock 掉 repository 就等于把被测对象换掉了。

#### F-1 场次确认页从主路径上移除(点击场次直达选座)

`/showtimes/{id}` 这一页只展示日期/时间/影厅/票价四项 + 一个"继续选座"按钮,而这
四项**用户在上一页刚点过的场次胶囊上全都有**(`showtime-list.tsx` 的胶囊本身就
显示时间/影厅/票价,日期是分组标题),**下一页选座页头部又会重复一遍**。也就是
说这是一次不产生新信息、也不需要新决策的纯确认跳转——正是对标 GSC/TGV"多步骤
跳转"痛点的同款形态。

- **改法只有一个字符串**:`showtime-list.tsx` 的 `href` 从 `/showtimes/{id}` 改成
  `/showtimes/{id}/seats`。实测确认站内路径从 `/` → `/movies/{id}` →
  `/showtimes/{id}` → `/seats` 缩短为 `/` → `/movies/{id}` → `/seats`(少一次跳转、
  少一次点击)。
- **路由本身保留,没有删**:一是外部深链/收藏可能命中它,二是 editorial 方案 3-a
  刚给这一页加过背景剧照处理,删路由会让那次工作变成死代码。实测确认直接访问
  `/showtimes/{id}` 仍然返回 200 并正常渲染。
- **补了唯一会丢的那一项信息**:选座页在选中座位之前不显示单价,所以头部那行
  加上了 `· RM {price}/座`(`pricePerSeat` 这个 prop 选座页本来就收到了,不需要
  改数据流)。

#### G-2 跳去 Stripe 之前,把"绝对截止时刻"交到用户手上

倒计时页本来就说清楚了"5 分钟内完成支付,否则座位释放"——**这句没问题**。真正的
缺口是:用户点"去支付"之后整页跳到 Stripe,**我们的倒计时从视野里消失了**,而
座位持有窗口是 5 分钟、Stripe 自己的 session 下限却是 30 分钟(Phase 6 记录在案)。
一个慢慢输卡号的新手完全可能超时,结果是钱付了、座位已释放、`Payment` 落到
`ORPHANED_SUCCESS` 需要人工对账退款——而他**在跳走之前根本不知道自己上了一个
5 分钟的钟**。

- 倒计时下方文案改成"请在 **HH:mm** 前完成支付";"去支付"按钮上方新增一行
  "支付页面由 Stripe 托管,期间倒计时不会暂停——请在 **HH:mm** 前完成"。两处都
  说同一个时刻是有意的冗余:重点就是让用户**带着这个信息离开本页**。
- **这个时刻刻意用设备本地时区,不用 `lib/format` 的影院固定时区**
  (`Asia/Kuala_Lumpur`)。场次时间是"影院那边发生的事",用影院时区对;但支付
  截止时刻是用户拿去和**自己手机上的钟**对照的,尤其是在 Stripe 页面上,所以跟随
  设备时区才对。这是这两类时间语义上的真实区别,不是漏用了现成的 formatter。
- 不存在 hydration 隐患:这个组件只在 `booking` state 有值之后才挂载,而那个 state
  只由用户操作或 resume effect 设置,服务端渲染时走不到这个分支。

#### 座位图例:补上图标编码,并挪到座位网格上方

- **图例色块此前只有边框、没有图标**,而真实座位上 `LOCKED` 会渲染锁图标、
  `BOOKED` 会渲染勾图标——也就是说 1.5 节要求的"边框 + 图标"双重编码**在座位上
  做到了,在图例上漏了**。后果是:对色弱用户,"使用中"和"已售出"两个灰色调色块
  在色觉上最难区分,而唯一能可靠区分它们的线索(锁 vs 勾)恰恰不在图例里。已给
  这两项补上和座位一致的图标(色块从 `size-4` 提到 `size-5` 让 12px 图标放得下),
  "可选/已选"保持无图标——真实座位上它们显示的也是座位号而不是图标,保持一致。
  这是**加强**既有标准,不是新增标准。
- **图例从网格下方挪到上方**:图例是解码表,新手从上往下扫,解码表印在密文后面
  只能帮到"已经知道要去找它"的人。放在横向滚动容器**外面**(所以宽影厅横向滚动
  时图例不会跟着滚走),银幕弧线仍然紧贴座位网格,空间隐喻没有被破坏。
  实测确认图例底边(y=189)在第一个座位上边(y=304)之上。

#### 本批次的无障碍复核结果

- **44×44 热区**:新增的可点击元素只有订单卡片(整卡可点,远超 44px)和"我的
  订单"入口按钮(`h-11`)。图例色块是 `aria-hidden` 的装饰,不可点击,不适用。
- **不靠颜色单独区分状态**:订单状态用图标 + 文字 + 颜色三重编码;座位图例这次
  是把缺失的图标补回来,严格加强。
- **`prefers-reduced-motion`**:本批次**没有新增任何动效**,新页面复用的
  `FadeIn`/`GlassCard` 自身已含两层降级。已在 `reducedMotion: 'reduce'` 上下文里
  实测跑过订单列表 → 电子票的完整链路,渲染正常。
- **没有新增任何 hover-only 的信息载体**:新增的都是常驻文本/图标。

#### 验证方式

后端:`mvn -o test` 全量跑过(含新增的 3 个单元用例 + 3 个集成用例)。接口层面
另外用 curl 直接核对过:匿名 401、认证后返回 14 条、`createdAt` 严格倒序、
`ticketCode` 只在 CONFIRMED 上出现。前端:`npm run build`/`npm run lint` 干净;
**F-2 的核心验收场景用 Playwright 实测了"关掉标签页之后还能不能拿回票"**——全新
浏览器上下文、只靠登录 + Navbar 点击(不用任何记下来的 booking id)走到那张
CONFIRMED 订单,确认二维码正常渲染。

### Admin 用户管理(2026-08-11)

新增 `GET/PATCH/DELETE /api/v1/admin/users`(分页查询/改角色/删除,仅 ADMIN)+
`/admin/users` 前端页面。这批改动最初以 Antigravity(另一套 agentic 工具)跑出来
的形式落地,落库前经过一次完整的人工复核——过程中发现日志里三条"已验证"的结论
其实不成立,细节见下面几条,不是走个过场重复抄一遍原始日志。

**新增 API**:

- `GET /api/v1/admin/users?page=&size=`:分页查询全部用户,响应形状是标准的
  Spring `Page<UserResponse>`(和 `GET /api/v1/movies` 同一套分页约定)。
- `PATCH /api/v1/admin/users/{id}/role`:请求体 `{"role":"ADMIN"|"CUSTOMER"}`,
  返回更新后的 `UserResponse`。**不能修改自己的角色**,命中会返回 409
  (见下面"自我锁定"一节)。
- `DELETE /api/v1/admin/users/{id}`:204 无内容。用户有订单记录时返回 409
  (`BookingRepository.existsByUserId`,和 `MovieService.delete()`/
  `ShowtimeService.delete()` 那套"先查存在性再删,不只靠 FK 报错兜底"的模式
  一致,见 Phase 4 补充)。**不能删除自己的账号**,同样返回 409。

**关键决定**:

- **Controller 上没有 `@PreAuthorize`,方法级安全注解本来就不生效**:这个项目
  从 Phase 0 起就没有开 `@EnableMethodSecurity`(全项目搜索确认过,不是这次顺手
  漏配),ADMIN-only 的访问控制统一走 `SecurityConfig` 里的 URL 级
  `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`——和 `ReportController`
  (Phase 8)、`TicketController`(Phase 7)完全同一套模式。最初的实现在
  `AdminUserController` 上加了一个 `@PreAuthorize("hasRole('ADMIN')")`,不会
  报错也不会生效(纯粹的死代码,不是安全漏洞——URL 级规则已经在生效),但和
  项目其余 admin 接口的写法不一致,已经去掉,不需要为它专门去开
  `@EnableMethodSecurity`。
- **自我锁定(self-lockout)防护是服务端强制的,不只是前端禁用按钮**:
  `AdminUserService.updateUserRole`/`deleteUser` 都在最前面判断
  `id.equals(callerId)`,命中直接 409,不查库、不产生副作用。最初的实现只在
  `admin/users/page.tsx` 里把自己那一行的"切换角色"/"删除"按钮 `disabled`,
  这只是 UI 提示,服务端完全没有对应校验——**用 curl 直接打
  `PATCH .../{自己的id}/role` 复现过一次真实的自我降级**:唯一的种子 ADMIN
  账号被成功改成 `CUSTOMER`,系统瞬间零 ADMIN,只能用 `docker exec ... psql`
  直接改数据库把角色改回来,APP 内没有任何路径能恢复(没有别的 ADMIN 账号能
  登进 `/admin/users` 把它改回去)。这不是一个边界情况,这是"唯一管理员"这个
  MVP 现状(`V2__seed_admin.sql` 只插入了一个 ADMIN)下必然会撞上的真实事故
  ——`Authentication` 已经是 Controller 方法签名里现成的参数(
  `UUID.fromString(authentication.getName())`,和 `BookingController` 取
  `currentUserId` 同一个模式),服务端加两行判断的成本远低于放着这个洞不管。
- **ADMIN 反向隔离(Bug 3):没有做成 `(customer)/layout.tsx` 整层拦截,而是
  精确加在会展示个人数据的两个页面上**——这是这次复核里推翻原方案、改动最大
  的一处,完整记录见下面单独一节。

（复核调试过程中遇到两个环境陷阱——本地 `mvn spring-boot:run` 增量编译
导致的假性 500、CI 才会跑的 `mvn clean test` 撞上
`TimestampedEntitySaveFlushRuleTest`(强制任何带 `@CreationTimestamp`/
`@UpdateTimestamp` 的实体存库必须用 `saveAndFlush()`,`AdminUserService
.updateUserRole` 原本用的是 `save()`,已修正为 `saveAndFlush()`,一行
改动)——两个陷阱已记录进 `docs/DEVELOPMENT.md`"本机环境注意事项",完整
排查过程见 `docs/DECISIONS.md`「Admin 用户管理」。）

- **"loading 状态从数据本身推导、用 request-id 丢弃过期响应"是一个可复用
  模式,不是这个页面专属的一次性修复**:`admin/users/page.tsx` 最初把
  `loading` 存成一个独立的 boolean,分页跳转时旧数据还没被新数据替换掉、
  `loading` 却已经先翻回 `false`,会有一瞬间把上一页的数据当成当前页
  显示;报错时也没有清空旧的 `usersPage`,导致表格照常渲染旧数据而不是
  报错提示。改法是两条绑在一起的规则:loading 不再是手动维护的标志位,
  而是直接由"手上的数据是否对应当前请求参数"推导(`!usersPage ||
  usersPage.number !== page`);每次发起请求前用一个自增的 `requestIdRef`
  记下"这是第几次请求",响应回来时先比对 `requestIdRef.current` 是否还是
  发起时的那个值,不是才丢弃——两个几乎同时触发的请求,慢的那个回来时
  不会覆盖快的那个已经渲染好的结果。这套组合后来在 `admin/dashboard/
  page.tsx`(用等价的闭包 `cancelled` 标记代替 `requestIdRef`,见 Phase 8
  前端补充)和 `admin/movies/page.tsx` 上原样复用,不是各自发明一遍——
  以后任何"分页/筛选条件变化触发重新请求"的页面,应该默认照抄这个模式,
  不要重新设计一个独立的 loading 布尔值。
- **登录成功后,ADMIN 一律跳 `/admin/dashboard`,忽略 `?from=` 参数**:
  `LoginForm` 原本对所有角色都跳 `?from=` 指定的地址(默认 `/profile`),
  这对 ADMIN 账号是错的——如果 ADMIN 是从一个顾客端页面(比如首页的
  "去登录"链接)触发的登录,跳回 `?from=` 会让他登录后落在一个顾客端页面
  上,而不是 admin 语境。修复是 `login()` 的返回类型从 `Promise<void>`
  改成 `Promise<AuthResponse>`(向后兼容,原来丢弃返回值的调用方不受
  影响),`LoginForm` 读取 `session.user.role === "ADMIN"` 时无条件跳
  `/admin/dashboard`,`CUSTOMER` 才遵循 `redirectTo`。这条规则和
  `admin/layout.tsx`/`(customer)` 页面里那几处"ADMIN 反向隔离"检查
  (见下面一节)方向一致:整个登录后的路由分发都是"ADMIN 永远进 admin
  语境,不看上下文",不是登录这一步单独破例。

#### 单元测试覆盖:AdminUserService(Mockito)+ AdminUserFlowIntegrationTest(Testcontainers)

自我锁定这条 409 校验此前完全没有测试覆盖(Antigravity 的原始交付里,
`user` 包下没有任何测试文件)。补了两层,和 `TicketServiceTest` +
`TicketFlowIntegrationTest`、`BookingServiceListTest` +
`BookingFlowIntegrationTest` 这两组既有的"service 层 Mockito 快测 + 真实
HTTP 流程慢测"配对是同一个模式,不是发明新的测试风格:

- **`AdminUserServiceTest`**(Mockito,无需 Testcontainers,跑得快):覆盖
  `updateUserRole`/`deleteUser` 各自的自我锁定分支(命中即 409,且
  `verifyNoInteractions` 断言连 `UserRepository`/`BookingRepository` 都没碰
  过,证明这个判断在任何查库动作之前就短路了,不是"查完了才拒绝"),以及
  正常改角色、正常删除、目标用户不存在(404)、目标用户有订单记录时删除
  被拒(409)几条主干逻辑。
- **`AdminUserFlowIntegrationTest`**(Testcontainers 真实 Postgres,过完整的
  Spring Security 过滤器链):Mockito 测试传的 `callerId` 是手工构造的参数,
  没办法验证 `AdminUserController` 有没有正确地把 `Authentication` 解析成
  "调用者自己的 id"这一步接线是不是对的——如果这里被改错(比如手滑传成了
  路径变量而不是当前认证主体),`AdminUserServiceTest` 的每个用例依然会
  全绿,但真实 HTTP 请求下的自我锁定漏洞完全不会被拦住。这个测试类直接用
  `V2__seed_admin.sql` 播种的唯一 ADMIN 账号当"自己"去打真实的
  `PATCH/DELETE .../admin/users/{id}`,复现的正是复核时手动用 curl 发现
  的那个真实场景的形状(这个项目从来只播种一个 ADMIN,所以"ADMIN 改自己"
  和"ADMIN 改唯一的另一个 ADMIN"在这里是同一件事)。断言不只停在状态码:
  409 之后紧跟着再打一次 `GET /users/me`,确认角色/账号真的原封未动,不是
  "响应报错但已经写库一半"。额外补了一条"改别人的角色/删别人的账号仍然
  正常工作"的用例,因为这是 `AdminUserController` 第一次拿到集成测试覆盖,
  之前连正常路径也没有任何自动化验证。

#### ADMIN 反向隔离改成精确定位到具体页面,不是整个顾客端路由组

**最终方案**:把 ADMIN 检查加在 `profile/page.tsx` 和 `bookings/page.tsx`
各自已有的鉴权 `useEffect` 里,紧跟在原有的
`unauthenticated -> router.replace("/login")` 分支后面,检查
`user?.role === "ADMIN"` 就跳 `/admin/dashboard`,并且**在发起
`fetchCurrentUser()`/`callAuthorized(listBookings)` 之前** return——这样
真正会泄露个人数据的那次请求从未发出。`(customer)/layout.tsx` 保持纯
Server Component,不持有任何鉴权逻辑——考虑过在这一层整层拦截,但放弃了
(公开只读页面不该为了防一个低频场景多等一次鉴权往返,且后端本身也允许
ADMIN 操作自己的 booking)。
- **`bookings/[id]/confirmed/page.tsx`(电子票页)刻意没有加同样的检查**:
  `GET /api/v1/bookings/{id}` 后端允许"本人或 ADMIN 都能看",是有意支持
  的客服场景,反向拦截会堵死这条合法路径。

被推翻的整层拦截方案、数据泄露复现细节、Playwright 验证记录见
`docs/DECISIONS.md`「Admin 用户管理」。

#### `AdminHeader` 的 `/admin/movies` 死链接:已移除,不新建页面

**已解决,这是历史记录**:Sprint 4 给 `AdminHeader` 加了导航链接但
`/admin/movies` 当时还没有对应页面,一度移除了这个导航项;2026-08-12
"Admin 电影管理页面 `/admin/movies`"一节交付了真正的页面后,导航项已
恢复。完整原文见 `docs/DECISIONS.md`「`AdminHeader` 的 `/admin/movies`
死链接」。

### 技术栈降级:Java 25 + Spring Boot 4.1 → Java 21 LTS + Spring Boot 3.5.15(2026-08-11)

**为什么**:纯粹是招聘市场匹配问题,不是技术栈本身有缺陷。目前主流招聘
语境下"Java LTS"/"Spring Boot 3"的默认所指仍是 Java 17/21 和 Spring
Boot 3.x,不是刚发布不久、生态还在追赶的 Java 25/Boot 4。选 Java 21 而不是
继续留在 25 或降到 17,是因为 21 才是当前市场语境下的"新一代默认项"。

**明知 Spring Boot 3.5.15 已经 OSS EOL(2026-06-30)仍然选它**:3.0~3.5
全部已停止免费安全补丁供给,当前只有 4.0.x/4.1.x 还在维护窗口内,不存在
"更新但仍在维护期的 3.x"这个选项。这个 trade-off 对一个不需要持续安全
补丁供给的作品集项目可以接受——面试官对齐的是"这人对 Spring Boot 3.x 这套
体系熟不熟",不是"这个部署实例今天能不能收到安全补丁"。如果项目以后要
长期在线上跑,需要重新评估这个决定。

完整改动清单(pom.xml、依赖坐标改名、包路径迁移、Jackson 2/3 namespace)、
验证方式(`mvn clean test` 144/144 全绿)、"本机没装 JDK 21"的验证缺口、
降级过程中的 git log 插曲,见 `docs/DECISIONS.md`「技术栈降级」。

### `/admin/movies` 契约核实:探测性调用误删真实种子电影的事故与操作纪律(2026-08-12)

不算新 Phase,是一次工具使用事故的复盘记录——原本没有专门收纳"跨 Phase 的
操作纪律"这类条目的独立小节(不存在名为"关键学习与原则"的章节),按现有
文档惯例(审计后修复、种子数据的海报图来源等)以日期归档的方式加在这里。
完整事故经过和恢复过程见 `antigravity.md` Sprint 5,这里只记录沉淀下来的
操作原则:

- **探测性验证调用(尤其是 `DELETE`/`PUT` 这类有副作用的)不能拿真实数据当
  第一个尝试对象,哪怕预期有 409/`RESTRICT` 兜底**——`/admin/movies` 的
  API 契约核实任务中,为了验证 `DELETE /api/v1/movies/{id}` 遇到排期场次
  时的 409 响应格式,选择直接对一部真实种子电影(`Interstellar`)发起
  `DELETE`,理由是"这部电影应该有排期场次,删除会被 `RESTRICT`/409 挡下,
  所以是安全的探测"。这个"应该会被挡下"的假设本身没有先用真实数据核实过,
  纯粹是预期;实际执行后,`DELETE` 返回了 204(真删除成功)而不是预期的
  409,因为这部电影在当时的数据库里恰好一场排期都没有。教训不是"应该更
  仔细检查",而是一条具体的操作纪律:任何有副作用的探测性调用,第一个尝试
  对象必须是自己创建、自己能完全控制生命周期的一次性数据——如果要验证的
  是某个防护机制(如"有排期场次时删除应该被拒绝"),必须先用自己创造的
  数据把触发条件真正搭建出来(自己建电影 + 自己建场次),而不是找一个"看起
  来应该会触发"的真实对象去赌这个假设成不成立。
- **执行前的安全假设如果和执行前一步刚查到的证据矛盾,必须先停下来重新
  判断这个假设本身,不能带着矛盾的证据继续按原计划执行**——上面这次事故
  里,`DELETE` 之前实际上先查过 `GET /api/v1/showtimes?movieId=...`,
  结果已经如实返回了空数组(这部电影没有排期场次),这个结果和"删除会被
  挡下"的预设假设是直接矛盾的,但操作时没有把这个矛盾当成一个需要重新
  评估计划的信号,而是带着旧假设继续执行了原本计划好的 `DELETE`。矛盾
  信号出现的那一刻就是该停下来的时刻,不是"反正流程都走到这一步了,先
  跑一下看看"。
- **事故发生后的处理顺序:先如实、完整地披露发生了什么,再动手恢复,恢复
  完成后主动核对影响范围有没有超出预期,不能"数量对了就默认没事"**——
  这次事故里,恢复动作(手动读 migration 源文件重建那一行数据 + 关联)
  本身做对了,但恢复后第一次只核对了被删除又恢复的那一条记录和 `总数`
  是否回到 11,没有主动去核对其余 10 部电影的字段/genre 关联有没有受到
  影响(即便事后回溯确认没有牵连,那也是运气好,不是核对到位)。这条原则
  和上面两条同样重要:一次事故的"恢复"不应该止步于"看起来数量对了"。

### Admin 电影管理页面 `/admin/movies`(2026-08-12)

补上了 Sprint 4(见 `antigravity.md`)留下的死链接对应的真实页面——不新建
后端 Controller,完全复用 Phase 2 已有的 `MovieController`
(`/api/v1/movies` CRUD + `/{id}/poster`、`/{id}/backdrop` 两个上传接口)和
公开的 `GET /api/v1/genres`,契约在正式动手前用 curl 逐项核对过(细节和
过程见 `antigravity.md` Sprint 5 前面的核实记录,不在这里重复)。

关键决定:

- **列表页(`/admin/movies`)照抄 `/admin/users` 已验证过的分页/竞态模式**
  ——`Page<T>` 响应结构逐字段相同(两者都是 Spring Data 默认序列化),
  分页 UI、`requestIdRef` 防竞态、确认删除 Dialog 全部照抄,唯一区别是
  `GET /api/v1/movies` 本身是公开接口,不需要 `callAuthorized` 包一层。
  删除的 409("电影仍有排期场次")直接把 `ApiError.message` 原样展示给
  管理员,不额外拼接文案——这条错误信息本身已经写得足够清楚
  (`MovieHasScheduledShowtimesException`)。
- **创建表单默认 `status = COMING_SOON`,不是 `NOW_PLAYING`**——新建的电影
  在信息还没被确认前不该直接对顾客端可见为"正在热映",管理员需要手动
  确认好海报/字段之后再切换状态,这是一个刻意的默认值选择,不是遗漏。
- **创建成功后跳转到编辑页,不是列表页**,因为海报/背景图只能在电影已经
  有 `id` 之后才能上传(`POST /{id}/poster`、`POST /{id}/backdrop` 两个
  接口都要求电影已存在)——这不是一个可以绕过的两步流程,所以编辑页顶部
  会显示一次性提示("电影已创建...现在可以上传海报/背景图"),避免管理员
  以为提交表单就等于整个录入流程结束了。这个提示状态从
  `?created=1` 这个一次性 query 参数读入(`useState` 的惰性初始值,不是
  在 `useEffect` 里同步 `setState`——后者会撞上这个项目已经踩过一次的
  `react-hooks/set-state-in-effect` 规则,见 Phase 8 admin dashboard 那次
  记录),读完之后用 `router.replace` 把参数从 URL 里去掉,避免刷新页面
  重复触发,跟座位选择页的 booking-resume 处理是同一个模式(见 Phase 6)。
- **编辑表单打开时必须用 `movie.genres` 回填 genre 多选框的选中状态,不能
  留空**——`PUT` 是全量替换不是增量 patch(契约核实阶段已确认),如果编辑
  表单默认不选中任何 genre,管理员只改了片名就直接保存,会在无声无息中
  清空这部电影原有的所有 genre 关联。`MovieForm` 组件里 `genreIds` 用
  独立的 `useState`(不是 react-hook-form 注册字段)管理,初始值来自
  `initialMovie?.genres.map(g => g.id)`,创建页则是空数组。
- **genre 多选没有新增 Checkbox/多选组件,复用的是 admin dashboard 报表
  页面粒度按钮同一个模式**(`variant` 在 `default`/`outline` 之间切换 +
  `aria-pressed`),而不是引入一个新的 shadcn Checkbox 组件——项目里已经
  有一个验证过的、无障碍达标的"多选态按钮组"模式(见 Phase 8 前端补充的
  时间粒度按钮),选中态额外叠加一个 `Check` 图标(不止靠颜色区分,延续
  1.5 节的双重编码标准),没有必要为同一件事再造一个新组件。
- **图片上传是独立的 `uploadMovieImage` 辅助函数,绕开 `apiFetch`**——原因
  和 `admin-reports.ts` 的 `downloadExport` 绕开 `apiFetch` 是同一个:
  `apiFetch` 固定把 body 当 JSON 处理并设置
  `Content-Type: application/json`,`multipart/form-data` 需要浏览器自己
  按 `FormData` 生成带 boundary 的 `Content-Type`,这个头不能手动设置,
  一旦设置就会破坏 boundary、后端解析不出文件。
- **验证边界(交付当时)**:`npm run build`/`npm run lint` 跑通,做过路由
  层面的冒烟测试,但当时没有做浏览器交互验证——这条边界很快被下一节的
  真实浏览器补测覆盖(且发现了一个真实 bug)。原文见 `docs/DECISIONS.md`
  「Admin 电影管理页面 `/admin/movies`」。

### `/admin/movies` 补测:真实浏览器验证 + 发现一个预先存在的图片渲染 bug(2026-08-13)

用 Playwright 在隔离环境里做了真实浏览器验证(13 项检查,12 项通过)。
唯一失败项:本地上传的海报/背景图上传成功但**预览图加载不出来**——根因
是 Next.js 图片优化器内置的 SSRF 防护(上游地址解析到私有/回环 IP 就拒绝
代理),这个 bug 从 Phase 2 就存在,只是此前从没有 UI 入口真正渲染过本地
上传的图。已在下一节修复。完整排查过程见 `docs/DECISIONS.md`「`/admin/
movies` 补测」。

### 本地上传图片渲染 bug 的修复:`images.dangerouslyAllowLocalIP`(2026-08-13)

上一条记录的图片渲染 bug 已修复:Next.js 16 提供了针对性的官方豁免机制
`images.dangerouslyAllowLocalIP`(默认 `false`,查证于本地安装的 Next.js
16 官方文档,不是凭训练数据猜的旧版本 API)。

**最终方案**:独立的、显式的 opt-in 环境变量
`NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION`,不从 `NEXT_PUBLIC_API_BASE_URL`
反推是否本地开发——第一版曾用 hostname 字符串匹配反推,复核后否决(会
误判"生产环境后端 origin 恰好也叫 localhost"的同机反代场景,而且环境
变量缺失时的兜底值本身也是 `localhost`,导致本该更安全的默认反而被判成
`true`,CI 实测验证过这个问题真实存在)。**可复用原则:安全开关必须是
独立的显式信号,不能从不相关的配置项反推**——和后端 `SPRING_PROFILES_
ACTIVE` 是同一个思路。

```ts
const LOOPBACK_HOSTNAMES = new Set(["localhost", "127.0.0.1", "::1"]);
const backendIsLoopback = LOOPBACK_HOSTNAMES.has(apiBaseUrl.hostname);
const localImageOptimizationOptIn =
  process.env.NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION === "true";
const allowLocalImageOptimization = backendIsLoopback && localImageOptimizationOptIn;
```

被否决的第一版方案、三种场景(CI/未 opt-in/显式 opt-in)的完整构建验证、
WebP 编解码调试插曲,见 `docs/DECISIONS.md`「本地上传图片渲染 bug 的
修复」。

### 创建电影的主路径改成「从 TMDB 搜索选择」,手打表单降级为兜底(2026-08-13)

`/admin/movies/new` 默认展示一个 TMDB 搜索框,选中一条结果后自动预填创建
表单;手打字段还在,只是从"唯一路径"降级成一个不起眼的"找不到?手动创建"
链接背后的兜底路径,不删除、不弱化其可用性。

**新增两类接口**:

- `GET /api/v1/admin/movies/tmdb-search?query=` + `GET .../tmdb-search/{tmdbId}`
  (新 `AdminMovieTmdbController`,仅 ADMIN):后端代理 TMDB 的
  `/search/movie` 和 `/movie/{id}`,前端拿到的是精简后的 DTO,TMDB 的
  API key 全程不经过浏览器——和 `OMDB_API_KEY` 从未出现在前端是同一个
  理由(见"种子数据的海报图来源"一节)。**没有加 `@PreAuthorize`**:和
  项目其余 admin 接口一样,靠 `SecurityConfig` 的 URL 级
  `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` 保护——这正是
  `antigravity.md` 记录过的那次教训(`AdminUserController` 曾经加过一个
  静默失效的 `@PreAuthorize`),这次直接照抄已验证的模式,没有重蹈。
- `PATCH /api/v1/movies/{id}/image-urls`(新增在**已有的** `MovieController`
  上,不是新 controller):直接把 `posterUrl`/`backdropUrl` 设成给定的外部
  URL 字符串,不经过 `StorageService`,不上传不转存——这是让 TMDB 热链图
  真正落到 `movies` 表所必需的一个环节,`POST /api/v1/movies` 本身完全
  没有改。

**为什么是 `PATCH` 新增一个字段(方案 B),不是给 `MovieRequest` 加
`posterUrl`/`backdropUrl` 字段(方案 A)**:方案 A 最直接,但
`MovieRequest` 同时被 `PUT`(全量替换语义)复用——`genreIds` 当初就是
因为全量替换踩过一次坑,才有了编辑页必须回填 `genreIds` 这条规则(见
"Admin 电影管理页面"一节)。如果 `posterUrl`/`backdropUrl` 也进
`MovieRequest`,编辑页每次保存都要连带传回这两个字段,否则全量替换会把
已经设置好的图片悄悄清空——等于在一个刚刚才补上防护的地方,又开一个新的
同类型豁口。方案 B(独立的 `PATCH` 端点,局部更新语义)完全不碰
`MovieRequest`/`PUT`,不给编辑页引入任何新的"忘记带某个字段就会丢数据"
的风险。这个 `PATCH` 局部更新语义和 `PATCH /api/v1/admin/users/{id}/role`
是同一个既有先例,不是这次新发明的模式。

**为什么 genre / 分级 / 评分不跟着 TMDB 自动填**:TMDB 的 genre 体系和
这个项目固定的 15 个值不是一一对应关系,分级口径(TMDB 没有统一的
MPAA/LPF 分级字段)也对不上——和种子数据阶段"OMDb 标签不能直译成本项目
genre"是同一个结论(见"种子数据扩充"一节的 `Oppenheimer` 例子)。这四个
字段(`contentRating`/`userRating`/`status`/`genreIds`)不管电影是搜出来
的还是手打的,一律留给 admin 自己填——不是遗漏,是不做看似省事、实则可能
糊弄出错误数据的自动映射。

**为什么图片是热链,不下载转存**:和现有 11 部种子电影的处理方式完全
一致(见"种子数据的海报图来源"一节)——`PATCH .../image-urls` 直接存
TMDB 返回的 `image.tmdb.org` URL,不经过 `StorageService.store()`。

**TMDB 调用次数,按具体交互动作数**:输入片名后按 Enter/点搜索按钮才发起
搜索(不是打字实时搜索,省掉防抖复杂度,也避免逐字符打字触发一堆搜索
请求)——1 次 TMDB 调用。点选中某一条结果——1 次 TMDB 调用
(`/movie/{id}?append_to_response=videos`,预告片信息用 `append_to_response`
和详情合并成一次请求,不是先查详情再单独查预告片)。没被选中的搜索结果
不会触发任何额外调用,也没有做分页(只取 TMDB 第一页,最多 20 条——
排不进第一页就换个搜索词,不做"翻页找电影"这种体验)。

**TMDB 调用失败的降级行为**:`TmdbGatewayException`(覆盖"key 没配置"
和"TMDB 调用本身失败"两种情况,前端不需要区分,反正都是同一句"暂时不可用,
请手动填写")经 `GlobalExceptionHandler` 映射成 502(不是通用 500 handler
兜底的那种)——这是上游服务失败,不是这个项目自己代码的 bug。消息本身是
英文,和 `GlobalExceptionHandler` 里其余所有 handler 保持一致(不是单独
给这一个 handler 换成中文,那样整个错误信封会中英文夹杂);中文提示由
**前端**的 `TmdbSearchPicker` 组件自己翻译展示,不依赖后端预翻译。

**`MovieForm` 的 `onSaved` 改成可以返回 `Promise`**:因为创建流程现在
多了一步"创建成功后,如果是 TMDB 预填的,再调一次 `PATCH .../image-urls`
把图片地址应用上去"——如果这一步失败(网络抖动等),不能让它悄悄失败:
`/admin/movies/new/page.tsx` 会带着 `?imageSetupFailed=1` 跳转到编辑页,
编辑页顶部显示一条明确的"电影已创建,但海报/背景图设置失败,请在下方
手动上传"提示(复用已有的 `AnimatedFormBanner` 一次性提示机制,和
"电影已创建...现在可以上传海报/背景图"那条是同一套机制,不是新造一个)。
如果 TMDB 预填但两张图都设置成功,则**不显示**"已创建"提示——编辑页
下方海报/背景图卡片本身已经显示的是真实图片而不是占位图,这就是确认,
再显示一条"现在可以上传"的提示反而是过时的指引。

**已知合规缺口,这次没有处理,按你的决定等后续单独一轮**:CLAUDE.md 已经
记录过 TMDB 免费层要求署名(官方 logo + 指定文案,见"backdrop 和 poster
分别用两个不同数据源"一节),这次是第一次让后端在运行时真正主动调用
TMDB(不只是种子数据阶段的一次性脚本),这个缺口的相关性比之前更高了一点,
但仍然不在这次改动范围内。

**已知缺口,尚未验证**:这个会话没有真实的 `TMDB_API_KEY`,只验证过"没
配置 key 时的降级路径"(502 + 中文兜底提示);**TMDB 搜索真的返回结果、
选中后表单预填、图片 URL 写入数据库这条"key 配置正确"的成功路径,只有
mock 网关的集成测试覆盖,没有拿真实 TMDB API 在浏览器里跑过**。等有真实
key 可用再补验证。完整测试清单见 `docs/DECISIONS.md`「创建电影主路径改成
「从 TMDB 搜索选择」」。

#### 补充:搜索结果从文字列表改成海报网格(同一分支的第二个 commit,2026-08-14)

上面那次交付只解决了"能不能搜到",没管"搜到之后好不好选"——初版结果列表是
一行一条的纯文字布局,海报缩成 48px 的缩略图塞在行首。**内容形态和展示形态
不匹配**:从搜索结果里挑一部电影是一个视觉识别任务("哪个才是我要的那部
《沙丘》"),真正被扫的是海报而不是标题字符串,把海报压到 48px、让文字占据
主要面积,等于把这个任务里最有辨识力的信息降到最次要的位置。改成海报为主体
的网格卡片(海报占满卡片宽度、`aspect-[2/3]`,标题/年份放下方)。

- **列数跟随容器宽度降级,用的是 `@container` 查询而不是视口断点**:这个组件
  渲染在一个固定 `max-w-2xl` 的卡片里,视口宽度并不能描述它实际拿到的空间——
  视口 1280px 时容器内容区其实只有 ~600px。用容器查询(`grid-cols-2
  @md:grid-cols-3 @xl:grid-cols-4`)才是对"容器变窄就降列"这个需求的直接
  表达。实测确认:1280px 视口下 4 列,560px 视口下 3 列。
- **卡片复用的是 `Card` 组件的视觉 token,不是嵌套一个真的 `<Card>`**:
  `rounded-xl`/`bg-card`/`ring-1 ring-foreground/10`/`shadow-sm` 这套值直接
  加在 `<button>` 上。原因是 `Card` 的内边距模型假设"内容带 padding",而这里
  要的是海报满幅出血;而且整张卡片本身就是按钮,把按钮塞进 `Card` 这个 `div`
  里反而多一层嵌套。这和座位图"只复用 `--glass-*` token、不复用 `GlassCard`
  组件本体"是同一类取舍(见 1.5 节)。**没有引入 `GlassCard`/指针跟随高光**
  ——`/admin/**` 是浅色平面的设计系统例外,这条边界是 1.5.1 定的,这次没破例。
- **选中态必须有即时视觉确认**:点一张卡片之后要先发一次详情请求才会切到
  预填表单,这中间有一段真实的等待。初版在这段时间里除了一个小 spinner 没有
  任何反馈,点击读起来像没生效,然后页面突然换掉——用户无法确认自己到底点中
  了哪一部。现在选中的卡片立刻变成主题色描边 + 海报上盖一个「✓ 已选择」徽章,
  同时其余卡片降到 40% 不透明度,三重编码(描边颜色 + 图标 + 兄弟项对比),
  不只靠颜色区分(1.5 节的既有标准)。
- **骨架屏解决的是一个具体的坏行为,不只是"填满空白"**:初版在重新搜索时,
  按钮转 spinner,但**上一次搜索的结果原样留在下面**——读起来像是"这就是你
  这次搜索的结果",而实际上是上一个关键词的。现在搜索期间用 6 个卡片形状的
  骨架屏替换掉旧结果,消除的是这个误读,不是单纯为了不空着。
- **无障碍**:卡片本体远大于 44×44(实测 138×260);骨架屏的 `animate-pulse`
  显式加了 `motion-reduce:animate-none`(实测 `prefers-reduced-motion` 下
  `animationName` 为 `none`);hover 的阴影/透明度过渡加了
  `motion-reduce:transition-none`;海报 `alt=""`(装饰性,标题就在旁边)。
- **这次是纯展示层改动,数据行为一行没动**(fetch 调用、request-id 防竞态、
  搜索触发时机、选中后拉详情的次数全部原样,回归验证细节见
  `docs/DECISIONS.md`「补充:搜索结果从文字列表改成海报网格」)。

(这次交付时发现共享组件 `components/ui/skeleton.tsx` 缺
`motion-reduce:animate-none`,当时标注未修——**已于 2026-08-14 解决,
见下面"skeleton.tsx 补 motion-reduce"一节**。)

### TMDB 署名合规:footer 组件(2026-08-14)

补上此前标注过的合规缺口——新建 `components/layout/tmdb-attribution.tsx`
(`<TmdbAttribution>`),在顾客端和 admin 端 layout 里各挂载一份(署名内容
不依赖登录态,两个分支都加)。

- **先验证网络访问权限,不是假设有或假设没有**:动手前用 curl 直接探测
  `themoviedb.org` 确认有出网权限,才决定走"下载官方 logo 素材"这条完整
  合规路径,不是退而求其次的纯文字方案。
- **官方文案不信 WebFetch 的 AI 摘要,改成 curl 原始 HTML 自己去标签
  核对**——这是一条通用操作纪律:第一次用 WebFetch 查文案要求时,拿到一个
  和已有记录直接矛盾的结论(声称"没有强制要求具体文案"),即使明确要求
  "逐字引用",摘要工具复述的文本也不能直接当可信原文。改用 curl 拉取
  条款页原始 HTML 手动核对,才是真正可信的版本。核对过的准确原文:
  > This [website, program, service, application, product] uses TMDB and
  > the TMDB APIs but is not endorsed, certified, or otherwise approved
  > by TMDB.

  `TmdbAttribution` 组件选的是"website"。
- **视觉权重用实测数字说话**:`Navbar`/`AdminHeader` 品牌标识是 20px 图标
  + 18px 半粗体文字;`TmdbAttribution` 的 logo 是 14px + 70% 不透明度,
  文案 12px + 80% 透明度的 `muted-foreground`——两组数字确保"署名视觉
  权重低于站点品牌"不只是主观感觉,Playwright 验证时读取了 computed
  style 数字逐一核对。

logo 素材来源(TMDB 官方原始 SVG)、组件挂载点论述、详细验证方式见
`docs/DECISIONS.md`「TMDB 署名合规」。

### skeleton.tsx 补 motion-reduce(2026-08-14)

修法只有一行:`components/ui/skeleton.tsx` 默认 className 加上
`motion-reduce:animate-none`,和 `GlassSkeleton`/`components/motion/`
下已有组件的降级写法一致。

**准确的受影响调用点清单**(先用 grep 重新核对,上面"审计后修复"一节里
当时列的六项清单有两处不准确,原样保留未改,这里给准确版本):4 个直接
受益(`(customer)/profile/page.tsx`、`admin/layout.tsx`、
`components/admin/report-card-skeleton.tsx`、
`components/layout/navbar.tsx`)+ 1 个通过 `ReportCardSkeleton` 间接
受益(`admin/dashboard/page.tsx`)+ 1 个从未受影响、旧清单误列
(`(customer)/bookings/page.tsx`,实际用的是 `GlassSkeleton`)。

验证方式(读实际 computed `animationName`,不只看 class 有没有挂上)见
`docs/DECISIONS.md`「skeleton.tsx 补 motion-reduce」。

### Admin 场次管理 `/admin/showtimes`(2026-08-14)

补上审计发现的最大缺口——场次此前只能靠 curl/Swagger 维护,没有对应的
admin 页面。后端 `ShowtimeController` 的 POST(20 分钟清场冲突校验)/
DELETE(有订单时 409)/GET 在 Phase 4 就已经齐全并测试过,这次只做了
两处小改动,新增页面照抄 `/admin/movies` 已验证过的分页/竞态/删除确认
模式。

关键决定:

- **`GET /showtimes` 没有改成分页**:这是顾客端选座流程共用的公开端点
  (`listShowtimesByMovie`),把返回类型从 `List<T>` 改成 `Page<T>` 会
  改变 JSON 形状、砸了顾客端的解析。Admin 列表页复用这同一个公开端点
  (加了 `movieId`/`hallId`/`date` 过滤),前端按天浏览,不做服务端
  分页——这个项目的场次数据量级(1 分店 3 影厅)不值得为此新开一个
  专属的管理端分页接口。
- **`ShowtimeResponse` 新增 `bookedSeats`/`totalSeats` 两个字段**
  (additive,不破坏现有消费者):口径复用 Phase 8 occupancy 报表已经
  验证过的"只统计 CONFIRMED"规则(和 `ReportRepository.occupancyRows`
  同一套计数逻辑,这次换成 Spring Data JPQL 而不是原生 SQL——Phase 8
  用原生 SQL 是为了展示复杂的 `generate_series`/`date_trunc` 聚合,这次
  只是两个简单的 GROUP BY 计数,跟项目其余模块一样走 JPQL 更一致,不是
  推翻 Phase 8 的选型)。两个批量查询(按 showtime id 集合查 booked、
  按 hall id 集合查 total),不是逐场次查,避免 N+1。`ShowtimeMapper
  .toResponse` 因此从"只吃 Showtime 实体"变成"吃 Showtime + 两个 int
  参数",用显式 `@Mapping(source=...)` 把参数名对应到 record 字段,不
  依赖 MapStruct 隐式的同名参数推断——这个项目对 MapStruct 生成代码
  踩过坑(见 Admin 用户管理一节),显式声明比隐式推断更值得信任。
- **创建表单的电影下拉只显示 `NOW_PLAYING`/`COMING_SOON`,排除
  `ENDED`**——给已下映的电影排新场次业务上说不通;`GET /movies?status=`
  已支持这个筛选,前端拉一次全量(`size=100`,种子数据 11 部电影完全
  够用)按状态客户端过滤,不用为两个状态各发一次请求。
- **开始时间选择器按影院时区(吉隆坡,UTC+8)解释,不是浏览器时区**——
  这是"发生在影院"的事件,和顾客端的 `formatShowDate`/`formatShowTime`
  同一类语义,和 Phase 6 G-2 的支付倒计时(故意用设备时区)刚好相反。
  马来西亚从 1982 年起固定 UTC+8、不实行夏令时,所以不需要 Intl 查表
  取偏移量——`lib/format.ts` 新增的 `cinemaLocalTimeToIso` 直接拼接
  `+08:00` 后交给 `Date` 解析,和 `CINEMA_TIME_ZONE` 本身的硬编码是
  同一条理由。
- **创建冲突(409)在表单上翻译成中文,不是原样展示**——这是这个项目里
  admin 表单错误处理**两种既有先例中的一种**,不是破例:`MovieForm`
  的既有模式是原样展示 `ApiError.message`(那些消息本身已经写得够
  清楚);但 `ShowtimeConflictException` 的消息里嵌着裸 `Instant` 时间
  戳和影厅名,读起来是内部细节转储。没有尝试解析这条消息去拼出更具体
  的提示(比如"和 X 点到 Y 点那场冲突")——这个项目一贯的原则是不解析
  错误字符串取结构化数据(参考 Phase 5 座位冲突处理的先例),所以就是
  一句固定但清楚的中文提示。删除时的 409("仍有订单")继续原样展示,
  跟 movies 页面删除保护是同一个模式——这条消息本身已经是完整的一句
  话,不需要改写。
- **没有编辑功能**:和后端契约一致(Phase 4"没有更新场次的 API",排期
  错了删除重建),`ShowtimeForm` 不接受 `initialShowtime`,不是"先做成
  `MovieForm` 那种 create/edit 共用再阉割掉一半",从一开始就只有创建
  这一种用途。
- **测试**:`ShowtimeAdminFlowIntegrationTest` 原有 5 个用例(创建/冲突/
  边界/401/403)覆盖的核心逻辑这次完全没动,新增 3 个:hallId 筛选
  返回正确子集、`bookedSeats` 只统计 CONFIRMED(直接用 autowired
  repository 构造一个 CONFIRMED + 一个 PENDING booking 断言,不走完整
  Stripe webhook 流程——这次要验证的是计数 SQL,不是支付流程,后者已
  经在 Payment 相关测试里覆盖过)、删除有订单的场次仍返回 409(此前
  完全没有测试覆盖,原有的删除测试只覆盖了"零订单场次删除成功"这一
  半,顺手补上)。`mvn clean test` 全量跑过,163/163 全绿,没有任何
  既有测试因为这次改动放宽断言或删除。

### Admin 电影编辑页:排场次引导卡片(2026-08-15)

补上"创建电影 → 编辑页调整状态 → 再跳去场次页面单独排场次"这条链路里缺失的
衔接——`status` 字段和"这部电影到底有没有排片"此前完全脱钩,编辑页上也没有
任何东西提示 admin 下一步该去排场次。讨论过三个方向(status 完全由场次数据
实时推导 / 只加一个孤立提示链接 / 在创建-编辑流程里把排场次当作自然的下一步
强调出来),采用最后一个——`status` 字段本身的写入方式不变,所有状态切换
依然是 admin 的一次显式点击,不是自动派生。

关键决定:

- **唯一新增的数据信号是"这部电影有多少场未来场次",不需要新后端接口**:
  `GET /api/v1/showtimes?movieId=` 本来就公开、本来就返回该电影全部场次
  (不分过去/未来),"未来"就是纯粹的 `Instant` 比较(`startTime > now`),
  和时区无关,不需要像展示日期那样走影院固定时区。这个 count 在编辑页上
  独立于 `Promise.all([getMovie, getGenres])` 单独抓取,失败时静默(count
  留 `null`,引导卡片直接不渲染),不能让这个次要信号的失败触发"电影可能
  已被删除"那个为核心加载失败准备的整页错误。
- **一张卡片,两种语气,由 `(status, upcomingShowtimeCount)` 驱动,不是两个
  独立功能**:`COMING_SOON` + 0 场未来场次是刚创建电影后的正常状态,卡片
  走平实的 `Card` 视觉,不着色;`NOW_PLAYING` + 0 场未来场次是真正的数据
  矛盾(网站告诉顾客"正在上映",实际订不到票),复用 `StatTile`
  `tone="warning"` 已经验证过的琥珀色 token(`--chart-amber-border`/
  `--chart-amber-surface`/`--chart-amber`),不新发明一套配色。`ENDED` 或者
  已有 ≥1 场未来场次时卡片直接不渲染——纯数据驱动、每次加载都重新判断,不
  需要一个"已关闭"标志位,已经排好场次的电影不会被反复提醒。
- **`/admin/showtimes/new` 支持 `?movieId=` 预选电影,而不是做成内嵌表单**:
  排场次的入口从编辑页跳过去,预选好电影下拉框,成功后跳回
  `/admin/movies/{id}/edit?showtimeAdded=1` 而不是通用的场次列表页(场次
  列表页本身"新增场次"按钮的行为不变,仍跳回 `/admin/showtimes`)。没有做
  成内嵌精简表单——那需要编辑页额外拉 cinemas/halls 数据、复制一份
  `ShowtimeForm` 的校验和 409 冲突文案,或者重构成两种 chrome 共用,成本
  明显高于收益;真正的痛点是"排场次这件事容易被忘记、还要在下拉框里重新
  找一遍电影",预选 + 跳回原页已经解决了这个问题,不需要连页面跳转本身
  也省掉。
- **"切换到 Now Playing"的一键建议是一次性的,不是常驻检查**:只在
  `showtimeAdded=1`(刚从排场次流程返回)那一刻出现,不是"只要
  COMING_SOON 且有未来场次就一直提示"——后者会误伤合法的预售场景(电影还
  没上映,提前开卖预售票,状态仍想保持 Coming Soon),也会变成这次任务
  本来就想解决的那种"反复提醒"。机制上复用编辑页已有的一次性 banner 模式
  (`created=1`/`imageSetupFailed=1` 那一套:`useState` 惰性初始值读
  query param,`router.replace` 清掉参数),`AnimatedFormBanner` 因此加了
  一个 additive 的 `action?: ReactNode` 插槽(现有调用方不受影响)。卡片上
  "标记为已下映"和 banner 上"切换到 Now Playing"两个一键操作,底层是同一个
  `applyStatus(next)`:用已加载的 `movie` 拼出完整的 `MovieRequest`(PUT
  是全量替换,字段一个不能少,原因见 `MovieRequest` 的既有注释)、只改
  `status`,复用的是 `MovieForm` Save 按钮已经在走的同一个 PUT,只是预填
  并自动提交——本质仍是 admin 的一次显式点击,不是把 status 改成自动派生
  字段。
- **"已下映提醒"(设计讨论里的开放问题之一)这次一起做,但只做到卡片这一
  层,不做列表页汇总**:`NOW_PLAYING` + 0 场未来场次复用的是同一个信号、
  同一个组件,边际成本只是多一个文案/配色分支,值得顺手做;但"这 11 部
  电影里哪些需要关注"更适合做成 `/admin/movies` 列表页上的一个标记(一眼
  扫完整个片单),而不是只能一部一部打开编辑页才发现——这是不同的界面、
  不同的设计问题,明确留给以后单独立项,这次没有做。
- **零后端改动**:复用的三个接口(`GET /showtimes?movieId=`、
  `POST /showtimes`、`PUT /movies/{id}`)全部是已有接口,原样调用。
- **验证方式**:这次会话没有可用的浏览器自动化工具,无法像此前几批
  (`/admin/movies` 补测等)那样做真实浏览器点击验证。改为写了一个 Node
  脚本,用真实跑着的 Postgres/Redis/后端(而不是 mock),对着**自己创建、
  自己清理**的一部测试电影(不碰种子数据,吸取的是"探测性验证不能拿真实
  数据练手"那次事故的教训,见「`/admin/movies` 契约核实」一节)完整走一遍
  状态机:创建(默认 COMING_SOON,0 场次)→ 排一场未来场次(count 变 1,
  卡片该消失)→ 一键切 Now Playing(PUT 保留其余字段)→ 删除该场次(count
  归零,复现 NOW_PLAYING + 0 场次的条件)→ 一键标记 Ended → 清理测试电影,
  全部断言通过。这证明了卡片逻辑依赖的数据契约和状态转换在真实数据库下
  完全正确;`npm run build`/`npm run lint` 干净说明类型和 JSX 组装没有
  问题;**没有验证的是真实浏览器里的实际渲染/交互**(卡片的具体像素观感、
  点击 `Button` 触发 `onClick` 这条链路)——这是本次交付相对以往几批唯一
  收窄的验证边界,如实记录,不是遗漏。

### Admin 电子票核销 `/admin/tickets/redeem`(2026-08-15)

补上此前只能靠 curl/Swagger 操作的入场核销能力(`POST /api/v1/tickets/redeem`,
Phase 7 就已实现测过)对应的 admin 页面——**零后端改动**,复用已有接口原样
调用,请求体格式、成功/失败响应结构先对着现有实现和集成测试核实过一遍才
动手。跟下面「影院/影厅管理」是同一天交付的两个独立功能,分两次提交,这里
只记录电子票核销自己的决定。

- **失败态区分三种情况,不是一句模糊错误覆盖**:400(签名/格式不对,
  `InvalidTicketCodeException`)和 409(签名是真的,但 booking 当前不能被
  核销)在视觉和标题上都不同——400 走红色 `CircleX`,标题"Invalid Ticket
  Code";409 走已经验证过的琥珀 warning 色调(`--chart-amber-*`,同
  `StatTile`/`ScheduleShowtimesNudge`),标题"Cannot Redeem This Ticket"。
  409 背后其实是两个不同的后端异常(`TicketAlreadyRedeemedException` /
  `BookingNotConfirmedException`),只在消息文本上不同,状态码和响应体里
  都没有更细的判别字段——没有尝试用字符串匹配拆出第四种更具体的标题(这个
  项目一贯反对从错误字符串里解析结构化数据,参考 Phase 5 座位冲突处理的
  先例),两条消息本身都已经是完整清楚的句子,原样展示。E2E 脚本验证过这
  两条消息文本确实不同("Ticket was already redeemed at ..." vs "Booking
  is EXPIRED, not CONFIRMED — cannot be redeemed"),不是巧合相同、也不是
  没测到区别就假设有区别。
- **表单提交后无论成功失败都清空输入框并重新聚焦**:这是一个"扫码/输入 →
  看结果 → 扫下一张"的连续核销流程(扫码枪当键盘输入 + 回车,或工作人员
  手动输入),不是需要反复修改重提交的表单;上一次尝试的结果留在下方结果
  面板里,不会因为清空输入框而丢失。
- 图标选用 `CircleX`/`CircleCheck`(不是 `XCircle`/`CheckCircle`)——这个
  项目当前的 lucide-react(1.28)两个名字都存在,但后者是 deprecated 别名
  (`x-circle.mjs` 内容就是 `export { default } from './circle-x.mjs'`),
  这个项目已有代码(`AnimatedFormBanner`)用的是前者,跟随既有约定,不是
  训练数据里更熟悉的旧名字。

**验证**:真实浏览器不可用(这次会话没有 Playwright 之类的工具),延续
"Admin 电影编辑页:排场次引导卡片"那次的验证方式——写了一个 Node 脚本
(`e2e-verify.mjs`),对着真实跑着的 Postgres/Redis/后端跑完整核销流程:
自建一笔测试订单(引用一部已有种子电影排一场自建的测试场次,不碰种子数据
本身)、直接改库把它标记 CONFIRMED(绕过 Stripe——这个任务验证的是核销
本身,不是 Phase 6 已经有自己集成测试覆盖的支付流程)拿到真实签发的票据
编码,验证核销成功、二次核销 409、伪造编码 400,以及"booking 签名是真的
但已不是 CONFIRMED"这第四种场景(409,消息文本和"已核销过"那条确实不同,
不是巧合相同)。跑完立刻清理并核对清理后计数为 0。`npm run build`/
`npm run lint` 干净。没有验证的:真实浏览器里的像素级渲染/交互。

### 影院/影厅管理 `/admin/cinemas`(2026-08-15)

补上此前只能靠 curl/Swagger 操作的分店/影厅浏览+创建能力(`POST /cinemas`、
`POST /cinemas/{id}/halls`,Phase 3 就已实现测过)对应的 admin 页面——同样
**零后端改动**。跟上面「电子票核销」是同一天交付的两个独立功能,分两次
提交,这里只记录影院管理自己的决定。

- **座位类型分布(STANDARD/COUPLE 各多少个)来自真实的
  `GET /halls/{id}/seats` 逐条统计,不是在前端按 totalRows/totalColumns
  重新实现一遍 `SeatLayoutGenerator` 的生成规则**——后者等于把后端的业务
  规则(最后一排是情侣座、每 2 列配对,奇数列时落单座退化成 STANDARD)复制
  一份到前端维护两份,一旦后端算法以后变了(哪怕只影响新建的影厅),这份
  前端复制会对老影厅的真实数据默默算出错误答案。影厅数量在 MVP 量级下
  (个位数)多几次这样的只读 GET 请求可以接受,跟这个项目一贯的"小规模
  场景下 N+1 可接受"取舍一致(如 `admin/showtimes/new` 的电影下拉、
  `admin/cinemas` 列表页本身统计每个分店的影厅数量)。
- **没有单一 cinema 的 GET 接口**(`CinemaController` 只有 `list()` 和
  按 cinema 分组的 `listHalls`),分店详情页 `/admin/cinemas/[id]` 因此
  拉全量 `listCinemas()` 后在前端按 id 过滤——跟 `admin/showtimes/new`
  按状态过滤电影下拉是同一个"拉全量、前端过滤"模式,不是这次新发明的。
- **`listCinemas`/`listHalls` 从 `admin-showtimes.ts` 挪到新建的
  `admin-cinemas.ts`**:这两个函数最初是为了给场次表单的影厅下拉框提供
  数据,顺手加在了 `admin-showtimes.ts` 里;这次影院管理成为一个完整功能
  后,它们真正的归属域是"cinemas"不是"showtimes",挪过去之后
  `admin/showtimes/new/page.tsx` 改成从新文件 import,行为完全不变。
- **警示文案如实反映当前系统能力,没有照抄 Phase 3 原文的"删除重建"说法**
  ——`CinemaController`/`HallController` 目前完全没有任何 delete 端点
  (grep 确认过,不是遗漏),所以创建影厅表单里的提示写的是"座位布局无法
  修改,且这个系统目前没有删除影厅的方式,创建前请仔细核对行列数",不是
  "删除重建"——后者在 Phase 3 文档里是概念性描述,不代表这个操作在 API
  层面真的存在;把一个不存在的能力写进 admin 提示文案,踩坑的还是 admin。

**验证**:同样用真实跑着的 Postgres/Redis/后端 + 自建自清理的数据(见上面
「电子票核销」的验证记录——两个功能的验证跑在同一个 `e2e-verify.mjs`
脚本里,一共 31 项断言全部通过):创建测试分店/影厅、核对它们出现在
`GET /cinemas`、`GET /cinemas/{id}/halls` 的返回里、核对
`GET /halls/{id}/seats` 逐条统计出的 STANDARD/COUPLE 数量正好等于 3 行
4 列布局按 `SeatLayoutGenerator` 规则应得的 8 + 2(验证的正是上面第一条
决定里提到的"不在前端重新实现这份规则、只信任真实返回数据"这个选择本身
是对的)。跑完立刻清理并核对清理后计数为 0。`npm run build`/`npm run
lint` 干净。没有验证的:真实浏览器里的像素级渲染/交互。

### Admin 订单查询/客服介入 `/admin/bookings`(2026-08-16)

补上一个真实缺口:`GET /api/v1/bookings/{id}` 和 `DELETE /api/v1/bookings/{id}`
从 Phase 5 起就支持 ADMIN 越权访问(客服场景),但**在这次之前完全没有任何
办法让 ADMIN 找到一个不知道 UUID 的订单**——`GET /api/v1/bookings`(订单
列表)是有意做成严格自范围的(见 F-2 那条决定:"没有 ADMIN 越权分支...
跨用户的经营数据属于 Phase 8 的 admin reports"),所以顺着这个接口找不到
别人的订单。这次新增的是**搜索能力本身**,不是查看/取消能力——后两者
Phase 5 就有,这次一行代码没改,原样复用。

**新增 API**:

- `GET /api/v1/admin/bookings?userEmail=&movieTitle=&status=&page=&size=`
  (仅 ADMIN,新增):三个搜索条件全部可选、可任意组合,全部为空则退化成
  分页浏览全部订单(和 `GET /api/v1/admin/users` 同一个形状)。
  `userEmail`/`movieTitle` 是大小写不敏感的模糊匹配(LIKE),`status` 是
  精确匹配。选这三个维度不是随便定的:`userEmail` 是最常见的客服场景
  ("这个邮箱订了什么"),`movieTitle` 覆盖"这场电影有哪些订单"这类反向
  查询,`status` 用来快速过滤"还没付款的"/"已经取消的"这类运营性问题
  ——三者互相独立、可以自由组合,不是各自开一个专属接口。
- 响应形状是新建的 `AdminBookingSearchResult`(`{userEmail, booking:
  BookingResponse}`),**不是往 `BookingResponse` 本身加一个 `userEmail`
  字段**:后者会让 `GET /bookings`/`GET /bookings/{id}` 这两个顾客也在用
  的接口平白多出一个自己用不上的字段,而且要在这两个从不需要
  `Booking.user` 这个 `FetchType.LAZY` 关联的读取路径上,为了这一个字段
  额外 fetch-join 或者容忍逐行 N+1——一个小的包装类型比在共享 DTO 上开一个
  只有一半调用方用得上的口子干净。
- **`GET /bookings/{id}` 和 `DELETE /bookings/{id}` 都没有改动,新的搜索
  页面直接复用**:两者的 ADMIN 越权分支从 Phase 5 起就存在
  (`BookingService.requireAccess`),取消订单后座位释放/懒惰过期/
  `BookingReleasedEvent` 这一整套逻辑不需要为 admin 场景重新实现一遍
  ——照抄 Phase 3/8 那次"改一个字段就要在两条代码路径上维护同一份逻辑"的
  反面教训,不给同一个业务规则开两个入口。**这也意味着 admin"代客取消"
  只能取消 `PENDING`(未支付)的订单**——`cancel()` 对非 `PENDING` 状态
  直接 409("Booking is X, cannot be cancelled"),这是订单状态机本来就有
  的边界,不是这次新加的限制;详情页因此在非 `PENDING` 时把取消按钮换成
  一句说明文字,而不是让 admin 点了才发现 409。
- **搜索结果列表需要展示 email,但详情页不需要新接口去补它**:
  `AdminBookingSearchResult` 只在**列表**层面存在,详情页
  (`/admin/bookings/[id]`)读的还是原始 `GET /bookings/{id}`(没有
  `userEmail` 字段)。链接从搜索结果跳转时把 email 通过 `?email=` 查询
  参数带过去,详情页只是显示这个参数(纯 UI 上下文,不参与任何鉴权判断)
  ——直接改地址栏删掉这个参数依然能看到订单本身的完整信息,只是暂时不
  知道是哪个顾客的,不是一个安全边界。这个"传一个上下文参数,不为了
  补一个字段就多开一个接口"的取舍,和 Admin 电影管理"排场次引导卡片"
  `?movieId=` 预选电影是同一个模式。

**踩到的一个坑,值得记录**:JPQL 原本写的是
`lower(u.email) like lower(concat('%', :userEmail, '%'))`,单元测试
(mock repository)全绿,但接入真实 Postgres 的集成测试一跑就 500——
`function lower(bytea) does not exist`。根因是 PostgreSQL 的 extended
query protocol 没法从"这个占位符只出现在 `lower(concat(...))` 里"这个
上下文推断出它的类型,`:userEmail is null` 这半句本身没有问题(替换 SQL
里能看到两个独立的 `?`,报错的是第二个)。修法是把"拼 `%...%` 通配符 +
转小写"这一步挪到 Java 里做(`AdminBookingService.likePattern`),JPQL
里只留 `lower(u.email) like :userEmailPattern`——参数在查询里只出现在
一个无歧义的位置(比较运算符右边),问题消失。**这不是靠猜出来的**:先
读了 `GlobalExceptionHandler` 兜底吞掉的真实异常堆栈才定位到这一行,
集成测试当时就是照这个真实报错改的断言,不是先猜结论再回头凑测试。

**测试**:`AdminBookingServiceTest`(Mockito,过滤参数原样透传、懒惰过期
批量处理、按各自 booking 分组座位、空结果短路,均照抄
`BookingServiceListTest` 已验证的模式)+ `AdminBookingFlowIntegrationTest`
(Testcontainers 真实 Postgres+Redis:401/403、按 email/电影名/状态搜索、
组合过滤、分页,每个用例用随机 email/电影名把断言范围收窄到自己创建的
数据,不假设整张表只有自己这一份数据——Postgres 容器在同一个测试类的
多个用例之间是共享的)。`mvn -o clean test` 全量跑过,174/174 全绿。

**验证**:这次涉及真实后端代码改动(不像上一批 admin 页面是纯前端),
而 8081 端口上跑着的后端进程是编译在这次改动**之前**的旧代码,又不确定
这个长期运行的实例是否还有人在用(参考 `DEVELOPMENT.md` 记录过的"长期
运行的后端进程不会自动感知新代码"这个环境陷阱)——所以没有重启它,而是
临时在 8082 端口另起一个用当前分支代码编译的实例,指向同一套
Postgres/Redis,跑完 Node 脚本(自建一部电影+一场场次+一个顾客+两笔
订单,按 email/电影名/状态搜索、代客取消一笔、确认座位释放、确认另一笔
不受影响,23 项断言全部通过并清理干净)之后立刻杀掉——`mvn spring-boot:
run` 会 fork 一个独立的 `java` 子进程,单纯停掉包着它的 shell 任务不会
连带杀死子进程,是额外按端口号找 PID 确认后才 kill 掉的,全程没有碰
8081 那个实例(验证前后分别 curl 确认过它仍然是 200)。

### Admin 支付异常(ORPHANED_SUCCESS)只读列表 `/admin/payments`(2026-08-16)

补上 Phase 8 销售报表留下的一个缺口:`pendingReconciliationAmount` 从一开始
就只是一个汇总金额(见 CLAUDE.md Phase 8),没有任何接口能看到具体是哪几条
`payments` 记录、金额多少、对应哪个用户/场次/订单——人工对账的第一步("找到
是哪几笔")在这之前其实做不到。这次只补这一步,不涉及退款或任何状态修改,
`ORPHANED_SUCCESS` 之后"要不要退款/怎么处理"仍然是运营层面的人工流程,
CLAUDE.md Phase 6 早就定过这个边界,这次没有动它。

**新增 API**:

- `GET /api/v1/admin/payments?status=&page=&size=`(仅 ADMIN,只读):
  `status` 可选,不传则分页浏览全部状态,和 `GET /api/v1/admin/users`
  "无过滤条件退化成全表分页浏览"是同一个形状。每条记录带完整的关联信息
  (用户邮箱、电影标题、影厅、场次时间、booking 状态)——全部从
  `payments` 表已有的外键关联(`booking_id` → `bookings.user_id`/
  `bookings.showtime_id` → `showtimes.movie_id`/`hall_id`)查出,没有新增
  任何字段或迁移。
- 响应形状是新建的 `AdminPaymentResponse`(内嵌一个 `BookingSummary`),
  不是往任何已有的顾客端 DTO 上加字段——这个项目目前没有独立的
  `PaymentResponse`(顾客端从来不需要直接看到 payment 记录,只通过
  booking 的 `ticketCode`/`redeemedAt` 间接感知支付结果),所以这次是
  从零设计,不存在"该不该复用"的选择,和上一批 `AdminBookingSearchResult`
  刻意不往共享的 `BookingResponse` 上加 `userEmail` 字段是同一个原则的
  自然延伸,不是巧合。
- `AdminPaymentMapper` 是普通的命令式 `@Component`,不是 MapStruct——
  和 `BookingMapper` 同样的理由:这次的映射要跨 5 层关联
  (`payment → booking → user`/`showtime → movie`/`hall`)组装成一个
  嵌套 record,读起来更像命令式装配而不是声明式字段映射,而且这个项目
  已经在 MapStruct 生成代码上踩过一次坑(见"Admin 用户管理"),显式方法体
  比隐式推断更值得信任这条经验这次继续适用。
- **`status` 参数是普通枚举相等比较,不是 `LIKE` 模糊匹配,所以完全不会
  撞上上一批发现的 Postgres 参数类型推断问题**(`lower(concat('%', ?,
  '%'))` 在 Postgres extended query protocol 下对纯粹只出现在字符串函数
  调用里的占位符推断不出类型)——那个坑的根源是"占位符只出现在
  `lower()`/`concat()` 内部",一个从比较运算符本身就能明确类型的枚举
  参数从一开始就不在受影响的范围内,这里不需要、也没有做任何特殊处理。

**信息架构决定:没有加进 `AdminHeader` 顶部导航**——这是一个偶尔查阅的
运维页面,不是 Movies/Bookings/Users 那种日常主工作流,7 个顶部导航项
已经不算少,再加一个不常用的入口只会稀释其余项的可发现性。改为:
`StatTile` 新增可选的 `href` 属性(不传时行为和之前完全一样,一个纯
展示的 `div`;传了则整个卡片变成 `<Link>`,末尾加一行"View details →"
提示可点击),仪表盘销售报表里"Pending Reconciliation"那张卡片链接到
`/admin/payments?status=ORPHANED_SUCCESS`——这个数字本身就是这个新页面
存在的理由,从数字本身点进明细,比在顶部导航里翻找一个不常用的入口更
符合这个页面实际会被用到的场景(先在报表上注意到这个数字不是 0,再想
知道具体是哪几笔)。页面本身仍然是一个完整的、可收藏/可直接访问的独立
URL(不是嵌进仪表盘页面的一个子区块),因为报表页已经塞了两张图表 +
两张数据表,再挤进一张可能有很多行的明细表会让那个页面本身变得拥挤。

**测试**:`AdminPaymentServiceTest`(Mockito,验证嵌套 `BookingSummary`
的五层组装、状态过滤透传)+ `AdminPaymentFlowIntegrationTest`
(Testcontainers Postgres+Redis:401/403、status 过滤、字段正确性)。
没有任何 API 路径能产生一条 `ORPHANED_SUCCESS` 记录(这个状态只在真实
Stripe webhook 处理内部触发,`PaymentFlowIntegrationTest` 已经完整覆盖
那条转换路径本身)——集成测试的 fixture 因此是直接 autowire
`BookingRepository`/`PaymentRepository` 构造,调用和真实 webhook 路径
同样的实体方法(`booking.markExpired()`、`payment.markOrphanedSuccess()`),
不重新驱动一遍完整的 Stripe checkout,这个"构造终态而不是重新走一遍
产生终态的流程"的取巧方式,和上一批 `ShowtimeAdminFlowIntegrationTest`
用 autowired repository 直接构造 CONFIRMED/PENDING booking 测
`bookedSeats` 计数是同一个已验证过的先例,不是这次新发明的。

**验证**:见下面「Admin 列表页搜索」一节的验证记录——这次两个独立功能
的验证跑在同一个 Node 脚本里,一共 21 项断言(含下面还没写进本节、
但同一批交付的列表页搜索功能的验证),全部通过,清理后核对计数为 0。
跑完立刻按端口号找 PID kill 掉 8082 临时实例(`mvn spring-boot:run`
fork 出的子 `java` 进程不会随包着它的 shell 任务一起停止,这一步和
上一批一样需要手动做),全程 8081 保持未受影响(验证前后分别 curl
确认过)。`npm run build`/`npm run lint` 干净。

### Backlog(MVP不做,时间充裕再加)
- 促销/会员积分
- 评价评分
- 邮件/短信通知
- **电影上架状态历史/审计追踪**(`movie_status_history` 表 + 独立
  `PATCH /movies/{id}/status` 接口,和通用 `PUT` 分离,避免历史记录被
  无关字段编辑一并污染)——设计方向已讨论确认,尚未实现,待单独立项。
  和上面三条不同:不是"价值低不做",只是还没排上;真要做的话在第 3 节
  另开一个 Phase/条目记录设计权衡,不要直接在这一行展开。

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
