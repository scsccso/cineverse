# CineVerse — 决策与历史记录(Decision Log)

> 本文件是 `CLAUDE.md` 的详细论述附属文件,2026-08-14 从 CLAUDE.md 拆分出来。
> CLAUDE.md 是每次开新 session 都要读的"活跃内容"(当前架构、仍有约束力的
> 操作纪律、未解决的缺口);本文件收纳的是**已经尘埃落定、不会再被推翻的
> 历史决定的完整论述过程**、**已经被后续决定取代的历史记录**、以及**具体的
> 调试过程叙事**——这些内容对理解"为什么系统长这样"仍有价值(尤其是面试官/
> 未来的你回顾项目时),但不需要每次 Claude Code session 都重新加载。
>
> **本文件的内容全部是从 CLAUDE.md 原文逐字搬迁过来的,没有做二次编辑、
> 精简或改写**——只是换了个文件存放。每一节标题都和 CLAUDE.md 里对应的
> 标题(或曾经存在过的标题)一致,方便对照查找。CLAUDE.md 对应位置留了
> 精简后的结论 + 指向本文件的指针。
>
> 阅读顺序建议:先读 CLAUDE.md 建立当前状态的整体理解,只有当你想深挖
> "这个结论具体是怎么推导出来的"或"当时踩过什么坑"时,再来查本文件对应的节。

---

## 1.5.1 Admin 后台的设计系统例外 —— 实现机制细节

> CLAUDE.md 保留了"为什么例外""卡片视觉规范""无障碍标准"的结论,下面是
> "怎么做到的"这条被移出的完整原文。

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

## 1.5.2 Admin 独立导航 shell —— 完整论述

> CLAUDE.md 保留了结论(独立 `AdminHeader`,路由组是兄弟关系不是父子嵌套)
> 和 `AdminHeader` 内容清单,下面是完整的原始论述,包括为什么不做成 if 分支、
> 路由组重构细节、以及两次补充记录。

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

## 1.5.3 登录/注册表单卡片保留 Card —— 完整论述

> CLAUDE.md 保留了结论(登录/注册用 `Card`,是设计系统第三个有意例外),
> 下面是完整原文,包括为什么是专注度驱动而不是性能/密度驱动。

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

## Phase 0 — 项目基建:Spring Boot 4.1 起步细节(已被 2026-08-11 降级取代)

> CLAUDE.md Phase 0 只保留 checklist。下面是"关键决定"完整原文——这些
> Boot 4 专属细节现在已经不适用于当前代码(项目已降级到 Boot 3.5.15/
> Java 21,见 CLAUDE.md「技术栈降级」一节的结论 + 本文件对应的完整版本)。

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

## Phase 1 — 用户管理:CI flaky test 插曲

> CLAUDE.md Phase 1 的"关键决定"(token 存储、rotation、CORS、前端框架)
> 是仍然生效的活跃架构,原样留在 CLAUDE.md 里。下面这句开场白(CI 曾经
> 红过的 flaky test 故事)移到这里。

CI 曾因为一个真实的 flaky test bug 红过一次(access token 缺 `jti`,
同一秒内签发的两个 token 会完全相同)——已修复并补了回归测试,细节见
`JwtService`/`JwtServiceTest`。

## Phase 5 前端补充 —— 接口契约核对记录

> CLAUDE.md 保留了轮询间隔、视觉状态编码、409 处理、倒计时/懒惰过期、
> 未登录跳转这几条活跃行为决定。下面这条测试验收记录移到这里。

- **接口契约核对结果:没有发现不一致**。用一个测试账号跑通了完整链路
  (`GET .../seats` → `POST /bookings` → 轮询看到 `LOCKED` → `DELETE
  /bookings/{id}` → 轮询看到座位回到 `AVAILABLE`,以及两个账号抢同一个座位
  触发 409),`ShowtimeSeatsResponse`/`BookingResponse` 的字段名、类型、
  `SeatStatus`/`BookingStatus` 枚举取值都和前端 TS 类型完全对得上,没有需要
  额外转换或兜底的地方。

## Phase 6 — 支付模块:被推翻的 35 分钟窗口方案

