# 顾客端交互反馈强化方案(方案文档,未落地)

> 状态:**已于 2026-08-09 落地**(第 5 节优先级 1~5 的各项;优先级 6 的两项
> ——座位按钮 hover 缩放、结算栏文案交叉淡入——有意未做)。实施记录、实测验证
> 方式,以及实施过程中发现本文档一处判断有误(见 3-b 的更正)都写在 CLAUDE.md
> 的"顾客端交互反馈强化"一节。下文保留提案时的原始措辞作为历史记录,不回溯改写。
>
> 原始状态:**方案讨论稿,零代码改动**。范围限定为顾客端桌面视口(PC),不涉及
> `/admin/**`、不涉及移动端专项(移动端收纳已在别的批次处理)。这是
> [`design-proposal-customer-editorial.md`](./design-proposal-customer-editorial.md)
> (视觉密度/编辑排版方向)的姊妹文档,那份文档里提出的改动(Hero 去卡片化、
> 个人中心居中、座位图银幕弧形)已经落地,这次是同一批页面的另一个维度——不是
> "看起来够不够有编辑感",而是"操作时有没有被系统听见"。
>
> 硬性前提(贯穿全文):**任何新增的交互反馈,不能让触屏/键盘用户失去等价的
> 可达路径**——纯 hover 触发的功能必须同时有点击/聚焦触发的等价方式。下文每一条
> 建议都在"自查"列里过一遍这条,不是走过场。

---

## 0. 先说结论:三类现状差异很大

- **悬停反馈**:大部分已经被 `GlassCard`(卡片级 hover:边框+光斑+`scale(1.02)`)
  和共享 `Button` 组件(各 variant 都带 `hover:` 背景色)系统性覆盖,真正的缺口
  集中在座位按钮(有意的性能例外,见 CLAUDE.md 1.5)和一处"双重缩放"的小冗余。
- **状态过渡**:路由级(`PageTransition`)和表单内banner(`AnimatedFormBanner`)
  已经做得很好;但选座页内部三个屏幕(选座网格 ↔ 倒计时确认 ↔ 已过期)之间是**纯
  硬切**,这是本文档里唯一算得上"体验缺口"而不只是"锦上添花"的一项。
- **关键操作反馈分量感**:登录/注册表单有 `SubmitProgressBar`(表单顶部扫光条)
  撑场面,但订票流程里风险最高的两步——"确认选座"(并发抢座的临界点)和"去支付"
  (触发真实网络请求+跳转 Stripe)——反而只有按钮文字从"确认选座"变成"提交中…"
  这一种最轻量的反馈,是全文档分量最重的发现。

---

## 1. 悬停反馈(Hover Feedback)

