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
- **怎么做到的:一个作用域内的 CSS 自定义属性覆盖,不是新写一套组件库**。
  `globals.css` 里新增一个 `.admin-light` 类,在其中把 `--background`、
  `--card`、`--border`、`--muted-foreground` 等和 `:root`(浅色)完全相同
  的值重新声明一遍,只有 `--primary`/`--primary-foreground`/`--ring`
  保留原来的暖金色(`#F4C430`/`#1a1506`)——按钮/焦点环这类"实心色块 + 深色
  文字"的场景不管外层是深是浅都够对比度,没有理由为了主题切换换掉品牌色。
  `app/admin/layout.tsx` 把这个类挂在包裹 `children` 的最外层 `<div>` 上;
  由于 `<html>` 始终带着 `.dark`(暗色主题是唯一主题,见本节开头),
  `.admin-light` 这个 class 是 `.dark` 的**后代**,而 CSS 自定义属性的
  继承规则是"离读取点最近的声明生效"——所以 `Card`/`Badge`/`Button`/
  `Input` 这些已有组件在 `.admin-light` 子树内直接读到浅色的值,不需要
  给它们各写一份 admin 专属变体,也不会影响 `.admin-light` 之外的任何页面
  (那些页面读到的仍然是 `.dark` 上声明的暗色值)。
- **卡片视觉规范**:细边框 + 低强度阴影(复用已有 `Card` 组件自带的
  `ring-1 ring-foreground/10`,外加 `shadow-sm` 类名),不是 `GlassCard`
  的模糊背景 + 跟随光标高光。
- **无障碍标准不因为换主题而降级**:44×44 最小点击热区、状态区分不能只靠
  颜色、`prefers-reduced-motion` 支持——这几条在 1.5 开头已经确立为全站
  默认标准,admin 页面同样遵守,具体应用见 Phase 8 前端补充里图表/筛选器/
  导出按钮各自的说明,这里不重复。

### 1.5.2 Admin 拥有完全独立的导航 shell,不复用顾客端 Navbar(2026-08-06)

Phase 8 交付时 `.admin-light` 只解决了**内容区**的主题反转(见 1.5.1),但顶部
导航栏当时仍然是全局挂在 `app/layout.tsx` 里的顾客端 `Navbar`(暗色 Liquid
Glass)——`/admin/dashboard` 实际渲染出来是"暗色导航栏 + 浅色内容区"硬拼在一
起,接缝很明显。这一条记录把导航栏也纳入 admin 的例外范围,是 1.5.1 那次
重构里遗漏、这次单独补上的部分,不是重新做一次决定。

- **为什么不能像 1.5.1 那样"在同一个 Navbar 组件里加 if 分支判断路由"**:
  两种设计语言(暗色玻璃拟态 vs admin 的浅色实底)共享一个组件意味着以后每次
  改 Navbar(配色、动效、布局)都要连带检查有没有波及从未设计给它跑的 admin
  分支,而且组件内部会同时堆两套互不相干的 JSX/样式,可读性和可维护性都会
  变差。拆成两个完全独立的组件(顾客端 `Navbar` 不变,新增
  `components/admin/admin-header.tsx`)让改动天然隔离——改 admin 头部不可能
  影响顾客端,反之亦然。
- **怎么做到 DOM 里完全不出现顾客端 Navbar,而不是靠 CSS 隐藏**:把顾客端的
  所有路由(首页、登录/注册、电影详情、选座、订单确认、个人中心)从
  `app/` 直接挪进一个新的路由组 `app/(customer)/`,顾客端专属的"chrome"
  (`Navbar` + `PageTransition` 路由转场)也从根 `app/layout.tsx` 下移一层,
  变成 `app/(customer)/layout.tsx` 里的内容;根 `layout.tsx` 收窄到只保留
  `<html>`/`<body>`/字体/`AuthProvider`(两边都需要的东西)。`app/admin/**`
  是 `app/` 下的另一个独立分支,和 `(customer)` 路由组是**兄弟关系,不是父子
  嵌套**——Next.js 按文件系统渲染组件树,兄弟分支不会互相出现在对方的渲染
  结果里,所以访问 `/admin/dashboard` 时 `(customer)/layout.tsx`(以及它里面
  的 `Navbar`)根本不在这次渲染路径上,不是"渲染了再被样式盖住"。路由组的
  括号命名不进入 URL,顾客端所有路径(`/`、`/login`、`/movies/:id` 等)不受
  影响。副作用:根 `app/loading.tsx`(首页的深色 `GlassSkeleton` 骨架屏,
  形状是 Hero 大图+海报网格)原来因为挂在根层级,理论上也会被 Next.js 当成
  `/admin/dashboard` 首次导航时的 Suspense fallback(内容和主题都对不上,
  是同一类"接缝"问题的另一种表现)——随着它一并挪进 `(customer)/loading.tsx`,
  这个理论上的错位 fallback 也顺带清掉了,不需要再额外为 admin 补一个骨架屏。
- **`AdminHeader`(`components/admin/admin-header.tsx`)的内容**:logo/wordmark
  (跳回 `/admin/dashboard`)、"返回前台"链接(跳回 `/`,离开 admin 语境)、
  当前用户名(`useAuth().user`,复用 `AuthProvider` 已有的会话状态,不重新
  发一次请求)、退出登录。退出登录按钮直接复用已有的 `LogoutButton` 组件
  (`components/auth/logout-button.tsx`)——它是一个通用的鉴权工具组件(读
  `useAuth().logout()`,自身不含 Navbar 或任何暗色主题相关样式,颜色/尺寸
  全部走 CSS 变量),在 `.admin-light` 子树内和顾客端 Navbar 里视觉表现自动
  各自正确,这不是"偷懒共享 Navbar 逻辑",和 `Button`/`Card` 这些跨两套主题
  复用的基础组件是同一类东西。
- **admin 唯一的入口仍然放在顾客端 Navbar 里**:一条不起眼的"管理后台"文字
  链接(`components/layout/navbar.tsx`,只在 `user?.role === "ADMIN"` 时渲染),
  这是 Phase 8 就有的设计,这次没有改动——顾客端 Navbar 负责"进入 admin 语境
  的入口",`AdminHeader` 负责"admin 语境内部的导航",两者分工明确,不重叠。
- **没有做成侧边栏**:目前 admin 只有一个 dashboard 页面,Backlog 里也没有排
  期更多 admin 子模块,侧边栏式的多级导航在这个阶段是过度设计——真的以后要
  加多个 admin 页面,`AdminHeader` 这一条顶部导航够用,不需要现在就为假设的
  未来铺一套导航框架。
- **无障碍复核结果:没有引入新的回归**。`AdminHeader` 里的"返回前台"链接
  显式给了 `h-11`(44px);复用的 `LogoutButton`(`size="sm"`,28px 高)和
  logo/wordmark 链接(28px 高,纯文字/图标 logo,不含点击态色块)维持的是
  1.5 节开头已经确立的既有例外(登出按钮、次要 nav 链接、logo 本身不强制
  44px),不是这次新引入的疏漏;拆分组件没有触碰筛选器预设按钮
  (`DateRangeFilter`)、图表、导出按钮的任何代码,原有的 `aria-pressed`、
  图标双重编码、`isAnimationActive={false}` 均原样保留,用 Playwright 实测
  确认预设按钮仍是 44×44、图表数量和交互未受影响。

**补充(2026-08-12,不重写以上内容,以上是当时的真实状态)**:"没有做成
侧边栏"那条的前提"目前 admin 只有一个 dashboard 页面"已经不成立——
`/admin/users`(2026-08-11)和 `/admin/movies`(2026-08-12)先后交付,
`AdminHeader` 也随之补上了 Dashboard/Movies/Users 三个顶部导航链接
(`usePathname` + `pathname.startsWith(href)` 判定当前项高亮)。仍然是顶部
导航,不是侧边栏——三个页面的量级还没有到需要多级导航的地步,这条结论
本身没有变,只是当时"只有一个页面"这个前提要更新。

### 1.5.3 登录/注册表单卡片保留 Card,不用 GlassCard(2026-08-08)

`LoginForm`/`RegisterForm` 的外层容器(`app/(customer)/login/page.tsx`、
`app/(customer)/register/page.tsx`)一直用的是普通 `Card`,不是全站默认的
`GlassCard`——这是继座位图(1.5 节,性能驱动)、admin 后台(1.5.1,内容形态/
可读性驱动)之后,第三个有意偏离"新卡片默认基于 `GlassCard`"这条规则的地方,
之前没有正式记录进设计系统文档,这次补上,不是这次新做的改动。

- **为什么例外:专注度驱动,不是性能或密度问题**。登录/注册是任务型页面——
  用户要做的唯一一件事是准确填对邮箱/密码然后提交,`GlassCard` 的核心卖点
  (跟随指针实时移动的高光,见 1.5 节开头)恰恰会在这种场景起反作用:眼睛
  在输入框之间移动、鼠标在表单区域内挪动去点击下一个字段时,高光跟着一起
  漂移,是在和"专心看清自己在填什么"这个任务抢注意力。电影卡片、场次卡片
  这类浏览型 UI 里,同样的高光是"引导视线、增加沉浸感"的加分项,但角色一换
  成"表单容器",这个反而是减分项。
- **不是"忘了套组件",是评估过后维持现状**:`Card` 本身自带的细边框 +
  `shadow-sm`(和 admin 卡片同一套基础视觉语言,见 1.5.1)已经足够把表单
  和背景区分开,不需要玻璃拟态的模糊背景 + 光斑去做这件事。
- **无障碍标准不受影响**:这两个页面的输入框/按钮高度(`Input`/`Button` 组件
  默认值已经是 `h-11`,见"审计后修复"一节)、表单校验的进出场动效
  (`AnimatedFieldError`/`AnimatedFormBanner`/`SubmitProgressBar`,均已支持
  `prefers-reduced-motion`)都和其余页面遵守同一套标准,只是外层容器换成
  `Card`,不影响这些已有的无障碍实现。

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
- Spring Boot 4.1 比预想中变动更大(模块化拆分)—— `springdoc-openapi` 要 3.0.x
  才兼容 Spring Framework 7;Flyway 的 Spring 胶水代码独立成
  `spring-boot-starter-flyway`;MockMvc 测试支持挪到
  `spring-boot-starter-webmvc-test`;默认 Jackson 版本变成 Jackson 3
  (`tools.jackson.*`,不是 `com.fasterxml.jackson.*`)。踩坑记录写进了对应模块的代码注释里。
  **2026-08-11 更新**:项目已从这里描述的 Spring Boot 4.1/Java 25 降级到
  Spring Boot 3.5.15/Java 21,这几条 Boot 4 专属的模块化细节不再适用于当前
  代码——原样保留在这里是真实的项目历史(起步阶段确实是这样),不做事后
  改写;完整的降级理由、影响范围、踩坑记录见第 3 节"技术栈降级"条目。