> CLAUDE.md 保留了最终方案的结论(5 分钟持有窗口不变,主动 expire Stripe
> session)。下面是完整的权衡论述,包括被推翻的方案和为什么推翻。

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

## Phase 6 事后修复:refresh cookie 从 `SameSite=Strict` 改成 `Lax`(2026-08-04)

> 结论已经体现在 CLAUDE.md Phase 1「Token 存储方式」里(`SameSite=Lax`——
> 最初定的是 `Strict`)。下面是完整的根因排查和修复论述。

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

## Phase 8 — 管理后台/报表:测试策略细节

> CLAUDE.md 保留了营收口径、分桶锚点、时区、原生 SQL、`generate_series` 补零、
> 上座率口径、filter 设计、CSV/PDF 共享结构、索引这些活跃架构决定。下面是
> "测试"这条移到这里。

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

## Phase 8 前端补充 —— Bar 动画发现过程 + 筛选器骨架屏论述

> CLAUDE.md 保留了 recharts 单色系规则、`--chart-amber` 不复用 `--primary`、
> Bar 动画关闭的结论、筛选器骨架屏模式的结论。下面是完整的发现过程和论述。

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

---

## 审计后修复(2026-08-08)—— 完整原文

> CLAUDE.md 保留了三类修复的结论(`Input`/`Button` 默认高度 h-11、四个
> motion 组件补齐 reduced-motion、新增 error.tsx/not-found.tsx)。下面是
> 完整原文,包括发现过程和多轮补充修正记录。

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
  **补充(2026-08-14):这条"补全"的覆盖范围不完整,当时的结论下早了**——
  这次审计扫的是 `components/motion/` 下的组件和 `seat-map.tsx`,漏掉了
  `components/ui/skeleton.tsx` 这个共享的 shadcn `Skeleton`:它的默认
  className 里有 `animate-pulse` 但没有 `motion-reduce:animate-none`,所以
  在 `prefers-reduced-motion` 下依然脉动(顾客端的 `GlassSkeleton` 是有的,
  两者不一致)。**留作后续钩子,不在这里展开**:下次做前端无障碍相关工作时
  一并处理——修法本身是一行,但会同时改变 `(customer)/profile`、
  `(customer)/bookings`、`admin/layout`、`report-card-skeleton`、
  `navbar`、`admin/dashboard` 这几个调用点的渲染行为,值得单独确认一次。
  **已解决(2026-08-14,同一天里更晚的一次独立 commit)**:修法就是这里
  说的那一行,但这里当时列的六个调用点有两处不准确——重新 grep 之后发现
  `(customer)/bookings` 用的其实是 `GlassSkeleton` 不是这个共享组件,
  从来没受过这个问题影响;`admin/dashboard` 不是直接调用点,是通过
  `report-card-skeleton` 间接受益。这条旧记录原样保留、不做静默修正,
  准确的清单和完整修复/验证过程见下面"skeleton.tsx 补 motion-reduce"
  一节。
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

## 种子数据的海报图来源(2026-08-08)

> 一次性数据操作记录,不是长期架构决定(这也是原文自己下的判断)。
> CLAUDE.md 只保留一句关于 OMDb 非商用限制的提醒。下面是完整原文。

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

## 种子数据扩充:删除测试 fixture + 补充 10 部真实电影(2026-08-08)

> 一次性数据迁移记录。CLAUDE.md 只保留一句"种子数据现在是 11 部真实电影"
> 的结论。下面是完整原文。

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

## `poster_url`/`backdrop_url` 从此是两个不同数据源(2026-08-08)—— 完整原文