| # | 页面/组件 | 现状 | 建议 | 自查:是否违反硬性前提 |
|---|---|---|---|---|
| 1-a | 首页 `MovieCard`(`components/movies/movie-card.tsx:19`) | 海报图片自带 `transition-transform duration-300 group-hover:scale-105`,**纯 CSS `:hover`,没有 `motion-reduce:` 降级**,同时外层 `GlassCard` 自己也有 `whileHover: {scale:1.02}` + 光斑——鼠标悬停时其实是"整卡放大 1.02 倍 + 卡内海报再放大 1.05 倍"两层缩放叠加,不是刻意设计的层次感,更像两处各写各的没对齐。 | 二选一:①去掉海报单独的 `scale-105`,只保留 `GlassCard` 的卡级缩放(更简单,一处维护);②如果想保留海报专属的"聚焦感",把 `group-hover:scale-105` 换成 `motion-reduce:scale-100`(纯 CSS 降级,不需要 JS)。两种都不新增交互,只是让现有效果自洽。 | 不违反——这是纯装饰性视觉反馈,叠加在一个本来就靠 `<Link>` 语义可点击/可聚焦/可回车激活的卡片上,hover 状态的有无不影响能不能进入电影详情页。 |
| 1-b | 电影详情/首页 `ShowtimeList`(场次胶囊,`showtime-list.tsx:25`) | 复用 `GlassCard`,自带 hover 边框加亮 + 光斑 + `scale(1.02)`。 | 不需要改,已经是本文档里"达标"的参照系。 | — |
| 1-c | 选座页 `SeatButton`(`components/booking/seat-map.tsx:152-163`) | 只有 `transition-colors duration-150` 做边框/文字颜色变化(`hover:border-primary/50 hover:text-foreground`),没有任何缩放/位移——这是 CLAUDE.md 1.5 里明确记录的性能例外(百来个座位按钮不能每个都挂 `GlassCard` 的 `pointermove` 监听)。 | 这条例外**只排除了 `GlassCard` 复用和指针跟随高光**,没有排除一个零成本的纯 CSS `hover:scale-[1.03]`(不需要 JS、不需要 `pointermove`、不新增监听器)。可以加,但优先级最低——座位按钮已经有 `whileTap: {scale:0.9}` 的点击反馈,hover 反馈的边际收益比其他两类小。 | 不违反——纯 CSS `:hover`,不影响 `aria-pressed`/键盘 Tab+Enter 的既有可达路径,且是可选项不是必做项。 |
| 1-d | 电影详情页顶部信息卡(`movies/[id]/page.tsx:48`) | `GlassCard` 包裹,同 1-b,已覆盖。 | 不需要改 | — |
| 1-e | 各页面 CTA 按钮(`components/ui/button.tsx`) | 所有 variant 都在 `buttonVariants` 里带 `hover:` 背景色变化(如 `hover:bg-primary/80`),这是全站共享基础组件,自动覆盖本文档涉及的每一个按钮。 | 不需要改,已经系统性覆盖 | — |
| 1-f | Hero 轮播箭头/圆点(`hero-carousel.tsx`) | 箭头 `hover:bg-white/15`,圆点 `transition-all duration-300`(选中态宽度/颜色变化),均已有 hover 反馈。 | 不需要改 | — |
| 1-g | 可点击卡片的**点击态**(`MovieCard`/`ShowtimeList` 内的 `GlassCard`) | `GlassCard` 只定义了 `whileHover`,**没有 `whileTap`**——鼠标点下瞬间到路由跳转完成之间,没有任何"点击已被接收"的即时视觉信号,要等 `PageTransition` 触发才有反馈,中间可能有几十到几百毫秒的"没反应"错觉。 | 给 `GlassCard` 补一个轻量 `whileTap: {scale: 0.98}`(用它已有的 `reduceMotion` 判断做降级,不新增判断分支)。这不是新组件,是给已有组件补一个和 `whileHover` 同源的属性,影响范围是 `glass-card.tsx` 一处,自动传播到所有复用它的卡片(`MovieCard`、`ShowtimeList`、电影详情信息卡)。 | 不违反——点击态本来就是点击/回车触发,不是 hover 独有,天然对触屏和键盘都成立(键盘 Enter 激活 `<a>` 时浏览器不会触发 CSS `:active`/framer 的 `whileTap`,但这不算"失去等价路径"——路由跳转本身仍然正常发生,只是缺一个纯装饰性的按压反馈,不影响任何人完成操作)。 |

---

## 2. 状态过渡(筛选器/标签页类切换)

顾客端目前**没有真正的筛选器或标签页组件**(genre/日期筛选目前只存在于后端 API
能力里,没有暴露成前端可交互的筛选 UI;首页"正在热映"/"即将上映"是两个静态区块
用锚点滚动衔接,不是标签切换)。下表覆盖的是功能最接近的等价场景:同一路由内、
同一组件树里发生的"整屏内容替换"。

