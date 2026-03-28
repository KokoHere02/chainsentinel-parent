# ChainSentinel 搭建方案（MVP，4 周）

## 1. 目标与范围

### 1.1 目标
在 4 周内交付一个可运行、可演示、可量化的 ETH 资产监控与告警网关（Java）。

### 1.2 本期范围（MVP）
1. 仅支持 Ethereum Sepolia（或主网只读）。
2. 监听 ETH 转账与 ERC20 Transfer 事件。
3. 支持地址、金额、频次三类规则告警。
4. 告警通道仅做 Webhook。
5. 提供事件与告警查询 API。

### 1.3 非目标（本期不做）
1. 不做跨链桥执行。
2. 不做 BTC/SOL/Tron 等多链。
3. 不做复杂 AI 风控评分。
4. 不做行情图表系统。

## 2. 技术栈与工程基线

1. Java 21
2. Spring Boot 3.3.x
3. web3j（EVM RPC）
4. MySQL 8（主存）
5. Redis（可选，后续做频次优化/限流）
6. Flyway（DB 版本管理）
7. Micrometer + Prometheus（可观测）

### 2.1 Maven 依赖（建议）
1. spring-boot-starter-web
2. spring-boot-starter-data-jpa
3. spring-boot-starter-validation
4. spring-boot-starter-actuator
5. web3j-core
6. flyway-core
7. mysql-connector-j
8. micrometer-registry-prometheus
9. lombok

## 3. 项目结构建议

```text
chainsentinel/
  src/main/java/.../
    api/                # REST 接口
    adapter/            # 链适配层（EthereumAdapter）
    scanner/            # 扫块与断点续扫
    event/              # 事件标准化、落库、去重
    rule/               # 规则引擎（ADDRESS/AMOUNT/FREQUENCY）
    alert/              # webhook 发送、重试、状态推进
    infra/              # 重试、时间、序列化、监控封装
  src/main/resources/
    db/migration/       # Flyway SQL
    application.yml
    application-dev.yml
  docker-compose.yml
  README.md
```

## 4. 核心模块设计

### 4.1 扫描与断点
1. 从 `scan_checkpoint.last_scanned_block` 读取起点。
2. 按窗口 `[last+1, last+window]` 拉取日志。
3. 仅窗口全成功后更新 checkpoint。

### 4.2 统一事件模型
事件统一落到 `asset_event`，核心字段包含：
1. 链信息：`chain/network`
2. 区块交易：`block_number/block_hash/tx_hash/log_index`
3. 转账主体：`from_address/to_address`
4. 资产信息：`token_type/token_contract/symbol/amount/decimals`
5. 状态信息：`status/confirmations/occurred_at/ingested_at`

### 4.3 去重与幂等
1. 唯一键：`(chain, tx_hash, log_index)`
2. 冲突策略：不重复插入，仅更新 `confirmations/status`

### 4.4 确认数推进
1. 新入库为 `PENDING`
2. 定时任务取链上最新高度，计算 `confirmations = latest - block_number + 1`
3. 满足阈值后推进为 `CONFIRMED`

### 4.5 规则与告警
1. ADDRESS：`from/to` 命中监控名单
2. AMOUNT：按 `symbol + op + threshold` 判断
3. FREQUENCY：按时间窗口聚合计数
4. 命中规则写 `alert_event`，异步发送 webhook，失败退避重试

## 5. 数据库落地（Flyway V1）

1. `chain_config`：链配置（rpc_url、confirm_required、enabled）
2. `scan_checkpoint`：断点续扫位点
3. `asset_event`：统一事件主表（含唯一键）
4. `monitor_address`：监控地址
5. `alert_rule`：规则配置（condition_json）
6. `alert_event`：告警记录（send_status/retry_count）

## 6. API 设计（MVP）

1. `POST /api/chains`：新增/更新链配置
2. `POST /api/addresses`：新增监控地址
3. `POST /api/rules`：新增规则
4. `POST /api/scanner/run`：手动触发扫描
5. `GET /api/events`：按链/地址/状态/时间范围分页查询
6. `GET /api/alerts`：按状态/级别/规则查询
7. `POST /api/alerts/retry/{id}`：手动重试告警发送

## 7. 可靠性策略

1. 断点续扫：窗口成功后提交 checkpoint。
2. 幂等去重：唯一键 + 冲突更新。
3. 有界重试：RPC 调用与 webhook 发送均支持指数退避。
4. 降级隔离：告警失败不影响事件入库。
5. 可观测：暴露采集延迟、重复率、告警成功率。

## 8. 指标与验收标准

### 8.1 指标
1. `scanner_lag_blocks`
2. `event_ingest_latency_ms`
3. `event_duplicate_rate`
4. `alert_send_success_rate`
5. `alert_retry_count`

### 8.2 验收目标
1. 扫描延迟 P95 < 5 秒（测试网）
2. 重复事件率 < 0.1%
3. 告警发送成功率 >= 99%
4. 节点短时故障可自动恢复并续扫
5. 可稳定演示 3 类规则命中

## 9. 4 周实施计划

### Week 1：链路打通
1. 完成 Flyway 建表与基础 API 框架。
2. 打通 Sepolia 扫块 + ERC20 Transfer 解析。
3. 完成 `asset_event` 入库与唯一键去重。

### Week 2：可靠性完善
1. 完成确认数推进任务。
2. 完成事件查询 API（分页+过滤）。
3. 完成 checkpoint 断点续扫。

### Week 3：规则与告警
1. 完成 ADDRESS/AMOUNT/FREQUENCY 三类规则。
2. 完成 webhook 发送与失败重试。
3. 完成告警查询与手动重试接口。

### Week 4：稳定性与演示
1. 接入 Prometheus 指标与告警面板。
2. 执行故障演练（RPC 不可用、Webhook 超时、重复扫描）。
3. 产出演示脚本、压测记录与 README。

## 10. 今天可执行任务（建议）

1. 初始化 Spring Boot 项目骨架与 Maven 依赖。
2. 编写 `docker-compose.yml`（MySQL + Prometheus）。
3. 落 Flyway `V1__init.sql`（6 张表 + 索引 + 唯一键）。
4. 先跑通 `POST /api/scanner/run -> asset_event 入库` 最小闭环。

## 11. 风险与应对

1. RPC 不稳定：多节点候选 + 超时 + 指数退避。
2. ERC20 精度处理：统一使用 `BigInteger/BigDecimal`。
3. 重组复杂度：MVP 先做基础重组检测与状态标记。
4. 告警噪声：规则分级 + 冷却时间 + 去重发送。
