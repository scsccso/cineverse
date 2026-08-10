# CineVerse — 开发者参考(Developer Reference)

本文件包含本地调试用的 API curl 示例、环境配置细节与手动验证步骤。项目简介、
技术栈、快速上手见 [`../README.md`](../README.md);完整的架构原则与模块路线图见
[`../CLAUDE.md`](../CLAUDE.md)。

## 本机环境注意事项

- **8080 端口冲突**:本机(开发机)8080 被 Oracle 的 `TNSLSNR.EXE`(TNS Listener,
  和本项目无关的系统服务)占用。后端本地开发固定改用 **8081**
  (`SERVER_PORT=8081 mvn spring-boot:run`),前端 `frontend/.env.local` 里的
  `NEXT_PUBLIC_API_BASE_URL` 相应指向 `http://localhost:8081`。换一台没有这个
  冲突的机器,直接用默认的 8080 即可(把 `SERVER_PORT=8081` 去掉,
  `frontend/.env.local` 也改回 8080)。
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
- **反复 `mvn spring-boot:run`(不带 `clean`)偶尔会让某个 bean 装配失败,
  哪怕代码本身完全正确**:2026-08-11 复核 Admin 用户管理时踩到过——
  `target/classes` 里 `UserMapperImpl.class` 明明已经生成,某次单纯重启
  `spring-boot:run` 却报 `No qualifying bean of type UserMapper`
  直接拒绝启动;另一次不完整的重启则是启动成功,但只有新加的
  `GET /api/v1/admin/users` 单独 500,复用同一个 `UserMapper` 的旧接口
  (`/users/me`)完好无损。两次都是 Maven 增量编译状态不一致导致的运行时
  假象,不是代码问题——`mvn clean compile` 之后重启即可稳定复现"启动成功、
  所有接口正常"。如果遇到一个新写的 controller/service 报奇怪的 bean 装配
  错误、或者只有新端点 500 而其余复用同一个 bean 的旧端点正常,先怀疑这个,
  不要急着去改代码。

## 环境配置(Profiles)

| Profile | 用途 | 配置文件 |
|---|---|---|
| `local`(默认) | 本地开发,连接 docker-compose 里的 Postgres,`cookie-secure=false`(纯 HTTP 方便 curl/浏览器测试) | `application-local.yml` |
| `prod` | 生产部署,所有连接信息 + JWT 密钥必须由环境变量提供,无默认值;`cookie-secure=true` | `application-prod.yml` |

前端只有一个环境变量:`NEXT_PUBLIC_API_BASE_URL`(后端地址),通过 `.env.local` 配置,不在代码里硬编码。

## 种子账号(仅本地开发)

`V2__seed_admin.sql` 会插入一个固定的管理员账号,方便本地联调,**生产环境部署前必须删除这个 migration**:

| Email | Password | Role |
|---|---|---|
| `admin@cineverse.local` | `Admin@12345` | `ADMIN` |

## API 快速上手(curl)

以下命令假设后端跑在 `http://localhost:8081`(按上面的说明替换成你实际的端口)。
refresh token 走 httpOnly cookie,所以要用 curl 的 cookie jar(`-c`/`-b`)把它在
请求之间带上,不能像 access token 一样直接从响应体里复制。

### 1. 注册

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"moviefan@example.com","password":"Sup3rSecret!","fullName":"Jane Doe"}'
```

返回 `201` + 用户信息(不含密码)。邮箱重复注册会返回 `409`。

### 2. 登录(拿 access token,refresh token 存进 cookie jar)

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"moviefan@example.com","password":"Sup3rSecret!"}'
```

响应体里的 `accessToken` 手动复制出来备用(下面用 `$ACCESS_TOKEN` 代替);
`cookies.txt` 里会多一行 `refresh_token`,`HttpOnly`,`Path=/`(broad path是有意的——前端
`proxy.ts` 靠这个 cookie 的存在与否判断路由要不要放行,Path 若收窄到 `/api/v1/auth`,
前端页面路径根本收不到这个 cookie)。
密码错误或邮箱不存在都返回同样的 `401 Invalid email or password`,不会告诉你
到底是哪一种,防止被拿来枚举已注册邮箱。

