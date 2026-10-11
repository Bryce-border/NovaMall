# 退款、退货物流与验收

当前支持未发货退款，以及已发货/已收货商品的退货退款。资金与物流仍用于个人项目模拟演示：登记快递单号不会真实叫件，也不会获取实时轨迹。

## 使用流程

买家在订单详情点击「申请退款」或「申请退货退款」，选择原订单商品和数量、填写原因，确认服务端报价后提交。导航栏「退款 / 售后」查询进度，商家工作台「退款管理」处理申请。

- 未发货：买家申请 → 商家审核 → 自动模拟退款 → 按退款数量返还库存。
- 已发货：买家申请 → 商家同意并填写退货收件人/电话/地址 → 买家登记物流 → 商家验收 → 自动模拟退款。
- 已确认收货：收货后 7 天内允许新申请，后续流程同退货退款。已经受理的申请不会因超过 7 天而中断。
- 商家验收须确认本申请商品全部收齐，并逐项填写可补库数量。损坏或不可再售商品填写 0；可补库数量不能超过退货数量。补库在退款成功后异步执行。
- 验收不通过必须填写原因，保留申请额度和原物流，买家可补充说明申请复核。寄回后不允许直接撤回，以免商品在商家手中时丢失售后记录。
- 买家在待审核或待寄回阶段可以撤回。物流登记与撤回使用同一订单锁；登记成功后不能撤回。商家验收前可更正物流，旧版本的验收页面必须重新加载。

不自动处理券包退款、含运费订单或没有可核对成功支付流水的历史订单。一个申请按原申请的全部数量作出验收结论；少件等争议先拒绝并复核，不在验收时直接更改退款金额。

## 退款状态

| 状态 | 含义与后续操作 |
| --- | --- |
| PENDING_REVIEW | 待商家审核，买家可撤回 |
| REJECTED / WITHDRAWN | 审核拒绝 / 撤回，释放金额与数量，可重新申请 |
| WAITING_RETURN | 已同意退货并提供地址，等待买家登记物流 |
| WAITING_INSPECTION | 物流已登记，等待验收；更正物流使版本递增 |
| INSPECTION_REJECTED | 验收未通过，买家可申请复核，额度继续保留 |
| QUEUED / PROCESSING | 待退款 / 退款执行中 |
| UNKNOWN | 渠道结果待确认，系统查原退款编号，禁止人工当作失败重付 |
| FAILED | 渠道明确失败，商家可重试原退款单，额度继续保留 |
| SUCCESS | 退款成功，已退数量与金额永久记账 |

处理中暂停未发货订单的发货，以及已发货订单的确认收货。部分成功退款后可继续履约剩余商品；全部商品退款后不能发货或确认收货。

原订单状态、商品价格、地址和支付流水不改写。订单列表和详情额外显示退款汇总，全部退完显示「已全额退款」。商家退款详情中的「查询原订单」读取快照，不依赖当前商品价格或是否已下架。

## 金额、优惠券和销量

金额使用 DECIMAL / BigDecimal，精度到分。首次查询退款资格时，在原订单锁内保存行实付分摊。按原订单明细 ID 排序，使用累计金额比例向下取整，保证各行合计等于原商品实付金额。兼容旧订单，但金额快照不一致时停止自动处理。

部分数量按行实付除以原数量并向下取整；最后剩余数量取得剩余可退金额。成功和未终结申请共同占用数量/金额。零元商品也按数量处理，并保留退款与库存记录。前端提交 expectedAmount 和 expectedMode；报价后发生发货或金额变化，必须重新确认。

例如 3 件每件 10 元，优惠 1 元，实付 29 元：先退 1 件 9.66 元，剩余 2 件合计 19.34 元。部分退款不返券；全退返还原券且不延长有效期，过期券仍不可用。

销量采用 MySQL 持久化的「各已完成订单保留商品数量」。收货和收货后退款会在订单事务中生成带递增版本的完整销量快照。商品服务按订单版本接收快照并更新商品合计；旧消息、重复消息不能覆盖新结果，也不会重复加减销量。排行榜和已售量均从这一统计读取。旧 Redis 销量键不再作为查询依据。

数据库迁移 012 从已完成订单和成功退款回填初始销量。保留旧 ORDER_COMPLETED 消费者用于接收历史通知；新的销量更新使用 ORDER_SALES_UPDATED。部署升级时应暂停订单和商品写入，避免旧版本程序在回填期间继续产生交易。

## 并发与恢复设计

1. 所有退款写操作先锁原订单，再锁退款单。发货、收货使用同一订单锁，不同订单可并行处理。
2. 退款写事务使用 READ COMMITTED，取得锁后读取最新已占数量。申请唯一键为 `(user_id, order_no, request_key)`；同键同内容返回原单，改变内容被拒绝。前端在请求发送前保存原请求，网络异常后重试原键。
3. 审核、撤回、物流、验收、复核均在锁内流转。物流和验收携带版本号，重复验收指纹返回原结果，互相冲突的验收结论不能覆盖已生效结果。
4. 退款任务持久化在数据库中，租约 60 秒，执行令牌隔离旧任务。任务中断后租约到期可重新领取，先查原渠道编号再决定是否提交同一退款号。
5. 渠道调用在订单事务外进行。完成时核对退款号、支付号、金额，核对不符保留 UNKNOWN。模拟渠道回执独立事务落库，订单事务失败仍能查回。
6. 成功状态、返券、库存事件和销量快照在同一订单事务中提交。库存按退款号与原预占记录去重，累计返还不能超过预占数量。退货按照验收数量补库，未发货退款按申请数量补库。
7. 退款事件早于付款事件时，库存先幂等确认原预占，再补回对应数量；后到付款事件不再扣库存。销量使用版本快照处理任意先后顺序。
8. 发货通知 ORDER_SHIPPED 为目前没有订阅者的信息通知，不要求路由；原物流和历史已在数据库保存。库存、销量等有业务效果的事件仍要求消息成功路由，否则继续重试。

