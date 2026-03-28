# ETH 资产监控与告警网关（Java，1个月可落地版）

## 0. 结论先行

这是一个“先做单链（ETH/EVM）再扩展跨链”的方案。目标是 4 周内交付一个可运行、可演示、可量化的 Java 项目。

不做大而全跨链执行网关，只做：

1. 链上转账事件监听
2. 统一事件落库
3. 地址/金额/频次规则告警
4. 断点续扫 + 去重 + 基础确认数

---

## 1. 项目背景与业务价值

在钱包、风控、托管等业务里，真实需求是“第一时间知道关键地址是否有异常资金流动”，而不是立即做跨链交易。

痛点：

1. 监控数据分散，无法统一查询关键地址资产流动。
2. 节点抖动容易漏事件或重复处理。
3. 人工盯链效率低，异常发现滞后。

项目价值：

1. 把链上数据变成可查询、可告警的业务能力。
2. 形成稳定的“采集 -> 去重 -> 规则 -> 告警”闭环。
3. 为后续扩展多链打基础（适配器架构已预留）。

---

## 2. 范围控制（必须遵守）

### 2.1 本期要做（MVP）

1. 仅支持 `Ethereum Sepolia`（或主网只读）。
2. 监听 `ETH 转账 + ERC20 Transfer`。
3. 支持监控地址名单、金额阈值、频次阈值。
4. 告警通道先做 `Webhook`。
5. 提供事件查询 API 与基础指标。

### 2.2 本期不做（明确边界）

1. 不做跨链桥执行。
2. 不做 BTC/SOL/Tron 多链支持（后续扩展）。
3. 不做复杂 AI 风控评分。
4. 不做 K 线行情图表系统。

---

## 3. 技术选型（Java）

1. `Spring Boot 3.x`
2. `web3j`（EVM RPC）
3. `MySQL 8`（主存）
4. `Redis`（可选，缓存/限速）
5. `Micrometer + Prometheus`（指标）
6. `Flyway`（数据库版本管理）

说明：先不引入 MQ，减少复杂度。需要削峰时再加 Kafka/RabbitMQ。

---

## 4. 总体架构

```text
+----------------------+      +---------------------+
|  Block Scanner       | ---> | Event Normalizer    |
|  (web3j pull logs)   |      | (统一字段)          |
+----------+-----------+      +----------+----------+
           |                             |
           v                             v
+----------------------+      +---------------------+
| Dedup + Confirm      | ---> | Rule Engine         |
| (去重/确认数推进)     |      | (地址/金额/频次)     |
+----------+-----------+      +----------+----------+
           |                             |
           v                             v
+----------------------+      +---------------------+
| MySQL (events/check) |      | Alert Dispatcher    |
|                      |      | (webhook + retry)   |
+----------------------+      +---------------------+

+----------------------+
| REST API             |
| 查询事件/规则/告警    |
+----------------------+
```

---

## 5. 核心数据流程（详细）

### 5.1 扫描流程

1. 读取 `scan_checkpoint.last_block`。
2. 调用 RPC 拉取 `[last+1, last+window]` 区块日志。
3. 解析 ETH/ERC20 转账事件。
4. 标准化成 `asset_event`。
5. 按唯一键去重写库。
6. 更新 checkpoint 到窗口末尾（仅在成功后更新）。

### 5.2 确认数流程

1. 新事件入库状态为 `PENDING`。
2. 定时任务读取最新链高度，计算 `confirmations = latest - block_number + 1`。
3. 当 `confirmations >= confirm_required`，状态改为 `CONFIRMED`。

### 5.3 告警流程

1. 规则引擎对 `PENDING/CONFIRMED` 事件做匹配。
2. 命中规则写入 `alert_event`。
3. 调度器发送 webhook。
4. 发送失败按退避重试，超限标记 `FAILED`。

---

## 6. 数据库设计（可直接建表）

### 6.1 `chain_config`

- 作用：链基础配置
- 核心字段：`chain, network, rpc_url, confirm_required, enabled`

### 6.2 `scan_checkpoint`

- 作用：断点续扫
- 核心字段：`chain, network, last_scanned_block, updated_at`

### 6.3 `asset_event`

- 作用：统一事件主表
- 唯一键：`uk_chain_tx_log (chain, tx_hash, log_index)`
- 关键字段：
  - `chain, network`
  - `block_number, block_hash, tx_hash, log_index`
  - `from_address, to_address`
  - `token_type, token_contract, symbol, amount, decimals`
  - `status(PENDING/CONFIRMED/REORGED)`
  - `confirmations, occurred_at, ingested_at`

### 6.4 `monitor_address`

- 作用：监控地址管理
- 字段：`chain, address, tag, enabled`

