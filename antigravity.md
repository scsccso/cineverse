# Antigravity Log

> 纯操作记录索引——每个 Sprint 只保留"做了什么、涉及哪些文件"。有长期
> 架构/工程价值的决定(为什么这么做、踩过什么坑、沉淀出什么可复用模式)
> 已经提炼进 `CLAUDE.md`,这里不重复展开,只留指引。

---

## Sprint 1:Admin User Management(用户管理)

新增 `GET/PATCH/DELETE /api/v1/admin/users`(分页查询/角色更新/删除)后端
三件套 + `/admin/users` 前端页面。

文件:`user/dto/UpdateUserRoleRequest.java`、`user/service/AdminUserService.java`、
`user/controller/AdminUserController.java`、
`booking/repository/BookingRepository.java`(追加 `existsByUserId`)、
`frontend/src/lib/api/admin-users.ts`、`frontend/src/app/admin/users/page.tsx`。

决定见 CLAUDE.md「Admin 用户管理(2026-08-11)」一节。

---

## Sprint 2:修复数据获取 Bug + 双端物理隔离

四个 bug:`@PreAuthorize` 冗余注解静默失效、分页时 loading 状态短暂显示
旧数据、ADMIN 能绕过隔离访问顾客端页面、登录后未按角色区分跳转目标。

文件:`AdminUserController.java`、`admin/users/page.tsx`、
`(customer)/layout.tsx`、`auth-context.tsx`、`login-form.tsx`。

决定见 CLAUDE.md「Admin 用户管理(2026-08-11)」一节(含 request-id 防竞态
的可复用模式说明,以及 ADMIN 反向隔离最终采用的方案——注意那一节记录的
是复核后的最终版本,不是这个 Sprint 当时的中间实现)。

---

## Sprint 3:MapStruct 生成报错排查

IDE 对 MapStruct 生成代码报红,`mvn clean compile` 验证过代码本身没问题,
判定为纯 IDE 缓存问题,无代码修改。

决定见 CLAUDE.md「Admin 用户管理(2026-08-11)」一节——这个结论后来被证实
是对但不完整,完整版本(还有一种真实的运行时 bean 装配失败场景不属于
IDE 缓存问题)见该节展开。

---

## Sprint 4:Admin 导航栏补全

`AdminHeader` 转 Client Component,补充指向 `/admin/dashboard`、
`/admin/movies`、`/admin/users` 的顶部导航链接。

文件:`frontend/src/components/admin/admin-header.tsx`。

决定见 CLAUDE.md 1.5.2 节补充说明,以及「Admin 用户管理(2026-08-11)」
一节「`AdminHeader` 的 `/admin/movies` 死链接」条目(`/admin/movies` 当时
还不存在,链接一度被移除,后来又因为该页面真的交付而恢复)。

---

## Sprint 5:`/admin/movies` API 契约核实——探测性 DELETE 误删真实种子电影事故(2026-08-12)

> 与 Sprint 1~4 不同:这一条不是 Antigravity 的工作记录,是同一天由 Claude
> Code(后续接手 `/admin/movies` 排查与契约核实任务的会话)在核实阶段造成
> 并自行发现、恢复的一次事故。附在这里是延续本文件"记录 admin 后台迭代过程
> 中的问题"这个用途,不代表这是 Antigravity 自己的产出或错误。

为验证 `DELETE /api/v1/movies/{id}` 遇到排期场次时的 409 响应,直接对一部
真实种子电影(`Interstellar`)发起 `DELETE`,而不是用自建的一次性测试数据
——这部电影实际没有排期场次,`DELETE` 真的执行成功,造成数据丢失。事后
读 `V16`/`V17` migration 源文件手动重建了这一行数据和它的 genre 关联,
核对全部 11 部电影确认没有其他数据受影响。

完整的事故经过、根因分析、恢复步骤、以及沉淀出的操作纪律(探测性调用不能
拿真实数据当第一个尝试对象、证据和假设矛盾时必须停下重新判断、恢复后要
主动核对影响范围)见 CLAUDE.md「`/admin/movies` 契约核实:探测性调用误删
真实种子电影的事故与操作纪律」一节。
