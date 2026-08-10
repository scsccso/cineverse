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
