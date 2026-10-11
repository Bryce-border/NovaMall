/* Refund screens use the same authenticated API and dialog helpers as the storefront. */
'use strict';
window.createNovaRefundUI = function ({ api, h, money, date, pathId, heading, empty, merchantTabs,
  showWorkspace, closeWorkspace, confirmAction, notify, go, render, isAdmin, getUsername }) {
  const states = { PENDING_REVIEW: '待商家审核', REJECTED: '审核未通过', WITHDRAWN: '已撤回',
    QUEUED: '等待退款', PROCESSING: '退款处理中', UNKNOWN: '退款结果待确认', FAILED: '退款失败', SUCCESS: '退款成功',
    WAITING_RETURN: '待买家寄回', WAITING_INSPECTION: '待商家验收', INSPECTION_REJECTED: '验收未通过' };
  let preview = null;
  let refreshTimer;
  let refreshDetail = false;
  const badge = state => `<span class="tag refund-state state-${h(state.toLowerCase())}">${h(states[state] || state)}</span>`;
  const storageKey = no => `nova.refund:${getUsername().toLowerCase()}:${no}`;
  function pending(no) { try { return JSON.parse(sessionStorage.getItem(storageKey(no))); } catch { return null; } }
  function detailLink(no, admin) { return admin ? `#admin?view=refunds&no=${pathId(no)}` : `#refund?no=${pathId(no)}`; }

  function orderPanel(order) {
    const r = order.refund || {};
    const label = r.fullyRefunded ? '全部商品已退款' : Number(r.refundedAmount) > 0 ? '部分商品已退款' : r.pendingCount ? '退款处理中' : '';
    return `<section class="panel refund-order-panel"><div class="section-top"><h2>退款 / 售后</h2>${label ? `<span class="tag">${label}</span>` : ''}</div>
      <div class="summary-line"><span>已退金额</span><strong>${money(r.refundedAmount || 0)}</strong></div>
      ${r.pendingCount ? `<p class="hint">${h(r.pendingCount)} 笔退款处理中，金额 ${money(r.pendingAmount)}。处理期间暂停发货或确认收货。</p>` : ''}
      ${order.items.some(i => i.refundedQuantity > 0) ? `<p class="hint">${order.items.filter(i => i.refundedQuantity > 0).map(i => `${h(i.productName)}：已退 ${h(i.refundedQuantity)} 件，保留 ${h(i.fulfillmentQuantity)} 件`).join('<br>')}</p>` : ''}
      <div class="button-row">${['PAID','SHIPPED','COMPLETED'].includes(order.status) && !r.fullyRefunded ? `<button class="button" data-action="refund-open" data-order="${h(order.orderNo)}">${order.status==='PAID'?'申请退款':'申请退货退款'}</button>` : ''}
      <a class="button" href="#refunds?orderNo=${pathId(order.orderNo)}">查看退款记录</a></div>
      ${['SHIPPED', 'COMPLETED'].includes(order.status) ? '<p class="hint">已发货商品需寄回并经商家验收。确认收货后 7 天内可申请退货。</p>' : ''}
      ${r.fullyRefunded ? '<p class="hint">原订单与支付记录保留，全部商品已退款，无需继续履约。</p>' : ''}</section>`;
  }

  async function list(params, admin = false) {
    refreshDetail = false;
    const query = new URLSearchParams();
    ['page', 'status', 'orderNo', 'refundNo', 'buyer', 'from', 'to'].forEach(k => { if (params.get(k)) query.set(k, params.get(k)); });
    const page = Math.max(1, Number(query.get('page')) || 1);
    query.set('page', page); query.set('size', 10);
    const data = await api(`/refunds${admin ? '/admin' : ''}?${query}`);
    const link = next => { const q = new URLSearchParams(query); q.set('page', next); return admin ? `#admin?view=refunds&${q}` : `#refunds?${q}`; };
    return `${admin ? merchantTabs('refunds') : ''}${heading(admin ? '退款工作台' : '我的退款', admin ? '核对原订单、审核退款，跟进每笔处理结果。' : '查看申请、审核与退款进度。')}
      <form id="refund-search-form" data-admin="${admin}" class="panel refund-filter">
      <label>退款状态<select name="status"><option value="">全部状态</option>${Object.entries(states).map(([k, v]) => `<option value="${k}" ${params.get('status') === k ? 'selected' : ''}>${v}</option>`).join('')}</select></label>
      <label>原订单号<input name="orderNo" value="${h(params.get('orderNo'))}" maxlength="32" placeholder="输入完整订单号"></label>
      <label>退款单号<input name="refundNo" value="${h(params.get('refundNo'))}" maxlength="32" placeholder="输入完整退款单号"></label>
      ${admin ? `<label>买家账号<input name="buyer" value="${h(params.get('buyer'))}" maxlength="32" placeholder="输入完整账号"></label>` : ''}
      <label>申请开始时间<input name="from" type="datetime-local" value="${h(params.get('from'))}"></label>
      <label>申请结束时间<input name="to" type="datetime-local" value="${h(params.get('to'))}"></label>
      <div class="button-row"><button class="button primary" type="submit">查询</button><a class="button" href="${admin ? '#admin?view=refunds' : '#refunds'}">重置</a></div></form>
      <div class="section-top"><p class="muted">共 ${h(data.total)} 笔退款 · 时间为北京时间</p><button class="text-button" data-action="refresh">刷新</button></div>
      <div class="stack">${data.items.map(r => `<article class="panel refund-list-card"><div class="section-top"><div><strong class="order-number">${h(r.refund_no)}</strong><p class="muted">${h(date(r.created_at))}${admin ? ` · 买家 ${h(r.user_name)}` : ''}</p></div>${badge(r.status)}</div><p>原订单 <span class="order-number">${h(r.order_no)}</span></p><p class="refund-reason">${h(r.reason)}</p><div class="order-bottom"><strong class="price">${money(r.amount)}</strong><a class="button small" href="${detailLink(r.refund_no, admin)}">${admin ? '查看 / 处理' : '查看进度'} →</a></div></article>`).join('') || empty('暂无退款记录', '可调整查询条件，或在未发货订单详情中申请退款。')}
      </div><div class="pager">${page > 1 ? `<a class="button" href="${link(page - 1)}">上一页</a>` : ''}<span>第 ${page} 页</span>${data.hasNext ? `<a class="button" href="${link(page + 1)}">下一页</a>` : ''}</div>`;
  }

  async function detail(params, admin = false) {
    const r = await api(`/refunds${admin ? '/admin' : ''}/${pathId(params.get('no'))}`);
    refreshDetail = ['QUEUED', 'PROCESSING', 'UNKNOWN'].includes(r.status);
    return `${admin ? merchantTabs('refunds') : ''}<a class="back" href="${admin ? '#admin?view=refunds' : '#refunds'}">← 返回退款列表</a>
      <div class="page-heading"><div class="detail-title"><h1>退款详情</h1>${badge(r.status)}</div><p class="order-number">${h(r.refund_no)} · ${h(date(r.created_at))}</p></div>
      <div class="two-col"><div class="stack"><section class="panel"><h2>退款商品</h2>${r.items.map(i => `<div class="item-row"><div class="item-main"><h3>${h(i.product_name)}</h3><p>${h(i.sku_name)} · 退款 ${h(i.quantity)} 件</p></div><strong>${money(i.amount)}</strong></div>`).join('')}<p class="hint">按下单时实付金额分摊，已扣除优惠券优惠。</p></section>
      <section class="panel"><h2>申请信息</h2><p>买家：${h(r.user_name)}</p><p class="refund-reason">原因：${h(r.reason)}</p><p>商家回复：${h(r.review_note || '等待商家审核')}</p></section>
      ${returnPanel(r,admin)}
      <section class="panel"><h2>退款进度</h2><ol class="timeline">${r.history.map(e => `<li><strong>${h(states[e.status] || e.status)}</strong><p>${h(date(e.created_at))} · ${{ USER: '买家', ADMIN: '商家', SYSTEM: '系统' }[e.actor_type] || h(e.actor_type)}</p><p>${h(e.note)}</p></li>`).join('')}</ol></section>
      ${admin && r.execution?.length ? `<section class="panel"><h2>执行记录</h2><p class="hint">最近 50 次执行；同一退款编号可重复查单，成功只入账一次。</p>${r.execution.map(e => `<div class="record"><strong>${h(({ STARTED: '执行中', LEASE_EXPIRED: '中断后已接管', ...states })[e.outcome] || e.outcome)}</strong><p>${h(date(e.started_at))}</p><p>${h(e.note)}</p></div>`).join('')}</section>` : ''}</div>
      <aside class="stack"><section class="panel summary"><h2>申请退款金额</h2><div class="payment-amount">${money(r.amount)}</div><p>退款方式：模拟原支付渠道退回</p><p class="order-number">原支付单 ${h(r.payment_no)}</p>${r.channel_refund_no ? `<p class="order-number">退款凭证 ${h(r.channel_refund_no)}</p>` : ''}
      ${refreshDetail ? '<div class="warning">系统正在处理，将自动更新进度。结果待确认期间无需重复申请。</div>' : ''}
      ${r.status === 'FAILED' ? '<div class="warning">渠道已确认失败，退款数量和金额继续保留，等待商家重试。</div>' : ''}
      <div class="stack">${!admin && ['PENDING_REVIEW','WAITING_RETURN'].includes(r.status) ? `<button class="button" data-action="refund-withdraw" data-no="${h(r.refund_no)}">撤回申请</button>` : ''}
      ${admin && r.status === 'PENDING_REVIEW' ? `<button class="button primary" data-action="refund-review-open" data-no="${h(r.refund_no)}" data-approve="true">同意退款</button><button class="button" data-action="refund-review-open" data-no="${h(r.refund_no)}" data-approve="false">拒绝并说明原因</button>` : ''}
      ${admin && r.status === 'FAILED' ? `<button class="button primary" data-action="refund-retry" data-no="${h(r.refund_no)}">重试原退款</button>` : ''}
      ${admin ? `<button class="button" data-action="refund-original" data-no="${h(r.refund_no)}">查询原订单</button>` : `<a class="button" href="#order?no=${pathId(r.order_no)}">查询原订单</a>`}
      <button class="text-button" data-action="refresh">刷新进度</button></div><p class="hint">本次为模拟退款，不产生真实资金转移。</p></section>
      <section class="panel"><h2>原订单</h2><p class="order-number">${h(r.order_no)}</p><p class="hint">退款单与原订单独立保存。部分退款不退券，全部商品退款后按原有效期返还优惠券。</p></section></aside></div>`;
  }

  async function open(no) {
    preview = null;
    const prior = pending(no);
    if (prior) {
      showWorkspace(`<h2 id="workspace-title">确认上次退款申请</h2><p>上次提交结果尚未确认。可以重试原请求，系统会返回同一退款单。</p><p>金额 ${money(prior.expectedAmount)} · ${h(prior.reason)}</p><form id="refund-apply-form" data-order="${h(no)}"><button class="button primary full" type="submit">查询 / 重试原申请</button></form><a class="button full" href="#refunds?orderNo=${pathId(no)}">查看此订单退款记录</a>`);
      return;
    }
    const data = await api(`/refunds/orders/${pathId(no)}/eligibility`);
    if (data.blockedReason) throw new Error(data.blockedReason);
    const available = data.items.filter(i => Number(i.remaining_quantity) > 0);
    if (!available.length) throw new Error('商品已退款或已有申请正在处理，请查看退款记录。');
    showWorkspace(`<h2 id="workspace-title">申请退款</h2><p class="order-number">原订单 ${h(no)}</p><form id="refund-quote-form" data-order="${h(no)}">
      <div class="stack">${available.map(i => `<label class="refund-line"><span><strong>${h(i.product_name)}</strong><small>${h(i.sku_name)} · 可退 ${h(i.remaining_quantity)} 件 · 剩余可退 ${money(i.remaining_amount)}</small></span><input aria-label="${h(i.product_name)}退款数量" name="item_${h(i.order_item_id)}" type="number" min="0" max="${h(i.remaining_quantity)}" value="0" step="1" required></label>`).join('')}</div>
      <label>退款原因<textarea name="reason" required maxlength="500" rows="3" placeholder="例如：买错了，暂时不需要"></textarea></label>
      <p class="hint">填写退款数量，0 表示不退款。${data.mode==='RETURN_REFUND'?'本单已发货，需商家同意后寄回商品，验收通过再退款。':'本单未发货，申请后暂停发货。'}寄回前可撤回。</p><button class="button primary full" type="submit">计算退款金额</button></form>`);
  }

  async function submit(form, values) {
    if (form.id === 'return-logistics-form') {
      await api(`/refunds/${pathId(form.dataset.no)}/return-logistics`,'POST',{carrier:values.carrier.trim(),trackingNo:values.trackingNo.trim(),version:Number(form.dataset.version)});
      closeWorkspace();await render();notify('退货物流已保存，等待商家验收。');return true;
    }
    if (form.id === 'return-inspect-form') {
      const accepted=values.result==='accept';
      if(accepted && values.received!=='on')throw new Error('请先确认已收到本申请中的全部商品。');
      const restockItems=accepted?Object.entries(values).filter(([k])=>k.startsWith('restock_')).map(([k,v])=>({orderItemId:k.slice(8),quantity:Number(v)})):[];
      await api(`/refunds/admin/${pathId(form.dataset.no)}/inspect`,'POST',{accepted,note:values.note.trim(),version:Number(form.dataset.version),restockItems});
      closeWorkspace();await render();notify(accepted?'验收通过，退款已进入处理队列。':'验收结果已保存，买家可申请复核。');return true;
    }
    if (form.id === 'return-recheck-form') {
      await api(`/refunds/${pathId(form.dataset.no)}/recheck`,'POST',{note:values.note.trim(),version:Number(form.dataset.version)});
      closeWorkspace();await render();notify('复核申请已提交。');return true;
    }
    if (form.id === 'refund-search-form') {
      const q = new URLSearchParams();
      Object.entries(values).forEach(([k, v]) => { if (v.trim()) q.set(k, v.trim()); });
      await go(form.dataset.admin === 'true' ? `admin?view=refunds&${q}` : `refunds?${q}`); return true;
    }
    if (form.id === 'refund-quote-form') {
      const items = Object.entries(values).filter(([k, v]) => k.startsWith('item_') && Number(v) > 0)
        .map(([k, v]) => ({ orderItemId: k.slice(5), quantity: Number(v) }));
      if (!items.length || !values.reason.trim()) throw new Error('请填写退款原因，并选择至少一件商品。');
      const no = form.dataset.order;
      const quote = await api(`/refunds/orders/${pathId(no)}/quote`, 'POST', { items });
      preview = { orderNo: no, owner: getUsername(), body: { requestKey: crypto.randomUUID(), items, reason: values.reason.trim(), expectedAmount: quote.amount, expectedMode:quote.mode } };
      showWorkspace(`<h2 id="workspace-title">确认退款申请</h2>${quote.items.map(i => `<div class="summary-line"><span>${h(i.productName)} × ${h(i.quantity)}</span><strong>${money(i.amount)}</strong></div>`).join('')}
        <div class="summary-line summary-total"><span>退款合计</span><strong>${money(quote.amount)}</strong></div><p class="refund-reason">退款原因：${h(values.reason.trim())}</p><p class="hint">${quote.mode==='RETURN_REFUND'?'退货退款：需寄回商品并经商家验收。':'未发货退款：无需寄回商品。'}金额已扣除优惠分摊，退款走模拟渠道。</p>
        <form id="refund-apply-form" data-order="${h(no)}"><button class="button primary full" type="submit">提交退款申请</button></form>`);
      return true;
    }
    if (form.id === 'refund-apply-form') {
      const no = form.dataset.order;
      const body = pending(no) || (preview?.orderNo === no && preview.owner === getUsername() ? preview.body : null);
      if (!body) throw new Error('申请信息已失效，请重新选择商品。');
      const key = storageKey(no);
      sessionStorage.setItem(key, JSON.stringify(body));
      try {
        const result = await api(`/refunds/orders/${pathId(no)}`, 'POST', body);
        sessionStorage.removeItem(key); preview = null; closeWorkspace(); await go(`refund?no=${pathId(result.refund_no)}`); notify('退款申请已提交，等待商家审核。');
      } catch (e) {
        if ([400, 409, 422].includes(e.status)) { sessionStorage.removeItem(key); preview = null; closeWorkspace(); }
        throw e;
      }
      return true;
    }
    if (form.id === 'refund-review-form') {
      if (!values.note.trim()) throw new Error('请填写审核说明。');
      await api(`/refunds/admin/${pathId(form.dataset.no)}/review`, 'POST', { approve: form.dataset.approve === 'true', note: values.note.trim(),receiverName:values.receiverName,receiverPhone:values.receiverPhone,returnAddress:values.returnAddress });
      closeWorkspace(); await render(); notify('审核结果已保存。'); return true;
    }
    return false;
  }

  async function action(action, button) {
    if(['return-logistics-open','return-inspect-open','return-recheck-open'].includes(action)) {
      const admin=action==='return-inspect-open';
      const r=await api(`/refunds${admin?'/admin':''}/${pathId(button.dataset.no)}`);
      const info=r.returnInfo;
      if(!info)throw new Error('此申请无需退货。');
      if(action==='return-logistics-open') {
        showWorkspace(`<h2 id="workspace-title">填写退货物流</h2><p>${h(info.receiver_name)} · ${h(info.receiver_phone)}<br>${h(info.return_address)}</p><form id="return-logistics-form" data-no="${h(r.refund_no)}" data-version="${h(info.version)}"><label>物流公司<input name="carrier" required maxlength="64" value="${h(info.carrier)}" placeholder="例如：顺丰速运"></label><label>退货单号<input name="trackingNo" required pattern="[A-Za-z0-9-]{6,64}" maxlength="64" value="${h(info.tracking_no)}" placeholder="6–64 位字母、数字或连字符"></label><p class="hint">这是模拟物流登记，不会实际下单叫件。商家验收前可以更正单号。</p><button class="button primary full" type="submit">保存物流</button></form>`);
      } else if(action==='return-inspect-open') {
        showWorkspace(`<h2 id="workspace-title">退货验收</h2><p>${h(info.carrier)} · ${h(info.tracking_no)}</p><form id="return-inspect-form" data-no="${h(r.refund_no)}" data-version="${h(info.version)}"><label>验收结论<select name="result"><option value="accept">通过，按申请金额退款</option><option value="reject">不通过，请买家核实</option></select></label><label><input type="checkbox" name="received"> 我已核对并收到本申请中的全部商品</label><p class="hint">通过时逐项填写可补库数量。损坏、不可再售商品填 0；不通过时不会退款或补库。</p>${r.items.map(i=>`<label class="refund-line"><span>${h(i.product_name)}<small>申请退货 ${h(i.quantity)} 件</small></span><input name="restock_${h(i.order_item_id)}" aria-label="${h(i.product_name)}补库数量" type="number" min="0" max="${h(i.quantity)}" step="1" value="0" required></label>`).join('')}<label>验收说明<textarea name="note" required maxlength="500" rows="3" placeholder="记录商品情况、数量及不补库原因"></textarea></label><button class="button primary full" type="submit">提交验收结果</button></form>`);
      } else {
        showWorkspace(`<h2 id="workspace-title">申请复核</h2><p>上次验收说明：${h(info.inspection_note)}</p><form id="return-recheck-form" data-no="${h(r.refund_no)}" data-version="${h(info.version)}"><label>补充说明<textarea name="note" required maxlength="500" rows="3"></textarea></label><button class="button primary full" type="submit">提交复核申请</button></form>`);
      }return true;
    }
    if (action === 'refund-open') { await open(button.dataset.order); return true; }
    if (action === 'refund-withdraw') {
      if (await confirmAction('撤回退款申请', '请确认尚未寄回商品。撤回后释放本次申请占用的数量与金额。')) {
        await api(`/refunds/${pathId(button.dataset.no)}/withdraw`, 'POST'); await render();
      } return true;
    }
    if (action === 'refund-review-open') {
      if (!isAdmin()) throw new Error('请使用商家账号。');
      const approve = button.dataset.approve === 'true';
      const r=await api(`/refunds/admin/${pathId(button.dataset.no)}`);
      const returns=approve&&r.mode==='RETURN_REFUND';
      showWorkspace(`<h2 id="workspace-title">${approve ? '同意申请' : '拒绝退款'}</h2><p>${approve ? (returns?'请填写退货地址，待买家寄回并验收通过后再退款。':'审核通过后系统执行模拟退款并返还对应库存。') : '请说明拒绝原因，拒绝后释放申请占用的数量。'}</p><form id="refund-review-form" data-no="${h(button.dataset.no)}" data-approve="${approve}">${returns?'<label>退货收件人<input name="receiverName" required maxlength="64"></label><label>联系电话<input name="receiverPhone" required maxlength="32"></label><label>详细退货地址<textarea name="returnAddress" required maxlength="255" rows="2"></textarea></label>':''}<label>审核说明<textarea name="note" rows="3" required maxlength="500">${approve ? '核对原订单无误，同意申请。' : ''}</textarea></label><button class="button primary full" type="submit">确认${approve ? '同意' : '拒绝'}</button></form>`);
      return true;
    }
    if (action === 'refund-retry') {
      if (await confirmAction('重试原退款', '使用原退款编号重新查单和执行，金额与商品保持原申请内容。')) {
        await api(`/refunds/admin/${pathId(button.dataset.no)}/retry`, 'POST'); await render();
      } return true;
    }
    if (action === 'refund-original') {
      const o = await api(`/refunds/admin/${pathId(button.dataset.no)}/order`);
      showWorkspace(`<h2 id="workspace-title">原订单查询</h2><p class="order-number">${h(o.orderNo)}</p><p>订单状态：${h(({ PENDING_PAYMENT: '待付款', PAID: '已付款', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消' })[o.status] || o.status)}${o.refund?.fullyRefunded ? ' · 全部商品已退款' : ''}</p>
        ${o.items.map(i => `<div class="item-row"><div class="item-main"><h3>${h(i.productName)}</h3><p>${h(i.skuName)} · 原购 ${h(i.quantity)} 件 · 已退 ${h(i.refundedQuantity)} 件</p></div><strong>${money(i.amount)}</strong></div>`).join('')}
        <div class="summary-line"><span>商品总额 / 优惠</span><span>${money(o.productAmount)} / ${money(o.discountAmount)}</span></div><div class="summary-line"><span>原订单实付</span><strong>${money(o.totalAmount)}</strong></div>
        <div class="summary-line"><span>已退款 / 处理中</span><span>${money(o.refund?.refundedAmount || 0)} / ${money(o.refund?.pendingAmount || 0)}</span></div>
        <h3>支付记录</h3>${o.payments.map(p => `<p class="order-number">${h(p.paymentNo)}<br>${money(p.amount)} · ${h(date(p.paidAt))}</p>`).join('') || '<p>无支付记录</p>'}
        <h3>收货信息</h3>${o.address ? `<p>${h(o.address.receiverName)} · ${h(o.address.receiverPhone)}</p><p>${h(o.address.province + o.address.city + o.address.district + o.address.detailAddress)}</p>` : '<p>原订单未保存地址</p>'}
        <p class="hint">以上为下单时保存的数据，不受商品价格变化或下架影响。</p>`);
      return true;
    }
    return false;
  }

  function afterRender(name, params) {
    clearTimeout(refreshTimer);
    if (refreshDetail && (name === 'refund' || name === 'admin' && params.get('view') === 'refunds' && params.has('no'))) {
      const hash = location.hash;
      const poll = () => {
        if (location.hash !== hash) return;
        if (document.querySelector('dialog[open]') || document.hidden) { refreshTimer = setTimeout(poll, 5000); return; }
        render();
      };
      refreshTimer = setTimeout(poll, 5000);
    }
  }
  function returnPanel(r,admin) {
    const info=r.returnInfo;if(!info)return '';
    return `<section class="panel"><h2>退货物流与验收</h2>${info.return_address?`<h3>寄回地址</h3><p>${h(info.receiver_name)} · ${h(info.receiver_phone)}</p><p>${h(info.return_address)}</p>`:'<p class="muted">商家审核通过后提供退货地址，请勿提前寄回。</p>'}
      ${info.tracking_no?`<div class="record"><strong>${h(info.carrier)} · ${h(info.tracking_no)}</strong><p>登记时间 ${h(date(info.shipped_at))}</p></div>`:''}
      ${info.inspection_result?`<p>最近验收：${info.inspection_result==='ACCEPTED'?'通过':'未通过'} · ${h(date(info.inspected_at))}</p><p class="refund-reason">${h(info.inspection_note)}</p>`:''}
      ${r.restockItems?.length?`<p class="hint">验收补库：${r.restockItems.map(i=>`${h(r.items.find(item=>String(item.order_item_id)===String(i.order_item_id))?.product_name)} ${h(i.restock_quantity)} 件`).join('；')}。退款成功后异步入库。</p>`:''}
      <div class="button-row">${!admin&&['WAITING_RETURN','WAITING_INSPECTION'].includes(r.status)?`<button class="button primary" data-action="return-logistics-open" data-no="${h(r.refund_no)}">${info.tracking_no?'更正退货物流':'填写退货物流'}</button>`:''}
      ${admin&&r.status==='WAITING_INSPECTION'?`<button class="button primary" data-action="return-inspect-open" data-no="${h(r.refund_no)}">验收退货</button>`:''}
      ${!admin&&r.status==='INSPECTION_REJECTED'?`<button class="button" data-action="return-recheck-open" data-no="${h(r.refund_no)}">申请复核</button>`:''}</div>
      <p class="hint">物流为手工登记，未接入快递实时轨迹。寄回后不自动撤销申请；验收不通过可补充说明申请复核。</p></section>`;
  }
  return { orderPanel, list, detail, submit, action, afterRender };
};
