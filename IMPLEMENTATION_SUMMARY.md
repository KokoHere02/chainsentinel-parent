# ChainSentinel 实现总结（当前版本）

## 1. 当前实现了什么

当前项目已经从 0 搭建为可运行的 Maven 多模块后端，并完成了 ETH 资产监控 MVP 的核心闭环：

1. 链配置管理（DB + API）
2. Sepolia 扫描（ETH 转账 + ERC20 Transfer）
3. 事件标准化入库 + 幂等去重
4. 断点续扫（scan_checkpoint）
5. 确认数推进（扫描时计算 confirmations 与状态）
6. 地址监控名单管理
7. 规则管理（已支持 ADDRESS 规则创建）
8. 地址规则命中生成告警
9. 告警查询
10. Webhook 发送 + 自动重试 + 手动重试

---

## 2. 当前整体流程

1. 配置链（`POST /api/chains`）写入 `chain_config`
2. 配置监控地址（`POST /api/addresses`）写入 `monitor_address`
3. 配置地址规则（`POST /api/rules`，`type=ADDRESS`）写入 `alert_rule`
4. 触发扫描（`POST /api/scanner/run`）
5. 扫描器按 checkpoint 拉链上数据，解析 ETH/ERC20 转账并入库到 `asset_event`
6. 入库后执行地址规则匹配，命中则写 `alert_event(send_status=PENDING)`
7. 定时任务拉取 PENDING 告警，发送 webhook
8. 成功标记 `SENT`；失败累计 `retry_count`，超阈值标记 `FAILED`
9. 通过 `GET /api/events` / `GET /api/alerts` 查询结果

---

## 3. 模块划分

1. `chainsentinel-common`
- 目前作为通用模块占位，后续可放工具类/常量/异常基类。

2. `chainsentinel-core`
- 领域模型、服务接口、DTO（不依赖具体基础设施实现）。

3. `chainsentinel-infra`
- JPA 实体、Repository、链扫描实现、规则匹配实现、告警发送实现、定时任务。

4. `chainsentinel-web`
- Spring Boot 启动类、REST API、配置文件、Flyway SQL。

---

## 4. 主要类职责说明（按模块）

### 4.1 core 模块

#### 模型枚举
1. `EventStatus`
- 事件状态：`PENDING / CONFIRMED / REORGED`

2. `TokenType`
- 资产类型：`ETH / ERC20`

3. `AlertRuleType`
- 规则类型：`ADDRESS / AMOUNT / FREQUENCY`
- 当前仅 ADDRESS 已接入执行链路。

#### 服务接口
1. `ScannerService`
- 扫描入口接口（`runOnce`）

2. `EventQueryService`
- 事件查询接口（分页+过滤）

3. `ChainConfigService`
- 链配置新增/更新

4. `MonitorAddressService`
- 监控地址新增/更新

5. `AlertRuleService`
- 规则创建

6. `AlertQueryService`
- 告警查询

7. `AlertDispatchService`
- 告警发送与重试（自动批量 + 单条手动）

#### DTO
1. `EventQuery / EventView`
- 事件查询入参与返回结构

2. `ChainConfigUpsertCommand / ChainConfigView`
- 链配置入参与返回

3. `MonitorAddressUpsertCommand / MonitorAddressView`
- 监控地址入参与返回

4. `AlertRuleCreateCommand / AlertRuleView`
- 规则创建入参与返回

5. `AlertQuery / AlertView`
- 告警查询入参与返回

### 4.2 infra 模块

#### 实体 Entity
1. `ChainConfigEntity`
- 对应 `chain_config`，存链 RPC、确认数、开关

2. `ScanCheckpointEntity`
- 对应 `scan_checkpoint`，记录扫描断点

3. `AssetEventEntity`
- 对应 `asset_event`，统一链上事件模型

4. `MonitorAddressEntity`
- 对应 `monitor_address`，监控地址名单

5. `AlertRuleEntity`
- 对应 `alert_rule`，规则配置（条件 JSON）

6. `AlertEventEntity`
- 对应 `alert_event`，告警事件与发送状态

#### Repository
1. `ChainConfigRepository`
- 按 `(chain, network)` 查询链配置

2. `ScanCheckpointRepository`
- 读取/更新扫描断点

3. `AssetEventRepository`
- 事件查询、按唯一维度幂等更新

4. `MonitorAddressRepository`
- 地址查询与命中判断

5. `AlertRuleRepository`
- 按规则类型取启用规则