| # | 场景 | 现状 | 建议 | 自查 |
|---|---|---|---|---|
| 2-a | 路由切换(`components/motion/page-transition.tsx`) | 已经是 `AnimatePresence` 包裹的 opacity+y 过渡,`prefers-reduced-motion` 两层降级齐全。 | 不需要改,是本文档的参照基准。 | — |
| 2-b | 表单顶部错误/提示 banner(`animated-form-banner.tsx`) | 已经是 height+opacity 的进出场过渡,`reduceMotion` 判断到位。登录/注册/选座页的错误提示、"座位刚被抢走"提示都在用它。 | 不需要改 | — |
| 2-c | **选座页内部三态切换**(`components/booking/seat-picker.tsx:247-275`) | `if (booking) return <BookingConfirmation>`、`if (expired) return <GlassCard>...过期提示`、否则渲染座位网格——三个分支是纯 React 条件渲染,**没有任何 `AnimatePresence`/过渡包裹**,一次 `setState` 后整个屏幕内容瞬间替换。这三个状态之间的切换恰恰是用户在这条流程里最需要"确认系统跟上了我的操作"的时刻(提交选座成功、倒计时到期),现在反而是全站唯一的硬切点。 | 用 `AnimatePresence mode="wait"` 包裹这三个分支(用 `booking?.id ?? (expired ? "expired" : "selecting")` 之类的值做 `key`),复用 `lib/motion.ts` 的 `EASE_APPLE` + 已有的 `useReducedMotion()` 降级写法——和 `HeroCarousel`/`PageTransition` 已经在用的模式完全一致,不是引入新模式。 | 不违反——纯状态切换动效,不依赖 hover,对所有输入方式一视同仁;`prefers-reduced-motion` 开启时和现在的硬切视觉效果一致(过渡时长归零),不会变得更差。 |
| 2-d | 路由级骨架屏 → 真实内容(`movies/[id]/loading.tsx`、`showtimes/[id]/seats/loading.tsx`) | Next.js Suspense 边界的默认行为——`GlassSkeleton` 卸载、真实内容挂载是瞬时替换,没有 crossfade。 | 用已有的 `<FadeIn>`(`components/motion/fade-in.tsx`,`profile`/电子票页已经在用)包一层真实页面的顶层返回值,做一个"内容挂载时淡入"的效果——不需要动 `loading.tsx` 本身(骨架屏到消失那一刻本来就是 Next.js 控制的,改不了那个边界),只是让"新内容出现"这个瞬间更柔和,而不是生硬弹出。 | 不违反——挂载动效,和 hover 无关,`FadeIn` 自带 reduced-motion 降级。 |
| 2-e | `SelectionSummaryBar` 内文案切换(`seat-picker.tsx:326-341`) | "请选择座位(可多选)" 到 "已选 N 个座位:…+ 价格" 之间是瞬时文字替换,每次点选/取消座位都会立刻跳变一次。 | 优先级最低的一项:可以给这段文案加一个很轻的 `AnimatePresence`(类似 `AnimatedFormBanner` 的做法)做 opacity 交叉淡入淡出,避免价格数字在快速多选时"闪烁感"过强。不是必须项,当前的瞬时替换不算体验缺陷,只是可以更顺滑。 | 不违反 |

---

## 3. 关键操作反馈分量感

| # | 操作 | 现状 | 建议 | 自查 |
|---|---|---|---|---|
| 3-a | 登录/注册提交(`login-form.tsx:54`、`register-form.tsx:66`) | `SubmitProgressBar`(表单顶部一条扫光进度条,`prefers-reduced-motion` 时降级成静态满宽条)+ 按钮文字变"登录中…"/"注册中…" + `AnimatedFormBanner` 承接失败态。三层反馈叠加,是全站分量最重、做得最完整的一处。 | 不需要改,是本节的参照基准和可复用的现成组件。 | — |
| 3-b | **选座页"确认选座"**(`seat-picker.tsx:343-350`,`SelectionSummaryBar` 内) | 只有按钮文字从"确认选座"变成"提交中…"(`isSubmitting` 状态),没有 `SubmitProgressBar` 或任何等价的进度提示。这一步在后端要经历"数据库预检查 + Redis 原子加锁"两层并发校验(见 CLAUDE.md Phase 5),失败率不算低(409 抢座冲突是设计内会发生的正常分支),恰恰是最需要让用户感觉"系统正在认真处理,不是卡住了"的一步,现在反馈强度反而是全站最弱的几处之一。 | `SubmitProgressBar` 不要求外层必须是 `<form>`,只吃一个 `active: boolean`,定位是 `absolute inset-x-0 top-0`——可以直接搬进 `SelectionSummaryBar` 顶部,`active={isSubmitting}`。不需要新造组件。**(提案时此处写的是"需要给它加 `relative`"——实施时确认这条判断有误:该容器已经是 `fixed`,而 `position: fixed` 本身就是定位元素,会为 `absolute` 子元素建立包含块,不需要任何额外类名。)** | 不违反——纯提交反馈,和 hover 无关,对鼠标/触屏/键盘提交(Enter 触发按钮)一视同仁。 |
| 3-c | **`BookingConfirmation` "去支付"**(`booking-confirmation.tsx:127-133`) | 同样只有按钮文字("去支付"→"正在跳转到支付页面…"),但这一步触发的是真实网络请求(`POST /bookings/{id}/checkout` 创建 Stripe Checkout Session)再跳转,不是纯本地状态切换——网络延迟期间用户盯着一个只变了文字的按钮。这是全流程里最高风险的一次点击(不确认好不好没关系,这里点错/没反应会直接影响到"钱"这件事的信任感)。 | 同 3-b,把 `SubmitProgressBar` 加到这张 `GlassCard`(它本身已经是常规布局,加一个 `relative` 类名即可)顶部,`active={isCheckingOut}`。 | 不违反,理由同上。 |
| 3-d | `BookingConfirmation` "取消选座" | 同款文字切换("取消中…"),风险和紧迫感都低于 3-c(取消不涉及金钱,失败了大不了再点一次或等自然过期)。 | 不建议加重——这是这次审计里少数几个"现状已经够用,不需要升级"的按钮,加 `SubmitProgressBar` 反而会让页面里两条进度条(去支付/取消选座各一条)同时存在时的视觉优先级混乱。 | — |
| 3-e | `LogoutButton`(`components/auth/logout-button.tsx`) | 按钮文字切换("退出登录"→"退出中…")。 | 不建议加重——登出是低风险、低焦虑操作,失败了刷新页面重试成本极低,现有反馈已经匹配这个操作的分量,过度强调反而不必要。 | — |
| 3-f | 座位点选(`SeatButton` 的 `whileTap: {scale:0.9}`) | 已经有即时的按压缩放反馈,鼠标/触屏都能触发(`whileTap` 是 framer-motion 的手势层,覆盖 pointer 事件,不区分输入设备)。 | 不需要改,是本节里"轻量操作、轻量反馈"匹配得当的例子。 | — |