### 3. 访问受保护接口

```bash
ACCESS_TOKEN="<上一步拿到的 accessToken>"

curl -i http://localhost:8081/api/v1/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

不带 token(或 token 过期/伪造)会返回 `401`。

### 4. 刷新 Token(Token Rotation)

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/refresh \
  -b cookies.txt -c cookies.txt
```

返回新的 `accessToken`,同时 `cookies.txt` 里的 `refresh_token` 也被替换成新的
——旧的那个在服务端已经标记 `revoked`,再拿旧 cookie 去 `/refresh` 会得到 `401`。

### 5. 登出

```bash
curl -i -X POST http://localhost:8081/api/v1/auth/logout \
  -b cookies.txt -c cookies.txt
```

返回 `204`,`cookies.txt` 里的 `refresh_token` 被清空(`Max-Age=0`)。登出之后
再拿这个 token 去 `/refresh` 同样是 `401`。

## Cinema / Hall / Seat(Phase 3)

浏览类接口全部公开,不需要 token。种子数据是 1 个分店(`CineVerse Downtown`)+ 3
个影厅,ID 是固定的(见 `V6__seed_cinema_halls_seats.sql`),本地可以直接照抄下面的
命令跑,不用自己先创建数据。

### 获取分店列表

```bash
curl -s http://localhost:8081/api/v1/cinemas | jq
```

### 获取某分店下的影厅列表

```bash
curl -s http://localhost:8081/api/v1/cinemas/11111111-1111-1111-1111-111111111111/halls | jq
```

### 获取影厅完整座位布局(下一步前端选座页要用的接口)

```bash
curl -s http://localhost:8081/api/v1/halls/21111111-1111-1111-1111-111111111111/seats | jq
```

种子里的 Hall 1 是 6 排 x 10 列(A-E 标准座,F 排是情侣座)。响应形状:

```json
{
  "hallId": "21111111-1111-1111-1111-111111111111",
  "hallName": "Hall 1",
  "totalRows": 6,
  "totalColumns": 10,
  "seats": [
    { "id": "...", "rowLabel": "A", "columnNumber": 1, "columnSpan": 1, "seatType": "STANDARD" },
    ...
    { "id": "...", "rowLabel": "F", "columnNumber": 1, "columnSpan": 2, "seatType": "COUPLE" },
    { "id": "...", "rowLabel": "F", "columnNumber": 3, "columnSpan": 2, "seatType": "COUPLE" },
    ...
  ]
}
```

`columnSpan` 是派生字段(STANDARD=1,COUPLE=2),F 排每个情侣座的 `columnNumber`
是这一对里左边那一列——渲染座位图时,一个座位要占几格直接读这个数字,不用自己
写"哪种类型该跨几列"的规则。

### Admin:创建分店 + 创建影厅(自动生成座位)

```bash
ACCESS_TOKEN="<用 admin@cineverse.local / Admin@12345 登录拿到的 accessToken>"

# 创建分店
curl -s -X POST http://localhost:8081/api/v1/cinemas \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"CineVerse Uptown","address":"88 Northgate Ave"}' | jq

CINEMA_ID="<上一步返回的 id>"

# 创建影厅——座位是自动生成的,请求体里不用也不能传座位数据
curl -s -X POST http://localhost:8081/api/v1/cinemas/$CINEMA_ID/halls \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Hall A","totalRows":8,"totalColumns":12}' | jq
```

未登录 POST 这些接口会得到 `401`(匿名);登录了但角色是 `CUSTOMER` 会得到 `403`
(已认证但角色不对)—— 这两种情况的区别在 Phase 2 就定下来了,详见 `CLAUDE.md`。

## Movie(Phase 2)

```bash
# 浏览(公开)
curl -s http://localhost:8081/api/v1/movies | jq
curl -s http://localhost:8081/api/v1/genres | jq

# Admin 创建电影(海报/背景图走单独的 multipart 上传接口)
curl -s -X POST http://localhost:8081/api/v1/movies \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Interstellar","durationMinutes":169,"contentRating":"PG-13","status":"NOW_PLAYING","genreIds":[]}' | jq
```

