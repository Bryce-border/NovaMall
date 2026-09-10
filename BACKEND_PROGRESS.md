# NovaMall 后端改造记录

本文记录可用于项目复盘和简历描述的后端设计演进。代码存在、编译通过、
单元测试通过、数据库集成验证、性能压测是不同层级的证据，不能互相替代。

## 阶段一：订单和商品基础模型扩展

- 商品主表增加 `description`、`cover_image_url`、`market_price`。
- 订单增加商品金额、优惠金额、运费和支付/发货/完成/取消时间。
- 增加用户地址、订单地址快照、支付、物流和购物车表。
- 设计原则：商品列表使用精简 DTO，商品详情使用独立 DTO；金额使用 `BigDecimal`，避免浮点误差。

## 阶段二：地址、购物车和订单分页

- 用户地址通过网关注入的 `X-User-Id` 做归属隔离，默认地址使用事务内先清理再设置。
  目前尚未覆盖并发新增默认地址，不能宣称已保证并发场景唯一性。
- 购物车按 `(user_id, sku_id)` 唯一，数量更新使用 `Math.addExact` 防止整数溢出。
- 订单使用 MyBatis-Plus `Page` 和 `PaginationInnerInterceptor`，返回 `items/page/size/total/hasNext`。
- 用户服务和订单服务之间通过 OpenFeign 传递地址快照，订单创建时复制字段，避免地址变更影响历史订单。

## 阶段三：商品 SKU 和快照

- 商品创建时生成 `P{productId}-DEFAULT` 默认 SKU，兼容旧商品模型。
- 阶段三时独立 SKU 查询从数据库读取最新价格，但原订单商品查询仍混合浏览缓存与数据库。
  阶段六已将订单路径切换到数据库 SKU 查询，并移除旧内部商品接口对浏览缓存的依赖。
- 订单项保存商品名、主图、SKU 名称、规格 JSON、成交价等快照。
- Jackson `ObjectMapper` 将 `spec_json` 转换为 `Map<String,String>`，接口层不直接暴露数据库 JSON 字符串。

## 阶段四：库存按 SKU 迁移

- 库存和预占记录增加 `sku_id`，旧请求仍可使用 `product_id`。
- 预占使用 MySQL 条件更新和 `version` 乐观锁：`available_stock >= quantity` 且版本一致才会成功。
- 释放和确认均按预占记录中的 SKU 分支处理，并通过状态更新实现幂等：`LOCKED -> RELEASED` 或 `LOCKED -> CONFIRMED`。
- `007-mall-model-upgrade.sql` 为历史商品生成默认 SKU，并把历史库存映射到默认 SKU。
- 旧 `product_id` 路径只在库存行 `sku_id IS NULL` 时启用；出现多个 SKU 库存后，按商品查询会明确要求使用 SKU，避免返回不确定库存。

## 阶段四验证边界

- `mvn ... -DskipTests package` 只验证编译和打包，不证明 Feign 序列化契约、
  SQL、事务回滚、服务发现和端到端业务正确。
- 2026-09-10：在 IntelliJ 所用 OpenJDK 25 上执行根项目
  `mvn -o -s .mvn-local-settings.xml verify -B -ntp`，15 个 reactor 项目构建成功，
  37 项测试通过，失败/错误/跳过均为 0。库存 14、秒杀 7、商品 5、订单 6、
  Trace ID 2、ID 1、JWT 2。用户模块目前没有测试，不能算作其行为已验证。
- Docker 读取被自动审批服务的 503 阻断。新版升级脚本未在 MySQL 8.4 实际执行，
  不应直接用于客户环境。需要先在隔离数据库验证首次升级、重复执行、旧数据映射和索引约束。

## 阶段五：OpenJDK 25 测试兼容与库存回归

### 问题与改动

1. 旧 Byte Buddy 1.14.19 无法处理 JDK 25 类文件，Mockito inline mock 在
   `StringRedisTemplate` 上失败。父 POM 覆盖 `byte-buddy.version=1.17.8`，
   由 Spring Boot dependency management 同时管理 core 与 agent 版本。
   Maven 运行 JDK 与源码目标版本不同：本地使用 JDK 25，源码/字节码目标暂保留 17。
   CI 增加 17/25 matrix；远程 CI 是否成功需在实际推送后查看。
2. `findInventory(productId)` 从 `selectOne` 改为
   `selectList(...last("LIMIT 2"))`，用最多两行识别商品是否有多规格库存。
   测试仍 stub 旧方法，导致返回“库存不存在”。修正模拟返回集合，
   并增加多规格请求明确报错的测试。没有降低断言或跳过测试。
3. `LambdaUpdateWrapper.set(ProductSkuEntity::getPrice, ...)` 依赖实体字段缓存。
   纯 Mockito 测试不启动 MyBatis，需通过 `TableInfoHelper.initTableInfo`、
   `MapperBuilderAssistant` 和 `MybatisConfiguration` 初始化测试元数据。