### Phase 1 — 用户管理(User Management)✅ 完成于 2026-08-01
CI 曾因为一个真实的 flaky test bug 红过一次(access token 缺 `jti`,
同一秒内签发的两个 token 会完全相同)——已修复并补了回归测试,细节见
`JwtService`/`JwtServiceTest`。

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

### Phase 6 事后修复:refresh cookie 从 `SameSite=Strict` 改成 `Lax`(2026-08-04)
本地手动测试完整支付链路时发现:选座 → 去支付 → Stripe 托管页完成付款 →
跳转回 `/bookings/{id}/confirmed` 之后,直接被弹回 `/login`,即使这个浏览器
session 本来是登录状态。根因是 `Strict` 和 `frontend/src/proxy.ts` 的组合:
Stripe 的 `success_url`/`cancel_url` 跳转回来是一次由 `checkout.stripe.com`
发起的跨站顶级导航(浏览器地址栏刚才还停在 Stripe 的域名上),即使目标 URL
是我们自己的站点,`SameSite=Strict` 的 cookie 在这一次"落地请求"上依然不会
被带上——而 `proxy.ts` 恰好就是靠这个请求里有没有 `refresh_token` cookie
来判断"要不要拦到 `/login`"(见 Phase 5"公开路由 vs 登录路由"那条原则的
落地实现),自然就被当成"未登录"处理。

修复是把 `AuthController.buildCookie` 的 `sameSite` 从 `Strict` 改成
`Lax`——`Lax` 恰好覆盖"跨站顶级导航 + GET"这一种请求(Stripe 的跳转正是
GET),但对跨站的 XHR/fetch、跨站 POST 仍然和 `Strict` 一样不带 cookie。
这个项目里所有真正的鉴权 API 调用都是前端对自己站点发起的同站 `fetch`
(`credentials: 'include'` 那一套,见 Phase 1 的 CORS 决定),从来不是"第三方
站点发起的跨站请求",所以 `Lax` 相对 `Strict` 并没有实际放宽任何这个 cookie
会遇到的 CSRF 面——真正需要 `Strict` 才能防住的场景(cookie 被跨站顶级导航
带到我们自己站点、且我们自己站点会仅凭这个 cookie 出现就执行敏感操作)在这
个项目里不存在,`proxy.ts` 本身也只是粗筛,不靠这个 cookie 的存在直接授权
任何操作(见 `proxy.ts` 注释)。第 1 节 Phase 1 的"Token 存储方式"决定已同步
改成 `Lax`,不是两处各说各话。

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
  数字,不是只断言 200**(`ReportFlowIntegrationTest`)。由于 Postgres 容器
  在同一个测试类的所有 `@Test` 方法之间是共享的(JUnit 默认的按类生命周期),
  每个测试方法都创建自己独有的电影(标题里带随机 UUID)、场次、订单,
  并且请求报表接口时**始终带上这个测试专属的 `movieId` 过滤**——这样几个
  测试方法即使在同一个共享数据库里跑,断言的聚合数字也不会被彼此的 fixture
  污染,不依赖测试执行顺序或者数据库重置,和 `TicketFlowIntegrationTest`
  用随机标题隔离测试数据是同一个思路。聚合计算本身最容易出 off-by-one 的
  两处——日期范围转换(`ReportDateRange`)、booked/total 占比计算
  (`OccupancyMath`)——额外拆成不依赖 Testcontainers 的纯单元测试覆盖边界值。

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
  {false}`)**——起初没关,手动用 Playwright 截图验证时发现一个真实的
  健壮性问题:`ResponsiveContainer` 基于 `ResizeObserver` 感知容器尺寸,
  容器尺寸变化(哪怕只是被截图工具触发的一次全页面重排)会让 recharts 把
  柱状图的入场动画从头重放一次(高度从 0 开始重新长出来),如果恰好在这个
  重放窗口内取一次快照/首屏渲染,看到的会是空的图表区域——这不是"截图
  工具的假象",是同一套机制在用户真实缩放浏览器窗口、或者字体加载导致
  布局抖动时同样会触发的问题。关掉动画之后柱子直接以最终高度渲染,不存在
  这个从 0 开始的中间态,顺带也满足了"支持 `prefers-reduced-motion`"这条
  全站默认标准——数据密集的管理后台本来就不需要图表柱子"长出来"这种
  装饰性动效。
- **筛选器:预设时间范围(今日/近7天/近30天)+ 自定义起止日期,切换时用
  骨架屏过渡,不是保留上一次渲染结果 + 半透明**。这里有意偏离了 dataviz
  方法论里"重新拉取数据时保持上一帧、不用骨架屏"的一般建议——具体到这个
  场景,"上一帧"对应的是*旧筛选条件*下的数字,如果筛选器已经显示"近30天"
  但图表还画着"近7天"的旧数据,读者看到的标签和数字对不上,比空白骨架屏
  更容易造成误判。是否要骨架屏由 `sales.from/to/granularity` 是否等于当前
  筛选器的值直接推导(`salesLoading`/`occupancyLoading`,组件里没有一个单独
  维护的 `loading` boolean),这样"正在加载"这个状态不可能和实际筛选条件
  脱节。之所以不用一个手动 `setLoading(true)` 标志位:React 19 的
  `react-hooks/set-state-in-effect` lint 规则不允许在 `useEffect` 里同步
  调用 setState(那样会触发一次多余的级联渲染),只允许在 `.then()`/
  `.catch()` 回调(也就是真正异步完成之后)里调用——这个约束反而引导出了
  一个更不容易出 bug 的方案:与其手动维护一个可能和数据脱节的 loading 标志,
  不如直接从"手上的数据是不是对应当前筛选条件"这个已有信息推导出来。
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
不是新功能,是一次针对既有代码的技术债审计,发现三类此前没系统性处理过的
问题,逐一修掉:

- **`Input` 组件默认高度从 `h-8` 提到 `h-11`**(`components/ui/input.tsx`)——
  照抄 1.5 节记录过的那次 `Button` 修法(`default`/`lg` 变体从 `h-8`/`h-9`
  统一提到 `h-11`):不是每个用到 `Input` 的地方各自记得加 `h-11` 覆盖,而是
  组件默认值本身达标,新写一个不带高度覆盖的 `<Input>` 也会自动符合 44×44。
  `components/admin/date-range-filter.tsx` 里两处因此变得多余的
  `className="h-11"` 已经删掉(改前后视觉高度一致,只是覆盖变成了默认值)。
- **`prefers-reduced-motion` 补全到此前遗漏的组件**:1.5 节记录的
  `GlassCard`/`GlassSkeleton`/`HeroCarousel` 三处只是当时做到位的部分,审计
  发现 `components/motion/` 下另外四个组件(`page-transition.tsx`、
  `submit-progress-bar.tsx`、`animated-form-banner.tsx`、
  `animated-field-error.tsx`)和 `seat-map.tsx` 的 `SeatButton`
  `whileTap` 完全没做判断——都补上了,统一沿用"把 duration/偏移量清零"这个
  已有的降级写法(`page-transition.tsx`/`HeroCarousel` 一直是这么做的),
  没有引入 `initial={false}` 这套第二种写法。`SubmitProgressBar` 单独
  值得一提:它内层那条无限循环滑动的进度条(`repeat: Infinity`)是真正需要
  处理的部分——循环动效比一次性过渡更该被这个设置关掉——reduced motion 时
  换成一条静态满宽度进度条,不是简单把循环动画时长缩短。`SeatButton` 的
  `useReducedMotion()` 判断特意提到父组件 `SeatMap` 里只调用一次、再作为
  prop 往下传,不是每个座位按钮各自订阅一次(一个影厅上百个座位按钮,原因
  和"座位按钮不各自套 `GlassCard`"是同一条密度性能考量,见 1.5 节)。
  顺带把四个文件里各自复制的 `EASE_APPLE` 缓动曲线数组提到
  `lib/motion.ts` 统一导出;`profile/page.tsx`、
  `bookings/[id]/confirmed/page.tsx`、`register-form.tsx`(注册成功态)
  三处几乎相同的 opacity+y 入场动画(此前都没做 reduced-motion 判断)合并成
  一个共享的 `components/motion/fade-in.tsx`(`<FadeIn>`),内置判断,三处
  改成直接调用它,不再各自维护一份。
- **新增 `app/(customer)/error.tsx` + `not-found.tsx`(暗色,复用
  `GlassCard`)、`app/admin/error.tsx` + `not-found.tsx`(浅色,复用
  `Card`)**:此前完全没有,`movies/[id]`/`showtimes/[id]` 页面已有的
  `notFound()` 调用、以及任何未捕获异常,实际渲染出来的都是 Next.js 默认的
  无样式兜底页。新增的这四个文件都不需要重新声明 `.admin-light`/
  `AdminHeader`/顾客端 `Navbar`——Next.js 的 `error.tsx`/`not-found.tsx`
  语义是"包裹同一 segment 的页面内容,但不包裹同一 segment 自己的
  `layout.tsx`",也就是说 `app/admin/layout.tsx`(以及
  `app/(customer)/layout.tsx`)在这两个文件渲染时依然正常渲染在外层——
  实测确认过:临时在 `AdminDashboardPage` 顶部塞一行 `throw new Error(...)`
  触发 `app/admin/error.tsx`,截图看到 `AdminHeader` 原样还在,内容区换成了
  新写的浅色错误卡片,验证完立刻还原(`git diff` 确认无残留)。**已知的
  局限**:`app/admin/not-found.tsx` 目前实际上触发不到——admin 现在只有
  `/admin/dashboard` 一个静态路由,没有任何动态 segment 或
  `notFound()` 调用会命中它,真访问一个不存在的 `/admin/xxx` 路径时
  Next.js 路由层面就已经无法匹配进 `app/admin/` 这棵子树,会掉到内置的
  全局兜底页,不是这个新文件(已用 Playwright 实测确认,不是猜测)。这个
  文件目前是为"以后 admin 加了动态路由/`notFound()` 调用"预先埋好的,不是
  当前就完整生效的功能——如果以后加了 admin 动态页面,记得给对应的
  "找不到"分支显式调用 `notFound()`,而不是假设这个文件会自动兜底。

### 种子数据的海报图来源(2026-08-08)
`movies` 表当前 4 行里,只有 **`Dune Part Three` 一部**配了真实海报——`Verify
Fix`/`Verify Movie`/`E2E Test Movie` 三部是历次手动测试/E2E 跑出来的测试
fixture(标题本身就说明了),故意没有动,继续显示占位图。判断依据、置信度
分级方法、以及"整批数据是不是普遍虚构"的排查过程不重复记录在这里——这是
一次性的数据操作,不是需要长期维护的架构决定。

- **图片来源:OMDb API**(`https://www.omdbapi.com/`,`t=` 精确标题查询,
  key 存在 `.env` 的 `OMDB_API_KEY`,不写死在代码里)。`Dune Part Three`
  精确匹配到 OMDb 的 `Dune: Part Three`(2026,导演 Denis Villeneuve,
  imdbID `tt31378509`)——这个库的 `movies` 表**没有 release_date/year
  字段**,没法按"年份相差不超过 1 年"这条对年份做二次校验,置信度判断
  只能基于标题匹配本身:`t=` 精确标题查询只返回了这一个无歧义结果,不是
  从多个候选里挑的,这一点撑得住"高置信度"的判断。