## Showtime(Phase 4)

浏览类接口（`GET`）公开，不需要 token；创建/删除需要 `ROLE_ADMIN`。没有更新
场次的 API——排期填错了删除重建，不支持局部改 `start_time`。`endTime` 由后端
根据 `movie.durationMinutes` 自动算出，请求体里不接受手动传入。

```bash
ACCESS_TOKEN="<用 admin@cineverse.local / Admin@12345 登录拿到的 accessToken>"
HALL_ID="21111111-1111-1111-1111-111111111111"   # 种子数据 Hall 1

# 先创建一部电影，拿到 movieId（种子数据没有预置电影）
MOVIE_ID=$(curl -s -X POST http://localhost:8081/api/v1/movies \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Interstellar","durationMinutes":169,"contentRating":"PG-13","status":"NOW_PLAYING","genreIds":[]}' \
  | jq -r '.id')

# 创建场次——只传 startTime，不传 endTime
curl -s -X POST http://localhost:8081/api/v1/showtimes \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"movieId\":\"$MOVIE_ID\",\"hallId\":\"$HALL_ID\",\"startTime\":\"2026-09-01T10:00:00Z\",\"price\":25.00}" | jq

# 浏览（公开）：按电影 + 日期筛选
curl -s "http://localhost:8081/api/v1/showtimes?movieId=$MOVIE_ID&date=2026-09-01" | jq

# 详情（公开，内联 movie/hall 基本信息）
curl -s http://localhost:8081/api/v1/showtimes/<上一步返回的 id> | jq
```

### 验证 20 分钟清场缓冲冲突（409）

复用上面创建的 `$MOVIE_ID`（时长 169 分钟，第一场 `10:00` 开始，`endTime` 自动
算出是 `12:49`）。再创建一场只间隔 10 分钟的场次，应该被拒绝：

```bash
curl -i -X POST http://localhost:8081/api/v1/showtimes \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"movieId\":\"$MOVIE_ID\",\"hallId\":\"$HALL_ID\",\"startTime\":\"2026-09-01T12:59:00Z\",\"price\":25.00}"
```

预期 `409 Conflict`，`message` 里会明确指出是和 Hall 1 的哪一场冲突。把
`startTime` 换成 `2026-09-01T13:09:00Z`（间隔正好 20 分钟）再跑一次，则会
`201 Created`——这正是清场缓冲的边界值。

未登录 POST 这些接口会得到 `401`（匿名）；登录了但角色是 `CUSTOMER` 会得到
`403`（已认证但角色不对）。

## Booking / 选座（Phase 5）

`GET /api/v1/showtimes/{id}/seats` 公开，不需要 token；`POST/DELETE/GET
/api/v1/bookings` 需要登录（CUSTOMER/ADMIN 都行，不限角色，但只有本人或
ADMIN 能看/取消自己的订单）。座位状态是**轮询**出来的（`AVAILABLE` /
`LOCKED` / `BOOKED`），前端选座页定时重新请求 `.../seats` 即可，没有 WebSocket。

```bash
ACCESS_TOKEN="<用任意已注册用户登录拿到的 accessToken，见上面 Auth 一节的 curl>"
HALL_ID="21111111-1111-1111-1111-111111111111"   # 种子数据 Hall 1

# 复用 Showtime 一节的方式先建一个 showtime，拿到 SHOWTIME_ID
# 查某个场次的座位状态（公开，不需要 token）
curl -s http://localhost:8081/api/v1/showtimes/$SHOWTIME_ID/seats | jq

SEAT_ID=$(curl -s http://localhost:8081/api/v1/showtimes/$SHOWTIME_ID/seats \
  | jq -r '.seats[0].seatId')

# 锁座 + 创建 PENDING 订单（5 分钟持有窗口）
BOOKING_ID=$(curl -s -X POST http://localhost:8081/api/v1/bookings \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"showtimeId\":\"$SHOWTIME_ID\",\"seatIds\":[\"$SEAT_ID\"]}" \
  | jq -r '.id')

# 再查一次座位状态——刚才那个座位应该变成 LOCKED
curl -s http://localhost:8081/api/v1/showtimes/$SHOWTIME_ID/seats \
  | jq ".seats[] | select(.seatId==\"$SEAT_ID\")"

# 查看订单详情（只有本人或 ADMIN 能看，见下面权限校验说明）
curl -s http://localhost:8081/api/v1/bookings/$BOOKING_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq

# 主动放弃选座——座位锁被释放，booking 状态变 CANCELLED
curl -i -X DELETE http://localhost:8081/api/v1/bookings/$BOOKING_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 再查一次——座位应该已经变回 AVAILABLE
curl -s http://localhost:8081/api/v1/showtimes/$SHOWTIME_ID/seats \
  | jq ".seats[] | select(.seatId==\"$SEAT_ID\")"
```