4. 移除商品创建中的 `if (skuMapper != null)`。必需依赖缺失时应暴露错误，
   不应为了测试通过而在生产逻辑跳过 SKU 创建。

### 库存方法与约束

| 方法/工具 | 用途 | 验证边界 |
| --- | --- | --- |
| `getBySku`、`findInventoryBySku` | 精确查询一个 SKU 的库存 | 单元测试覆盖，待数据库集成 |
| `validate`、`requireOwner` | 校验正数数量及已存库存行的商品归属 | 不等同于验证新建库存的 SKU 存在于商品服务 |
| `reserveSku` | `version` 条件更新并原子转移可用库存到锁定库存 | 单元测试验证路由；尚无 SKU 并发压测 |
| `findByOrderAndSku` | 同订单同 SKU 重试查重 | 相同键不同数量返回冲突，不静默成功 |
| `releaseSku`、`confirmSku` | 按预占快照恢复库存或扣减已售库存 | 同商品不同 SKU 独立处理测试通过 |
| `increaseSku` | 补库存并增长版本号 | 更新后重查同一 SKU，避免按商品误查多行 |
| `@Transactional` | 本地状态变更与库存更新共同提交/回滚 | Mock 测试不能证明真实事务回滚 |
| `ArgumentCaptor`、`verify(...never())` | 检查预占记录实际保存 SKU，且不会调用旧商品扣库 SQL | 行为测试，非性能指标 |

新增 8 项库存测试：精确 SKU 预占、商品归属错误、重试数量冲突、商品库存歧义、
补库存后重查相同 SKU、两个 SKU 独立释放、按 SKU 确认、负数量禁止写数据库。

### 数据库迁移修正

- MySQL 8.4 不支持此前写入的 `ADD COLUMN IF NOT EXISTS`。
  升级脚本改为查询 `information_schema.columns/statistics`，
  配合临时存储过程、`PREPARE/EXECUTE/DEALLOCATE PREPARE` 执行条件 DDL。
- 保留商品索引，移除商品唯一约束，改由 `UNIQUE(sku_id)` 和
  `UNIQUE(order_no, sku_id)` 约束 SKU 库存与预占。
- 使用生成列 `IF(sku_id IS NULL, product_id, NULL)` 为旧库存提供独立唯一约束。
  MySQL 唯一索引允许多个 NULL，因此 SKU 行不会被商品唯一性限制。
- 旧商品按默认 SKU code 判断是否已迁移；使用确定性 ID 回填，
  发生 ID 冲突则 `SIGNAL SQLSTATE '45000'` 明确终止，避免 `INSERT IGNORE` 隐藏缺失数据。
- 升级需停止业务写入并备份；DDL 可能隐式提交，不应宣称脚本全程事务原子。

## 后续验收清单

- [ ] MySQL 首次建库、旧库升级、重复升级与 SKU 并发扣库存集成测试。
- [x] 订单显式 SKU 请求；旧 `productId` 请求只解析为默认 SKU，同商品不同 SKU 分开合并和预占。
- [x] 购物车服务器侧选择、SKU 结算、幂等 requestKey、版本化异步清理和失败重试。
- [ ] SKU 管理和商品图片管理接口；目前支持已有多 SKU 查询与下单，新增商品仍只生成默认 SKU。
- [ ] 购物车实时商品价格/库存汇总、全选和数量更新并发集成验证；当前列表展示仍是精简 DTO。
- [ ] 地址编辑/默认切换并发约束；订单地址快照读取及必填校验。
- [ ] 支付、物流记录与状态历史：目前仅有表，尚未完成业务写入。
- [ ] 前端 DTO 金额/ID/时间格式统一，状态中文说明与 actions。
- [ ] 面向客户的用户/管理员权限隔离，内部接口保护，异常响应和限流联调。
- [ ] 独立前端项目与后端接口联调、浏览器端流程验收。

## 阶段六：SKU 下单与购物车结算

### 订单校价和规格边界

- `OrderApiModels.OrderItemRequest` 现在要求 `productId` 与 `skuId` 二选一，
  并限制每单最多 100 项。旧商品请求通过默认 SKU 解析；显式 SKU 直接调用
  `/internal/products/skus/{id}`。服务端校验 SKU 的 ID、商品归属、上架状态、
  非负金额和金额小数位，不使用前端传入的商品名、价格或小计。
- 先解析全部 SKU 并合并数量，再开始库存预占。相同 SKU 重复行只预占一次；
  同商品不同 SKU 使用独立库存和订单项。订单项复制当前商品名、主图、SKU 名、
  规格 JSON、成交价，后续商品修改不会改写历史订单。
- 地址在预占前通过用户服务核验 `userId` 归属并在订单服务保存快照。事务回滚
  后通过 `TransactionSynchronization` 补偿已经发出的远程预占；远程超时仍需要
  依赖库存预占的订单幂等键和后续人工/定时补偿，不能声称分布式事务已解决。

### 购物车结算