- **直接热链 OMDb 返回的 Amazon 图片 URL,没有下载后经 `StorageService`
  重新托管**:`poster_url`/`backdrop_url` 两个字段被设成了同一个
  `m.media-amazon.com` 图片地址(OMDb 免费层没有单独的 backdrop 大图,
  复用同一张——`resolveMediaUrl()`(`lib/api/client.ts`)本来就支持
  `http(s)://` 开头的绝对地址原样透传,不需要额外改动)。
- **`next.config.ts` 需要新增一条 `images.remotePatterns`**:
  `next/image` 对外部图片域名有白名单限制,之前只放行了后端自己的 origin
  (posters 一直是后端相对路径),这是第一次出现真正的外部图片域名
  ——加了 `m.media-amazon.com` 这条(所有 OMDb `Poster` 字段返回的图片都
  是这个域名,不是这一部电影专属的),改完需要重启 `next dev` 才生效
  (`next.config.ts` 只在启动时读取,不参与热更新)。
- **Flyway migration(`V14__update_movie_poster_urls.sql`)是数据补丁,
  不是新 schema**:这几部电影本身不是通过 Flyway 灌进去的(灌库脚本只到
  `V4__seed_genres.sql` 为止,后面这 4 行 `movies` 记录是运行中的应用
  自己插入的——Admin API 或者手动/E2E 测试),但用一条新 migration 去
  `UPDATE` 依然是对的做法:这样如果本地 Postgres 卷被清掉重建,
  `flyway migrate` 会自动把这条图片补丁重新应用一遍,不用记得手动补一次。
  `WHERE` 按 `title` 匹配,不是 `id`——这个环境里的 `id` 是启动时生成的
  UUID,换一个环境不会存在,按标题匹配在任何没有这行数据的环境上是安全的
  空操作(0 行受影响,不会报错)。
- **OMDb 的使用条款,读原文确认过、不是凭印象写的**:官网首页标注
  "All content licensed under CC BY-NC 4.0"(要求署名 + 禁止商用);
  `legal.htm` 的条款里更直接:4.2.5 条"You may not build a business
  utilizing the Contributions, whether or not for profit",第 10 条
  "The Site is made available to you only for your personal use, and
  you may not use the Site or any Contributions or Materials in
  connection with any commercial endeavors"——**这个限制比"署名"严格
  得多,是"个人/非商用"的硬约束,不是加个credit就能商用**。CineVerse 本身
  是求职作品集、非商业项目(见第 0 节),现状完全符合这条限制;但如果这个
  项目以后真的要往商业化方向走,OMDb 这个数据源必须换掉(换成 TMDB 的
  商用授权层级,或者付费的海报图库),不能继续沿用现在这几张热链图。
  署名要求(README.md 已加)是这条更严格的非商用限制之外顺带满足的,不是
  唯一要处理的合规点。

### 种子数据扩充:删除测试 fixture + 补充 10 部真实电影(2026-08-08)
上一条记录的三部测试 fixture(`Verify Fix`/`Verify Movie`/`E2E Test Movie`)
这次正式删除了,同时把种子数据从"1 部真实电影"扩到"11 部",覆盖科幻/剧情/
动作/动画/喜剧/恐怖/犯罪/奇幻/传记等不同类型,给首页 Hero 轮播和"正在热映"
分区网格足够的视觉区分度(之前 3/4 的卡片都是"暂无海报"占位图)。

- **`V15__delete_test_seed_movies.sql`**:按 FK 依赖顺序显式删——
  `payments`(`booking_id` RESTRICT)→ `bookings`(`showtime_id` RESTRICT,
  `booking_seats` 自动 CASCADE)→ `showtimes`(`movie_id` RESTRICT)→
  `movies`(`movie_genres` 自动 CASCADE)。这三部测试电影里有两部
  (`Verify Movie`/`E2E Test Movie`)在历次手动测试里已经产生了真实的
  showtime/booking/payment 记录(选座锁座、Stripe 支付成功/失败都测过),
  不是空表,直接 `DELETE FROM movies` 会先撞上 RESTRICT 报错——这条
  migration 按依赖链从下游往上游删,不是绕过约束。全部按 `title` 匹配,
  和 `V14` 同样的理由(`id` 是运行时生成的,按标题匹配在没这行数据的
  环境上是安全空操作)。执行后核对过:没有孤儿 `showtimes`/`bookings`/
  `payments` 残留。
- **`V16__seed_diverse_movies.sql`**:10 部电影,判断流程和置信度规则
  跟 `V14` 完全一致(OMDb `t=` 精确标题查询,只要返回单个无歧义结果就是
  高置信度)——这一批全部一次查中,没有产生需要人工确认的候选清单。
  选片时刻意覆盖不同年代(1994~2023)和类型,理由见文件顶部注释。字段
  来源分工:`description`/`content_rating`/`user_rating`/
  `duration_minutes` 直接取自 OMDb 的 `Plot`/`Rated`/`imdbRating`/
  `Runtime`;`tagline` 是原创的一句话文案,不是照搬真实营销文案——OMDb
  本身不提供 tagline 字段,而且没必要为了填这一列去逐字复用片方的广告语。
  **genre 映射不是 OMDb 标签的直译**:这个项目的 `genres` 表是固定的 15
  个值(`V4__seed_genres.sql`,没有管理 API),OMDb 返回的标签有时候超出
  这个范围——`Oppenheimer` 在 OMDb 上是 "Biography, Drama, History",
  但这个库没有 Biography/History,映射成了 `Drama` + `War`(电影本身是
  二战时期曼哈顿计划题材,`War` 是现有 15 个值里最贴切的替代,不是随便挑
  的)。`movies`/`movie_genres` 都用显式硬编码的 UUID 字面量(不是
  `gen_random_uuid()`),沿用 `V6__seed_cinema_halls_seats.sql` 的既有
  惯例,方便同一个 migration 里把电影和它的 genre 关联串起来。
- **海报图片来源、热链方式、`next.config.ts` 白名单、OMDb 条款限制**这些
  跟 `V14` 完全一样,不重复记录,见上一条。