未登录 GET `.../seats` 能成功；未登录 POST `/bookings` 会得到 `401`。查看/取消
别人的订单会得到 `403`。

### 前端选座页

打开 `http://localhost:3000/showtimes/<SHOWTIME_ID>/seats`（从电影详情页选一个
场次点"继续选座"进来即可，不用手拼 URL）。座位图按 `rowLabel` 分行、按
`columnNumber`/`columnSpan` 排列，情侣座会明显占两格宽；每 4 秒轮询一次座位
状态，可多选后在底部结算栏看到已选座位和总价（移动端结算栏固定在屏幕底部，
座位图本身可横向滚动）。点"确认选座"：未登录会提示后跳转 `/login`，登录后
回到这个页面继续选（座位选择不会跨登录保留，需要重新点一次）；提交成功进入
5 分钟倒计时页，"去支付"按钮点击后调用 Stripe Checkout（见下面 Payment 一节）；
倒计时到 0 或手动点"取消选座"都会把座位释放回选座页。

### 并发加锁怎么验证

**手动验证(两个浏览器窗口/一个正常窗口 + 一个隐私窗口)**:两个窗口分别用
两个不同账号登录,同时打开同一个 `/showtimes/<SHOWTIME_ID>/seats`,选中同一个
座位后几乎同时点"确认选座"。预期:一个窗口进入 5 分钟倒计时确认页,另一个
窗口收到"部分座位刚被其他用户抢先锁定"的提示,座位图自动刷新,刚才那个座位
从可选变成锁定态(虚线灰底 + 锁形图标)。这种手动方式没法保证两次点击真正
落在同一毫秒,只能大致验证交互和提示文案是否合理;真正的竞态由下面的自动化
集成测试覆盖。

`curl` 本身没法方便地演示"两个请求真正同时到达"这种竞态场景（两次 `curl`
调用之间必然有先后顺序），这部分的验证详见
`BookingConcurrencyIntegrationTest`——用 `ExecutorService` 真正并发发起两个
线程,对同一个 `showtimeId` + `seatId` 同时调用 `POST /api/v1/bookings`,
断言恰好一个成功（`201`）、一个失败（`409`，错误信息里明确指出是哪个座位
已被占用），并且验证失败的那次请求没有在数据库留下任何 `bookings`/
`booking_seats` 记录。同一个测试类里还有一个 TTL 过期测试：创建一个
booking 后把它的 `expires_at` 直接改到过去，验证下一次读取座位状态时
（懒惰过期,见 `CLAUDE.md` Phase 5）能正确判定为已过期并把座位释放回
`AVAILABLE`。本地跑：

```bash
cd cineverse-backend
mvn test -Dtest=BookingConcurrencyIntegrationTest
```

## Payment / 支付（Phase 6）

`POST /api/v1/bookings/{id}/checkout` 需要登录（只能是 booking 本人，ADMIN 不能代发起）；
`POST /api/v1/webhooks/stripe` 公开但校验 `Stripe-Signature`。本地要跑通完整链路需要
Stripe 账号的**测试模式** key —— 免费注册、不需要真实营业执照/银行信息：

