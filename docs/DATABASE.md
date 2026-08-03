# CineVerse — 数据库 Schema 参考(docs/DATABASE.md)

> 本文件是 `cineverse-backend/src/main/resources/db/migration/` 下所有 Flyway
> migration 的人类可读整理版,回答"这张表长什么样、为什么长这样"。
> **维护规则(已写入 CLAUDE.md 第 2 节)**:任何新增/修改 migration 的
> Phase,交付前必须同步更新本文件,和更新 README.md/CLAUDE.md 一样是
> 强制项,不是可选项。
> 每张表标注了"引入于"哪个 Phase——对照 `CLAUDE.md` 第 3 节的 Phase
> 记录,能查到"当时为什么这么设计"的完整背景和权衡过程,本文件本身只
> 整理"现状是什么",不重复那些设计推理。
> 当前覆盖:V1 ~ V12(Phase 1~7)。

---

## 目录速览(按迁移顺序)

| 迁移文件 | 引入的表 / 变更 | Phase |
|---|---|---|
| `V1__init.sql` | `users`、`refresh_tokens` | Phase 1 |
| `V2__seed_admin.sql` | 种子数据(管理员账号),无 schema 变更 | Phase 1 |
| `V3__movies.sql` | `genres`、`movies`、`movie_genres` | Phase 2 |
| `V4__seed_genres.sql` | 种子数据(15 个固定 genre),无 schema 变更 | Phase 2 |
| `V5__cinemas_halls_seats.sql` | `cinemas`、`halls`、`seats` | Phase 3 |
| `V6__seed_cinema_halls_seats.sql` | 种子数据(1 分店 3 影厅 + 座位),无 schema 变更 | Phase 3 |
| `V7__showtimes.sql` | `showtimes` | Phase 4 |
| `V8__showtimes_fk_restrict.sql` | 修正 `showtimes.movie_id`/`hall_id` 外键:`CASCADE` → `RESTRICT` | Phase 4(事后补丁) |
| `V9__bookings.sql` | `bookings`、`booking_seats` | Phase 5 |
| `V10__payments.sql` | `payments` | Phase 6 |
| `V11__payments_orphaned_success_status.sql` | 修正 `payments.status` 的 CHECK 约束,新增 `ORPHANED_SUCCESS` | Phase 6(事后补丁) |
| `V12__bookings_redeemed_at.sql` | `bookings` 新增 `redeemed_at` 字段(入场核销) | Phase 7 |

---

## 表关系总览

```
users ──1:N──▶ refresh_tokens
users ──1:N──▶ bookings

cinemas ──1:N──▶ halls ──1:N──▶ seats
                                   ▲
                                   │ N:1(经 booking_seats)
movies ──N:N──▶ genres            │
   │        (经 movie_genres)     │
   │                              │
   ├──1:N──▶ showtimes            │
   │            ▲                │
   │            │ N:1(hall_id)   │
   │          halls               │
   │            │                │
   │            ▼ 1:N            │
   │         bookings ──1:N──▶ booking_seats ──N:1──┘
   │            │
   │            ▼ 1:N
   │         payments
```

纯文字版(和上面 ASCII 图对应,方便搜索/引用):

- `users` 1──N `refresh_tokens`(一个用户可有多条 refresh token 历史记录,token rotation 产生)
- `users` 1──N `bookings`(一个用户可有多笔订单)
- `cinemas` 1──N `halls`
- `halls` 1──N `seats`
- `movies` N──N `genres`(通过 `movie_genres` 连接表)
- `movies` 1──N `showtimes`
- `halls` 1──N `showtimes`
- `showtimes` 1──N `bookings`
- `bookings` 1──N `booking_seats`
- `seats` 1──N `booking_seats`(同一座位在不同场次/不同时间会出现在多条 `booking_seats` 里,这是历史记录,不代表"当前占用")
- `bookings` 1──N `payments`(一笔订单可能对应多次 Checkout 尝试——放弃后重试会新建一行,而不是复用旧行,见 `payments` 表说明)

---

## ⚠️ ON DELETE RESTRICT 一览(高优先级,别手滑写成 CASCADE)

