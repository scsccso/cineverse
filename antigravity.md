# Antigravity Log

---

## Sprint 1：Admin User Management（用户管理）

### 后端新增文件

| 文件路径 | 说明 |
|---|---|
| `cineverse-backend/.../user/dto/UpdateUserRoleRequest.java` | 角色更新请求 DTO |
| `cineverse-backend/.../user/service/AdminUserService.java` | 分页查询/角色更新/删除的 Service 层实现 |
| `cineverse-backend/.../user/controller/AdminUserController.java` | `GET/PATCH/DELETE /api/v1/admin/users` 三个接口 |

### 后端修改文件

| 文件路径 | 变更 |
|---|---|
| `cineverse-backend/.../booking/repository/BookingRepository.java` | 追加 `existsByUserId(UUID)` 方法，供 `AdminUserService.deleteUser()` 用于删除前校验 |

### 前端新增文件

| 文件路径 | 说明 |
|---|---|
| `frontend/src/lib/api/admin-users.ts` | API Client：`getAdminUsers`/`updateUserRole`/`deleteUser`，均通过 `apiFetch` + `Authorization` header |
| `frontend/src/app/admin/users/page.tsx` | Admin 用户管理页面，`.admin-light` 作用域，使用 `Card`/`Badge`/`Button`，含确认 Dialog + 分页 |

---

## Sprint 2：修复数据获取 Bug + 双端物理隔离

### Root Cause 分析

**Bug 1（后端冗余注解）**：`AdminUserController` 使用了 `@PreAuthorize("hasRole('ADMIN')")` 注解，但项目的 `SecurityConfig` 没有 `@EnableMethodSecurity`，导致该注解**静默失效**（不报错、不生效）。安全性本身没有漏洞（`SecurityConfig` 的 URL 规则 `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` 已覆盖），但冗余注解与项目其他 Admin 接口（`ReportController`、`TicketController` 等均使用 URL-level 保护）的既有模式不一致——移除 `@PreAuthorize`，对齐现有规范。

**Bug 2（前端 loading 状态逻辑缺陷）**：`loading` 原先通过 `!usersPage && !error` 推导，分页时旧数据仍在、`loading=false`，切换页面会先短暂显示旧数据；且错误时未清空 `usersPage`，导致表格仍显示旧数据而非错误提示。修复：改用显式 `setLoading(true)` + request-ID ref（`requestIdRef`）取消竞态响应，错误时同步清空 `usersPage(null)`。这一模式与 `dashboard/page.tsx` 的 `cancelled` 标记一致。

**Bug 3（前端 ADMIN 反向拦截缺失）**：`(customer)/layout.tsx` 原本是纯 Server Component，无法读取 `AuthContext`，ADMIN 用户可以随意访问 `/profile` 等顾客端页面，双向物理隔离不完整。修复：转为 Client Component，`useEffect` 监听 `status/user`，ADMIN 登录态下 `router.replace('/admin/dashboard')`，与 `admin/layout.tsx` 的顾客反向拦截方向对称。

**Bug 4（LoginForm 登录后跳转未区分角色）**：`login()` 原返回 `Promise<void>`，`LoginForm` 拿不到 session 信息，所有角色一律跳转 `redirectTo`（默认 `/profile`）。修复：`AuthContext.login()` 改为返回 `Promise<AuthResponse>`（向后兼容，原有丢弃返回值的调用方不受影响）；`LoginForm` 读取 `session.user.role`，ADMIN → `/admin/dashboard`，CUSTOMER → `redirectTo`。

### 修改文件清单

| 文件路径 | 变更说明 |
|---|---|
| `cineverse-backend/.../user/controller/AdminUserController.java` | 移除 `@PreAuthorize`（与项目 URL-level 保护模式对齐） |
| `frontend/src/app/admin/users/page.tsx` | 重写 loading 状态机；request-ID ref 防竞态；`ApiError instanceof` 检查 409；Dialog 补 `role`/`aria-*` 属性 |
| `frontend/src/app/(customer)/layout.tsx` | 改为 Client Component；添加 ADMIN 反向拦截 `router.replace('/admin/dashboard')` |
| `frontend/src/lib/auth/auth-context.tsx` | `login()` 返回类型 `Promise<void>` → `Promise<AuthResponse>`；实现中 `return session` |
| `frontend/src/components/auth/login-form.tsx` | 读取 `session.user.role`，ADMIN 跳 `/admin/dashboard`，CUSTOMER 跳 `redirectTo` |

### 验证结果

- `mvn clean compile -q`：**BUILD SUCCESS**（仅 JDK 25 / Guava 的平台警告，与本次改动无关）
- `npm run build`：**编译成功，0 错误，0 警告**；`/admin/users` 路由正确出现在构建输出中

---

## Sprint 3：MapStruct 生成报错排查

### 排查过程
- **基准测试**：在后端执行 `mvn clean compile`，终端输出 `BUILD SUCCESS`，`UserMapperImpl` 等均正常生成并无报错。
- **Root Cause**：代码与注解本身完全正确，没有任何硬伤。大面积飘红纯粹是 IDE 的 Java Language Server 缓存未能及时同步 Maven `target/generated-sources` 目录的最新产物导致。

### 修改文件清单
- **无代码修改**：属于 IDE 环境问题，无需为了迎合缓存而擅自破坏现有的 Record DTO 签名或 MapStruct 规范。

---

## Sprint 4：Admin 导航栏补全

