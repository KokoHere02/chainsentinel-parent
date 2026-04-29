# ChainSentinel

ChainSentinel 是一个基于 Java/Spring Boot 的链上监控与告警后端项目。

## 当前能力

1. 链配置管理（支持 HTTP/WS 字段）
2. 监控地址、地址作用域、作用域代币管理
3. 事件与告警查询、告警重试
4. 规则管理（`EVENT` / `PRICE_THRESHOLD`）
5. 价格能力：
- HTTP 拉价
- OKX WebSocket 订阅与重连
- `price_tick` 落库、聚合查询、回填任务
6. 仪表盘聚合接口（overview / price / alerts / backfill / health）

## 技术栈

1. Java 17
2. Spring Boot 3.3.x
3. MySQL 8
4. Flyway
5. Micrometer + Prometheus
6. Maven 多模块

## 仓库结构

1. `chainsentinel-common`：通用工具与基础组件
2. `chainsentinel-core`：领域模型、服务接口、DTO
3. `chainsentinel-infra`：仓储层、任务、规则与告警实现
4. `chainsentinel-price`：价格采集与流处理
5. `chainsentinel-web`：REST API 与应用启动模块
6. `docs`：设计与阶段性文档
7. `http`：联调请求脚本
8. `ops`：运维与部署相关文件

## 快速启动

### 1. 启动依赖

```bash
docker compose up -d
```

默认会启动：
- MySQL：`localhost:3306`
- Prometheus：`localhost:9090`

### 2. 准备配置

参考并复制：
- `chainsentinel-web/src/main/resources/application-example.yml`

至少需要配置：
- 数据库连接
- `chainsentinel.security.crypto.key-base64`
- 链 RPC 地址

### 3. 启动服务

```bash
mvn -pl chainsentinel-web -am spring-boot:run
```

默认端口：`8080`

## API 与联调

1. API 文档见 `docs/` 下最新版本
2. 错误码规范见 `docs/ERROR_CODE_错误码规范_2026-04-29.md`
3. 联调脚本见 `http/` 目录
4. 健康检查：`GET /api/health`

## 说明

1. 当前项目仍在持续迭代中，接口和字段可能演进
2. 内部运维接口位于 `/api/internal/**`，建议仅内网使用并加鉴权
3. 生产环境请使用 `application-prod.yml` 并通过环境变量注入敏感配置