以下外键全部是 **Phase 4(V7 踩坑后由 V8 修正)和 Phase 5/6 吸取同一个教训后,
从一开始就写对**的 `RESTRICT`。核心原则:**删除一条"上游"记录,绝不能悄无声息
地连带销毁下游的排期/交易历史**。以后新增外键,凡是"下游记录代表真实发生过的
业务事实(排期、订单、支付)"的场景,默认应该是 `RESTRICT`,`CASCADE` 只留给
"纯粹的从属/联接行,离开父记录本身没有独立意义"的场景(比如 `booking_seats`、
`movie_genres`)。

| 外键 | 指向 | 策略 | 为什么 |
|---|---|---|---|
| `showtimes.movie_id` | `movies.id` | `RESTRICT` | V7 最初写成 `CASCADE`,删电影会连带删光它所有排期——V8 修正。应用层 `MovieService.delete()` 也会先查 `existsByMovieId` 主动挡掉,返回干净的 409,这个约束是兜底防线。 |
| `showtimes.hall_id` | `halls.id` | `RESTRICT` | 同上一并修正,虽然 Hall 目前没有 delete API,还触发不到,但从一开始就写对。 |
| `bookings.user_id` | `users.id` | `RESTRICT` | 删除用户不能销毁其订单历史(交易记录)。 |
| `bookings.showtime_id` | `showtimes.id` | `RESTRICT` | 删除场次不能销毁已产生的订单——`ShowtimeService.delete()` 同样先查 `existsByShowtimeId` 挡在应用层。 |
| `booking_seats.seat_id` | `seats.id` | `RESTRICT` | 座位一旦被任何订单引用过,就不能被删除(座位本身目前也没有独立的 delete API,但约束提前写对)。 |
| `payments.booking_id` | `bookings.id` | `RESTRICT` | 支付记录是财务凭证,不能因为删除 booking 就消失。booking 目前也没有 delete API,同样是"提前写对"。 |

**对照组(有意的 `CASCADE`,不是疏漏)**:`refresh_tokens.user_id`(用户没了,
会话历史没有留存价值)、`movie_genres.*`、`booking_seats.booking_id`(纯连接/
从属行,离开父记录没有独立意义——注意 `booking_seats` 对 `bookings` 是
`CASCADE`,对 `seats` 却是 `RESTRICT`,这两个方向的策略不对称是有意的,取决于
"谁是这条从属记录真正依附的一方")。

---

## 🧮 计算 / 派生字段一览(不是用户直接输入,别加"编辑"接口)

| 字段 | 怎么算出来的 | 备注 |
|---|---|---|
| `showtimes.end_time` | `start_time + movie.duration_minutes` | 应用层计算(`ShowtimeService`),请求体不接受手动传入;没有 DB 触发器保证同步,因为**没有更新场次的 API**——改时间只能删除重建,所以不存在"改了 start_time 但 end_time 没跟着变"的路径。 |
| `seats` 的"占几列宽"(`columnSpan`) | **不是数据库字段**——`seats` 表里根本没有 `column_span` 列。它是 API 响应层的派生值(`STANDARD` → 1,`COUPLE` → 2),每次响应时按 `seat_type` 现算(`BookingService.columnSpanFor` / `SeatMapper`),没有落库,也没有必要落库。 |
| `booking_seats.price_at_booking` | 创建订单那一刻从 `showtimes.price` 快照下来的 | 之后 showtime 改价不会回溯影响历史订单——这是"写入时计算一次,写入后不再跟随源头变化"的一种派生,不是普通的用户输入字段。 |
| `payments.amount` / `payments.currency` | 创建 Checkout Session 那一刻从 `bookings.total_price` / `app.stripe.currency` 配置快照下来的 | 同上,不是 Stripe 或用户直接指定的值。 |
| `bookings.expires_at` | 创建 booking 时按常量 `HOLD_DURATION`(5 分钟)算出的 `now() + 5min` | 不是用户输入;懒惰过期读取时如果超过这个时间戳会被应用层标记 `EXPIRED`(没有 DB 端定时任务或触发器)。 |

---

## 表详情