1. 打开 `https://dashboard.stripe.com` 注册/登录，确认右上角处于 **Test mode**。
2. Developers -> API keys 页面拿到 `sk_test_...`，填进 `.env` 的 `STRIPE_SECRET_KEY`
   （不是 docker-compose 变量，是后端进程直接读的环境变量，见根目录 `.env.example`）。
3. 安装 [Stripe CLI](https://stripe.com/docs/stripe-cli),登录后本地转发 webhook：

```bash
stripe login
stripe listen --forward-to localhost:8081/api/v1/webhooks/stripe
```

`stripe listen` 启动时会打印一个 `whsec_...`,把它填进 `STRIPE_WEBHOOK_SECRET`
(每次重新跑 `stripe listen` 这个值都会变,要跟着更新)——这是本地开发**唯一**能
拿到有效 webhook secret 的方式,Stripe 官方文档里不存在一个能公用的示例值。

### 发起一次支付

```bash
ACCESS_TOKEN="<任意已注册用户登录拿到的 accessToken>"

# 复用前面 Booking 一节创建的 $BOOKING_ID(状态必须是 PENDING 且未过期)
curl -s -X POST http://localhost:8081/api/v1/bookings/$BOOKING_ID/checkout \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq
```

返回 `{"checkoutUrl": "https://checkout.stripe.com/..."}`——浏览器打开这个 URL
用 Stripe 测试卡号 `4242 4242 4242 4242`（任意未来到期日、任意 CVC、任意邮编）
即可走完整的托管支付页流程。支付成功后 Stripe 会把事件发到 `stripe listen`
转发的地址,`cineverse-backend` 的日志里能看到 Flyway/Hibernate 之外新增的
webhook 处理请求;此时再查一次 booking 应该已经变成 `CONFIRMED`：

```bash
curl -s http://localhost:8081/api/v1/bookings/$BOOKING_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq '.status'
```

在 Stripe 托管页面点"返回"或者直接关掉标签页(模拟用户放弃支付)：booking
会保持 `PENDING`,不会立刻变化——这是有意为之,不是漏了处理,见 `CLAUDE.md`
Phase 6"用户取消支付"一节。此时回到前端选座页(`cancel_url` 会自动带你回去),
booking 只要还没到期就能重新点"去支付"再试一次。

### 幂等性怎么验证

`stripe trigger checkout.session.completed` 只会构造一个全新的、和真实
booking 无关的测试 session,不会命中我们自己创建的 Payment 行(找不到对应
`stripe_session_id` 会直接 no-op 返回 200),所以验证幂等性的正确方式不是
这个命令,而是自动化集成测试:`PaymentFlowIntegrationTest` 里用 Stripe 官方
文档记录的签名算法(`t=<timestamp>,v1=hex(HMAC-SHA256(secret, "<timestamp>.<payload>"))`)
真实构造一个签名正确的 `checkout.session.completed` 事件,对同一个 webhook
端点连续 POST 两次,断言 `payments` 表里始终只有一条记录、booking 只被
确认一次。同一个测试类里还覆盖了签名错误必须被拒绝(`400`)的场景。本地跑：

```bash
cd cineverse-backend
mvn test -Dtest=PaymentFlowIntegrationTest
```

## Order / 电子票(Phase 7)

`POST /api/v1/tickets/redeem` 仅 ADMIN,不需要额外密钥/第三方账号——票据
编码是本地签名的 JWT(和 access/refresh token 同一套 jjwt 机制),`app.ticket
.signing-secret` 本地开发有 dev-only 默认值,不像 Stripe key 那样需要外部
账号。

### 走一遍完整流程(选座 → 支付成功 → 拿到票 → 核销)

```bash
ACCESS_TOKEN="<顾客账号登录拿到的 accessToken>"
ADMIN_TOKEN="<用 admin@cineverse.local / Admin@12345 登录拿到的 accessToken>"

# 复用前面 Payment 一节走完一次真实支付,或者本地图省事直接用管理员权限
# 之外没有别的路径能把 booking 标记 CONFIRMED——票据编码只在 CONFIRMED 之后
# 才会出现在响应里
curl -s http://localhost:8081/api/v1/bookings/$BOOKING_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq '{status, ticketCode, redeemedAt}'
```

`ticketCode` 字段就是二维码里编码的原始字符串(前端拿它喂给
`<QRCodeSVG value={ticketCode} />` 画出图案);本地没有扫码枪的话直接复制
这段字符串模拟"扫码结果":

```bash
TICKET_CODE="<上一步拿到的 ticketCode>"

curl -s -X POST http://localhost:8081/api/v1/tickets/redeem \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"ticketCode\":\"$TICKET_CODE\"}" | jq
```

返回场次/座位信息供工作人员核对入场。**再用同一个 `TICKET_CODE` 核销一次**
应该得到 `409`(同一张票不能核销两次,这是本 Phase 的核心验收场景,自动化
测试见 `TicketFlowIntegrationTest.redeemingATicketTwiceRejectsTheSecondAttempt`)：

```bash
curl -i -X POST http://localhost:8081/api/v1/tickets/redeem \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"ticketCode\":\"$TICKET_CODE\"}"
```

未登录或者 CUSTOMER 角色调用 `/tickets/redeem` 会得到 `401`/`403`(仅
ADMIN)；编码本身被篡改(随便改一个字符)会得到 `400`；booking 还没支付
成功(仍是 `PENDING`)或者已经被取消/过期,拿一个手动构造的编码去核销会
得到 `409`。本地跑完整测试：

```bash
cd cineverse-backend
mvn test -Dtest=TicketFlowIntegrationTest
```

## Admin Reports / 管理后台报表(Phase 8)

`/api/v1/admin/reports/**` 全部仅 `ADMIN`,`from`/`to` 是必填的 ISO 日期
(`YYYY-MM-DD`,cinema 所在时区 `Asia/Kuala_Lumpur` 的日历日期,含
起止两端);预设时间范围(今日/近7天/近30天)是前端把它们换算成具体
`from`/`to` 再调这同一个接口,后端不单独接受"预设"这种参数。

```bash
ADMIN_TOKEN="<用 admin@cineverse.local / Admin@12345 登录拿到的 accessToken>"

# 销售报表——按日粒度统计近 7 天营收(只统计 CONFIRMED booking 的 SUCCEEDED
# 支付;ORPHANED_SUCCESS 的金额单独在 pendingReconciliationAmount 里,不计入
# totalRevenue,见 CLAUDE.md Phase 8 的营收口径说明)
curl -s "http://localhost:8081/api/v1/admin/reports/sales?from=2026-08-01&to=2026-08-07&granularity=day" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# 可选按电影/影厅细分——加 movieId 或 hallId 缩小到某一部电影/某个影厅
curl -s "http://localhost:8081/api/v1/admin/reports/sales?from=2026-08-01&to=2026-08-07&granularity=week&hallId=21111111-1111-1111-1111-111111111111" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# 上座率分析——按场次统计已订座位数(仅 CONFIRMED)/ 总座位数
curl -s "http://localhost:8081/api/v1/admin/reports/occupancy?from=2026-08-01&to=2026-08-07" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

响应形状(销售报表)：

```json
{
  "from": "2026-08-01", "to": "2026-08-07", "granularity": "DAY",
  "currency": "myr",
  "buckets": [
    { "periodStart": "2026-08-01", "revenue": 0, "bookingCount": 0 },
    { "periodStart": "2026-08-04", "revenue": 50.00, "bookingCount": 2 }
  ],
  "totalRevenue": 50.00,
  "pendingReconciliationAmount": 0
}
```

`buckets` 覆盖 `from`~`to` 范围内每一个粒度单位(哪怕当天/当周没有营收也会
补一条 `revenue: 0` 的记录,不会跳过——见 `ReportRepository.salesBuckets`
的 `generate_series` 补零逻辑),前端画图不需要自己判断缺口。

### CSV / PDF 导出

在对应查询接口的路径后面加 `/export`,参数完全一样,额外加一个
`format=csv|pdf`：

```bash
# 销售报表导出 CSV(响应带 Content-Disposition: attachment,浏览器/curl -O 都会触发下载)
curl -s "http://localhost:8081/api/v1/admin/reports/sales/export?from=2026-08-01&to=2026-08-07&granularity=day&format=csv" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -o sales-report.csv

# 上座率分析导出 PDF
curl -s "http://localhost:8081/api/v1/admin/reports/occupancy/export?from=2026-08-01&to=2026-08-07&format=pdf" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -o occupancy-report.pdf
```

`format` 传除 `csv`/`pdf` 之外的值(大小写不敏感)会得到 `400`。未登录调用
任意 `/admin/reports/**` 接口得到 `401`;登录了但角色是 `CUSTOMER` 得到
`403`;`to` 早于 `from` 得到 `400`。本地跑完整测试(用固定 fixture 断言
聚合数字,不是只断言 200)：

```bash
cd cineverse-backend
mvn test -Dtest=ReportFlowIntegrationTest
```

### 前端管理后台

打开 `http://localhost:3000/admin/dashboard`(用 `admin@cineverse.local` 登录
后,导航栏会出现"管理后台"入口)。未登录访问会被 `proxy.ts` 弹到登录页;
登录了但角色是 `CUSTOMER` 直接改地址栏输入这个 URL,会被
`app/admin/layout.tsx` 的角色校验弹回首页——这个校验在页面内容渲染之前就
生效,不是"入口对非 ADMIN 隐藏"这种前端伪保护,详见 CLAUDE.md Phase 8。

## Admin User Management(用户管理,2026-08-11)

```bash
ADMIN_TOKEN="<用 admin@cineverse.local / Admin@12345 登录拿到的 accessToken>"

# 分页查询所有用户
curl -s "http://localhost:8081/api/v1/admin/users?page=0&size=20" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# 修改指定用户的角色(不能是自己,见下面)
curl -s -i -X PATCH "http://localhost:8081/api/v1/admin/users/<user-id>/role" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"role":"ADMIN"}'

# 删除指定用户(有订单记录或是调用者自己都会 409)
curl -s -i -X DELETE "http://localhost:8081/api/v1/admin/users/<user-id>" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

未登录 `401`;登录了但角色是 `CUSTOMER` `403`(和其余 `/api/v1/admin/**`
接口同一套语义)。**改角色/删除接口传自己的 user id 会得到 409**——这是服务端
强制的,不是只有前端按钮变灰,用不属于自己的 token 试一遍就能验证:

```bash
MY_ID=$(curl -s "http://localhost:8081/api/v1/users/me" -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r .id)
curl -s -i -X PATCH "http://localhost:8081/api/v1/admin/users/$MY_ID/role" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"role":"CUSTOMER"}'
# => 409 {"message":"Cannot change your own role."}
```

前端页面在 `http://localhost:3000/admin/users`(`AdminHeader` 的 "Users" 导航
项)。`AdminHeader` 曾经短暂多带过一个指向不存在页面的 "Movies" 导航项,已
移除,见 CLAUDE.md 对应小节。

本地跑这个模块的测试(service 层 Mockito 快测 + Testcontainers 真实 HTTP
流程测试):

```bash
cd cineverse-backend
mvn test -Dtest=AdminUserServiceTest,AdminUserFlowIntegrationTest
```

**CI 跑的是 `mvn clean test`(全量,含全部模块 + 架构治理测试),不是
`mvn compile`**——这两者不是同一件事,`mvn compile`/`mvn clean compile`
干净不代表 `mvn clean test` 会绿,2026-08-11 这次复核就在 CI 上被
`TimestampedEntitySaveFlushRuleTest`(见 `architecture` 包)拦下过一次,
本地只跑过 `mvn compile` 的验证完全没有触及这条规则。改动了任何调用
`XxxRepository.save()`/`saveAll()` 的地方,如果对应实体带
`@CreationTimestamp`/`@UpdateTimestamp`(目前是 `save`/`saveAndFlush` 而不是
别的方法名的问题,`delete`/`findById` 等不受这条规则约束),提交前跑一次
本地全量 `mvn clean test` 比只跑 `mvn compile` 更接近 CI 真实会检查的范围。
