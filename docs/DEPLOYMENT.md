# CineVerse — 部署环境变量清单(docs/DEPLOYMENT.md)

本文件回答"要把这个项目部署到真实公网,部署平台上需要配置哪些环境变量、
哪些是必填、Stripe webhook 要怎么注册"。本地开发环境配置见
[`DEVELOPMENT.md`](./DEVELOPMENT.md);部署平台选型、演示数据重置、第三方
API 额度保护这类更宏观的部署策略讨论不在本文件范围内(那是一次性的审计
讨论,不是持续维护的操作清单)。

> 本文件只列"部署这一步需要配置什么",不代表这个项目已经有一个正在运行的
> 公开 demo——见 CLAUDE.md 第 4 节,是否部署仍是一个未决定的开放问题。

---

## 后端环境变量(`cineverse-backend`)

必须先设置 `SPRING_PROFILES_ACTIVE=prod`——不设置的话会用默认的 `local`
profile,应用会以为自己还在连本机 Postgres,大概率直接启动失败或者连接
到错误的实例。`prod` profile(`application-prod.yml`)对下面这些变量**没有
兜底默认值**,漏设任何一个都会在启动时报错,不会静默用错误的值跑起来:

| 变量 | 必填 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 是 | 固定设为 `prod` |
| `SPRING_DATASOURCE_URL` | 是 | 形如 `jdbc:postgresql://<host>:<port>/<db>` |
| `SPRING_DATASOURCE_USERNAME` | 是 | |
| `SPRING_DATASOURCE_PASSWORD` | 是 | |
| `REDIS_HOST` | 是 | |
| `REDIS_PORT` | 是 | |
| `JWT_ACCESS_SECRET` | 是 | 至少 32 字节的随机字符串,和下面两个 secret 各自独立(轮换一个不影响另外两个) |
| `JWT_REFRESH_SECRET` | 是 | 同上 |
| `TICKET_SIGNING_SECRET` | 是 | 同上,用于电子票二维码签名 |
| `ADMIN_SEED_PASSWORD_HASH` | 是 | 唯一种子 admin 账号(`admin@cineverse.local`)的 BCrypt hash——**存 hash,不存明文密码**,生成步骤见下面单独一节 |
| `CORS_ALLOWED_ORIGINS` | 是 | 前端真实域名,支持逗号分隔多个(`CorsProperties.allowedOrigins` 是 `List<String>`,Spring Boot 会自动按逗号切分) |
| `FRONTEND_BASE_URL` | 是 | 前端真实域名(用于 Stripe Checkout 的 success/cancel 跳转地址拼接) |
| `STRIPE_SECRET_KEY` | 是 | Test mode 的 `sk_test_...`——这个项目从设计上就没打算收真实的钱,继续用 test key 上线是合理的选择,不需要申请 live mode |
| `STRIPE_WEBHOOK_SECRET` | 是 | 部署后在 Stripe Dashboard 注册 webhook 端点时生成,**不是**本地 `stripe listen` 那个临时值,见下面"Stripe webhook 一次性配置"一节 |

以下有兜底默认值,不设置也能启动,按需覆盖:

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PORT` | 沿用 `SERVER_PORT`,再沿用 `8080` | 大多数 PaaS 平台(Railway/Render)会自动注入这个变量并要求应用监听它,不需要手动设置——平台自己会处理 |
| `SERVER_PORT` | `8080` | 本项目自己的既有约定(本地跑多实例时用,见 DEVELOPMENT.md),`PORT` 优先级更高 |
| `STRIPE_CURRENCY` | `myr` | |
| `TMDB_API_KEY` | 空(禁用 TMDB 搜索预填,不影响手动创建电影) | 免费 v3 key,`themoviedb.org` → Settings → API |

### 生成 `ADMIN_SEED_PASSWORD_HASH`

这个值必须是**你自己生成的一个真实密码对应的 BCrypt hash**,不要沿用
`V2__seed_admin.sql`/本地开发文档里那组公开的本地测试密码(`Admin@12345`)
——那组密码和它的 hash 已经提交进 git 历史,任何人都能看到,继续用它上线
等于没有密码保护。生成方式(复用项目已经下载好的 `spring-security-crypto`
依赖,不需要额外装任何工具、不需要把密码发给任何第三方网站):

```bash
cd cineverse-backend
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP=$(cat /tmp/cp.txt)

cat > /tmp/HashPassword.java <<'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashPassword {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
EOF

