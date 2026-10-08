# 订单详情与模拟交易记录

接口：`GET /api/orders/{orderNo}/detail`，通过网关 8080 携带买家 JWT。只能查询当前用户自己的订单，不允许通过请求参数指定其他用户。原有查询和下单响应保持兼容，新页面和客服查询可使用此接口。

## 字段约定

响应使用现有 `ApiResponse` 包装，以下字段位于 `data`：

| 字段 | 含义 |
| --- | --- |
| `id` / `orderNo` / `status` | 订单标识和状态，ID 使用字符串 |
| `currency` | 当前为 `CNY` |
| `productAmount` / `discountAmount` / `freightAmount` / `totalAmount` | 商品总额、优惠、运费和应付总额，均为两位小数字符串，如 `"199.80"` |
| `address` | 下单时收货地址快照；旧的无地址订单返回 `null` |
| `items` | 下单时商品名称、图片、SKU 名称、`specs`、单价、数量和小计；不读取当前商品信息 |
| `payments` | 支付单号、渠道、金额、状态、支付及创建时间 |
| `shipments` | 物流公司、单号、状态、发货、送达及创建时间 |
| `history` | 按发生时间和记录 ID 排序的状态历史，包含前后状态、事件、操作者类型与 ID、说明 |
| `actions` | 当前可用操作，包含 `code`、`label` 和 `simulated` |
| `paymentDeadline` | 创建时间加 30 分钟；订单完成后仍保留原支付期限 |
| `createdAt` / `updatedAt` / `paidAt` / `shippedAt` / `completedAt` / `cancelledAt` | 数据库中的订单时间，未发生的状态时间为 `null` |
| `remark` / `cancelReason` | 备注和取消原因；用户取消为 `USER_CANCELLED`，超时取消为 `PAYMENT_TIMEOUT` |

时间沿用项目的无时区 `LocalDateTime`，客户端应按同一本地演示环境的时间解释，不应自行当成 UTC。后续统一时间协议时再迁移。

详情读取使用一个只读、可重复读事务，订单状态与关联记录来自同一数据库快照。`actions` 是读取当时的提示，点击操作时服务端仍会重新检查状态、用户归属及支付期限。

## 操作映射

| 订单状态 | action code | 对应 POST 接口 | 条件 |
| --- | --- | --- | --- |
| `PENDING_PAYMENT` | `CANCEL` | `/api/orders/{orderNo}/cancel` | 待支付订单可取消 |
| `PENDING_PAYMENT` | `MOCK_PAY` | `/api/orders/{orderNo}/pay` | Mock 支付开启且未超过 30 分钟 |
| `PAID` | `MOCK_SHIP` | `/api/orders/{orderNo}/ship` | Mock 履约开启 |
| `SHIPPED` | `MOCK_COMPLETE` | `/api/orders/{orderNo}/complete` | Mock 履约开启 |
| `COMPLETED` / `CANCELLED` | 无 | 无 | `actions: []` |

关闭 Mock 开关后，相应操作会从 `actions` 消失，已保存的模拟记录仍可查询。Mock 发货和收货目前由买家对自己的订单触发，用于演示。

## 持久化与旧数据

启动新版订单服务前执行 `database/init/009-order-history.sql`，并确认 007 中的支付、物流表已存在。新数据的写入与订单状态更新、Outbox 写入共用本地事务：

- 创建：写入 `ORDER_CREATED`，初始状态为 `PENDING_PAYMENT`。
- 支付：生成 `MOCK-PAY-{orderNo}` 支付单，渠道为 `MOCK`、状态为 `SUCCESS`，写入 `ORDER_PAID`。
- 发货：生成 `MOCK-SHIP-{orderNo}` 物流单，公司为 `MOCK`、状态为 `SHIPPED`，写入 `ORDER_SHIPPED`。
- 收货：将已有模拟物流单更新为 `DELIVERED`，写入 `ORDER_COMPLETED`。
- 取消：写入 `ORDER_CANCELLED`；用户取消的 `actorType=USER`，超时取消为 `SYSTEM` 且 `actorId=null`。

这些模拟单号不对应真实支付平台或快递公司，响应中 `simulated=true`。支付和发货仍会拒绝重复操作；已取消订单重复取消不会重复写历史。当前流程只向前推进，历史表用 `(order_id, to_status)` 防止重复写入；未来加入可反复进入同一状态的售后流程时需调整此约束。

旧数据只返回已有记录：缺失的支付、物流和历史返回空数组，已发货旧订单确认收货时不会凭空生成物流单。旧订单后续操作可生成新的历史，因此历史不一定从创建事件开始。

## 客服 Agent 使用边界

第一版可读取此接口回答金额、订单状态和模拟物流问题，明确说明模拟记录。空数组表示没有已保存记录，不能据此推断未支付或未发货，应结合订单状态回答。不要让 Agent 直接查数据库或使用管理员账号；涉及地址和电话时只返回用户问题所需的信息。未来提供订单操作工具时，需先向用户展示具体订单和操作影响，再执行用户确认的操作。

目前完成了代码与演示脚本接入，尚未执行真实 MySQL 迁移或在线购买联调。
