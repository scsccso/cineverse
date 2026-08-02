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
5 分钟倒计时页（下方的"去支付"按钮是禁用状态，注明 Phase 6 开发中）；倒计时
到 0 或手动点"取消选座"都会把座位释放回选座页。

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