javac -cp "$CP" -d /tmp /tmp/HashPassword.java
java -cp "$CP:/tmp" HashPassword '替换成你自己的真实密码'
```

Windows PowerShell 下把 classpath 分隔符从 `:` 换成 `;`(`"$CP;/tmp"`)。
输出的 `$2a$10$...` 那一整行就是要存进部署平台密钥库的值——只存这一行,
不要把明文密码存在任何地方(包括这个命令本身的 shell 历史,用完可以
`history -d` 删掉那一条,或者干脆手动敲密码而不是粘贴)。

---

## 前端环境变量(`frontend`)

| 变量 | 必填 | 说明 |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | 是 | 后端真实域名。**这是构建时变量**,会被打包进客户端 bundle——必须在触发构建**之前**就在部署平台的环境变量设置里配好;构建完之后再改这个变量不会生效,需要重新触发一次构建 |
| `NEXT_ALLOW_LOCAL_IMAGE_OPTIMIZATION` | 否 | 只在后端是真正的 localhost 时才有意义(本地开发用),真实部署里**不要设置**这个变量——留空即可,`next.config.ts` 已经把它和"后端是不是 loopback 地址"这两个条件做了 AND,不会因为漏删这个变量就意外放宽生产环境的图片优化限制 |

Vercel 部署 Next.js 项目**不需要 Dockerfile**——Vercel 原生识别
`package.json`/`next.config.ts`,直接跑 `next build`,这是它作为 Next.js
官方托管平台的原生能力,不需要额外的容器化配置。

---

## Stripe Webhook 一次性配置

代码本身(`StripeWebhookController`/`PaymentService`)已经在做签名校验和
幂等处理,这里需要的是纯运维步骤,部署后手动做一次:

1. Stripe Dashboard(**保持 Test mode**)→ Developers → Webhooks → Add
   endpoint。
2. Endpoint URL 填 `https://<后端真实域名>/api/v1/webhooks/stripe`——必须
   是真实公网可达的 HTTPS 地址,Stripe 不接受 `http://`。
3. 订阅的事件类型至少要勾 `checkout.session.completed` 和
   `checkout.session.expired`(其余事件类型会直接返回 200 但不处理,多勾
   了没关系,少勾这两个会让核心流程静默失效)。
4. 创建后 Stripe 会给一个新的 `whsec_...` 签名密钥——**和本地
   `stripe listen` 命令拿到的那个不是同一个值**,必须用这个新的替换掉
   部署平台上的 `STRIPE_WEBHOOK_SECRET`。
5. 确认 `STRIPE_SECRET_KEY` 是同一个 Test mode 账号下的 `sk_test_...`,
   不要跟 webhook secret 搞混。
6. 建议先用 Stripe Dashboard 自带的"Send test webhook"功能打一次,确认
   后端真的返回 200,不要等第一个真实用户下单才发现签名校验配错了。

---

## Cookie `SameSite` 策略

`app.security.cookie-same-site` 在 `prod` profile 下固定是 `None`(不是
环境变量,写死在 `application-prod.yml` 里,不需要额外配置)——这个值
在前后端共享同一个注册域名(比如 `app.yourdomain.com` +
`api.yourdomain.com`)和分别部署在两个不同域名(比如平台各自分配的默认
子域名)这两种拓扑下都能正常工作,不需要根据实际部署形态再做选择。

---

## Demo 数据重置(可选,只有公开 demo 才需要)

`admin` 面板部署后保持私密(不对访客开放)的前提下,公开访客能碰到的
"会被用坏"的数据只有两类:顾客端交易数据(座位被订、余额记录)、以及
场次本身会随时间推移过期。`/internal/demo-reset/**` 这两个端点就是为了
应对这个,只有配置了 `DEMO_RESET_SECRET` 才会启用——不打算做公开 demo
的部署可以完全不设这个变量,端点会对任何请求返回 503,不产生任何影响。
完整设计权衡见 CLAUDE.md"Demo 数据重置"一节,这里只列部署时需要配置的
部分。

| 变量 | 必填 | 说明 |
|---|---|---|
| `DEMO_RESET_SECRET` | 否(不设=功能关闭) | 一个足够随机的字符串,建议用 `openssl rand -hex 32` 之类的方式生成,不要手打一个好记的短语——这是这两个数据清空端点唯一的访问控制 |

两个端点,分别配不同的调度频率:

- `POST /internal/demo-reset/transactions`(高频,建议每 6 小时一次):
  清空 `bookings`/`payments`/`booking_seats` 和 Redis 里所有 `seat-lock:*`
  key,不动场次排期。