### `poster_url`/`backdrop_url` 从此是两个不同数据源(2026-08-08)
`V14`/`V16` 把 `backdrop_url` 设成了跟 `poster_url` 一样的 OMDb 海报图—— 
当时是有意为之的权宜之计(见 `V14` 那条记录:"OMDb 免费层没有单独的
backdrop 大图,复用同一张"),但实际效果不好:首页 Hero 是横版
70vh 的沉浸式区域,拿一张竖版海报硬撑,不只是"构图不对"——OMDb 那批
海报图本身是给"卡片缩略图"场景校准的分辨率(多数只有 380px 宽),被
`object-cover` 拉伸铺满一个上千像素宽的横版区域,视觉上明显发虚,肉眼
能看出来是放大模糊,不是设计意图里的柔焦效果。

- **换成 TMDB(`https://api.themoviedb.org`)专门取 `backdrop_path`**:
  跟 OMDb 不同,TMDB 的 `backdrop_path` 字段就是为"横版沉浸式背景图"这个
  场景设计的(真正的剧照,不是海报),`https://image.tmdb.org/t/p/w1280/`
  前缀能拿到 1280px 宽的版本——用对口的数据源解决对口的问题,不是让
  OMDb 继续做它本来没设计支持的事。**`poster_url` 完全没动**,还是
  `V14`/`V16` 那次 OMDb 验证过的数据,这次只碰 `backdrop_url` 一个字段,
  改动范围刻意收窄。
- **匹配方式和置信度规则原样沿用**:TMDB 的 `/search/movie` 端点用
  `query=`(标题)+`year=`(年份)查询,置信度判断标准比 OMDb 那次更严格
  一点——不只是"标题匹配",还要求 `release_date` 的年份也对得上(TMDB
  不像 OMDb 的 `t=` 那样能返回单一无歧义结果,`/search/movie` 一律返回
  数组,所以多加了年份这层校验);这一轮 11 部电影全部一次命中,标题、
  年份都对得上,即使个别查询返回了多个候选(比如 "Interstellar" 还搜出
  一部不相关的同名纪录片),排第一的结果和其余候选的热度(`popularity`
  字段)差距都很大,没有真正意义上的歧义,所以这次也没有产生需要人工
  确认的清单。
- **`V17__update_movie_backdrop_urls.sql` 只写 `UPDATE ... SET
  backdrop_url = ...`,不碰 `poster_url`/其余字段**——延续 `V14` 起的
  按 `title` 匹配惯例(不依赖运行时生成的 `id`)。`next.config.ts` 加了
  `image.tmdb.org` 这条 remotePattern,跟当初给 `m.media-amazon.com`
  开白名单是同一件事的重复,同样需要重启 `next dev` 才生效。
- **顺手复核了 `HeroCarousel` 的渐变蒙层,结论是不用改**:换真实背景图
  之前怀疑"发虚"是蒙层盖太厚导致的,复核后发现这个组件本来就**没有任何
  CSS 模糊滤镜**——唯一的处理是一层渐变(`from-background
  via-background/40 to-background/10`,底部深、顶部浅,配合文字卡片
  常驻在底部这个布局),之前的发虚完全是"低分辨率海报被拉伸"这一个原因
  造成的,换上 TMDB 的 1280px 剧照后问题就没了,这层渐变本身的透明度
  参数没有改动的必要——换完图之后实测过(截图对比 4 个不同的 Hero 轮播
  画面),渐变现在读起来刚好:文字卡片区域够暗、可读性没问题,同时图片
  细节在上半部分清晰可见,没有被过度遮盖。
- **TMDB 的使用条款,同样读了原文,不是凭印象**:免费层**明确禁止任何
  商业用途**("does not permit any commercial use of TMDB, the TMDB
  APIs, or TMDB Content"),且要求可见的署名——必须展示 TMDB 官方 logo,
  加一句原文规定的文案("This product uses the TMDB API but is not
  endorsed or certified by TMDB"),logo 的视觉权重还必须"低于应用自己的
  品牌标识"。这比 OMDb 的条款更具体、约束力更强(OMDb 没有规定必须放
  logo,TMDB 明确要求)。**这次没有在界面上加这个 TMDB 署名**——这次改动
  范围只到"换数据源、验证效果",没有被要求做 UI 层的合规展示;如果这个
  项目要严格满足 TMDB 的条款,还差一步:在页面上(比如 footer)加显示
  TMDB logo + 那句指定文案,这个待办目前没有对应的代码。CineVerse 本身
  非商用这一点仍然成立(跟 OMDb 那条记录一样),但"非商用"不能替代
  "署名"这个独立的条款要求,两者不是一回事。

### Hero 背景图:裁切位置修正 + 一次排除法排查(2026-08-08)
换上 TMDB 真实剧照之后(见上一条),又发现两个独立的观感问题——这两个
问题分开处理,没有混在一起改,原因和排查过程记录如下。

- **裁切位置:`object-position` 从默认居中改成 `50% 30%`**
  (`hero-carousel.tsx`)。根因是 Hero 容器(`h-[70vh]`,1440×900 视口下
  约 1440×630,宽高比≈2.29)比 TMDB 的 16:9 剧照(1280×720,宽高比
  1.78)更"扁宽"——`object-cover` 默认居中裁切会导致上下各裁掉一截
  (算下来大约各 90px),很多剧照的关键构图(天空、远景)正好在这一截
  里被切掉,角色头部因此贴着画面顶部边缘。**不是"poster 缩略图挡住了
  backdrop"**——排查时先确认了一个前提:这个组件从来没有渲染过 poster
  缩略图,`<section>` 里全程只有 backdrop 这一张图 + 一个纯文字的
  `GlassCard`,没有第二张图片可以挪走或调整层级。往上偏 30% 的具体数值
  是拿这个 Hero 轮播实际会展示的全部 5 部电影(**不是全部 11 部**——
  首页只把 `nowPlaying.content.slice(0, 5)` 传给 `HeroCarousel`,
  另外 6 部只在下面"正在热映"网格里以 poster 形式出现,从来不会作为
  Hero 背景,所以对比范围以这 5 部为准)分别截图对比过,5 部里没有一部
  因为往上偏而丢失关键内容,大多数(3/5)观感明显更好,不存在"顾此
  失彼"。
- **清晰度:排除法排查,不是加大图片尺寸糊弄过去**。怀疑过是不是
  `<Image fill>` 没设 `sizes` 导致 Next 保守地选了偏小的 srcset 候选
  ——**这个假设被证伪了**:直接读代码确认 `sizes="100vw"` 一直都在
  (不是这次新加的),而且实测过它确实在正常工作——用不同视口宽度触发
  请求,桌面端(1440 css px)选中的是 `w=1920` 候选,移动端(390 css
  px、DPR 3)选中的是 `w=1200` 候选,这正是 `sizes` 该起的作用(挑一个
  跟渲染尺寸匹配的候选,不多下载)。**真正的瓶颈是 Next.js 图片优化
  管线在生成 WebP 的这一步**,发生在"选中哪个候选宽度"这个环节**之后
  **:抓了浏览器实际收到的响应字节(不是读 URL 参数猜),桌面端请求
  `w=1920`(源头封顶 1280)最终解码出来是 960×540,移动端请求
  `w=1200` 最终解码出来是 390×219——移动端这组数字额外验证了一件事:
  `390` 恰好等于渲染框的 **CSS 像素宽度**,也就是说 DPR=3 的高分屏
  上,这张图完全没有按视网膜屏幕的需求加倍取样,缺口比桌面端(960 vs
  理论需要的 1440)更明显。这一层问题出在 Sharp/Next 的 WebP 转码路径
  里,不是 `sizes` 配置的问题,`sizes` 那道题已经排除掉了,不需要再
  在这上面花时间——**这个 WebP 管线问题目前还没有修**,是排查完
  `sizes` 假设之后主动叫停的下一步,留给专门的排查任务,不要顺手在
  这次改动里蒙混过关。

### Hero 修复没有覆盖到电影详情页,提成共享组件补上(2026-08-08)
上一条记录的 `object-position: 50% 30%` 只改了 `hero-carousel.tsx`,
电影详情页(`movies/[id]/page.tsx`)顶部的 backdrop 横幅是完全独立的
第二份实现——不是共享同一个组件,是两处各自维护一份几乎一样的
`<Image fill sizes="100vw" className="object-cover">` + 渐变蒙层
`<div>`,所以上次的修复没有传播过去,不是这次改动引入的新 bug,是
同一个问题一直存在于两个地方,只是先在 Hero 发现、先在 Hero 修的。
用截图验证过电影详情页确实是同款"居中裁切"症状(角色头顶贴着画面
上边缘),换上 `50% 30%` 之后跟 Hero 一样有改善(截图对比过 7 部电影:
5 部是 Hero 轮播也会出现的,视觉表现现在和 Hero 一致;另外 2 部
Hero 轮播里从来不会出现——`The Grand Budapest Hotel`、`Oppenheimer`
——单独确认了修复对 Hero 覆盖不到的电影同样生效)。`sizes="100vw"`
这边本来就有,不是这次新加的。

- **提成了 `components/movies/movie-backdrop.tsx`(`<MovieBackdrop>`)**
  ——只抽了会"两边同步改、容易漏改一边"的那一层(`Image` 的
  `fill`/`sizes`/`object-cover`/`object-position` 组合,紧跟着的渐变
  蒙层 `<div>`),没有把外层的尺寸容器(`h-[70vh]` vs `h-[50vh]`)和
  周围的卡片布局也塞进去——Hero 那边这一层还包在一个
  `AnimatePresence`/`motion.div` 的轮播切换动画里,电影详情页是一次性
  静态渲染,两边需要的外层结构本来就不一样,硬塞进同一个组件只会换来
  一堆条件分支,不划算。渐变蒙层的透明度(Hero 是 `via-background/40`,
  详情页是 `via-background/50`)也保持不统一——这次任务只要求裁切/
  清晰度参数一致,没有人要求这两处的视觉调性也变成同一个值,所以
  `gradientClassName` 留成了调用方各自传入的 prop,不是被这次提取
  强行拉齐的另一个变量。
- **顺手修了一个真正的潜在 bug,不是这次任务原本要求的**:两处原来
  用的都是 `preload` 这个 prop——`next/image` 真正的 API 是
  `priority`,不是 `preload`,`preload` 会被 React 当成未知属性透传到
  `<img>` 标签上,浏览器不认识这个 HTML 属性,直接忽略,等于两边的
  首屏图片一直都没有真正拿到"优先加载"的提示。既然这次要重写这几行
  改成调用共享组件,顺手把 prop 名字改成了真正生效的
  `priority`——影响范围仅限于加载优先级这一个维度,不影响本次任务
  验证过的裁切/清晰度效果。
- `npm run build`/`npm run lint` 跑过,干净。

### 设计债批次修复(2026-08-08,不算新 Phase)
排好但一直没做的四项技术债,一次性清掉,不涉及新的视觉方向判断——具体
决定见 1.5.3 节(登录/注册卡片例外)以及下面各条:

- **登录/注册表单卡片保留 `Card`、不用 `GlassCard` 的设计决定补充记录进
  1.5.3 节**——这是既有事实的补记,不是这次新改的代码,见该节。
- **首页 Hero 去卡片化**(`hero-carousel.tsx`):去掉包裹文字的 `GlassCard`
  容器,标题从 `text-3xl sm:text-4xl` 提到 `text-4xl sm:text-5xl
  lg:text-6xl`,直接叠在背景图上,靠强化过的渐变(`via-background/40` →
  `/75`)+ 每个文字元素各自的 `text-shadow` 保证可读性,不再靠卡片实底
  背景。截图对比过桌面端和 390px 移动端,效果符合 Apple TV/apple.com 式
  的编辑排版观感。
  - **移动端截图发现了一个自己引入的真实 bug,已修**:标题放大后在 390px
    宽度下会换行到两行,与垂直居中定位的左右箭头导航按钮
    (`CarouselArrow`,`top-1/2`)在视觉上重叠。第一次尝试用
    `top-[38%]`(移动端)/`sm:top-1/2`(sm+)这种任意值 + 断点组合去把箭头
    往上挪,结果箭头位置完全不对(实测 `getComputedStyle` 显示
    `top: 590.797px`,即 100% 而不是 38%)——查编译后的 CSS bundle 发现
    Turbopack dev 的增量编译根本没有为 `top-[38%]` 生成规则(bundle 里
    完全搜不到 `38%`,只有该文件里本来就存在的 `top-1\/2`),这是一次
    真实的 Turbopack HMR 增量扫描漏判,不是任意值语法写错。放弃了这个
    方向,改用桌面端已经验证过、不依赖任意值的方案:箭头改成
    `hidden sm:flex`(移动端直接不渲染箭头)——下方的圆点指示器本来就是
    各自独立可点击的 44×44 目标(`aria-label="第 N 部影片"`),移动端并
    不会因此失去手动切换电影的入口,这也更贴近 Apple 自家轮播在小屏上
    "只留圆点、不留箭头"的常见做法,不是退而求其次的将就。
- **`/profile` 页面垂直居中**:三个分支(loading 骨架屏、错误态、正常内容)
  的外层 `<section>` 统一从 `mx-auto max-w-2xl px-6 py-16` 改成
  `mx-auto flex min-h-[calc(100vh-4rem)] max-w-2xl flex-col justify-center
  px-6 py-16`——和 `/login`、`/register` 页面已经在用的居中模式完全一致
  (`4rem` 对应 sticky Navbar 的 `h-16`),之前是顶部对齐,内容量少时头重
  脚轻。
- **移动端导航收纳**(A4/A5):
  - `Navbar`(`components/layout/navbar.tsx`):新增一个 `md:hidden` 的汉堡
    按钮 + 下拉面板(简单的 disclosure 模式,不是模态对话框,没有引入新的
    UI 基础组件),面板内包含"正在热映"/"即将上映"/"影院"三个链接,以及
    登录态相关的操作(登录/注册,或管理后台/用户名/退出登录)。之前这些
    在窄屏下的表现是:导航链接 `hidden md:flex` 直接消失,管理后台/用户名
    链接是单独的 `hidden sm:inline`(和导航链接不同的断点),两条断点线
    不一致,而且完全没有替代入口——现在统一成一个 `md`(768px)断点:
    达到即显示完整桌面导航,低于则只显示汉堡按钮,所有内容进面板,不再
    有两条不同步的断点。面板关闭的时机用 React 官方"根据 prop 变化调整
    state"的模式(`if (pathname !== prevPathname) { setPrevPathname(...);
    setMobileOpen(false); }`,渲染期间同步调用而不是塞进
    `useEffect`)——最初写成 `useEffect(() => setMobileOpen(false),
    [pathname])`,被 `react-hooks/set-state-in-effect` 规则拦下(effect
    body 内同步 setState 会触发级联渲染,这条规则 Phase 8 admin dashboard
    那次已经踩过一次,见上面对应记录),改成这个模式后 lint 通过。
  - `AdminHeader`(`components/admin/admin-header.tsx`):logo 旁的"管理后台"
    副标题文字加上 `hidden sm:inline`(之前无条件常驻);"返回前台"链接
    的文字包一层 `hidden sm:inline`,只留图标(补了一个 `aria-label`
    "返回前台",因为窄屏下这个链接变成纯图标,需要一个无障碍名称)。用户
    全名本来就是 `hidden sm:inline`,未改动。窄屏截图确认修复前是"管理\n
    后台"和"返回前台"各自被挤成两行的乱版式,修复后是干净的单行头部。
- **admin `StatTile` 警示色统一**(A6):`tone="warning"` 分支原来直接写
  Tailwind 内置的 `amber-400/60`(边框)/`amber-50`(背景)/`amber-600`
  (图标),和已经用 `validate_palette.js` 校验过的 `--chart-amber`
  (`#B8860B`,见上面 Phase 8 关于图表色的记录)不是同一套色相,纯属巧合
  凑近似色。`globals.css` 的 `.admin-light` 块里新增
  `--chart-amber-border`/`--chart-amber-surface`,用 `color-mix(in oklch,
  var(--chart-amber) …, …)` 从这同一个已验证色值派生边框/背景两个色调
  (`button.tsx` 的 `secondary` variant 已经在用同样的 `color-mix(in
  oklch, …)` 写法,不是这次新引入的技术)。边框/背景是对同一个已验证
  色值的透明度/色调派生,不是独立选色,所以不需要单独再跑一次
  `validate_palette.js`——真正涉及 WCAG 图形对比度的只有
  `AlertTriangle` 图标本身,它直接用 `--chart-amber`,复用的正是已经
  验证过 ">=3:1 против 白色 chart 表面" 的那个结论。
- **验证方式**:四项视觉改动(Hero、profile、两个导航栏)都用 Playwright
  截图做了修复前后对比,不是只改代码不验证——`before` 截图来自主工作区
  当时仍在运行的开发服务器(未受这批改动影响,天然是修复前的基准),
  `after` 截图来自这次改动所在的 worktree 里单独起的开发服务器;因为
  这两个来源各自需要登录态(profile、admin 页面),而后端 CORS
  `allowedOrigins` 默认只放行 `http://localhost:3000`(见 Phase 1),
  额外临时起了一个指向同一个 Postgres/Redis、只是 `CORS_ALLOWED_ORIGINS`
  换成新端口的第二个后端实例用于截图验证,验证完随手关闭,不是永久新增
  的运行方式。`npm run build`/`npm run lint` 全部跑过,`mvn compile`
  (离线模式,复用本地 Maven 仓库缓存)也确认过新增的四个 migration 文件
  (`V14`~`V17`,均为上一批未提交改动里已经写好、这次一并验证的历史遗留
  文件)不影响编译。

### 顾客端交互反馈强化(2026-08-09,不算新 Phase)
方案文档见 `docs/design-proposal-customer-interaction.md`(和
`docs/design-proposal-customer-editorial.md` 是姊妹文档:那份管"看起来够不够有
编辑感",这份管"操作时有没有被系统听见")。范围只到顾客端桌面视口,admin 和
移动端专项都没碰。**贯穿这批改动的硬性前提:任何新增的交互反馈都不能让触屏/
键盘用户失去等价的可达路径**——下面每一条要么是叠加在既有可点击/可聚焦元素上的
装饰层,要么是和输入设备无关的提交态反馈,没有一条把某个功能的唯一触发方式
设计成 hover。

- **`MovieCard` 双重缩放(独立 bug 修复,不算这批交互改动)**:海报 `<Image>`
  自带 `group-hover:scale-105`,外层 `GlassCard` 又有 `whileHover:{scale:1.02}`,
  悬停时是两层缩放叠加,而且海报那层是**纯 CSS `:hover`,没有任何
  `prefers-reduced-motion` 判断**——两处各写各的没对齐,不是刻意设计的层次感。
  去掉海报这一层,只保留 `GlassCard` 的卡级缩放(一处维护,而且那一层本来就有
  `useReducedMotion()` 的 JS 判断)。实测确认:静止时两层 transform 都是 `none`,
  悬停时只有卡片是 `matrix(1.02,...)`、海报保持 `none`。
- **`GlassCard` 补上 CSS 那一层 reduced-motion 兜底**:1.5 节确立的标准是
  **JS + CSS 两层**降级,但 `whileHover` 的缩放此前只有 `useReducedMotion()`
  这一层 JS 判断(高光图层有 `motion-reduce:hidden`,缩放没有对应的 CSS 兜底)。
  加了 `motion-reduce:transform-none!`——**必须带 `!`**,因为 framer-motion 把
  scale 写成内联 style,普通类名的优先级压不过内联样式。这样以后就算有人加了
  一个忘记判断 `reduceMotion` 的 transform,CSS 这层也会兜住。
- **`SubmitProgressBar` 复用到订票流程风险最高的两个按钮**:此前"确认选座"和
  "去支付"都只有按钮文字切换("提交中…"/"正在跳转到支付页面…"),是全站反馈
  最弱的一档,却恰好是风险最高的两次点击——前者要过后端"数据库预检查 + Redis
  原子加锁"两层并发校验(见 Phase 5),409 抢座失败是设计内的正常分支;后者要
  真的发一次 `POST /bookings/{id}/checkout` 建 Stripe session 再跳转,网络延迟
  期间用户只能盯着一个改了字的按钮。两处都复用登录/注册表单已有的
  `SubmitProgressBar`,没有新造组件。
  - **`SelectionSummaryBar` 不需要额外加 `relative`**——方案文档里当时写的是
    "需要给它加 `relative` 才能让 `absolute` 定位生效",这条**是错的,实施时
    纠正了**:那个容器已经是 `fixed`,而 `position: fixed` 本身就是定位元素、
    会为 `absolute` 子元素建立包含块。实测确认进度条渲染成 1440×2px、贴在
    结算栏顶边,容器 `position` 仍是 `fixed`。
  - `BookingConfirmation` 那条放在 `GlassCard` 内部(落在 `GlassCard` 自己那层
    `<div className="relative">` 里),因此会被卡片的 `p-8` 内边距缩进——这和
    登录/注册的进度条被 `Card` 内边距缩进是同一种视觉处理,两处读起来是同一个
    模式,不是两套。
- **选座页三态切换补上过渡动效**:`booking` / `expired` / 座位网格三个分支此前
  是纯 React 条件渲染的早返回,一次 `setState` 后整屏内容同帧替换——这是顾客端
  最后一处硬切,而且正好落在用户最需要确认"系统跟上了我的操作"的时刻(提交
  成功、持有到期)。它们共享同一个路由,所以 `PageTransition`(按 pathname 分
  key)根本看不到这次切换。改成一个 `AnimatePresence mode="wait"`,用
  `booking ? "confirmation" : expired ? "expired" : "selecting"` 做 key,复用
  `lib/motion.ts` 的 `EASE_APPLE` 和既有的 `useReducedMotion()` 降级写法。
  - **只做 opacity,刻意不做 y 位移**——这不是偷懒,是一个真实的 CSS 约束:
    座位网格里的 `SelectionSummaryBar` 是 `position: fixed`,而**带 transform 的
    祖先元素会成为其 fixed 子孙的包含块**,一旦这层 wrapper 动 y,那条结算栏在
    过渡期间就会改为相对这个 wrapper 定位而不是视口。实测复核过:往返切换之后
    结算栏 `bottom` 仍等于视口高度(900)、宽度仍是满宽 1440,且从结算栏往上
    遍历到根节点**没有任何祖先带 transform**(`ancestorTransforms: []`)。
    交叉淡入淡出对"原地换屏"本来也比方向性滑动更贴切——后者会暗示一次并不存在
    的导航。
- **`GlassCard` 新增 `interactive` prop(`whileTap:{scale:0.98}`),opt-in 不是
  默认**:鼠标按下到路由真正跳转之间此前没有任何"点击已被接收"的即时信号,要等
  `PageTransition` 触发才有反馈。但**没有把 `whileTap` 全局铺开**——全站大多数
  `GlassCard` 是静态卡面(电影详情信息卡、倒计时确认卡、电子票、选座过期提示),
  一张按下去会缩小的卡片读起来就是"这里能点",对点不动的卡片来说是假的可供性。
  只有本身就是链接/按钮的两处(`MovieCard`、`ShowtimeList` 场次胶囊)显式传
  `interactive`。实测确认:可点击卡片按下时 `scaleX` 到 0.98,静态卡片按下时
  维持在 1.02(那是它的 hover 值)不发生按压缩放。
- **验证方式**:每一项都用 Playwright 在 `reducedMotion: 'no-preference'` 和
  `'reduce'` 两种上下文下各跑一遍,而且**不只截图,还直接读 `getComputedStyle`
  的实测值**(transform 矩阵、过渡期间采样到的分数 opacity、进度条的
  `getBoundingClientRect()`),避免"截图上看着对"但实际降级没生效。三态过渡这项
  的降级证据是:正常动效下过渡期间采样到 27 个互不相同的分数 opacity 值(说明
  确实在渐变),开启 reduced motion 后一个分数值都采不到(说明是瞬时切换、
  duration 归零)。`npm run build` / `npm run lint` 跑过,干净。
- **已知遗留(本次没做,不是遗漏)**:方案文档第 5 节里优先级最低的两项——
  座位按钮的轻量 hover 缩放、`SelectionSummaryBar` 文案的交叉淡入——没有实施,
  它们是锦上添花项,不做也不算缺陷。

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
- **`GET /api/v1/admin/users` 在验证过程中一度稳定返回 500**,根因不是这几个
  新文件的代码本身有问题(`mvn clean compile` 之后这个接口和其余所有接口都
  工作正常),而是当时一直开着的本地 `mvn spring-boot:run` 进程处在一个不一致
  的增量编译状态——`target/classes` 里 `UserMapperImpl.class` 确实存在,
  `mvn spring-boot:run` 却仍然报 "No qualifying bean of type UserMapper"
  拒绝启动一次、又在另一次不完整重启后让这一个新端点单独 500(其余复用
  `UserMapper` 的老端点如 `/users/me` 完全正常)。杀掉进程、跑一次
  `mvn clean compile` 再重启后问题消失,之后再没复现。Sprint 3 的日志(见
  `antigravity.md`)已经观察到 IDE 对 MapStruct 生成代码报红,但把结论定成
  "纯 IDE 缓存问题,`mvn clean compile` 成功就说明代码没问题"——这个结论对
  代码本身是对的,但不完整:它没有覆盖到"一个跑了很久、经历过多次增量
  `spring-boot:run` 重启的进程,即使代码正确,也可能因为 Maven 增量编译状态
  不一致而在运行时表现出编译期发现不了的 bean 装配错误"这个真实场景。
  已经在 `docs/DEVELOPMENT.md` 补了一条对应的环境注意事项。
- **CI 的 backend 检查一度真实地红过,根因和上面那次本地 500 完全无关,是一个
  独立的、更实质的问题**:分支推上去之后 GitHub Actions 的 `mvn --batch-mode
  --no-transfer-progress clean test` 失败,而这次复核此前只跑过
  `mvn compile`/`mvn clean compile`,从没跑过全量 `mvn test`——这是本次复核
  流程本身的一个盲区,`antigravity.md` 的验证记录同样只到 `mvn clean
  compile` 为止,两边都没有跑测试套件,所以这个问题在落地前完全没被发现。
  真正命中的是项目已有的一条架构治理测试
  ——`TimestampedEntitySaveFlushRuleTest`(用 ArchUnit 扫描全部生产代码,
  强制"任何 `@Entity` 带 `@CreationTimestamp`/`@UpdateTimestamp` 的字段,
  存库必须用 `saveAndFlush()`/`saveAllAndFlush()`,不能用普通的
  `save()`/`saveAll()`",因为 Hibernate 要到 flush 时才会真正填充这类
  字段)——这条规则本身是从 Movie/Cinema/Hall 各自独立踩过同一个 bug 之后
  加的护栏(见该测试类的类注释),`User` 实体的 `updatedAt` 正是
  `@UpdateTimestamp`,而 `AdminUserService.updateUserRole` 原本调用的是
  `userRepository.save(user)`——这是 Antigravity 原始代码里已经存在的问题,
  不是这次新引入的,只是从没被跑过全量测试的环境验证到。修复是把这一处的
  `save` 改成 `saveAndFlush`,一行改动。
- **补的单元测试第一次跑的时候,自己又踩了同一条规则一次**:
  `AdminUserServiceTest` 里原本写了一行
  `verify(userRepository, never()).save(any())` 想反向锁定"确实没调用过
  `save()`,而是叫 `saveAndFlush()`",结果这一行本身在字节码层面就是一次对
  `UserRepository.save(..)` 的调用(Mockito 的 `verify()` 就是这么实现的),
  而 `TimestampedEntitySaveFlushRuleTest` 用 `ClassFileImporter()
  .importPackages("com.cineverse.backend")` 扫描的是整个包前缀,并不区分
  生产代码和测试代码(`target/test-classes` 同样在扫描范围内)——于是这条
  测试代码本身把测试跑红了第二次。删掉这一行 `never()` 断言即可,理由是
  "不写 `save()` 这件事本来就已经被 `TimestampedEntitySaveFlushRuleTest`
  在生产代码层面强制锁定了,不需要在这个 service 测试里对同一件事再断言
  一遍,而且这样断言反而会自己触发这条规则"。
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

最初的修复方式是把 `(customer)/layout.tsx` 从 Server Component 改成 Client
Component,在 `useEffect` 里读 `status`/`user`,ADMIN 登录态下
`router.replace("/admin/dashboard")`。用 Playwright 在跳转瞬间连续截帧复核后,
发现这个方案有两个问题,一个是真实的数据泄露 bug,一个是这次复核过程中主动
放弃、没有采纳的架构方向:

- **确认存在闪烁,而且泄露的是真实数据,不只是布局跳动**:连续截帧显示
  `/profile` 页面在跳转前完整渲染出了 ADMIN 账号自己的姓名、邮箱、用户 ID、
  加入时间(不是骨架屏,是 `ProfilePage` 真正拿到 `fetchCurrentUser()` 结果
  之后渲染的卡片),大约 100~300ms 之后才跳到 `/admin/dashboard`。根因是
  `(customer)/layout.tsx` 无条件渲染 `children`,只在 `useEffect` 里事后发起
  跳转——这和 `app/admin/layout.tsx` 的模式不对称:后者在确认角色之前**不
  渲染** `children`,只渲染骨架屏,两者不是同一种"客户端拦截"。
- **没有采纳"整层拦截"方向,即使改成正确的"先挂起再渲染"模式**:考虑过照抄
  `admin/layout.tsx` 的模式,在 `(customer)/layout.tsx` 整层挂起渲染直到确认
  "不是 ADMIN"。放弃的原因:`admin/layout.tsx` 能负担这个代价,是因为
  `/admin/**` 下每个页面本来就需要登录态,天然要等一次异步校验;但
  `(customer)/layout.tsx` 包着的绝大多数路由(首页、电影详情、场次列表、
  登录/注册页)是**公开只读**的,今天硬刷新这些页面不等待任何鉴权状态就立即
  渲染。整层挂起会让首页这类高频公开页面的每次硬导航都多等一次
  `/api/v1/auth/refresh` 往返(哪怕是匿名访客,没有 cookie 也要等这次请求
  失败才知道),用一个真实、影响面广的性能回退去防一个只在"ADMIN 手动输入
  客户端 URL"这种低频场景才会出现的问题,不划算。此外后端本身也不认为
  ADMIN 完全不该碰顾客端功能——`SecurityConfig` 里 `/api/v1/bookings/**`
  是 `authenticated()`,不限角色,ADMIN 一样能创建/查看/取消自己的订单;
  一个"ADMIN 绝不能停留在任何顾客端页面"的整层拦截和后端自己的权限模型是
  矛盾的。
- **最终方案:把 ADMIN 检查加在 `profile/page.tsx` 和 `bookings/page.tsx`
  各自已有的鉴权 `useEffect` 里**,紧跟在原有的
  `unauthenticated -> router.replace("/login")` 分支后面,检查
  `user?.role === "ADMIN"` 就跳 `/admin/dashboard`,并且**在发起
  `fetchCurrentUser()`/`callAuthorized(listBookings)` 之前** return——这样
  真正会泄露个人数据的那次请求从未发出,页面在跳转完成前始终停在原有的
  加载骨架屏上,和"未登录"分支复用同一套骨架屏,不需要新状态。这两个页面
  本来就已经是"等 `authStatus` 落定,再决定渲染什么"的写法(`ProfilePage`/
  `BookingsPage` 早在 F-2 就是这个模式),这次只是多加一个分支,不是新引入
  一种模式。用 Playwright 复测过:即使故意用一个较慢(冷启动 Turbopack
  编译)的场景把跳转窗口拉长到近 3 秒,截帧显示全程只有加载骨架屏,姓名/
  邮箱/用户 ID/订单数据没有在任何一帧出现过。
  `(customer)/layout.tsx` 改回了纯 Server Component(和 Phase 8 之前一致),
  不再持有任何鉴权逻辑。
- **`bookings/[id]/confirmed/page.tsx`(电子票页)刻意没有加同样的检查**:
  `GET /api/v1/bookings/{id}` 在后端是"本人或 ADMIN 都能看"(`BookingController
  .getById`,见 Phase 5),这是有意支持的客服场景——ADMIN 需要能替顾客核对
  某一张具体的票。如果这个页面也做 ADMIN 反向拦截,会直接堵死这条已经存在
  的合法路径。`bookings/page.tsx`(订单列表)不受这条限制,因为列表接口
  本身对所有角色都只返回调用者自己的订单(F-2 已有决定,ADMIN 也不例外)
  ——ADMIN 在这个列表页不会看到别人的订单,加不加反向拦截都不构成数据泄露,
  加上纯粹是为了不让 ADMIN 停留在一个对他们没有实际意义的"我的订单"页面,
  是 UX 层面的选择,不是安全要求。

#### `AdminHeader` 的 `/admin/movies` 死链接:已移除,不新建页面

Sprint 4 给 `AdminHeader` 加了 Dashboard/Movies/Users 三个导航链接,但只有
`/admin/users`(这次一起交付)和已有的 `/admin/dashboard` 真实存在——
`/admin/movies` 没有对应的 `page.tsx`,点击会落到 Next.js 的全局兜底 404。
复核时先把这个问题原样报告、不擅自处理;拿到决定后处理方式是**移除这个导航
项**,不是补一个电影管理页面——电影管理目前完全是后端接口驱动(Phase 2 的
`/api/v1/movies/**`,ADMIN-only 的 CRUD 走 Swagger/curl,没有配套前端页面),
给它单独建一个 admin CRUD 页面是比"移除一个死链接"大得多的工作量,不属于
这次任务范围,以后要做再单独排期。

**这条决定后来被推翻**:2026-08-12"Admin 电影管理页面 `/admin/movies`"一节
交付了真正的电影管理页面,导航项也随之恢复。原文保留在这里是当时的真实
判断(范围收窄是当时任务下的正确决定),不是错误,只是后来范围变了。

### 技术栈降级:Java 25 + Spring Boot 4.1 → Java 21 LTS + Spring Boot 3.5.15(2026-08-11)

**为什么**:纯粹是招聘市场匹配问题,不是技术栈本身有缺陷。Java 25(2025-09
LTS)和 Spring Boot 4.1/Framework 7 在这个项目起步时(2026-08-01)是"能拿到
的最新 LTS/最新大版本"这个默认假设下的合理选型,但作为求职作品集,面试官/
招聘方筛简历和现场提问的参照系是市场上实际主流的版本,不是发布日期最新的
版本——目前这个市场语境下"Java LTS"和"Spring Boot 3"的默认所指仍然是
Java 17/21 和 Spring Boot 3.x,不是刚发布不久、生态和教程都还在追赶的
Java 25/Boot 4。选 Java 21 而不是继续留在 25,或者反过来降到更老的 17,是
因为 21 才是当前市场语境下的"新一代默认项",17 会显得刻意保守。

**明知 Spring Boot 3.5.15 已经 OSS EOL(2026-06-30)仍然选它**:降级前查证
过——Spring Boot 3.5 是 3.x 线的最后一个次版本,3.0~3.5 全部已经停止免费
安全补丁供给,当前只有 4.0.x/4.1.x 还在维护窗口内,不存在"更新但仍在维护期
的 3.x"这个选项。这个 trade-off 对一个不需要持续安全补丁供给的作品集项目
可以接受:面试官/招聘方筛简历和交流时对齐的是"这人对 Spring Boot 3.x 这套
体系熟不熟",不是"这个部署实例今天能不能收到安全补丁"。如果这个项目以后
真的要长期在线上跑(不只是作品集展示),需要重新评估这个决定。

**改动范围**(完整清单,不是摘要):

- `pom.xml`:`spring-boot-starter-parent` 4.1.0 → 3.5.15;`java.version`
  25 → 21;`springdoc.version` 3.0.3(配合 Framework 7)→ 2.8.17(配合
  Framework 6);删除 `spring-boot-starter-flyway`(Boot 3.x 没有这个
  starter,Flyway 自动配置内建在 `spring-boot-autoconfigure` 里,
  `flyway-core`/`flyway-database-postgresql` 保留为普通依赖);删除
  `spring-boot-starter-webmvc-test`(Boot 3.x 的 `spring-boot-starter-test`
  本身就自带 MockMvc 支持,不需要单独的 starter)。`jjwt`/`archunit`/
  `stripe-java`/`openpdf`/`mapstruct` 五个第三方库版本独立于 Boot,未改动。
- **Testcontainers 依赖坐标改名**(静态审计没有覆盖到的一处,`mvn
  dependency:resolve` 报错才发现):`org.testcontainers:testcontainers-
  junit-jupiter`/`testcontainers-postgresql`(Boot 4.1 管理的 Testcontainers
  2.x 命名)在 Boot 3.5.x 管理的 Testcontainers 1.21.x 上不存在,改回未加
  前缀的 `org.testcontainers:junit-jupiter`/`postgresql`。
- **`@AutoConfigureMockMvc` 包路径改回**(同样是编译报错才发现,静态审计
  没查到):Boot 4 把这个注解挪到了新包
  `org.springframework.boot.webmvc.test.autoconfigure`,Boot 3.x 上还在
  `org.springframework.boot.test.autoconfigure.web.servlet`——10 个用
  `@AutoConfigureMockMvc` 的集成测试类逐一改了 import。
- **Jackson 3 → Jackson 2 namespace**:全仓库 `tools.jackson.databind.*` →
  `com.fasterxml.jackson.databind.*`,12 个文件(2 个安全处理器
  `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` + 10 个集成
  测试)。`ReportFlowIntegrationTest` 里还额外用到了 Jackson 3 专属的
  `JsonNode.asString()` 方法(Jackson 2 只有 `asText()`,同名方法在
  Jackson 2 上不存在)——这一处是编译报错才发现的,静态审计当时只查了
  `import`,没查到这种"两边包名一改就都能编译过,但个别方法签名其实不兼容"
  的情况,一并改成了 `asText()`。
- **Hibernate 7.1 → 6.6.53**、Jackson 2.21.4、Spring Security 6.5.11、
  Spring Framework 6.2.19、Testcontainers 1.21.4——这些都是 Boot 3.5.15
  的 BOM 自动管理的版本,没有手动指定。

**验证方式,不是只跑通编译**:`mvn clean test` 全量跑过(Docker 本地已有
Testcontainers 需要的环境),**144/144 全部通过**,和降级前(Java 25/Boot
4.1 下)的基线完全一致,没有任何测试因为这次降级需要放宽断言或删除。重点
盯过的两处(降级前评估为"低风险但没有绝对把握"的点)都在真实 Hibernate
6.6/Jackson 2 环境下验证通过:`ShowtimeAdminFlowIntegrationTest` 里对
`startTime`/`endTime` 的精确 ISO-8601 字符串断言,以及全仓库所有对
`createdAt`/`updatedAt` 的断言(验证 `@CreationTimestamp`/`@UpdateTimestamp`
"只在 flush 时才真正填充"这条 Hibernate 行为在 6.6 和 7.1 上一致,
`TimestampedEntitySaveFlushRuleTest` 这条 ArchUnit 护栏因此不需要任何调整
就继续有效)。

**本机没有装 JDK 21,测试是在 JDK 25 运行时上跑的**——`mvn` 用的是本机
`JAVA_HOME`(JDK 25),`java.version=21` 只让 `maven-compiler-plugin` 用
`--release 21` 编译出 Java 21 语言级别/字节码版本的 class 文件,但实际
执行这些 class 文件的仍然是 JDK 25 的 JVM(新版 JVM 向下兼容旧版本
bytecode,这是可行的,但不完全等同于在真实 JDK 21 运行时上跑过)。这不是
回避,是如实记录一个还没有闭环的验证缺口——如果要百分百确认在 JDK 21
运行时下行为一致,需要在本机装一个 JDK 21 发行版(比如 Eclipse Temurin)
重新跑一次 `mvn clean test`,这一步留给你自己决定要不要做,降级本身没有
去动系统级 JDK 安装。

**降级过程中的一段插曲,值得记录**:动手改代码前查过 `git log`,发现本地
`main` 落后 `origin/main` 恰好 1 个 commit(`d8cc64b`,"fix: admin user
management data leak, self-lockout guard, and CI compliance"),而这个
commit 修的问题(`AdminUserService.updateUserRole` 用 `save()` 而不是
`saveAndFlush()`,撞上 `TimestampedEntitySaveFlushRuleTest`)恰好在第一次
跑 `mvn test` 时被独立发现——逐个 diff 之后确认:当时本地未提交的
`AdminUserController.java`/`AdminUserService.java`/`UpdateUserRoleRequest.java`
等一批文件,是这个已经合并的 commit 修复之前的旧草稿(Antigravity 的原始
交付),其余几个重叠文件(`BookingRepository.java`、`login-form.tsx`、
`auth-context.tsx` 等)则和 `origin/main` 字节级相同。按"先复核旧草稿是否
被新 commit 完全覆盖、确认后才丢弃"的顺序处理:删除本地这几份旧草稿、
`git pull origin main` 干净 fast-forward、在新拉下来的
`AdminUserFlowIntegrationTest.java` 里补了同样的 Jackson 3/
`@AutoConfigureMockMvc` 包名修复(它也是在 Boot 4.1 环境下写的),之后
`mvn test` 才第一次真正跑到 144/144 全绿。这次降级的改动本身和这个 commit
触碰的文件完全不重叠,过程中没有产生任何合并冲突。

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
- **验证边界,如实记录**:`npm run build`(TypeScript 严格模式)、
  `npm run lint` 全部跑通(过程中改了两处:`react-hooks/set-state-in-effect`
  见上,以及 JSX 文本里的直引号改成中文书名号「」规避
  `react/no-unescaped-entities`)。额外做了一次路由层面的冒烟测试——
  `next build` 产物用 `next start` 起在一个独立端口上,带着仍然有效的
  `refresh_token` cookie 直接 curl 三个新路由,确认都是 200、且渲染出的
  是和已知正常工作的 `/admin/dashboard` 完全相同的结构(`AdminHeader` +
  `AdminLayout` 的骨架屏——`/admin/**` 的实际页面内容本来就要等客户端
  角色校验通过之后才渲染,curl 拿不到这一层,两个页面在这一层的表现
  一致就是这次冒烟测试能给到的全部信心)。**这个会话里没有可用的浏览器
  自动化工具(没有 Playwright MCP 或等价工具),创建表单提交、图片上传、
  编辑页 genre 回填这几个真正需要交互验证的行为没有在真实浏览器里跑过
  ——这些结论目前只到"代码走查 + 已经用 curl 验证过的后端契约对得上"这一
  层,没有到"在浏览器里点过一遍"这一层,这个边界如实记录在这里,不假装
  已经做了完整验证。**

### `/admin/movies` 补测:真实浏览器验证 + 发现一个预先存在的图片渲染 bug(2026-08-13)

上一条记录里说"这个会话没有可用的浏览器自动化工具",这个结论后来被重新核实
并推翻了——不是环境里真的没有,是第一次只搜了工具列表就下了结论。重新排查
发现这台机器上 Playwright 的 Chromium 二进制其实已经缓存好了
(`~/AppData/Local/ms-playwright/chromium-1234`,`INSTALLATION_COMPLETE`
标记都在),只是 `playwright` 这个 npm 包当时没装在项目里——本地
`npm install --no-save playwright` 之后就能跑(不写入 `package.json`,
纯本次验证用的临时依赖)。这和 CLAUDE.md 历史上好几次"用 Playwright 实测"
的记录能对上,说明这个能力确实存在,只是需要主动装一下,不是要去外部下载
一个全新的浏览器内核。

**验证环境**:延续本项目已有的"起第二个后端实例做隔离验证"惯例(见 Hero
背景图那次、设计债批次修复那次)——`cineverse-backend` 用
`SERVER_PORT=8082 CORS_ALLOWED_ORIGINS=http://localhost:3005` 起了一个独立
实例(共用同一个 Postgres/Redis 容器),前端临时把 `.env.local` 指向 8082、
`next build` 之后 `next start -p 3005`。全程只用脚本自己创建的一次性测试
电影(标题带 `Playwright E2E Test Movie` 前缀),验证完主动删除,过程中
专门核对过 `totalElements` 前后都是 11、没有碰任何真实种子电影或已有场次
——这条纪律就是上一次事故之后新加的操作原则,这次是它第一次真正派上用场。

**跑通的检查项(13 项里 12 项通过,细节见下面唯一的失败项)**:登录后按角色
跳转、genre 多选按钮点击后正确显示按下状态、创建成功后跳到编辑页、编辑页
一次性提示 banner 显示、上传接口本身不报错、列表页能看到新电影、**重新打开
编辑页时 genre 多选框正确回填成已关联的分类而不是空的**、只改标题不碰
genre 直接保存后刷新页面**genre 关联确实没有被清空**(这是最担心的那个回归
点,实测确认没有问题)、删除后列表里确实不见了。

**唯一的失败项,而且是一个真实存在、这次之前从没被发现过的 bug**:上传成功
之后海报/背景图的**预览图实际上加载不出来**。第一版脚本只检查了
`<img src>` 属性值不再是占位图路径就判定"通过",这个检查本身是错的——
只证明了 URL 拼接对了,没有证明浏览器真的把图片加载出来了。往深查(加上
`page.on("response")` 记录真实状态码)才发现每一次 `/_next/image?url=...`
请求都是 400,`next start`/`next dev` 的服务端日志给出了明确原因:
```
upstream image http://localhost:8082/uploads/xxx.png resolved to private ip ["::1","127.0.0.1"]
```
这是 Next.js 图片优化器一个内置的 SSRF 防护——上游图片地址如果解析到
私有/回环 IP 就直接拒绝代理。用 `next dev` 复测过,同样的请求同样 400,
不是 `next start`(生产模式)专属的问题。

**这个 bug 和这次 `/admin/movies` 的代码本身无关,是一个从 Phase 2 就存在、
一直没被触发过的缺口**:`next.config.ts` 的 `images.remotePatterns` 早就按
`NEXT_PUBLIC_API_BASE_URL` 动态注册了后端 origin(这次核对过生成的配置确实
精确匹配 `localhost:8082`,remotePattern 本身没写错),问题出在 Next.js 图片
优化器另一层内置的、和 `remotePatterns` 独立的私有 IP 检查上,`remotePatterns`
写对了也绕不过去。**没有真正触发过的原因**:11 部种子电影的 `poster_url`/
`backdrop_url` 全部来自 OMDb/TMDB 热链(公网地址),从 Phase 2 到现在,
从来没有一部电影真的通过 `StorageService` 走本地上传拿到过
`/uploads/xxx.png` 这种相对路径、又被 `next/image` 渲染出来过——本地上传
接口本身在 API 层面一直是好的(`curl` 直接传文件、拿到正确的
`posterUrl` 都没问题),缺的是"前端真的把这样一张图渲染出来"这一步,而这一
步在这次之前完全没有 UI 入口能触发到(`/admin/movies` 是第一个)。这正是
坚持要做真实浏览器验证、不满足于"代码走查 + API 契约对得上"的原因——这个
bug 不管怎么读代码都读不出来,`next.config.ts` 配置完全合规,`npm run
build`/`lint` 也不会报,只有真的把图渲染到浏览器里才会暴露。

**已知但还没决定怎么修**,几个方向(未擅自选择,等待决定):
- 给渲染后端上传图片的 `<Image>` 加 `unoptimized`(放弃 Next 自动优化,
  原样透传),影响范围只限于本地上传的图,OMDb/TMDB 热链图不受影响,因为
  那些是公网地址,不会撞上这个私有 IP 检查。
- 检查这个版本的 Next.js 是否有针对性的配置项可以豁免特定 remotePattern
  的私有 IP 检查(没来得及查证,不确定存不存在)。
- 生产部署如果后端不是裸 `localhost`(比如挂在一个真实域名/反向代理后面),
  这个问题自然不会触发——但本地开发环境(前后端都在 `localhost`)会一直
  撞上,不只是这次验证用的临时 8082 实例,**当前默认的 8081 后端同样会
  撞上**(检查的是 hostname 解析结果,不针对某个特定端口),所以不是这次
  验证临时环境特有的问题。

### 本地上传图片渲染 bug 的修复:`images.dangerouslyAllowLocalIP`(2026-08-13)

上一条记录的图片渲染 bug 已经修复并重新用 Playwright 实测过。修之前先按
`frontend/AGENTS.md` 的指示去读了本地实际安装的 Next.js 16 文档
(`node_modules/next/dist/docs/`),而不是凭训练数据里的旧版本 API 猜——
这次真的在这份文档里查到了针对性的官方豁免机制,不是"只能用 `unoptimized`
放弃优化"这一条路:

- 官方文档原文,两处:
  - `node_modules/next/dist/docs/01-app/02-guides/upgrading/version-16.md`
    第 819~821 行("Local IP Restriction (Breaking change)"):"A new security
    restriction blocks local IP optimization by default. Set
    `images.dangerouslyAllowLocalIP` to `true` only for private networks."
  - `node_modules/next/dist/docs/01-app/03-api-reference/02-components/
    image.md` 第 896~918 行(`dangerouslyAllowLocalIP` 配置项完整说明):
    默认值 `false`;"If you need to optimize remote images hosted elsewhere
    in your local network, you can set the value to true."

**实现,第一版(被合并前的复核推翻,不是最终方案)**:`next.config.ts` 新增
`images.dangerouslyAllowLocalIP`,按 `NEXT_PUBLIC_API_BASE_URL` 的 hostname
是不是字面量 `localhost`/`127.0.0.1`/`::1` 来反推"是不是本地开发",反推为真
就打开这个开关。提交之后、开 PR 之前的复核问出了两个直接命中的真实问题:

1. **字符串匹配不是环境判断,分不清"本地开发"和"生产环境的后端 origin
   碰巧也叫 localhost"**——同机反向代理场景下(前端和后端部署在同一台机器,
   通过内部 `localhost` 互通)生产环境完全可能合法地把
   `NEXT_PUBLIC_API_BASE_URL` 配成 `http://localhost:xxxx`,这时字符串匹配
   会误判成本地开发,把生产环境的 SSRF 防护也关掉。
2. **`NEXT_PUBLIC_API_BASE_URL` 缺失时的兜底值本身是 `http://localhost:8081`
   ——hostname 恰好也是 `localhost`,导致"环境变量缺失/读取失败"这个应该
   选更安全一侧(`false`)的场景,反而被判成 `true`**。而且这不是假设情景:
   `.github/workflows/ci.yml` 的 `frontend` job 跑 `npm run build` 时完全
   没有设置这个环境变量,复核时直接拿 CI 的真实条件(临时移走
   `.env.local`、不设任何环境变量)本地重跑构建验证过——第一版逻辑下,CI
   打出来的生产构建产物里 `dangerouslyAllowLocalIP` 真的是 `true`。

**最终方案:改成一个独立的、显式的 opt-in 环境变量
`NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION`,不再从 `NEXT_PUBLIC_API_BASE_URL`
反推**——这个值只有字面量等于 `"true"` 才生效,其余任何情况(未设置、拼错、
CI、任何真实部署)一律是安全的 `false`,不存在"猜错"的空间。仍然要求
`backendIsLoopback`(hostname 命中回环地址)同时成立才真正打开
`dangerouslyAllowLocalIP`,这一层判断保留下来只是让代码本身能自解释"这个
开关真的只在后端是本地地址时才有意义",不是安全判断的主要依据——真正决定
开不开的是那个必须显式设置的 opt-in 变量:
```ts
const LOOPBACK_HOSTNAMES = new Set(["localhost", "127.0.0.1", "::1"]);
const backendIsLoopback = LOOPBACK_HOSTNAMES.has(apiBaseUrl.hostname);
const localImageOptimizationOptIn =
  process.env.NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION === "true";
const allowLocalImageOptimization = backendIsLoopback && localImageOptimizationOptIn;
// ...
images: {
  ...(allowLocalImageOptimization ? { dangerouslyAllowLocalIP: true } : {}),
  remotePatterns: [...],
}
```
这才是真正对应后端 `app.security.cookie-secure` 那个思路的版本——
`SPRING_PROFILES_ACTIVE` 是一个专门用来声明"当前是什么环境"的独立信号,不是
从数据库连接串之类的旁路信息反推出来的,第一版恰恰是拿一个"要请求哪个
URL"的变量去顺带回答一个不相关的安全姿态问题,这是原设计的根本问题所在,
不只是"多加一个环境变量"这么表面。

**三点都重新用实际构建验证过,不是只看代码**:
1. 完全模拟 CI 条件(挪走 `.env.local`、不设任何环境变量)构建,读取
   `.next/required-server-files.json`,确认 `dangerouslyAllowLocalIP:
   false`。
2. 正常本地开发配置(`.env.local` 里只有 `NEXT_PUBLIC_API_BASE_URL`,不设
   opt-in 变量)构建,同样确认是 `false`——不小心漏设 opt-in 变量的本地
   开发者也不会意外打开这个豁免。
3. `.env.local` 里显式加上 `NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION=true` 构建,
   确认变成 `true`,并重新跑了一遍下面的 Playwright 图片渲染验证,结果不变
   (依然全部解码成功)。

**影响范围核对**:全仓库 `resolveMediaUrl` 的调用点核对了一遍——
`movie-card.tsx`、`movie-backdrop.tsx`(`hero-carousel.tsx`/电影详情页共用
这一个组件)、`(customer)/showtimes/[id]/page.tsx`、
`components/booking/booking-confirmation.tsx`,加上这次新增的
`admin/movies/page.tsx`/`movie-image-upload.tsx`,一共 7 个真正的渲染点。
但这次修复本身**一个都没有改**——`dangerouslyAllowLocalIP` 是 `next.config.
ts` 里的全局开关,不是像 `unoptimized` 那样要逐个 `<Image>` 实例加的 prop,
一次配置对全部 7 个渲染点同时生效。列出这份清单是为了确认"改完之后这 7 个
地方都会受益,不会有漏网的",不是这次改动本身要触达的文件列表。

**重新验证,针对性只补上一项,不是重跑全部 13 项**:延续之前那套隔离环境
(8082 后端 + 3005 前端),Playwright 直接检查图片是否真的解码成功
(`img.decode()` + `naturalWidth > 0`,不是只看 `src` 属性),覆盖编辑页的
海报预览、背景图预览、列表页缩略图三个不同渲染点(三处的 `<Image>`
`sizes` 不同,请求的 srcset 候选宽度也不同)。

**过程中的一段插曲,值得记录**:第一次重新验证时用的还是最早那张手工拼的
68 字节 1×1 像素测试 PNG,结果三处全部显示 `naturalWidth: 0`——一度怀疑
修复没生效。逐层排查(直接访问 `/_next/image` 拿到的原始字节、用
`img.decode()` 在隔离的 `createElement` 场景里单独测试同一个 URL)才发现
`/_next/image` 端点本身已经不再 400 了(SSRF 拦截确实解除了),问题出在
另一个地方:真实浏览器请求这个端点时会带上 `Accept: image/webp` 之类的
头,触发 Next 把图片转码成 WebP,而这张 1×1 像素的测试图转码成 WebP 之后
`complete` 是 `true`(没有报错)但 `naturalWidth` 是 `0`——这是测试用的
退化 fixture(真实场景不会有人上传 1×1 像素的图)在 WebP 编解码路径上踩到
的一个边界情况,不是这次修复引入或遗留的问题。换成一张从项目里已有海报
URL 下载下来的正常尺寸 JPEG(380×562)重新测,编辑页海报/背景图预览、
列表页缩略图三处的 `naturalWidth` 分别读到 200/200/48(数值对应各自请求
的候选宽度),确认解码成功。

**结论**:修复生效,已经用真实浏览器验证过(不是只信 `next.config.ts`
配置正确、也不是只信没有 400 了),`/admin/movies` 的上传→预览这条链路
现在端到端可用;开关本身也补了三种场景的真实构建验证(CI 条件/正常本地
开发未 opt-in/显式 opt-in),不存在"环境变量缺失就默认打开"或"生产环境
恰好也叫 localhost 就被误判"这两个第一版真实存在过的问题。相关的
`frontend/.env.example`、`docs/DEVELOPMENT.md` 也同步补了这个新变量的
说明。

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

**验证边界,如实记录**:后端 `mvn clean test` 160/160 全绿(新增
`AdminMovieTmdbServiceTest` 纯 mapping 单元测试、`TmdbGatewayImplTest`
空 key 单元测试、`AdminMovieTmdbFlowIntegrationTest` 集成测试——401/403/
mock 网关成功/mock 网关失败,均使用 `@MockitoBean` 替换 `TmdbGateway`,
不依赖真实网络调用 TMDB,和 `PaymentFlowIntegrationTest` mock
`StripeCheckoutGateway` 是同一个模式)。`npm run build`/`npm run lint`
干净。**这个会话没有真实的 `TMDB_API_KEY`**,用 Playwright 在一个没有配置
key 的隔离后端实例上实测了"没配置 key 时的降级路径"(搜索请求真实收到
502、前端正确显示中文兜底提示、点"找不到?手动创建"能正常切换、手动创建
流程完整走通、清理干净)——**但没有真实验证过 TMDB 搜索真的返回结果、
选中结果后表单被正确预填、图片 URL 真的写入数据库这条"key 配置正确"
的成功路径**,这条路径目前只有集成测试(mock 网关)覆盖,没有拿真实
TMDB API 在浏览器里跑过一遍。这个边界如实记录在这里,等有真实 key 可用
再补一次真实验证。

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