> CLAUDE.md 保留了结论(poster 用 OMDb、backdrop 专用 TMDB)以及移动端 WebP
> 清晰度这个**仍未修复的已知缺口**。下面是完整原文,包括匹配置信度规则、
> TMDB 条款、以及排查过程。

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
  加一句原文规定的文案(**补充,2026-08-14**:当时这里凭印象转述的英文
  只是一句不准确的复述,不是逐字引用——真正动手做署名合规交付时改用
  curl 拉取条款页原始 HTML、手动去标签核对过,准确原文比这里当时记的
  版本多了"and the TMDB APIs"、多了"or otherwise approved"两处;这条
  记录原样保留,是当时"凭印象记了什么"的真实历史,不做事后静默改写,
  经核对过的准确原文和完整合规实现见 CLAUDE.md"TMDB 署名合规"一节),
  logo 的视觉权重还必须"低于应用自己的品牌标识"。这比 OMDb 的条款更具体、
  约束力更强(OMDb 没有规定必须放 logo,TMDB 明确要求)。**这次没有在
  界面上加这个 TMDB 署名**——这次改动范围只到"换数据源、验证效果",
  没有被要求做 UI 层的合规展示;如果这个项目要严格满足 TMDB 的条款,
  还差一步:在页面上(比如 footer)加显示 TMDB logo + 那句指定文案,
  这个待办目前没有对应的代码——**已于 2026-08-14 补上,见 CLAUDE.md"TMDB
  署名合规"一节**。CineVerse 本身非商用这一点仍然成立(跟 OMDb 那条
  记录一样),但"非商用"不能替代"署名"这个独立的条款要求,两者不是
  一回事。

## Hero 背景图:裁切位置修正 + 一次排除法排查(2026-08-08)—— 完整原文

> CLAUDE.md 保留了结论(`object-position: 50% 30%`)以及移动端 WebP 清晰度
> 未修复这个已知缺口。下面是完整的排查过程。

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

## Hero 修复没有覆盖到电影详情页,提成共享组件补上(2026-08-08)—— 完整原文

> CLAUDE.md 保留了结论(`MovieBackdrop` 共享组件、`priority` prop 修正)。
> 下面是完整原文。

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

## 设计债批次修复(2026-08-08)—— Turbopack HMR 插曲 + 验证方式细节

> CLAUDE.md 保留了 Hero 去卡片化、profile 居中、移动端导航收纳(含
> set-state-in-effect 模式)、StatTile 警示色统一这几条结论。下面是被
> 移出的 Turbopack 调试插曲和详细验证方式记录。

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

## 顾客端交互反馈强化(2026-08-09)—— MovieCard bug 修复 + 验证方式细节

> CLAUDE.md 保留了 `GlassCard` CSS reduced-motion 兜底、`SubmitProgressBar`
> 复用、三态切换动效(含 fixed 定位约束)、`GlassCard.interactive` prop
> 这几条结论。下面是被移出的独立 bug 修复记录和详细验证方式。

- **`MovieCard` 双重缩放(独立 bug 修复,不算这批交互改动)**:海报 `<Image>`
  自带 `group-hover:scale-105`,外层 `GlassCard` 又有 `whileHover:{scale:1.02}`,
  悬停时是两层缩放叠加,而且海报那层是**纯 CSS `:hover`,没有任何
  `prefers-reduced-motion` 判断**——两处各写各的没对齐,不是刻意设计的层次感。
  去掉海报这一层,只保留 `GlassCard` 的卡级缩放(一处维护,而且那一层本来就有
  `useReducedMotion()` 的 JS 判断)。实测确认:静止时两层 transform 都是 `none`,
  悬停时只有卡片是 `matrix(1.02,...)`、海报保持 `none`。
- **`SelectionSummaryBar` 不需要额外加 `relative`**——方案文档里当时写的是
  "需要给它加 `relative` 才能让 `absolute` 定位生效",这条**是错的,实施时
  纠正了**:那个容器已经是 `fixed`,而 `position: fixed` 本身就是定位元素、
  会为 `absolute` 子元素建立包含块。实测确认进度条渲染成 1440×2px、贴在
  结算栏顶边,容器 `position` 仍是 `fixed`。
  `BookingConfirmation` 那条放在 `GlassCard` 内部(落在 `GlassCard` 自己那层
  `<div className="relative">` 里),因此会被卡片的 `p-8` 内边距缩进——这和
  登录/注册的进度条被 `Card` 内边距缩进是同一种视觉处理,两处读起来是同一个
  模式,不是两套。
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

---

## Admin 用户管理(2026-08-11)—— 调试插曲 + ADMIN 反向隔离完整论述