- `POST /internal/demo-reset/showtimes`(低频,建议每天一次):在
  `.../transactions` 同样的清理之上,额外删除全部现有场次并按固定模板
  (3 影厅 × 每天 3 个时段 × 未来 7 天)重新生成——场次的绝对时间戳会
  过期,只清空交易数据不够,需要这个更完整的重置来保证 demo 永远至少
  有未来一周可订的场次。

两个请求都要带 `X-Demo-Reset-Secret: <DEMO_RESET_SECRET 的值>` 请求头。

### 调度方式一:GitHub Actions `schedule` 触发器

不依赖部署平台,免费。**这段 YAML 不是一个已经启用的文件**——这个仓库
目前没有实际部署,把它加进 `.github/workflows/` 会立刻开始按 cron 表
定时触发、并因为打不到任何真实后端地址而持续失败,所以先留在这里作为
配置示例,等真的有一个部署好的后端 URL、并且已经把下面两个 secret 配
进仓库(Settings → Secrets and variables → Actions)之后,再另存为
`.github/workflows/demo-reset.yml`:

```yaml
name: Demo Reset

on:
  schedule:
    - cron: "0 */6 * * *"  # every 6 hours — transactions
    - cron: "0 4 * * *"    # once daily at 04:00 UTC — showtimes
  workflow_dispatch: {}     # manual trigger, for testing

jobs:
  reset-transactions:
    if: github.event.schedule == '0 */6 * * *' || github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    steps:
      - name: Reset transactions
        run: |
          curl -sf -X POST "${{ secrets.DEMO_RESET_BACKEND_URL }}/internal/demo-reset/transactions" \
            -H "X-Demo-Reset-Secret: ${{ secrets.DEMO_RESET_SECRET }}"

  reset-showtimes:
    if: github.event.schedule == '0 4 * * *' || github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    steps:
      - name: Reset showtimes
        run: |
          curl -sf -X POST "${{ secrets.DEMO_RESET_BACKEND_URL }}/internal/demo-reset/showtimes" \
            -H "X-Demo-Reset-Secret: ${{ secrets.DEMO_RESET_SECRET }}"
```

两个 repo secrets:`DEMO_RESET_BACKEND_URL`(后端真实域名,不带末尾
斜杠)、`DEMO_RESET_SECRET`(和部署平台上配的 `DEMO_RESET_SECRET`
环境变量必须是同一个值)。

### 调度方式二:Railway Cron Job(如果后端部署在 Railway,推荐路径)

Railway 支持在同一个项目下新增一个 "Cron Job" 类型的服务,不需要额外的
计算资源单独计费(仍按用量算,但不需要一个常驻进程)。步骤(Railway
控制台里操作,不是提交一个仓库文件):

1. 项目内 New Service → 选 Cron Job(不是 Empty Service/Deploy from Repo
   那个常驻服务类型)。
2. Schedule 字段填 cron 表达式(交易清理 `0 */6 * * *`,场次重置
   `0 4 * * *`,和上面 GitHub Actions 示例的频率保持一致)。
3. Command 字段:
   ```
   curl -sf -X POST https://<后端真实域名>/internal/demo-reset/transactions -H "X-Demo-Reset-Secret: $DEMO_RESET_SECRET"
   ```
   (场次重置那个 Cron Job 换成 `.../showtimes`。)
4. 这个 Cron Job 服务本身也要能读到 `DEMO_RESET_SECRET`——Railway 项目
   级的环境变量默认对同项目下所有服务可见,和后端服务共用同一个值即可,
   不需要单独再配一份。
5. Railway Cron Job 的确切配置字段以控制台当前实际界面为准——Railway
   的产品细节变化较快,这里给的是核心步骤,不是逐字段截图,创建前建议
   现场核对一下控制台。

---

## 首次部署顺序建议

1. 先生成好 `ADMIN_SEED_PASSWORD_HASH`(见上面),连同其余必填变量一起
   配进部署平台。
2. 部署后端,确认 `/actuator/health` 返回 200(部署平台的健康检查也会看
   这个)。
3. 部署前端,`NEXT_PUBLIC_API_BASE_URL` 指向后端真实域名。
4. 用 `ADMIN_SEED_PASSWORD_HASH` 对应的真实密码登录
   `admin@cineverse.local`,确认能进 `/admin/dashboard`。
5. 按上面的步骤注册 Stripe webhook,发一次测试事件确认签名校验通过。
6. 如果打算做公开 demo:生成 `DEMO_RESET_SECRET` 配进部署平台,按上面
   "Demo 数据重置"一节接入一种调度方式,手动触发一次确认两个端点都返回
   200(而不是等 cron 第一次自然触发才发现配错了)。