### `users`(Phase 1,`V1__init.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `email` | `VARCHAR(255)` | 否 | — | **唯一**(`uq_users_email`) |
| `password_hash` | `VARCHAR(255)` | 否 | — | BCrypt hash,从不存明文 |
| `role` | `VARCHAR(20)` | 否 | — | CHECK 限定 `CUSTOMER` \| `ADMIN` |
| `full_name` | `VARCHAR(255)` | 是 | — | |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。外键:无(顶层表)。索引:无额外索引(`email` 的唯一约束自带索引)。

### `refresh_tokens`(Phase 1,`V1__init.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `user_id` | `UUID` | 否 | — | FK → `users.id`,**`ON DELETE CASCADE`** |
| `token_hash` | `VARCHAR(255)` | 否 | — | **唯一**(`uq_refresh_tokens_token_hash`);只存 hash,不存原始 JWT |
| `expires_at` | `TIMESTAMPTZ` | 否 | — | |
| `revoked` | `BOOLEAN` | 否 | `false` | token rotation 时旧 token 标记为 `true`,不是物理删除 |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。索引:`idx_refresh_tokens_user_id`。

### `genres`(Phase 2,`V3__movies.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `name` | `VARCHAR(100)` | 否 | — | **唯一**(`uq_genres_name`) |

主键:`id`。外键:无。种子数据见 `V4__seed_genres.sql`(15 个固定值,没有
genre 管理 API)。

### `movies`(Phase 2,`V3__movies.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `title` | `VARCHAR(255)` | 否 | — | |
| `description` | `TEXT` | 是 | — | |
| `tagline` | `VARCHAR(500)` | 是 | — | |
| `duration_minutes` | `INTEGER` | 否 | — | CHECK `> 0` |
| `content_rating` | `VARCHAR(20)` | 是 | — | 分级("PG-13"),和 `user_rating` 是两个独立概念,不要混用 |
| `user_rating` | `NUMERIC(3,1)` | 是 | — | Admin 手填的数值评分;CHECK `0~10` 或 `NULL` |
| `poster_url` | `VARCHAR(500)` | 是 | — | 应用层永不返回 `NULL`(没上传时用占位图 URL 兜底,兜底逻辑在 Service 层,不在 DB) |
| `backdrop_url` | `VARCHAR(500)` | 是 | — | 同上 |
| `trailer_url` | `VARCHAR(500)` | 是 | — | |
| `status` | `VARCHAR(20)` | 否 | — | CHECK 限定 `NOW_PLAYING` \| `COMING_SOON` \| `ENDED` |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。外键:无。索引:`idx_movies_status`。

### `movie_genres`(Phase 2,`V3__movies.sql`)

纯连接表,复合主键,无自增 `id` 列。

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `movie_id` | `UUID` | 否 | — | FK → `movies.id`,**`ON DELETE CASCADE`**;复合主键的一部分 |
| `genre_id` | `UUID` | 否 | — | FK → `genres.id`,**`ON DELETE CASCADE`**;复合主键的一部分 |

主键:`(movie_id, genre_id)`。索引:`idx_movie_genres_genre_id`。

### `cinemas`(Phase 3,`V5__cinemas_halls_seats.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `name` | `VARCHAR(255)` | 否 | — | |
| `address` | `VARCHAR(500)` | 是 | — | |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。外键:无(顶层表)。种子数据:1 家分店,见 `V6`。

### `halls`(Phase 3,`V5__cinemas_halls_seats.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `cinema_id` | `UUID` | 否 | — | FK → `cinemas.id`,**`ON DELETE CASCADE`** |
| `name` | `VARCHAR(255)` | 否 | — | |
| `total_rows` | `INTEGER` | 否 | — | CHECK `> 0`(和 `total_columns` 一起) |
| `total_columns` | `INTEGER` | 否 | — | CHECK `> 0` |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。索引:`idx_halls_cinema_id`。**没有更新/局部改布局的 API**——影厅
创建后座位随之自动生成,换布局要删除整个 hall 重建(Phase 3 有意收窄的 MVP
边界)。种子数据:3 个影厅,见 `V6`。

