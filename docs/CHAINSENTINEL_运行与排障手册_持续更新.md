# ChainSentinel 运行与排障手册（持续更新）

> 本手册用于统一记录：运行步骤、配置约定、常见问题、快速排障、变更注意事项。
> 维护原则：只增量更新，不删除关键历史结论。

## 1. 手册目标

1. 让项目在新环境可快速跑通。
2. 降低“环境问题”和“配置漂移”带来的排障成本。
3. 给后续迭代保留稳定的操作基线。

---

## 2. 当前系统基线（2026-04-05）

1. 价格 provider 运行时配置来源：数据库（`price_provider_config`）。
2. 运行时配置缓存：本地 Caffeine，TTL 10 秒。
3. 支持手动刷新缓存：
   - `POST /api/internal/runtime-config/price/refresh`
4. 运行时配置回退指标：
   - `price_runtime_config_db_fallback_total{scene,reason,provider}`

---

## 3. 启动前检查清单

1. 数据库可连接，迁移脚本已执行。
2. `application-dev.yml` 至少包含：
   - `chainsentinel.security.crypto.key-base64`
3. 国内网络如访问 OKX 受限，先准备代理（见第 5 节）。
4. 关键表有基础数据：
   - `price_provider_config`
   - `price_pull_target`

---

## 4. 价格运行时配置说明

### 4.1 数据库字段建议

`price_provider_config` 关键字段：

1. `provider_name`：如 `okx`
2. `enabled`：是否启用
3. `priority`：越小优先级越高
4. `base_url`：provider 基础地址
5. `timeout_ms`：请求超时（毫秒）

### 4.2 非法配置回退规则

1. `priority <= 0`：回退为 `Integer.MAX_VALUE`
2. `base_url` 为空：回退到调用方默认地址
3. `timeout_ms <= 0`：回退到调用方默认超时
4. DB 查询异常：回退默认值并记录告警日志与回退指标

### 4.3 缓存刷新策略

1. 常规情况：依赖 10 秒 TTL 自动刷新即可。
2. 需要立即生效：手动调用刷新接口。
3. 当前刷新范围：本实例本地缓存。
4. 后续预留：多实例场景可扩展为分布式失效通知。

---

## 5. 国内网络代理运行（OKX）

### 5.1 先验证链路

```bash
curl.exe --socks5-hostname 127.0.0.1:10808 "https://www.okx.com/api/v5/market/ticker?instId=BTC-USDT"
```

### 5.2 Maven 启动示例

```powershell
$env:MAVEN_OPTS='-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=10808 -Djava.net.useSystemProxies=true'
mvn -pl chainsentinel-web -am spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev
```

### 5.3 IDEA VM 参数

```text
-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=10808
```

---

## 6. 常见问题与处理

### 6.1 `Connection reset`（OKX）

结论：优先判断网络链路/地区访问限制，不是业务参数问题。

处理顺序：

1. `curl --socks5-hostname` 验证代理链路
2. 确认 JVM 代理参数生效
3. 观察 `price.fetch.*` 相关日志

### 6.2 `chainsentinel.security.crypto.key-base64 must be configured`

结论：配置缺失。

处理：补齐 `application-dev.yml` 中 `chainsentinel.security.crypto.key-base64`。

### 6.3 YAML 编码异常（MalformedInputException）

结论：文件编码损坏。

处理：统一为 UTF-8（无 BOM）。

---

## 7. 回归测试建议（最小集）

1. 运行时配置读取与回退：

```bash
mvn --% -pl chainsentinel-infra -am test -Dtest=DbPriceProviderRuntimeConfigTest -Dsurefire.failIfNoSpecifiedTests=false
```

2. 价格缓存兜底路径：

```bash
mvn --% -pl chainsentinel-price -am test -Dtest=DefaultPriceServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

3. 内部接口（规则评估 + 缓存刷新）：

```bash
mvn --% -pl chainsentinel-web -am test -Dtest=InternalRuleControllerTest,InternalRuntimeConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

---

## 8. 变更操作建议

1. 任何“配置读取逻辑”改动都必须补单测。
2. 任何“降级/回退路径”改动都必须补回归测试。
3. 提交建议分组：
   - `feat/fix`（代码）
   - `test`（测试）
   - `docs`（文档）

---

## 9. 持续更新记录

### 2026-04-05

1. 增加运行时配置 Caffeine 缓存（TTL 10s）。
2. 增加 DB 回退指标 `price_runtime_config_db_fallback_total`。
3. 增加手动刷新入口 `/api/internal/runtime-config/price/refresh`。
4. 补齐对应单测与回归测试。

---

## 10. 下一步待办（滚动维护）

1. 刷新接口增加最小鉴权与审计日志（当前阶段可先不做）。
2. 为回退指标增加告警阈值建议。
3. 多实例部署时补分布式缓存失效机制。