> CLAUDE.md「Admin 用户管理」一节保留了 API 清单、自我锁定强制校验、
> ADMIN 反向隔离的最终方案、`requestIdRef`/loading 推导可复用模式、登录
> 跳转规则这些活跃规则。下面是被移出的两段调试叙事、自测踩坑插曲,以及
> ADMIN 反向隔离被推翻方案的完整论述。

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

### ADMIN 反向隔离改成精确定位到具体页面,不是整个顾客端路由组 —— 完整论述

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
- **最终方案的验证细节**:用 Playwright 复测过——即使故意用一个较慢(冷启动
  Turbopack 编译)的场景把跳转窗口拉长到近 3 秒,截帧显示全程只有加载骨架屏,
  姓名/邮箱/用户 ID/订单数据没有在任何一帧出现过。(最终方案本身——把 ADMIN
  检查加在 `profile/page.tsx` 和 `bookings/page.tsx` 各自已有的鉴权
  `useEffect` 里——已经作为结论保留在 CLAUDE.md。)

## `AdminHeader` 的 `/admin/movies` 死链接:已移除,不新建页面(已被 2026-08-12 推翻)

> 这条决定后来被推翻(2026-08-12 交付了真正的 `/admin/movies` 页面,导航项
> 恢复)。完整原文保留在这里作历史记录——`antigravity.md` Sprint 4 按标题
> 引用过这一节,原标题予以保留。

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

---

## 技术栈降级:Java 25 + Spring Boot 4.1 → Java 21 LTS + Spring Boot 3.5.15(2026-08-11)—— 完整改动清单与验证

> CLAUDE.md 保留了"为什么降级""明知 EOL 仍选它"这两条结论。下面是完整的
> 改动清单(pom.xml、依赖坐标改名、包路径迁移)、验证方式、以及降级过程中
> 的 git log 插曲。

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

---

## Admin 电影管理页面 `/admin/movies`(2026-08-12)—— 当时的验证边界声明(已被 2026-08-13 补测取代)

> 这条"未做浏览器验证"的边界声明已经被下一天的补测(见下一节 + CLAUDE.md
> 当前结论)取代——原文保留作历史记录。

**验证边界,如实记录**:`npm run build`(TypeScript 严格模式)、
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

## `/admin/movies` 补测:真实浏览器验证 + 发现一个预先存在的图片渲染 bug(2026-08-13)—— 完整原文

> CLAUDE.md 只保留了这次发现的 bug 已经在下一节修复的结论。下面是完整的
> 发现过程,包括 Playwright 工具可用性的重新核实。

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

（下面这个决定后来采纳了第一条以外的方案——见 CLAUDE.md「本地上传图片
渲染 bug 的修复」一节的结论,以及本文件对应的完整版本。）

## 本地上传图片渲染 bug 的修复:被否决的第一版方案 + 完整验证记录(2026-08-13)

> CLAUDE.md 保留了最终方案(独立 opt-in 环境变量
> `NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION`)和"安全开关必须显式、不能反推"
> 这条可复用原则。下面是被否决的第一版方案、官方文档原文引用、以及三种
> 场景的完整构建验证记录、WebP 调试插曲。

修之前先按 `frontend/AGENTS.md` 的指示去读了本地实际安装的 Next.js 16 文档
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

---

## 创建电影主路径改成「从 TMDB 搜索选择」(2026-08-13)—— 验证边界声明

> CLAUDE.md 保留了新增接口、`PATCH` 而非扩展 `MovieRequest` 的理由、
> genre/分级不自动填的规则、图片热链规则、TMDB 调用失败降级规则这些活跃
> 决定,以及"TMDB 搜索成功路径还没用真实 key 验证过"这条未解决缺口。下面
> 是完整的验证边界原文。

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

## 补充:搜索结果从文字列表改成海报网格(2026-08-14)—— 验证细节

> CLAUDE.md 保留了容器查询、卡片复用 `Card` token、三重编码选中态这几条
> 结论。下面是被移出的详细回归验证记录。