---

## 4. 无障碍硬性约束复核清单

和 `design-proposal-customer-editorial.md` 第 3 节同样的做法——列出本方案涉及
的改动分别会不会碰到既定的三条约束,方便实施后逐条勾选复核:

| 约束 | 本方案里可能涉及的改动 | 复核方式 |
|---|---|---|
| 悬停反馈必须有非 hover 的等价可达路径(本文档新增的硬性前提) | 1-a/1-b/1-c/1-g 全部是叠加在已有可点击/可聚焦元素上的装饰性反馈,不新增任何"只能靠 hover 才能触发"的功能;3 类建议里没有一条把某个操作的唯一触发方式设计成 hover。 | 实施后分别用纯键盘(Tab + Enter)和触屏模拟走一遍首页→详情→选座→支付的完整链路,确认每一步都能不依赖 hover 完成。 |
| 44×44 最小点击热区 | 本方案不改任何按钮尺寸(`SeatButton` 的 `h-11`、各 CTA 按钮尺寸均未涉及),`SubmitProgressBar` 是一条 `h-0.5` 的装饰条,不是可点击元素。 | 无需重新量,尺寸没有改动点。 |
| `prefers-reduced-motion` 两层降级(JS+CSS) | 2-c(选座三态切换)、2-d(骨架屏→内容淡入)复用的都是已有的 `useReducedMotion()` + `EASE_APPLE` 模式;1-a 的建议本身就是"给一个漏掉降级判断的纯 CSS hover 补上 `motion-reduce:`";3-b/3-c 复用 `SubmitProgressBar`,该组件自己已经处理好降级(见组件内注释)。 | 实施后系统开启"减弱动态效果",逐项确认新增/修改的动效要么被跳过要么退化成静态展示,而不是新增一个没做降级判断的动效。 |

---

## 5. 汇总与优先级建议

按"当前反馈强度和操作/内容重要性之间的落差"排序,不是按实现成本排序:

1. **3-b、3-c(选座提交 + 去支付复用 `SubmitProgressBar`)**——本文档分量最重的
   两项,现成组件直接复用,改动范围小(各一处 JSX + 一处可能需要的 `relative`
   定位类名),但解决的是全流程里风险最高、反馈最弱的落差。
2. **2-c(选座页三态切换加 `AnimatePresence`)**——唯一称得上"硬切体验缺口"的
   状态过渡问题,复用已有动效模式,不引入新概念。
3. **1-g(`GlassCard` 补 `whileTap`)**——一行属性,影响范围自动扩散到所有复用
   `GlassCard` 的可点击卡片,性价比高。
4. **1-a(`MovieCard` 双重缩放去重/补降级)**——修复一个现状里的小冗余/小疏漏,
   不是新功能。
5. **2-d(骨架屏→内容用 `FadeIn` 淡入)**——低风险的体验打磨。
6. **1-c(座位按钮可选的轻量 hover 缩放)、2-e(结算栏文案交叉淡入)**——锦上添花,
   优先级最低,不做也不算缺陷。

这份文档到此为止是纯讨论稿,没有修改任何代码。你看完之后告诉我想先做哪几项,
我再动手。