- 购物车增加 `version`，新增和数量合并使用数据库 `ON DUPLICATE KEY UPDATE`，
  数量/选中状态使用 `WHERE version = ?` 的条件更新。并发修改会返回冲突，避免
  覆盖用户的新值。
- `POST /api/orders/checkout` 使用用户和 UUID `requestKey` 唯一键。请求先锁定
  结算意图，再读取服务端选中项，构造 SKU 订单；重复请求返回原订单，换地址复用
  同键会拒绝。结算不删除购物车行，而是写入 `mall_cart_cleanup` 任务。
- `CartCleanupWorker` 调用用户服务按原购物车版本删除；用户在结算后编辑过的行不会
  被误删。远程失败或确认失败会按退避重试，任务处理设计为可重复执行。
- 新增 `database/init/008-cart-checkout.sql`，包括购物车版本列、结算意图唯一表和
  清理任务表。与 007 一样尚未在真实 MySQL 8.4 执行，必须先在隔离库验证 DDL、
  唯一键冲突和服务重启恢复。

### 阶段六验证

- JDK 25 下根项目 `mvn -o -s .mvn-local-settings.xml verify -B -ntp`：全部模块
  构建成功，新增订单/购物车/SKU 测试与原有测试合计 69 项通过，失败、
  错误、跳过均为 0（测试使用 Mockito/MVC，不等价于数据库或跨服务集成测试）。
- 新增 `scripts/cart-checkout-test.ps1`，需启动基础设施和全部服务后运行；当前
  环境上次 Docker 读取被自动审批服务的 503 阻断，本轮没有再次尝试，未执行该脚本。
- 本轮 69 项分布：订单 28、用户 7、商品 8、库存/秒杀 21、公共组件 5；相较上一轮
  37 项新增 32 项。包含真实 Jackson 序列化和 MVC 入参校验，但未启动完整服务或真实 Feign 网络调用。

### 结算方法与故障边界

| 方法 | 行为 | 验证边界 |
| --- | --- | --- |
| `resolveItems` | 统一 SKU 身份、合并数量、检查整单金额上限 | 单测覆盖多 SKU、混合引用、数量溢出和下架 |
| `loadAddress` | 预占前检查地址 ID 和用户归属 | 单测覆盖非本人地址和快照写入 |
| `afterCompletion` | 已知回滚时尝试按订单号释放库存 | 单测手动调用回调；不是数据库提交失败实测 |
| `CartCheckoutMapper.claim/findForUpdate` | 唯一键插入/更新后锁行，串行化同键请求 | SQL 尚未在 MySQL 并发验证 |
| `CartOrderService.checkout` | 服务端选中项生成订单，并写清理任务、记录原订单号 | 单测覆盖重试、空购物车、地址冲突和下单失败 |
| `CartItemMapper.updateVersioned/deletePurchased` | 修改增长版本，清理只接受原版本和原用户 | Mock 验证调用参数；实际 SQL 竞争条件待验证 |
| `CartCleanupWorker.processPending` | 成功后删任务，远程或确认失败延迟重试 | 单测覆盖成功顺序、超时和确认失败 |

- 当前一次结算代表首次成功请求读取到的选中项，重试不会重新读取变更后的购物车。
  同一意图需要前端持续使用同一个 UUID；不同 UUID 或旧直接下单接口不提供重复点击保护。
- 购物车版本更新使用 MySQL 原子语句，数量溢出依赖 MySQL 默认严格模式拒绝写入；
  上线前应确认 `STRICT_TRANS_TABLES`，并增加错误到业务响应的映射。
- 订单、结算意图和清理任务同属订单数据库事务；购物车清理是最终一致性的，
  清理前购物车可能暂时仍显示已购买行。使用旧版本清理不会减掉用户后续新增的数量。
- 进程崩溃、远程预占超时后延迟提交、释放失败和事务结果未知仍可能遗留库存锁定，
  需要持久化预占补偿/对账任务。这一轮的回滚回调不保证跨服务原子性。
- 部署前必须执行 007、008，更新商品/用户/库存/订单服务；为新商品补库存必须带 SKU。
  `smoke-test`、`payment-test`、`order-lifecycle-test`、`sales-ranking-test` 已调整补库存请求，
  保留旧 productId 下单请求用于兼容验证。脚本未做在线运行。

## 可用于简历的技术描述

- 基于 Spring Boot 3、Spring Cloud Alibaba、MyBatis-Plus 和 MySQL 构建订单、商品、用户、库存微服务。
- 使用 OpenFeign 进行服务间调用，使用 DTO 和快照模型隔离数据库存储模型与前端展示模型。
- 设计 MySQL 条件更新、版本号乐观锁和状态机以防止超卖，
  使用唯一键和状态迁移约束重复处理。尚不能写入未经压测的 QPS、TPS 或“零超卖”实测结论。
- 使用 RabbitMQ Outbox 事件链路衔接订单、库存和商品销量，使用 Trace ID 贯穿网关与 Servlet 服务。
- 使用 Redis Lua 脚本实现秒杀库存扣减和用户去重。