- **这次是纯展示层改动,数据行为一行没动**:fetch 调用、request-id 防竞态、
  搜索触发时机、选中后拉详情的次数全部原样。用 Playwright 专门回归验证过
  (不是只看截图):故意让先发的慢请求(3.5s)在后发的快请求(200ms)之后
  才返回,断言慢的那个**没有**覆盖掉新结果;选中后表单的
  title/durationMinutes/trailerUrl/description 都被正确预填;详情接口每次
  选中**只调用一次**;`contentRating`/`userRating` 仍然是空的(TMDB 不自动
  映射这两个字段)。改造前后各截了 5 张图对比。

## TMDB 署名合规:footer 组件(2026-08-14)—— logo 素材/挂载点/视觉权重实测细节

> CLAUDE.md 保留了官方文案原文引用、WebFetch 不可靠这条操作纪律、组件
> 存在的结论、以及视觉权重的实测数字。下面是完整的素材获取过程、挂载点
> 论述、以及详细验证方式。

- **logo 素材是 TMDB 官方原始 SVG,不是重新画的近似图形**:同样用 curl
  从 `themoviedb.org` 的品牌指南页直接下载,三个官方尺寸变体(短版/长版/
  方形)里选了"短版"(`blue_short.svg`,约 7.7:1 宽高比)——长版
  (约 13.8:1)在一行 footer 里过宽,方形版(约 1.39:1)对"文字旁边的
  一枚小 logo"这个位置来说太高,短版是唯一同时适合"一行内、和文字并排"
  的官方尺寸。文件字节不做任何修改直接进仓库
  (`frontend/public/tmdb-attribution.svg`),不重新导出/压缩/改色,和
  官方源文件逐字节一致,避免被认定为"篡改后的 logo"。
- **没有复用/新建通用 footer 组件,先确认过项目里确实没有已有的**——全仓库
  搜索过 footer/Footer 相关命名,结果只有各页面内部局部的收尾说明区块,
  没有一个是"全站落款"用途的共享组件。新建了
  `components/layout/tmdb-attribution.tsx`(`<TmdbAttribution>`),组件
  本体就是一个语义化的 `<footer>`,调用方不需要再包一层容器。
- **挂载点是现有布局的兄弟元素,没有改造布局结构**:顾客端
  `app/(customer)/layout.tsx` 本来就是 `flex min-h-full flex-col`
  (`Navbar` + `flex-1` 的 `<main>`),`<main>` 之后直接加一行
  `<TmdbAttribution />` 就自然获得"内容不够高时贴底、内容够高时跟着
  滚动"的效果,不需要额外样式。admin 端 `app/admin/layout.tsx` 没有这套
  flex-col 骨架(未授权骨架屏分支、授权后正常分支,两个 return 分支外层
  都只是普通的 `min-h-screen` `div`)——按明确指示不为了塞一行署名信息
  去重构现有布局,没有把它也改成 flex-col 追求同款贴底效果,两个分支各自
  在内容之后原样加一行,footer 停在内容流的自然末尾。顾客端有粘性贴底、
  admin 端没有,这个不对称是刻意接受的,不是遗漏。两个分支都加(不是只加
  授权后那个)是因为署名内容不依赖登录态,没有理由让未授权访问
  `/admin/**` 时少一份合规展示。
- **文案保持英文原文,没有翻译**:这是合规文本,翻译成中文可能不再满足
  条款里"place the following notice"这个要求逐字复述的语义,所以组件里
  唯一一段面向用户的文字保留英文——和站内其余全中文 UI 文案不一致是刻意
  的例外,不是遗漏本地化。