### 修改文件清单
- `frontend/src/components/admin/admin-header.tsx`

### 变更说明
- 将 `AdminHeader` 转为 Client Component。
- 补充了后台主导航区，增加指向 `/admin/dashboard`、`/admin/movies`、`/admin/users` 的 `<Link>`。
- 严格遵循 `.admin-light` 浅色设计规范，使用 `bg-secondary text-secondary-foreground` 样式表示高亮；通过 `usePathname` 获取当前路径并使用 `pathname.startsWith(href)` 来判定和实现当前模块的 Active 态。

---

## Sprint 5：`/admin/movies` API 契约核实——探测性 DELETE 误删真实种子电影事故（2026-08-12）

> 与 Sprint 1~4 不同：这一条不是 Antigravity 的工作记录，是同一天由 Claude
> Code（后续接手 `/admin/movies` 排查与契约核实任务的会话）在核实阶段造成
> 并自行发现、恢复的一次事故。附在这里是延续本文件"记录 admin 后台迭代过程
> 中的问题"这个用途，不代表这是 Antigravity 自己的产出或错误。

### 事故经过

在核实 `DELETE /api/v1/movies/{id}` 遇到"该电影仍有排期场次"时的 409 响应
格式时，为避免用一次性测试数据测不出真实的 409 路径，选择直接对一部真实
种子电影（`Interstellar`，`id 30000000-0000-0000-0000-000000000001`，来自
`V16__seed_diverse_movies.sql`）发起 `DELETE`，前提假设是"这部电影应该有
排期场次，删除会被 `RESTRICT`/409 挡下，所以是安全的探测"。这个假设本身
没有先核实过。

### Root Cause 分析

**事故（探测性调用命中真实数据，且忽略了与假设矛盾的证据）**：`DELETE`
之前实际先查过 `GET /api/v1/showtimes?movieId=<Interstellar 的 id>`，
返回了空数组——这个结果已经如实说明这部电影在当前数据库里没有任何排期
场次，`RESTRICT`/409 根本没有触发条件，`DELETE` 会真的执行成功，不是一次
安全的探测。但操作时仍然带着"应该会被挡下"的旧假设继续执行了 `DELETE`，
未把这个矛盾结果当成需要重新评估计划的信号。服务端返回 204 是它按契约
正确执行的结果，不是服务端 bug——问题完全出在验证方法的选择和对已有证据
的误读上。

事后复查确认：这**不是**查询参数写错导致的误报（`GET /api/v1/showtimes`
的 `movieId` 参数名经回读 `ShowtimeController` 源码确认无误），空结果是
准确的。

### 恢复操作

未重跑 Flyway migration（`V16`/`V17` 已记录在 `flyway_schema_history`
中标记为已应用，正常 `flyway migrate` 不会重新执行；`repair`/`clean`
这类强制重跑手段对其余 9 部真实电影和全库风险更大，未采用）。改为手动
恢复：

1. 从 `V16__seed_diverse_movies.sql` 原文抄出 Interstellar 那一行
   `INSERT` 的字段值和它在 `movie_genres` 里对应的 3 个分类关联
   （Adventure/Drama/Sci-Fi）。
2. `backdrop_url` 改用 `V17__update_movie_backdrop_urls.sql` 对
   Interstellar 的 `UPDATE` 语句设的最终值（TMDB 图），而不是 V16 当时
   复用 poster_url 的临时值——因为 V17 代表这个字段更新后的当前状态。
3. 通过 `docker exec -i cineverse-postgres psql` 手动执行
   `INSERT INTO movies(...) VALUES(...)`（沿用原 UUID，
   `ON CONFLICT DO NOTHING`）+ `INSERT INTO movie_genres`（按分类名 join
   回 3 个关联）。
4. 额外执行一条 `UPDATE`，把 `created_at`/`updated_at` 改回删除发生之前、
   同一次会话里已经实际请求并记录下来的原始时间戳
   （`2026-08-07T18:36:52.885023Z`），不是重新生成的新时间戳。

### 验证结果

- 恢复后单条记录（Interstellar）逐字段核对，与删除前的响应一致，
  `GET /api/v1/movies?page=0&size=20` 的 `totalElements` 回到 11。
- 用户要求逐项复核后，追加执行了两条只读 SQL（`SELECT title,
  content_rating, user_rating, status, poster_url, backdrop_url FROM
  movies` 和按 `movie_genres` 聚合 genre 名称的查询），对全部 11 部电影
  的基础字段和 genre 关联做了逐行核对，未发现除 Interstellar 之外的任何
  一行有偏差。这一步是在用户明确追问"数量对了不代表没事"之后才补做的，
  不是恢复动作本身自带的验证步骤——这正是这次事故沉淀出的操作原则之一
  （见 `CLAUDE.md` "`/admin/movies` 契约核实：探测性调用误删真实种子电影
  的事故与操作纪律" 一节）。

### 教训（完整原则见 CLAUDE.md 对应条目，此处仅摘要）

1. 有副作用的探测性调用，第一个尝试对象必须是自己创建、自己能完全控制
   生命周期的一次性数据，不能是真实数据——即使认为有防护机制兜底。
2. 执行前如果查到的证据和预设的安全假设矛盾，必须先停下来重新判断假设
   本身，不能带着矛盾证据继续按原计划执行。
3. 事故恢复后必须主动核对影响范围有没有超出预期，不能"数量对了就默认
   没事"。
