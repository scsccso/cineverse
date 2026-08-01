-- 仅本地开发用,生产环境部署前必须删除此 migration(或用 Flyway
-- 的环境过滤机制跳过),不要把固定密码的管理员账号带上线。
--
-- 登录信息(本地测试用):
--   email:    admin@cineverse.local
--   password: Admin@12345
-- 密码已用 BCrypt(strength 10)加密,下面存的是 hash,不是明文。
INSERT INTO users (id, email, password_hash, role, full_name)
VALUES (
    gen_random_uuid(),
    'admin@cineverse.local',
    '$2a$10$JbfdJLYzBh/g66wDQQsb4eS22gpUvBdZXEiaZqAXCz96y.wBY8rGS',
    'ADMIN',
    'CineVerse Admin'
);