### `seats`(Phase 3,`V5__cinemas_halls_seats.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `hall_id` | `UUID` | 否 | — | FK → `halls.id`,**`ON DELETE CASCADE`** |
| `row_label` | `VARCHAR(5)` | 否 | — | 如 "A"、"F";和 `column_number` 一起构成 `uq_seats_hall_row_column` |
| `column_number` | `INTEGER` | 否 | — | CHECK `> 0`;COUPLE 座位存的是这一对里左边那一列 |
| `seat_type` | `VARCHAR(20)` | 否 | — | CHECK 限定 `STANDARD` \| `COUPLE`(只有这两种,VIP 用价格系数解决,不新增座位类型) |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | **没有 `updated_at`**——座位创建后不可变,改布局是删除整个 hall 重建,不是逐条更新 |

主键:`id`。唯一约束:`uq_seats_hall_row_column`(`hall_id, row_label,
column_number`)——只能挡住"起始坐标"重复,挡不住 COUPLE 座位隐含的第二列
和邻座重叠;那部分靠 `SeatLayoutGenerator` 在生成时通过构造过程保证不重叠
(建表约束之外的应用层保证,见其单元测试)。索引:`idx_seats_hall_id`。
"占几列宽"是纯派生字段,不落库——见上面 🧮 一览。

### `showtimes`(Phase 4,`V7__showtimes.sql`;外键在 Phase 4 由 `V8` 修正)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `movie_id` | `UUID` | 否 | — | FK → `movies.id`,**`ON DELETE RESTRICT`**(V8 修正,原为 `CASCADE`) |
| `hall_id` | `UUID` | 否 | — | FK → `halls.id`,**`ON DELETE RESTRICT`**(V8 修正,原为 `CASCADE`) |
| `start_time` | `TIMESTAMPTZ` | 否 | — | |
| `end_time` | `TIMESTAMPTZ` | 否 | — | **派生字段**,见上面 🧮 一览;CHECK `end_time > start_time` |
| `price` | `NUMERIC(8,2)` | 否 | — | 该场次的统一票价(不分座位类型加价);CHECK `>= 0` |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。索引:`idx_showtimes_hall_id`、`idx_showtimes_movie_id`、
`idx_showtimes_start_time`。**没有更新场次的 API**——排期填错了只能删除重建
(和 `halls`/`seats` 同样的 MVP 边界收窄逻辑)。20 分钟清场缓冲不落库,只在
应用层冲突校验时临时应用(`ShowtimeOverlapChecker`),`end_time` 本身不含
缓冲。

### `bookings`(Phase 5,`V9__bookings.sql`;`redeemed_at` 由 Phase 7 的 `V12` 追加)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `user_id` | `UUID` | 否 | — | FK → `users.id`,**`ON DELETE RESTRICT`** |
| `showtime_id` | `UUID` | 否 | — | FK → `showtimes.id`,**`ON DELETE RESTRICT`** |
| `status` | `VARCHAR(20)` | 否 | — | CHECK 限定 `PENDING` \| `CONFIRMED` \| `EXPIRED` \| `CANCELLED` |
| `total_price` | `NUMERIC(10,2)` | 否 | — | CHECK `>= 0`;创建时按 `showtime.price × 座位数` 算出 |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `expires_at` | `TIMESTAMPTZ` | 否 | — | **派生字段**,见上面 🧮 一览 |
| `redeemed_at` | `TIMESTAMPTZ` | 是 | — | (V12,Phase 7)入场核销时间;`NULL` = 尚未核销,非 `NULL` = 核销时刻。一个字段同时表达"是否核销"和"何时核销",不是布尔值 + 时间戳两个字段的组合 |

主键:`id`。索引:`idx_bookings_user_id`、`idx_bookings_showtime_id`、
`idx_bookings_showtime_status`(`showtime_id, status` 复合)。`CONFIRMED`
这个状态值从 Phase 5 建表起就存在,但直到 Phase 6 webhook 接入之后才第一次
被真正设置——建表时预留列值,不需要为此再加一次 migration,是有意的提前
设计。`EXPIRED` 没有定时任务扫描,是懒惰过期(见 `BookingStateMachine`)。
**没有单独的 `tickets` 表**——一张电子票就是一条 `CONFIRMED` 的 booking,
从入场核验角度重新看待而已;票据编码本身(签名过的 JWT,booking id 为
subject)不落库,是 booking id + 服务端签名密钥的确定性函数,需要时现算
(见 `TicketCodeService`)。

