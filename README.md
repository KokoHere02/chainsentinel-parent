# ChainSentinel

ChainSentinel 是一个以 Java/Spring Boot 为核心的链上监控与告警后端项目。  
当前仓库处于持续开发阶段，已具备可运行的后端主链路，可用于本地联调与功能演示。

## 当前能力（阶段性）

1. 链配置、监控地址/代币管理
2. 规则管理（含模板、启停、条件更新、调试匹配）
3. 事件与告警查询、告警重试
4. 价格链路：
- HTTP 拉价
- OKX WS 订阅（重连、重订阅、心跳保活）
- WS 数据写缓存 + 异步批量落库 `price_tick`
- `price_tick` 明细与聚合查询
- `price_tick` TTL 清理任务

## 技术栈

1. Java 17
2. Spring Boot 3.x
3. MySQL 8
4. Flyway
5. Micrometer
6. Maven 多模块

## 仓库结构

1. `chainsentinel-common`: 通用组件
2. `chainsentinel-core`: 领域模型、服务接口、DTO
3. `chainsentinel-infra`: JPA/Repository、任务、规则实现、告警实现
4. `chainsentinel-price`: 价格服务与 WS 链路
5. `chainsentinel-web`: 启动模块与 REST API
6. `docs`: 设计文档、工作总结、运行手册
7. `http`: HTTP 联调脚本
8. `ops`: 运维脚本与初始化 SQL

## 快速开始

## 1) 启动依赖

```bash
docker compose up -d
```

默认会启动：
- MySQL: `localhost:3306`
- Prometheus: `localhost:9090`

## 2) 配置本地参数

1. 复制示例配置（脱敏）并按本机改值：
- 参考文件：`chainsentinel-web/src/main/resources/application-example.yml`

2. 建议不要把真实密钥/RPC URL 提交到仓库。

## 3) 启动服务

```bash
mvn -pl chainsentinel-web -am spring-boot:run
```

默认端口：`8080`

## API 文档与联调

1. API 文档（当前接口）：`docs/API_接口文档_2026-04-10.md`
2. HTTP 联调文件：`http/api.http`

## 配置安全说明

`application-dev.yml` 可能包含敏感信息（如密钥、私有 RPC）。  
对外发布代码时请使用示例配置，不要上传真实密钥。

## 项目状态说明

本项目仍在迭代中，接口与数据结构可能继续演进。  
对外依赖建议优先基于 `docs/API_接口文档_2026-04-10.md` 做联调，并关注后续变更提交。

