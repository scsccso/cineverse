-- password_hash 是 Flyway placeholder(${adminPasswordHash}),不是写死
-- 的 hash——本地开发的默认值(对应下面这组本地测试账号)在 application.yml
-- 的 spring.flyway.placeholders.adminPasswordHash 里;application-prod.yml
-- 里同一个 placeholder 没有默认值,ADMIN_SEED_PASSWORD_HASH 环境变量没设
-- 会直接部署失败,而不是悄悄把这组本地测试密码带上线。生成一个真实 hash
-- 的步骤见 docs/DEPLOYMENT.md。
--
-- 本地开发默认登录信息(只在 ADMIN_SEED_PASSWORD_HASH 未设置时生效,
-- 见 application.yml):
--   email:    admin@cineverse.local
--   password: Admin@12345
INSERT INTO users (id, email, password_hash, role, full_name)
VALUES (
    gen_random_uuid(),
    'admin@cineverse.local',
    '${adminPasswordHash}',
    'ADMIN',
    'CineVerse Admin'
);