- **用 `<img>` 而不是 `next/image`**:这是一枚固定尺寸的本地静态 SVG,
  不需要 `next/image` 的响应式候选图/懒加载管线(矢量图也没有"多分辨率
  候选"这个概念,优化对它没有实际收益),直接用原生 `<img>` + 一行
  `eslint-disable-next-line @next/next/no-img-element` 跳过默认要求用
  `next/image` 的 lint 规则——跑过 `npm run lint` 确认这一行确实按预期
  消除了警告,不是猜测这条注释"应该"有效。
- **验证方式**:延续本项目一贯的"起一对隔离的前后端实例做验证,不碰主
  开发服务器"惯例——后端 `SERVER_PORT=8084`、
  `CORS_ALLOWED_ORIGINS=http://localhost:3006`,前端
  `NEXT_PUBLIC_API_BASE_URL=http://localhost:8084` 重新 build 后
  `next start -p 3006`。用 Playwright 分别访问顾客端首页(匿名)、admin
  未授权骨架屏分支(直接访问 `/admin/dashboard`,故意把
  `/api/v1/auth/refresh`/`/api/v1/users/me` 两个请求延迟 3 秒拉长这个
  分支的可观察窗口)、admin 授权后分支(用种子 ADMIN 账号
  `admin@cineverse.local` 走真实登录表单),三处全部读到 footer
  `isVisible()===true`、文案逐字符匹配、logo `naturalWidth: 300`
  (图片真的解码成功,不是碎图标)、以及视觉权重的实测数字。
  验证完杀掉两个隔离实例、删除临时的 `.env.local`/验证脚本,`git status`
  确认没有残留。

## skeleton.tsx 补 motion-reduce(2026-08-14)—— grep 核对六项清单的完整过程

> CLAUDE.md 保留了 1 行修法结论 + 准确的调用点清单。下面是重新核对旧清单
> 的完整过程和详细的验证方式。

- **先用 grep 重新核对"六个调用点"这份清单,没有直接沿用**——
  `grep -rln 'from "@/components/ui/skeleton"'` 只找到 5 个直接
  importer,不是 6 个。逐一核对差异:
  - `(customer)/bookings/page.tsx` 实际引入的是 `GlassSkeleton`
    (`components/glass/glass-skeleton.tsx`,本来就带
    `motion-reduce:animate-none`),不是这个共享 `Skeleton`——这个页面
    从来没有被这个问题影响过,旧清单把它算进去是错的。
  - `admin/dashboard/page.tsx` 本身不直接 import `Skeleton`,用的是
    `<ReportCardSkeleton />`,这个包装组件内部才引入共享 `Skeleton`——
    是一个真实但*间接*的受益点,旧清单没有区分"直接调用"和"通过包装
    组件间接受益",笼统混成了六个同等地位的调用点。
  - 另有 `components/admin/tmdb-search-picker.tsx` 这个第 5 个直接
    importer——它在上一次交付时已经对自己的三处 `Skeleton` 用法手动加过
    同款 `motion-reduce:animate-none`(见该次交付记录),这次共享组件的
    修复对它而言是重复(不是新增受益),这次没有顺手去删那三处手动加的
    冗余类名——用户这次任务范围明确只到"改
    `components/ui/skeleton.tsx` 这一个文件",清理另一个文件里的冗余
    类名不属于这次改动,不影响正确性(同一条规则被断言了两次,不冲突),
    留给以后有机会再顺手清。
  - 上面两处旧记录原样保留,没有静默改写那份六项清单本身,只在原处加了
    指向这里的"已解决"标注。
- **验证:读实际 computed `animationName`,不是只看 class 有没有挂上**
  ——延续这次 TMDB 署名合规交付定下的同一套隔离环境(后端 8084、前端
  3006)。用 Playwright 在两种 `reducedMotion` 上下文下分别打开
  `/admin/dashboard`(同样延迟 `/api/v1/auth/refresh`/`/api/v1/users/me`
  3 秒,拉长 `admin/layout.tsx` 未授权骨架屏分支——这个分支直接用到共享
  `Skeleton`——的可观察窗口):`reducedMotion: 'reduce'` 下读到
  `animationName: "none"`;`reducedMotion: 'no-preference'` 下读到
  `animationName: "pulse"`,确认修复只是给 reduced-motion 加了降级
  分支,没有连正常场景下的动效一起关掉。**两种上下文下骨架屏元素本身的
  存在和尺寸完全没变**(`boundingBox` 两次读到的都是同一个 64×32 矩形、
  `display: block`、`visibility: visible`)——修复去掉的只是动效,不是
  隐藏了整个 loading 状态,这一点也是实测确认的,不是从"应该不会有副
  作用"推断出来的。
