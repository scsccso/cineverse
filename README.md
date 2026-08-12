# CineVerse — 电影院在线选座订票系统

[![CI](https://github.com/scsccso/cineverse/actions/workflows/ci.yml/badge.svg)](https://github.com/scsccso/cineverse/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21_LTS-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-6DB33F)
![Next.js](https://img.shields.io/badge/Next.js-16-black)
![React](https://img.shields.io/badge/React-19.2-61DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)

一个电影院在线选座订票系统:浏览正在上映/即将上映的电影 → 挑场次 → 座位图实时
选座 → Stripe 在线支付 → 支付成功即拿到带二维码的电子票;管理员可以管理电影、
分店影厅、场次排期、用户账号,并查看销售/上座率报表。前后端分离(Next.js +
Spring Boot),PostgreSQL 存储,Redis 处理选座并发锁。

**这是一个求职作品集(Portfolio)项目**,目标是展示完整的全栈工程能力(并发
处理、支付幂等性、安全性、测试、CI),而不是一个 CRUD 堆砌的练习项目。

---

## 截图

| 首页 | 选座 |
|---|---|
| ![首页](docs/screenshots/homepage.png) | ![选座页](docs/screenshots/seat-selection.png) |

| 支付成功 · 电子票 | 管理后台 · 报表 |
|---|---|
| ![支付确认](docs/screenshots/payment-confirmed.png) | ![Admin 报表](docs/screenshots/admin-reports.png) |

以上均为本地跑起来后的真实截图(种子数据,非设计稿/占位图)。

**目前没有部署在线 demo**,只能按下方步骤本地运行——是否要部署到云端见
`CLAUDE.md` 第 4 节的开放问题,还没有定案,这里不假装有一个链接。

---

## 核心功能

- **实时座位锁定**——选座时,座位在持有窗口内对其他用户实时显示"锁定",避免
  同一座位被多人同时抢订。
- **完整的在线支付闭环**——接入 Stripe Checkout,选座 → 创建订单 → 跳转支付 →
  webhook 回调确认,一条真正走得通的支付流程,不是停在"模拟支付成功"。
- **电子票 + 入场核销**——支付成功后即拥有一张签名过的二维码电子票,现场扫码
  核验入场;同一张票重复核销会被拒绝。
- **管理后台报表**——销售报表(按日/周/月)与上座率分析,支持 CSV/PDF 导出。
- **Liquid Glass 视觉设计**——高光跟随指针动态移动的玻璃拟态卡片,配合流畅的
  页面转场与微交互动效。

---

## 技术亮点(给愿意往下深挖的读者)

下面每一条不只是技术名词罗列,而是"这么设计是为了解决什么问题":

- **Redis 分布式锁处理选座并发**——加锁用单条 `SET key value NX EX ttl` 命令,
  不是"先 GET 判断再 SET"的两步操作,从根本上排除两个并发请求都读到"未锁定"
  然后都执行 SET 的竞态窗口。Redis 没配置持久化,所以数据库层面还有一层预检查
  兜底真相来源;真正解决竞态的还是 Redis 原子锁——任何一个座位加锁失败,这次
  请求已经拿到的锁全部释放,返回 409 并明确指出冲突的是哪个座位。并发集成测试
  用两个线程真实并发提交同一座位,断言"恰好一个成功、一个失败,失败的那次数据库
  零残留"。
- **Stripe webhook 幂等性 + `ORPHANED_SUCCESS` 边界状态**——幂等键是
  `payments.stripe_session_id` 唯一约束,真正防止竞态的是 `SELECT ... FOR
  UPDATE` 行锁,不是"先查再判断"。更有意思的是一个容易被忽略的边界:如果座位
  持有窗口(5 分钟)到期释放后,用户的 Stripe 支付才姗姗来迟地成功——这时座位
  可能已经被别人订走,不能无脑把订单改回"已确认"。这种情况被标记成
  `ORPHANED_SUCCESS`(钱确实收到了,但不自动关联订单、也不自动退款),留痕
  但不做危险的自动化决策,退款走人工对账。
- **JWT 认证:内存 access token + httpOnly cookie 刷新令牌轮换**——access token
  (15 分钟)只存在前端内存里,不落 localStorage,降低 XSS 窃取风险;refresh
  token(7 天)走 httpOnly cookie,每次 `/refresh` 旧 token 立即在数据库标记
  `revoked`、下发新的一对 token(轮换,不是简单延期)。
- **管理后台报表用原生 SQL 聚合,不是拉全表到内存里用 stream 算**——`GROUP BY`
  + `date_trunc` + `generate_series` 直接在 Postgres 里完成,时间桶用
  `generate_series` 补零,保证前端拿到的永远是一条连续时间序列而不是稀疏数据。
- **无障碍设计不是事后补丁,是全站默认标准**——最小点击热区 44×44px(WCAG
  2.5.5);状态区分从不只靠颜色,边框样式 + 图标双重编码(比如座位图的"锁定"
  是虚线边框+锁图标,"已售"是实心灰底+勾选图标,色弱用户也能分辨);
  `prefers-reduced-motion` 做了 JS(`useReducedMotion()`)+ CSS
  (`motion-reduce:`)两层降级,任何一层代码路径漏掉判断,另一层还会兜底。
- **一条 ArchUnit 规则,真实拦下过 4 次同一类 bug**——`Hibernate` 的
  `@CreationTimestamp`/`@UpdateTimestamp` 只在 flush 时才真正填充字段,如果
  用普通 `save()` 而不是 `saveAndFlush()` 存库,拿到手的响应会带着一个空/
  过期的时间戳。这个 bug 在 Movie、Cinema、Hall 三个模块里被各自独立踩过一次
  之后,加了一条 ArchUnit 测试(`TimestampedEntitySaveFlushRuleTest`)扫描
  全部生产代码强制这个约定——后来在合并 Admin 用户管理模块时,CI 第四次
  真实拦下了同一类 bug(`AdminUserService.updateUserRole` 用了裸 `save()`)。
  这条规则不是摆设,是真的挡过东西。
- **主动设计并实测了"唯一管理员自锁"这个边界场景,不是被动修 bug**——admin 用户
  管理模块上线前,专门验证过一个真实场景:如果唯一的 ADMIN 账号把自己的角色
  改掉,或者把自己删除,会发生什么?直接用 curl 打真实接口复现了一次——种子
  ADMIN 账号真的被改成了 CUSTOMER,系统瞬间零管理员,APP 内没有任何路径能
  恢复,只能 `docker exec` 进容器直接改数据库把角色写回去。复现之后才落地
  服务端强制校验(不是前端禁用按钮那种"提示级"防护):`AdminUserService` 的
  `updateUserRole`/`deleteUser` 最前面判断操作对象是不是调用者自己,命中直接
  返回 409、不查库、不产生副作用。两层测试锁死这个保护:Mockito 单元测试用
  `verifyNoInteractions` 证明这个判断在任何数据库访问之前就短路了;
  Testcontainers 集成测试用真实种子账号打真实 HTTP 请求,确认自我锁定之后
  角色和账号确实原封未动,不是"响应报错但已经写库一半"。

### 测试哲学

后端(`cineverse-backend/`):JUnit 5 + Mockito 做快速单元测试,涉及数据库/
并发的场景用 **Testcontainers 起真实 Postgres**,不用 H2 糊弄——座位并发锁、
支付幂等性、时区边界这类问题在内存数据库上很可能测不出来。当前 **144 个测试
全部通过**,CI(GitHub Actions)在每次 push 时强制跑 `mvn clean test`,红了
不能合并。

前端(`frontend/`):CI 强制 `npm run build` + `npm run lint`;Playwright 在
开发过程中用于人工验证交互/无障碍效果(比如本页顶部的截图),但**目前没有
提交到仓库里的自动化前端测试套件**——这是一个诚实的现状,不是隐藏起来的
缺口。

---

## 已知的权衡与限制

这些是有意识做出的取舍或者尚未处理的缺口,不是发现问题后假装它不存在:

- **技术栈选了 Java 21 LTS + Spring Boot 3.5.15,而不是项目最初用的 Java 25 +
  Spring Boot 4.1**——纯粹是招聘市场匹配问题:目前主流招聘语境下"Java LTS"
  "Spring Boot 3" 的默认所指还是这两个版本,不是发布不久、生态还在追赶的
  更新版本。代价是 Spring Boot 3.5 已于 2026-06-30 OSS EOL(3.x 线的最后一个
  次版本,不存在"更新但仍在维护期"的 3.x 选项)——对一个不需要持续安全补丁
  供给的作品集项目,这个代价可以接受;完整的调研过程和取舍记录在 `CLAUDE.md`。
- **种子数据的电影海报/背景图来自 OMDb / TMDB,两者条款都禁止商业用途**——
  CineVerse 是非商业作品集项目,符合限制;但如果这个项目以后要商业化,这批
  图片数据源必须换掉。
- **TMDB 要求的可见署名(官方 logo + 指定文案)目前还没有在界面上实现**——
  这是一个明确的待办,不是遗漏:代码里确实还没有对应的 UI 元素。
- **电影分级显示的是 OMDb 提供的美国 MPAA 分级(如 PG-13、R),没有映射成
  马来西亚本地的 LPF 分级**——尽管应用本身以马来西亚分店为背景(MYR 定价、
  吉隆坡时区),这一条目前是已知的、有意暂缓处理的差距,不是被忽略掉了。
- **没有在线 demo**——部署目标(本地 Docker 够用 vs. 部署到云端给一个可访问
  链接)还是一个开放决策,见 `CLAUDE.md` 第 4 节。

---

## 技术栈

### 后端(`cineverse-backend/`)
- Java 21 (LTS) + Spring Boot 3.5.15 + Spring Security 6.x
- PostgreSQL 16 + Flyway(schema 版本化迁移)
- Redis 7(座位锁;refresh token 撤销走数据库 `revoked` 字段,不经过 Redis)
- Spring Data JPA + Hibernate 6.6 + MapStruct(Entity 不直接暴露给 Controller)
- JWT(jjwt)+ BCrypt;电子票二维码编码复用同一套 jjwt 签名机制
- Stripe Checkout(测试模式;webhook 签名验证 + 幂等确认)
- OpenPDF(LGPL/MPL,报表 PDF 导出)+ 手写 CSV 导出
- springdoc-openapi(Swagger UI)
- JUnit 5 + Mockito + Testcontainers + ArchUnit
- Maven

### 前端(`frontend/`)
- Next.js 16(App Router + Turbopack)+ React 19.2
- TypeScript(严格模式)
- Tailwind CSS 4 + shadcn/ui(深色主题,暖金色强调色)
- framer-motion(页面转场 / 微交互动效,全站支持 `prefers-reduced-motion`)
- react-hook-form + zod
- qrcode.react(电子票二维码)
- recharts(管理后台报表图表)

---

## 项目结构

```
CineVerse/
├── CLAUDE.md                 # 项目记忆:架构决策、路线图、每一步取舍的完整记录
├── docs/DEVELOPMENT.md       # 开发者参考:API curl 示例、环境配置、手动验证步骤
├── docs/DATABASE.md          # 数据库 schema 参考:表结构、外键策略、字段级说明
├── docker-compose.yml        # 本地依赖:Postgres 16 + Redis 7
├── .env.example               # docker-compose 变量 + 后端进程直接读取的 Stripe key
├── cineverse-backend/        # Spring Boot 后端(Maven 项目)
│   └── src/main/resources/db/migration/  # Flyway 迁移脚本
└── frontend/                 # Next.js 前端
    ├── .env.example           # NEXT_PUBLIC_API_BASE_URL 样例
    └── src/
        ├── app/
        │   ├── (customer)/    # 顾客端路由组:/, /login, /register, /profile, /showtimes/[id](/seats), /bookings(/[id]/confirmed)
        │   └── admin/         # 管理后台(独立导航外壳 + 角色校验):/admin/dashboard, /admin/movies(/new, /[id]/edit), /admin/users
        ├── components/        # ui(shadcn) / auth / layout / motion / booking / admin
        ├── lib/                # api 客户端、auth context、zod schema
        └── proxy.ts           # 路由保护(Next 16:middleware 改名 proxy)
```

---

## 本地运行

### 前置条件

- JDK 21(推荐 [Eclipse Temurin](https://adoptium.net/))+ Maven 3.9+
- Node.js 20.9+ + npm
- Docker + Docker Compose

### 最短路径

```bash
# 1. 启动依赖(Postgres + Redis)
cp .env.example .env
docker compose up -d

# 2. 启动后端(新终端)
cd cineverse-backend
mvn spring-boot:run

# 3. 启动前端(新终端)
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

打开 http://localhost:3000 。种子管理员账号 `admin@cineverse.local` /
`Admin@12345` 可以直接体验后台管理功能;也可以自行注册账号体验顾客端选座
订票流程。

跑测试、配置 Stripe 测试 key、更详细的环境排错见
[`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md)——这里只给最短路径。

---

## 数据来源与致谢

种子数据里的电影海报(`poster_url`)来自 [OMDb API](https://www.omdbapi.com/),
横版背景图(`backdrop_url`)来自 [TMDB API](https://www.themoviedb.org/)——两者
分工不同:OMDb 免费层没有专门的横版剧照字段,TMDB 的 `backdrop_path` 才是,
所以海报和背景图特意用了两个不同数据源。图片都是直接从对方返回的地址读取
展示,没有下载后二次分发。两边的使用条款都禁止商业用途,CineVerse 是非商业
个人作品集项目,符合这条限制(完整决策记录见 `CLAUDE.md`)。

---

想深挖某个技术决定背后的权衡过程(比如为什么选 Stripe Checkout 而不是自建
支付表单、座位锁 TTL 为什么是 5 分钟、这次降级 Java/Spring Boot 版本时踩过
哪些坑)可以看 [`CLAUDE.md`](./CLAUDE.md)——完整记录了每一步架构选型和取舍
的原因,是给愿意刨根问底的读者(比如面试官)准备的加分材料,**不是理解这个
项目必须先读完的前提**。