### `booking_seats`(Phase 5,`V9__bookings.sql`)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `booking_id` | `UUID` | 否 | — | FK → `bookings.id`,**`ON DELETE CASCADE`**(纯从属行,离开 booking 没有独立意义) |
| `seat_id` | `UUID` | 否 | — | FK → `seats.id`,**`ON DELETE RESTRICT`** |
| `price_at_booking` | `NUMERIC(8,2)` | 否 | — | **派生字段**(下单时从 `showtimes.price` 快照),见上面 🧮 一览;CHECK `>= 0` |

主键:`id`。唯一约束:`uq_booking_seats_booking_seat`(`booking_id, seat_id`)。
索引:`idx_booking_seats_booking_id`、`idx_booking_seats_seat_id`。注意
`booking_id` 是 `CASCADE`、`seat_id` 是 `RESTRICT`——同一张表的两个外键
策略不对称是有意的,见上面 ON DELETE RESTRICT 一览的"对照组"说明。

### `payments`(Phase 6,`V10__payments.sql`;`status` 约束在 Phase 6 由 `V11` 扩展)

| 字段 | 类型 | 可空 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | `UUID` | 否 | `gen_random_uuid()` | 主键 |
| `booking_id` | `UUID` | 否 | — | FK → `bookings.id`,**`ON DELETE RESTRICT`** |
| `stripe_session_id` | `VARCHAR(255)` | 否 | — | **唯一**;webhook 幂等去重的锚点(见 `PaymentRepository.findByStripeSessionIdForUpdate`) |
| `stripe_payment_intent_id` | `VARCHAR(255)` | 是 | — | 支付成功/迟到成功后才有值 |
| `amount` | `NUMERIC(10,2)` | 否 | — | **派生字段**(创建 Checkout Session 时从 `bookings.total_price` 快照),见上面 🧮 一览;CHECK `>= 0` |
| `currency` | `VARCHAR(3)` | 否 | — | 同样是创建时从配置(`app.stripe.currency`)快照,不是逐条可变的值 |
| `status` | `VARCHAR(20)` | 否 | — | CHECK 限定 `PENDING` \| `SUCCEEDED` \| `FAILED` \| `ORPHANED_SUCCESS`(`ORPHANED_SUCCESS` 由 V11 追加) |
| `created_at` | `TIMESTAMPTZ` | 否 | `now()` | |
| `updated_at` | `TIMESTAMPTZ` | 否 | `now()` | |

主键:`id`。索引:`idx_payments_booking_id`(**不是唯一索引**——一笔 booking
可以对应多行 `payments`:放弃/过期的 Checkout 尝试之后重试会新建一行,不是
更新旧行,历史尝试全部保留)。`ORPHANED_SUCCESS` 表示 Stripe 报告支付成功,
但当时 booking 已经不是 `PENDING`(座位可能已经易主)——钱确实收到但不会
自动改回 `CONFIRMED`、也不自动退款,是留痕待人工对账的状态,不是错误状态。

---

## 种子数据 migration(非 schema 变更,仅记录用途)

| 迁移 | 内容 | 生产环境注意事项 |
|---|---|---|
| `V2__seed_admin.sql` | 固定管理员账号 `admin@cineverse.local` | **上线前必须删除或用 Flyway 环境过滤跳过**,不能把固定密码账号带上线 |
| `V4__seed_genres.sql` | 15 个固定 genre | 没有 genre 管理 API,这是唯一的数据来源 |
| `V6__seed_cinema_halls_seats.sql` | 1 分店 + 3 影厅(各自不同行列数)+ 对应座位 | MVP 规模的固定演示数据,ID 是硬编码的 UUID,方便本地/集成测试直接引用(见 `docs/DEVELOPMENT.md`) |