### 6.5 `alert_rule`

- 作用：规则配置
- 字段：`name, type(ADDRESS/AMOUNT/FREQUENCY), condition_json, severity, enabled`

### 6.6 `alert_event`

- 作用：告警记录
- 字段：`rule_id, asset_event_id, severity, send_status, retry_count, last_error, sent_at`

---

## 7. 规则引擎设计（MVP）

### 7.1 地址规则

示例：

```json
{
  "type": "ADDRESS",
  "condition": { "watchlist": ["0xabc...", "0xdef..."] },
  "severity": "HIGH"
}
```

命中条件：`from/to` 命中监控名单。

### 7.2 金额规则

示例：

```json
{
  "type": "AMOUNT",
  "condition": { "symbol": "USDT", "op": ">=", "threshold": "100000" },
  "severity": "CRITICAL"
}
```

### 7.3 频次规则

示例：

```json
{
  "type": "FREQUENCY",
  "condition": { "windowMinutes": 10, "txCountGte": 20 },
  "severity": "MEDIUM"
}
```

说明：频次统计可先用 SQL 聚合实现，后续再切 Redis 滑动窗口。

---

## 8. API 设计（MVP）

1. `POST /api/chains`
   - 新增/更新链配置
2. `POST /api/addresses`
   - 新增监控地址
3. `POST /api/rules`
   - 新增告警规则
4. `POST /api/scanner/run`
   - 手动触发一次扫描
5. `GET /api/events`
   - 查询事件（链、地址、状态、时间范围）
6. `GET /api/alerts`
   - 查询告警（状态、级别、规则）
7. `POST /api/alerts/retry/{id}`
   - 手动重试告警发送

---

## 9. 关键可靠性策略

1. **断点续扫**：checkpoint 只在当前窗口处理成功后提交。
2. **幂等去重**：唯一键防重复插入；重复时仅更新确认数。
3. **失败重试**：RPC 与告警发送都有有界重试。
4. **降级**：告警发送失败不阻塞事件入库。
5. **可观测**：暴露扫描延迟、重复率、告警发送成功率。

---

## 10. 指标与验收标准

### 10.1 核心指标

1. `scanner_lag_blocks`（扫描滞后区块数）
2. `event_ingest_latency_ms`
3. `event_duplicate_rate`
4. `alert_send_success_rate`
5. `alert_retry_count`

### 10.2 验收目标（1个月）

1. 扫描延迟 P95 < 5 秒（测试网）
2. 重复事件率 < 0.1%
3. 告警发送成功率 >= 99%
4. 节点短时故障后可自动恢复并继续扫描
5. 能稳定演示 3 类规则告警命中

---

## 11. 4 周开发计划（可执行）

### 第 1 周：链路打通

1. 完成 Flyway 建表与基础 API。
2. 打通 Sepolia 扫块 + ERC20 Transfer 解析。
3. 完成 `asset_event` 入库与唯一键去重。

### 第 2 周：确认数与查询

1. 完成确认数推进任务。
2. 完成事件查询 API（分页、条件过滤）。
3. 完成 checkpoint 断点续扫。

### 第 3 周：规则与告警

1. 完成地址/金额/频次规则。
2. 完成 webhook 告警发送与重试。
3. 完成告警查询 API。

### 第 4 周：稳定性与演示

1. 增加 Prometheus 指标。
2. 做故障演练（节点不可用、Webhook 超时、重复扫描）。
3. 输出压测/演示报告与 README。

---

## 12. 风险与应对

1. **RPC 不稳定**
   - 应对：多节点配置 + 超时重试 + 指数退避。

2. **ERC20 精度处理错误**
   - 应对：统一使用 `BigInteger/BigDecimal` 与 `decimals` 换算。

3. **重组处理复杂**
   - 应对：MVP 先实现“基础重组检测 + 状态标记”，不做复杂补偿。

4. **告警噪声**
   - 应对：规则分级 + 冷却时间 + 去重发送。

---

## 13. 简历写法（可直接用）

1. 基于 Java + web3j 实现 ETH 资产监控网关，支持 ERC20 转账实时采集与统一事件模型。
2. 设计断点续扫与幂等去重机制，在节点抖动场景下保障数据采集连续性。
3. 实现地址/金额/频次三类规则告警，Webhook 发送成功率达到 99%+。
4. 构建确认数推进与基础重组处理流程，支撑链上事件状态可追踪。

---

## 14. 下一阶段扩展（可选）

1. 新增 `TronAdapter`，扩展到双链。
2. 引入 MQ 解耦扫描与告警。
3. 增加简单前端看板（事件趋势、告警趋势、扫描滞后）。
