# CineVerse — 电影院订票系统

CineVerse 是一个作品集(Portfolio)项目:一个完整的电影院订票系统后端,用来展示架构设计、并发处理(座位锁)、安全性(JWT认证)、测试(Testcontainers 集成测试)与 CI/CD 等后端工程能力。

项目按 Phase 迭代式交付,详细的技术栈选型、架构原则与模块路线图见 [`CLAUDE.md`](./CLAUDE.md)。

**当前状态:Phase 0 — 项目基建。** 只有骨架代码(全局异常处理、Swagger、Flyway 占位迁移),尚无业务 API。

## 技术栈

- Java 25 (LTS) + Spring Boot 4.1.x + Spring Security 7
- PostgreSQL 16 + Flyway
- Redis 7(座位锁 / refresh token 黑名单,Phase 5+ 接入)
- Spring Data JPA + Hibernate + MapStruct
- springdoc-openapi(Swagger UI)
- Maven

## 项目结构

```
CineVerse/
├── CLAUDE.md              # 项目唯一真相来源:架构、路线图、当前冲刺范围
├── docker-compose.yml     # 本地依赖:Postgres 16 + Redis 7
├── .env.example           # docker-compose 使用的环境变量样例
└── cineverse-backend/     # Spring Boot 后端(Maven 项目)
    └── src/main/resources/db/migration/  # Flyway 迁移脚本
```

## 本地开发

### 前置条件

- JDK 25(推荐 [Eclipse Temurin](https://adoptium.net/))
- Maven 3.9+
- Docker + Docker Compose

### 1. 启动依赖服务(Postgres + Redis)

```bash
cp .env.example .env   # 按需修改账号密码/端口
docker compose up -d
```

验证:

```bash
docker compose ps        # 两个服务都应为 healthy
```

### 2. 启动后端

```bash
cd cineverse-backend
mvn spring-boot:run
```

默认激活 `local` profile,连接 `docker-compose` 起的 Postgres(见 `application-local.yml`)。启动时 Flyway 会自动跑 `db/migration` 下的迁移脚本。

### 3. 验证

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Phase 0 阶段还没有业务 endpoint,Swagger UI 页面能打开、显示空的 API 列表即为正常。

### 4. 跑测试 / 构建

```bash
cd cineverse-backend
mvn clean install
```

GitHub Actions(`.github/workflows/ci.yml`)在每次 push 时执行同样的 `mvn test`。

## 环境配置(Profiles)

| Profile | 用途 | 配置文件 |
|---|---|---|
| `local`(默认) | 本地开发,连接 docker-compose 里的 Postgres | `application-local.yml` |
| `prod` | 生产部署,所有连接信息必须由环境变量提供,无默认值 | `application-prod.yml` |

## 路线图

完整的 Phase 0 ~ Phase 8 模块规划、每个 Phase 的完成定义(Definition of Done)见 [`CLAUDE.md`](./CLAUDE.md) 第 3、4 节。