列表在数据库分页，每页最多 50 条，按创建时间和退款号稳定排序。精确筛选支持原订单、退款单号、买家、状态、日期；执行日志不随列表批量返回。管理员接口同时受网关和订单服务角色校验；买家仅能访问自己的订单、退款与物流。

当前仍使用单实例启动方式。任务租约支持重复调度，但项目现有固定 Snowflake worker ID 不适合直接增加副本；多实例前需独立分配 worker ID。

## 数据库升级与运行

项目已有 001–010 时，停止订单/商品写入后执行：

```powershell
node scripts/migrate-refunds.mjs --apply --returns
mvn -s .mvn-local-settings.xml -pl mall-order,mall-product,mall-inventory,mall-gateway -am -DskipTests package
```

脚本仅操作本机 novamall-mysql 容器的 mall 数据库，先将全库备份到 `logs/pre-refunds-*.sql`，再应用 011 和 012。已有卷不会在 Docker 重启时自动升级。重启订单、商品、库存和网关服务；静态资源版本为 `20261011-returns-1`。

| 表 | 用途 |
| --- | --- |
| mall_refund_allocation | 原订单行实付分摊快照 |
| mall_refund / mall_refund_item | 申请、审核、原支付号、退款数量/金额、请求去重和执行租约 |
| mall_refund_history / mall_refund_attempt | 操作时间线和执行/查单结果 |
| mall_mock_refund_receipt | 独立持久化的模拟退款回执 |
| mall_inventory_refund | 库存返还去重与数量台账 |
| mall_refund_return | 原发货状态、退货地址、物流、验收结果和版本 |
| mall_refund_return_item | 逐项验收可补库数量 |
| mall_order_sales_version | 订单服务的销量快照源版本 |
| mall_product_sales_order / line / total | 商品服务消费版本、逐单贡献和商品净销量 |

## 接口

| 请求 | 功能 |
| --- | --- |
| GET /api/refunds/orders/{orderNo}/eligibility | 可退数量、受理条件及退款方式 |
| POST /api/refunds/orders/{orderNo}/quote | 服务端报价和退款方式 |
| POST /api/refunds/orders/{orderNo} | 幂等申请，提交 requestKey/reason/expectedAmount/items/expectedMode |
| GET /api/refunds | 个人退款分页 |
| GET /api/refunds/{refundNo} | 退款、退货、验收和操作历史 |
| POST /api/refunds/{refundNo}/withdraw | 寄回前撤回 |
| POST /api/refunds/{refundNo}/return-logistics | carrier/trackingNo/version，登记或更正物流 |
| POST /api/refunds/{refundNo}/recheck | note/version，申请验收复核 |
| GET /api/refunds/admin | 商家退款分页 |
| GET /api/refunds/admin/{refundNo} | 商家详情及执行记录 |
| GET /api/refunds/admin/{refundNo}/order | 原订单查询 |
| POST /api/refunds/admin/{refundNo}/review | 审核；退货同意时须提供 receiverName/receiverPhone/returnAddress |
| POST /api/refunds/admin/{refundNo}/inspect | accepted/note/version/restockItems；通过时逐项确认补库数量 |
| POST /api/refunds/admin/{refundNo}/retry | 明确渠道失败后的重试 |

分页参数：page、size、status、orderNo、refundNo、buyer（仅商家）、from、to。时间为北京时间，开始包含、结束不包含。金额和长 ID 返回字符串。

## 验证与复现

本次执行结果见 [退款与退货验收结果](REFUND_TEST_RESULTS.md)：69 项单元测试、20 组联调检查和 6 组 DOM 交互检查通过。

```powershell
mvn -s .mvn-local-settings.xml -pl mall-order,mall-inventory,mall-product,mall-gateway -am test
node scripts/refund-check.mjs
node scripts/refund-dom-check.cjs
```

DOM 脚本依赖本地 `logs/ui-tools/node_modules/jsdom`，缺少时执行 `npm install --prefix logs/ui-tools jsdom@26`。DOM 测试执行真实页面脚本、模拟 API，不代表实际浏览器视觉验收。

接口脚本会创建 12 个隔离买家、独立测试商品/库存/优惠券和订单，结束后软删除测试商品并暂停测试活动。退款及订单审计记录保留供回查。测试不消耗现有商品库存。支持本机 NOVAMALL_GATEWAY、NOVAMALL_ADMIN_USERNAME、NOVAMALL_ADMIN_PASSWORD，以及 RabbitMQ 连接账号环境变量。

多人退款走真实网关；同一订单争抢、并发商家操作使用本机订单服务，避免网关速率限制掩盖数据库竞争。脚本会对本次创建的数据注入金额不符的渠道回执和过期租约，验证结果待确认与恢复；这属于故障模拟，不是银行退款或实际杀进程测试。

结果文件：`logs/refund-integration-result.json`、`logs/refund-dom-result.json`，单元测试日志为 `logs/refund-unit-tests.log`。本地有限并发验证不代表生产压测或真实支付验收。

库存及销量消费失败沿用重试与死信队列：`mall.inventory.refund-succeeded.dlq`、`mall.product.sales-updated.dlq`。需修复原因后重放原消息，不能用手工加库或加减销量替代；本期未提供死信重放界面。真实支付接入仍需渠道签名与回调验签、超时与对账策略，真实快递轨迹需单独对接。