6. `AlertEventRepository`
- 告警存在性判重、分页查询、取待发送队列

#### 配置类
1. `ScannerProperties`
- 扫描默认配置（可被 DB 配置覆盖）

2. `AlertProperties`
- 告警发送配置（webhook、重试次数、调度周期）

#### 服务实现
1. `EthereumScannerService`（核心）
- 扫描实现类，职责：
- 读取运行配置（优先 DB `chain_config`，回退 yml）
- 读取/初始化 checkpoint
- 按窗口扫描区块
- 解析 ERC20 Transfer 与 ETH 转账
- 计算确认数与状态
- `asset_event` 幂等写库
- 推进 checkpoint
- 调用 `AddressAlertMatcher` 做地址告警匹配

2. `DefaultEventQueryService`
- 事件分页查询实现

3. `DefaultChainConfigService`
- 链配置新增/更新实现

4. `DefaultMonitorAddressService`
- 监控地址新增/更新实现

5. `DefaultAlertRuleService`
- 规则创建实现（条件 Map 序列化为 JSON）

6. `AddressAlertMatcher`
- 地址规则匹配器
- 逻辑：事件 from/to 命中监控名单 -> 对启用 ADDRESS 规则生成 `alert_event`
- 防重：同 `rule_id + asset_event_id` 只生成一次

7. `DefaultAlertQueryService`
- 告警分页查询实现

8. `WebhookAlertDispatchService`
- Webhook 发送与重试实现
- 发送成功：`SENT`
- 发送失败：`retry_count + 1`，未超限保持 `PENDING`，超限变 `FAILED`
- 支持 `retryOne(id)` 手动重试

#### 定时任务
1. `AlertDispatchJob`
- 周期性发送待处理告警（读取 `chainsentinel.alert.dispatch-interval-ms`）

### 4.3 web 模块

#### 启动类
1. `ChainSentinelApplication`
- 启动 Spring Boot
- 启用 `@ConfigurationPropertiesScan`
- 启用 `@EnableScheduling`

#### 控制器 API
1. `HealthController`
- `GET /api/health`

2. `ChainController`
- `POST /api/chains`

3. `AddressController`
- `POST /api/addresses`

4. `RuleController`
- `POST /api/rules`

5. `ScannerController`
- `POST /api/scanner/run`

6. `EventController`
- `GET /api/events`

7. `AlertController`
- `GET /api/alerts`
- `POST /api/alerts/retry/{id}`

#### 配置与迁移
1. `application.yml` / `application-dev.yml`
- 数据库、扫描配置、告警配置

2. `db/migration/V1__init.sql`
- 初始化 6 张核心表

---

## 5. 已实现 API 清单

1. `GET /api/health`
2. `POST /api/chains`
3. `POST /api/addresses`
4. `POST /api/rules`
5. `POST /api/scanner/run`
6. `GET /api/events`
7. `GET /api/alerts`
8. `POST /api/alerts/retry/{id}`

---

## 6. 已实现能力 vs 未完成能力

### 已实现
1. 单链（ETH Sepolia）扫描
2. ETH + ERC20 Transfer 解析
3. 幂等去重、断点续扫
4. 地址规则触发告警
5. Webhook 发送与重试
6. 事件/告警查询 API

### 未完成（下一阶段）
1. AMOUNT / FREQUENCY 规则执行链路
2. 更完整确认数推进定时任务（当前主要在扫描时更新）
3. Reorg 深度处理（当前未做完整回滚补偿）
4. Webhook 退避策略细化（当前为基础重试）
5. 指标埋点完善（scanner_lag、duplicate_rate、alert_success_rate 等）
6. 更完整测试（集成测试、链路压测）

---

## 7. 运行说明（当前）

1. 启动依赖：`docker compose up -d`
2. 配置环境变量：
- `ETH_RPC_URL`
- `ALERT_WEBHOOK_URL`（可选，不配置则告警发送关闭）
3. 启动服务：
- `mvn "-Dmaven.repo.local=./.m2/repository" -pl chainsentinel-web -am spring-boot:run`

---

## 8. 关键设计决策

1. 先单链落地，架构按模块分层，后续可扩多链 Adapter。
2. 先不用 MQ，降低复杂度，优先保证端到端闭环可运行。
3. 规则与告警先做最小可用（ADDRESS + webhook），逐步扩展复杂规则。
4. 运行配置采用“DB 优先，配置文件兜底”，支持在线变更链参数。
