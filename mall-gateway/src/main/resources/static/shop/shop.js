/* NovaMall's browser client uses only same-origin public APIs. */
(() => {
  'use strict';
  const $ = selector => document.querySelector(selector);
  const app = $('#app');
  const statusNames = { PENDING_PAYMENT: '待付款', PAID: '待发货', SHIPPED: '待收货', COMPLETED: '已完成', CANCELLED: '已取消', SUCCESS: '支付成功', DELIVERED: '已送达' };
  const eventNames = { ORDER_CREATED: '订单已创建', ORDER_PAID: '模拟支付成功', ORDER_SHIPPED: '模拟商品已发货', ORDER_COMPLETED: '已确认收货', ORDER_CANCELLED: '订单已取消' };
  let token = sessionStorage.getItem('nova.token') || '';
  let username = sessionStorage.getItem('nova.username') || '';
  let epoch = 0;
  let busy = false;
  let registering = false;
  let cart = [];
  let addresses = [];
  let currentProduct;
  let currentOrder;
  let selectedSku;
  let noticeTimer;
  let adminCategories = [];
  let adminProduct = null;
  let adminSkus = [];

  const h = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pathId = value => encodeURIComponent(String(value));
  const money = value => value == null ? '价格待确认' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  const date = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
  function historyNote(event) {
    const notes = {
      'Order created': '订单已提交，请在有效期内完成模拟支付。',
      'Simulated payment; no money charged': '模拟支付已完成，未产生真实扣款。',
      'Simulated shipment; no parcel dispatched': '模拟发货已完成，未寄出真实包裹。',
      'Simulated receipt confirmed by buyer': '买家已确认模拟收货。',
      'Payment window expired': '支付超时，系统已取消订单。',
      'Cancelled by buyer': '买家已主动取消订单。'
    };
    return notes[event.note] || event.note || '';
  }
  const badge = status => `<span class="tag">${h(statusNames[status] || status)}</span>`;
  const empty = (title, message, link = '') => `<div class="empty"><div class="empty-mark" aria-hidden="true">${art}</div><h2>${h(title)}</h2><p>${h(message)}</p>${link}</div>`;
  const heading = (title, subtitle) => `<div class="page-heading"><h1>${h(title)}</h1><p class="muted">${h(subtitle)}</p></div>`;
  const art = '<svg viewBox="0 0 120 120" fill="none" aria-hidden="true"><path d="M28 51 60 32l32 19v39l-32 19-32-19V51Z" stroke="currentColor" stroke-width="2"/><path d="m28 51 32 19 32-19M60 70v39M44 42l32 19v14" stroke="currentColor" stroke-width="2"/><path d="M48 21h24M60 9v24" stroke="currentColor" stroke-width="2"/></svg>';
  const phoneArt = '<svg viewBox="0 0 320 270" fill="none" aria-hidden="true"><ellipse cx="163" cy="244" rx="109" ry="14" fill="#000" opacity=".08"/><g transform="translate(48 18) rotate(-10 58 112)"><rect width="116" height="222" rx="22" fill="#b6603f"/><rect x="3" y="3" width="110" height="216" rx="20" fill="#df916b" stroke="#e9b393" stroke-width="2"/><rect x="9" y="12" width="98" height="69" rx="16" fill="#d88058"/><g fill="#24272e" stroke="#ad593c" stroke-width="5"><circle cx="33" cy="35" r="16"/><circle cx="76" cy="35" r="16"/><circle cx="33" cy="70" r="16"/></g><circle cx="77" cy="64" r="6" fill="#f3d4af"/></g><g transform="translate(158 28) rotate(9 57 112)"><rect width="114" height="218" rx="22" fill="#202431"/><rect x="5" y="5" width="104" height="208" rx="18" fill="#eecfc2"/><path d="M5 91C33 38 100 98 109 147V193Q109 213 90 213H23Q5 213 5 195Z" fill="#bd6142"/><path d="M5 149C27 100 68 117 109 170V193Q109 213 90 213H23Q5 213 5 195Z" fill="#723e3a"/><rect x="36" y="12" width="42" height="11" rx="6" fill="#202431"/></g></svg>';
  function illustration(name = '') {
    return `<div class="illustration">${/iphone|手机|phone/i.test(name) ? phoneArt : art}<span>商品示意图</span></div>`;
  }
  function image(url) {
    if (!url) return art;
    try {
      const parsed = new URL(url, location.origin);
      if (!['http:', 'https:'].includes(parsed.protocol)) return art;
      return `<img src="${h(parsed.href)}" alt="商品图片" loading="lazy" referrerpolicy="no-referrer">`;
    } catch { return art; }
  }

  // Legacy endpoints serialize Long IDs as JSON numbers. Quote only unsafe integer
  // tokens before parsing; matching strings first leaves names and escaped text intact.
  function parseJson(text) {
    return JSON.parse(text.replace(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g, value => {
      if (/^-?\d+$/.test(value) && (BigInt(value) > 9007199254740991n || BigInt(value) < -9007199254740991n)) return `"${value}"`;
      return value;
    }));
  }
  class ApiError extends Error {
    constructor(message, status) { super(message); this.status = status; }
  }
  async function api(path, method = 'GET', body, authenticated = true) {
    const usedToken = token;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(`/api${path}`, {
        method, signal: controller.signal, cache: 'no-store',
        headers: { 'Content-Type': 'application/json', ...(authenticated && usedToken ? { Authorization: `Bearer ${usedToken}` } : {}) },
        body: body === undefined ? undefined : JSON.stringify(body)
      });
      const text = await response.text();
      let payload;
      try { payload = parseJson(text); } catch { throw new ApiError(`服务暂时无法响应（${response.status}），请稍后重试。`, response.status); }
      if (!response.ok || !payload.success) {
        if (response.status === 401 && authenticated && usedToken === token) {
          endSession();
          showAuth();
        }
        const messages = { 401: authenticated ? '登录已过期，请重新登录。' : '用户名或密码不正确。', 403: '当前账号没有此操作权限。', 429: '操作有些频繁，请稍候再试。', 503: '服务暂未就绪，请稍后重试。' };
        const message = messages[response.status] || payload.message || '操作失败，请稍后重试。';
        throw new ApiError(`${message}${payload.traceId ? `（参考编号：${payload.traceId}）` : ''}`, response.status);
      }
      return payload.data;
    } catch (error) {
      if (error.name === 'AbortError') throw new ApiError('请求超时，操作结果尚未确认。请刷新查看，结算请使用原请求重试。', 0);
      if (error instanceof TypeError) throw new ApiError('无法连接服务，请检查连接后重试。', 0);
      throw error;
    } finally { clearTimeout(timer); }
  }
  function notify(message, error = false) {
    clearTimeout(noticeTimer);
    $('#notice').textContent = message;
    $('#notice').className = `notice${error ? ' error' : ''}`;
    $('#notice').hidden = false;
    noticeTimer = setTimeout(() => { $('#notice').hidden = true; }, error ? 12000 : 5000);
  }
  function account() {
    $('#admin-nav').hidden = !isAdmin();
    $('#account').innerHTML = token
      ? `<span class="account-name">${h(username)}</span><button class="text-button" data-action="logout">退出</button>`
      : '<button class="button small" data-action="login">登录 / 注册 ↗</button>';
  }
  function isAdmin() {
    // Used only for navigation. The gateway enforces the signed ADMIN claim for writes.
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return String(JSON.parse(atob(payload)).role).toUpperCase() === 'ADMIN';
    } catch { return false; }
  }
  function endSession() {
    token = ''; username = ''; epoch++;
    cart = []; addresses = []; currentOrder = null; currentProduct = null;
    sessionStorage.removeItem('nova.token'); sessionStorage.removeItem('nova.username');
    account();
    app.innerHTML = empty('登录后继续', '我的购物车、收货地址和订单会在登录后显示。', '<button class="button primary" data-action="login">登录 / 注册</button>');
  }
  function showAuth() { if (!$('#auth-dialog').open) { $('#auth-error').textContent = ''; $('#auth-dialog').showModal(); } }
  function flow() { try { return JSON.parse(sessionStorage.getItem(`nova.flow:${username.toLowerCase()}`)) || null; } catch { return null; } }
  function saveFlow(value) {
    const key = `nova.flow:${username.toLowerCase()}`;
    if (value) sessionStorage.setItem(key, JSON.stringify(value)); else sessionStorage.removeItem(key);
  }
  function confirmAction(title, message) {
    $('#confirm-title').textContent = title; $('#confirm-message').textContent = message;
    const dialog = $('#confirm-dialog'); dialog.returnValue = ''; dialog.showModal();
    return new Promise(resolve => dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), { once: true }));
  }
  function routeInfo() {
    const [name, query = ''] = (location.hash.slice(1) || 'catalog').split('?');
    return { name, params: new URLSearchParams(query) };
  }
  function go(hash) { if (location.hash === `#${hash}`) return render(); location.hash = hash; }
  function pagination(name, page, hasNext, extra = '') {
    return `<div class="pager">${page > 1 ? `<a class="button small" href="#${name}?page=${page - 1}${extra}">上一页</a>` : ''}<span>第 ${page} 页</span>${hasNext ? `<a class="button small" href="#${name}?page=${page + 1}${extra}">下一页</a>` : ''}</div>`;
  }
  async function render() {
    const generation = ++epoch;
    const { name, params } = routeInfo();
    document.body.classList.toggle('admin-mode', name === 'admin');
    $('#global-search-form input').value = params.get('q') || '';
    document.querySelectorAll('[data-nav]').forEach(a => {
      if (a.dataset.nav === name || (name === 'product' && a.dataset.nav === 'catalog') || (name === 'order' && a.dataset.nav === 'orders')) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
    if (!token && !['catalog', 'product'].includes(name)) {
      if (name === 'admin') {
        app.innerHTML = heading('管理员工作台', '管理商品、分类、价格和库存。') + empty('请使用管理员账号登录', '登录后可查看商品经营数据，并进行新增、编辑、上下架和补库存操作。', '<button class="button primary" data-action="login">管理员登录</button>');
        return;
      }
      app.innerHTML = heading('会员中心', '登录后管理购物车与订单。') + empty('请先登录', '登录或注册账号，继续购物。', '<button class="button primary" data-action="login">登录 / 注册</button>');
      return;
    }
    app.innerHTML = '<div class="loading" role="status">正在加载商品信息…</div>';
    try {
      let markup;
      if (name === 'admin') markup = isAdmin() ? await adminPage(params, generation) : empty('需要管理员账号', '当前是买家账号，请切换管理员账号后管理商品。', '<button class="button primary" data-action="switch-admin">切换管理员账号</button>');
      else if (name === 'cart') markup = await cartPage(generation);
      else if (name === 'addresses') markup = await addressPage(generation);
      else if (name === 'orders') markup = await ordersPage(params);
      else if (name === 'order') markup = await orderPage(params, generation);
      else if (name === 'product') markup = await productPage(params, generation);
      else markup = await catalogPage(params);
      if (generation === epoch) app.innerHTML = markup;
    } catch (error) {
      if (generation === epoch) app.innerHTML = empty('暂时没能加载', error.message, '<button class="button primary" data-action="refresh">重新加载</button>');
    }
  }
  function catalogLink(categoryId = '', keyword = '', page = 1) {
    return `#catalog?${new URLSearchParams({ categoryId, q: keyword, page: String(page) })}`;
  }
  function storefrontTop(categories, featured, display) {
    const shortcuts = categories.filter(c => c.status === 1)
      .sort((a, b) => Number(/手机|数码/.test(b.name)) - Number(/手机|数码/.test(a.name)))
      .slice(0, 8);
    return `<section class="storefront-top" aria-label="商城导购">
      <aside class="category-menu"><h2>商品分类</h2><a class="category-all" href="#catalog">全部商品 <span>›</span></a>${shortcuts.map(c => `<a href="${catalogLink(String(c.id))}"><span>${h(c.name)}</span><b>›</b></a>`).join('') || '<p class="hint">暂无商品分类</p>'}<div class="category-foot">按分类查找商品，选购更轻松</div></aside>
      <div class="feature-banner"><div class="feature-copy"><span class="feature-kicker">手机数码 · 精选推荐</span><h1>${featured ? h(featured.name) : '发现你的下一件数码好物'}</h1><p>${featured ? h(display.categories.get(String(featured.categoryId)) || '在售商品') : '查看商品参数、价格与实时库存'}</p>${featured ? `<div class="feature-price"><span>商城价</span><strong>${money(featured.price)}</strong></div><a class="button primary" href="#product?id=${pathId(featured.id)}">查看商品 <span>›</span></a>` : '<a class="button primary" href="#catalog?q=手机">浏览手机数码 ›</a>'}<small>演示商城 · 不产生真实交易</small></div><div class="feature-art">${illustration(featured?.name || '手机')}</div><div class="feature-bottom"><span>商品详情</span><i></i><span>价格透明</span><i></i><span>库存可查</span></div></div>
      <aside class="member-panel"><div class="member-icon" aria-hidden="true"><svg viewBox="0 0 40 40" fill="none"><circle cx="20" cy="13" r="7" stroke="currentColor" stroke-width="2"/><path d="M7 34c0-14 26-14 26 0" stroke="currentColor" stroke-width="2"/></svg></div><h2>${token ? `你好，${h(username)}` : '欢迎来到网上商城'}</h2><p>${token ? '查看购物车，管理你的订单' : '登录后享受完整购物体验'}</p>${token ? '<a class="button primary full" href="#orders">我的订单</a>' : '<button class="button primary full" data-action="login">登录 / 免费注册</button>'}<div class="member-shortcuts"><a href="#cart"><strong>购物车</strong><span>已选商品</span></a><a href="#addresses"><strong>收货地址</strong><span>配送信息</span></a></div><div class="mall-notice"><h3>商城公告</h3><p>本商城为个人项目，支付与物流均为模拟演示。</p><a href="#admin">商家管理入口 ›</a></div></aside>
    </section>`;
  }
  async function catalogPage(params) {
    const page = Math.max(1, Number(params.get('page')) || 1);
    const keyword = params.get('q') || '';
    const categoryId = /^\d+$/.test(params.get('categoryId') || '') ? params.get('categoryId') : '';
    const data = await api(`/products?status=1&page=${page}&size=12&keyword=${encodeURIComponent(keyword)}${categoryId ? `&categoryId=${pathId(categoryId)}` : ''}`);
    const display = await productDisplay(data.items.map(p => p.id));
    const categoryName = display.categories.get(categoryId);
    const featured = data.items.find(p => /iphone|手机/i.test(p.name)) || data.items[0];
    const activeCategories = display.categoryItems.filter(c => c.status === 1);
    const front = !keyword && !categoryId && page === 1 ? storefrontTop(activeCategories, featured, display) : '';
    const categoryChips = `<div class="catalog-filters"><strong>商品分类</strong><div class="filter-options"><a href="${catalogLink('', keyword)}" ${!categoryId ? 'aria-current="true"' : ''}>全部</a>${activeCategories.map(c => `<a href="${catalogLink(String(c.id), keyword)}" ${String(c.id) === categoryId ? 'aria-current="true"' : ''}>${h(c.name)}</a>`).join('')}</div></div>`;
    return `${front}<section class="catalog-section"><div class="catalog-heading"><h2>${keyword ? '搜索结果' : categoryName ? h(categoryName) : '为你推荐'}</h2><p>${keyword ? `关键词：${h(keyword)} · ` : ''}共 <strong>${h(data.total)}</strong> 件商品</p><a href="#catalog">全部商品 ›</a></div>${categoryChips}<div class="catalog-toolbar"><span class="sort-current">最新上架</span><span>价格、已售量、库存一目了然</span><span class="result-pages">第 ${page} 页</span></div>${data.items.length ? `<div class="products">${data.items.map(p => `<article class="product-card"><a class="product-cover" href="#product?id=${pathId(p.id)}"><div class="product-art">${illustration(p.name)}</div></a><div class="product-info"><div class="product-price-row"><span class="price">${money(p.price)}</span><span class="price-label">商城价</span></div><a href="#product?id=${pathId(p.id)}"><h3>${h(p.name)}</h3></a><p class="product-category">${h(display.categories.get(String(p.categoryId)) || '分类暂不可用')}</p>${productMetrics(p.id, display)}<div class="card-bottom"><span class="store-label">商城在售</span><a class="button small" href="#product?id=${pathId(p.id)}">查看详情</a></div></div></article>`).join('')}</div>` : empty('没有找到相关商品', keyword ? '请更换关键词，或选择其他分类。' : '该分类暂无上架商品。', '<a class="button" href="#catalog">查看全部商品</a>')}${pagination('catalog', page, page * 12 < Number(data.total), `&q=${encodeURIComponent(keyword)}&categoryId=${pathId(categoryId)}`)}</section>`;
  }
  async function productPage(params, generation) {
    const id = params.get('id');
    const [product, skus] = await Promise.all([api(`/products/${pathId(id)}/detail`), api(`/products/${pathId(id)}/skus`)]);
    const display = await productDisplay([product.id]);
    const sku = skus.find(s => String(s.skuId) === params.get('sku')) || skus.find(s => s.status === 1) || skus[0];
    let stock = null;
    try { if (sku) stock = await api(`/inventory/skus/${pathId(sku.skuId)}`); } catch { /* Unknown stock must not look available. */ }
    if (generation === epoch) { currentProduct = product; selectedSku = sku; }
    const available = product.status === 1 && sku?.status === 1 && stock?.availableStock > 0;
    return `<a class="back" href="#catalog">← 返回商品列表</a><div class="detail-product"><div class="product-art">${(sku?.coverImageUrl || product.coverImageUrl) ? image(sku?.coverImageUrl || product.coverImageUrl) : illustration(product.name)}</div><section><div class="eyebrow">商品详情</div><h1>${h(product.name)}</h1><p class="product-category">${h(display.categories.get(String(product.categoryId)) || '分类暂不可用')}</p>${productMetrics(product.id, display)}<p class="muted">${h(product.subtitle)}</p><span class="price">${money(sku?.price ?? product.price)}</span><div class="sku-choice">${skus.map(s => `<a class="button small" href="#product?id=${pathId(id)}&sku=${pathId(s.skuId)}" ${String(s.skuId) === String(sku?.skuId) ? 'aria-current="true"' : ''}>${h(s.skuName)}${s.status !== 1 ? ' · 已下架' : ''}</a>`).join('')}</div><p class="muted">${h(Object.entries(sku?.specs || {}).map(([k, v]) => `${k}：${v}`).join(' / '))}</p><p class="hint">${stock ? `可用库存 ${h(stock.availableStock)} 件` : '暂时无法确认库存，请稍后刷新。'}</p><button class="button primary" data-action="add-cart" ${!available ? 'disabled' : ''}>${available ? '加入购物车 ＋' : '暂不可购买'}</button><p class="hint">模拟购买体验 · 不产生真实扣款</p></section></div><section class="panel" style="margin-top:32px"><h2>商品介绍</h2><p class="muted description">${h(product.description || product.subtitle || '这件商品暂时没有更多介绍。')}</p></section>`;
  }
  async function productDisplay(ids) {
    const query = ids.map(pathId).join(',');
    const [categories, sales, stock] = await Promise.allSettled([
      api('/categories'),
      ids.length ? api(`/products/sales-counts?productIds=${query}`) : Promise.resolve([]),
      ids.length ? api(`/inventory/summary?productIds=${query}`) : Promise.resolve([])
    ]);
    const categoryItems = categories.status === 'fulfilled' ? categories.value.items : [];
    return {
      categoryItems,
      categories: new Map(categoryItems.map(c => [String(c.id), c.name])),
      sales: new Map((sales.status === 'fulfilled' ? sales.value : []).map(s => [String(s.productId), s.soldQuantity])),
      stock: new Map((stock.status === 'fulfilled' ? stock.value : []).map(s => [String(s.productId), s]))
    };
  }
  function productMetrics(id, display) {
    const sold = display.sales.get(String(id));
    const stock = display.stock.get(String(id));
    return `<div class="product-metrics"><span title="已完成订单累计件数，异步更新">已售量：<strong>${sold == null ? '暂不可用' : h(sold)}</strong></span><span title="所有规格的可用库存合计">库存：<strong>${stock ? h(stock.availableStock) : '暂不可用'}</strong></span></div>`;
  }
  async function adminPage(params, generation) {
    const page = Math.max(1, Number(params.get('page')) || 1);
    const keyword = params.get('q') || '';
    const status = ['0', '1'].includes(params.get('status')) ? params.get('status') : '';
    const data = await api(`/products?page=${page}&size=10&keyword=${encodeURIComponent(keyword)}${status ? `&status=${status}` : ''}`);
    const display = await productDisplay(data.items.map(p => p.id));
    let product = null;
    let skus = [];
    if (params.get('edit')) {
      [product, skus] = await Promise.all([
        api(`/products/${pathId(params.get('edit'))}/detail`),
        api(`/products/${pathId(params.get('edit'))}/skus`)
      ]);
    }
    if (generation !== epoch) return '';
    adminCategories = display.categoryItems; adminProduct = product; adminSkus = skus;
    const categoryOptions = adminCategories.filter(c => c.status === 1 || String(c.id) === String(product?.categoryId));
    return `<div class="admin-heading"><div>${heading('商品管理', '管理商品目录、销售价格、上架状态和库存。')}</div><a class="button" href="#catalog">查看商城 ↗</a></div>
      <div class="admin-layout"><section class="panel admin-list"><div class="section-top"><div><h2>商品目录 <span class="tag">${h(data.total)} 件</span></h2><p class="muted">商品名称 · 分类 · 售价 · 已售量 · 库存</p></div><a class="button primary small" href="#admin?new=1">＋ 新增商品</a></div>
      <form id="admin-search-form" class="admin-filter"><input name="q" aria-label="搜索商品名称" placeholder="搜索商品，例如 iPhone 17 Pro" value="${h(keyword)}" maxlength="128"><select name="status" aria-label="上架状态"><option value="">全部状态</option><option value="1" ${status === '1' ? 'selected' : ''}>已上架</option><option value="0" ${status === '0' ? 'selected' : ''}>已下架</option></select><button type="submit" class="button small">查询</button><button type="button" class="text-button" data-action="refresh">刷新</button></form>
      <div class="table-scroll"><table class="product-table"><thead><tr><th>商品 / 分类</th><th>价格（元）</th><th>已售量</th><th>可用库存</th><th>状态 / 操作</th></tr></thead><tbody>${data.items.map(p => {
        const sold = display.sales.get(String(p.id)); const stock = display.stock.get(String(p.id));
        return `<tr><td><a href="#product?id=${pathId(p.id)}"><strong>${h(p.name)}</strong></a><small>${h(display.categories.get(String(p.categoryId)) || '分类暂不可用')}</small></td><td class="numeric">${money(p.price)}</td><td class="numeric">${sold == null ? '—' : h(sold)}</td><td class="numeric">${stock ? h(stock.availableStock) : '—'}<small>预占 ${stock ? h(stock.lockedStock) : '—'}</small></td><td><span class="tag ${p.status === 1 ? '' : 'mock'}">${p.status === 1 ? '已上架' : '已下架'}</span><div class="table-actions"><a href="#admin?edit=${pathId(p.id)}" class="text-button">编辑 / 补库</a><button class="text-button" data-action="admin-status" data-id="${h(p.id)}" data-status="${p.status === 1 ? 0 : 1}">${p.status === 1 ? '下架' : '上架'}</button></div></td></tr>`;
      }).join('') || '<tr><td colspan="5">暂无商品，请在右侧添加。</td></tr>'}</tbody></table></div>
      <p class="hint">已售量按完成订单累计件数，异步更新；库存是各规格可用库存合计，预占库存单独显示。“—”表示数据暂不可用。</p>${pagination('admin', page, page * 10 < Number(data.total), `&q=${encodeURIComponent(keyword)}&status=${status}`)}</section>
      <aside class="stack"><section class="panel"><div class="section-top"><h2>${product ? '编辑商品' : '新增商品'}</h2>${product ? '<a class="text-button" href="#admin">取消编辑</a>' : ''}</div>
      <form id="admin-product-form"><label>商品名称<input name="name" required maxlength="128" placeholder="例如：iPhone 17 Pro" value="${h(product?.name || '')}"></label><label>商品分类<select name="categoryId" required><option value="">请选择分类</option>${categoryOptions.map(c => `<option value="${h(c.id)}" ${String(c.id) === String(product?.categoryId) ? 'selected' : ''}>${h(c.name)}${c.status !== 1 ? '（停用）' : ''}</option>`).join('')}</select></label>
      <label>价格（元）<input name="price" type="number" min="0" max="9999999999.99" step="0.01" required placeholder="例如：10000" value="${product ? h(Number(product.price).toFixed(2)) : ''}"></label><label>商品简介<input name="subtitle" maxlength="255" placeholder="例如：旗舰手机，日常与创作兼顾" value="${h(product?.subtitle || '')}"></label><label>上架状态<select name="status"><option value="1" ${product?.status !== 0 ? 'selected' : ''}>上架</option><option value="0" ${product?.status === 0 ? 'selected' : ''}>下架</option></select></label><p class="hint">售价同步到默认规格。新增后可在下方补充库存；销量由订单自动累计。</p><button class="button primary full" type="submit">${product ? '保存商品' : '创建商品'}</button></form></section>
      ${product ? `<section class="panel"><h2>补充库存</h2><p class="muted">${h(product.name)}</p><form id="admin-stock-form"><label>选择规格<select name="skuId" required>${skus.map(s => `<option value="${h(s.skuId)}">${h(s.skuName)} · ${money(s.price)}</option>`).join('')}</select></label><label>本次增加数量<input name="quantity" type="number" min="1" max="1000000" step="1" required placeholder="例如：100"></label><p class="hint">输入的是新增数量，会累加到已有库存，不会覆盖当前库存。</p><button class="button primary full" type="submit" ${skus.length ? '' : 'disabled'}>确认补库</button></form></section>` : ''}
      <section class="panel"><h2>新增分类</h2><form id="admin-category-form"><label>分类名称<input name="name" required maxlength="64" placeholder="例如：手机数码产品"></label><button class="button full" type="submit" style="margin-top:16px">创建分类</button></form></section></aside></div>`;
  }

  async function cartPage(generation) {
    const [rows, savedAddresses] = await Promise.all([api('/users/cart'), api('/users/addresses')]);
    const skuLists = new Map();
    for (const row of rows) {
      const id = String(row.productId);
      if (!skuLists.has(id)) {
        try { skuLists.set(id, await api(`/products/${pathId(id)}/skus`)); } catch { skuLists.set(id, []); }
      }
      row.sku = skuLists.get(id).find(s => String(s.skuId) === String(row.skuId));
    }
    if (generation !== epoch) return '';
    cart = rows; addresses = savedAddresses;
    let pending = flow();
    if (pending?.cleanupIds && !rows.some(r => pending.cleanupIds.includes(String(r.id)))) { saveFlow(null); pending = null; }
    const locked = !!pending;
    const selected = rows.filter(r => r.selected);
    const knownPrices = selected.every(r => r.sku?.price != null && r.sku.status === 1);
    const estimate = selected.reduce((sum, r) => sum + Math.round(Number(r.sku?.price || 0) * 100) * r.quantity, 0) / 100;
    const preferred = pending?.addressId || savedAddresses.find(a => a.isDefault)?.id || savedAddresses[0]?.id;
    const warning = pending?.requestKey ? '<div class="warning">上次结算结果尚未确认。请重试原请求，系统会查回原订单，不会重复创建。确认结果前暂时不能修改购物车或地址。</div>' : pending?.cleanupIds ? `<div class="warning">订单 ${h(pending.orderNo)} 已创建，购物车正在同步。请稍后刷新，或<a href="#order?no=${pathId(pending.orderNo)}">查看订单 →</a>。</div>` : '';
    return heading('我的购物车', '确认商品数量和收货地址后提交订单。') + warning + `<div class="two-col" style="margin-top:18px"><div class="panel">${rows.length ? rows.map(r => `<div class="item-row"><input type="checkbox" class="cart-check" data-select="${h(r.id)}" aria-label="选择 ${h(r.sku?.productName || '商品')}" ${r.selected ? 'checked' : ''} ${locked ? 'disabled' : ''}><div class="mini-art" aria-hidden="true">${art}</div><div class="item-main"><h3><a href="#product?id=${pathId(r.productId)}">${h(r.sku?.productName || '商品信息暂不可用')}</a></h3><p>${h(r.sku?.skuName || `规格 ${r.skuId}`)}</p><p>${money(r.sku?.price)}${r.sku?.status !== 1 ? ' · 暂不可购买' : ''}</p></div><div class="quantity"><button data-action="quantity" data-id="${h(r.id)}" data-step="-1" aria-label="减少数量" ${locked || r.quantity <= 1 ? 'disabled' : ''}>−</button><span>${h(r.quantity)}</span><button data-action="quantity" data-id="${h(r.id)}" data-step="1" aria-label="增加数量" ${locked || r.quantity >= 99 ? 'disabled' : ''}>＋</button></div><button class="text-button" data-action="remove-cart" data-id="${h(r.id)}" ${locked ? 'disabled' : ''}>移除</button></div>`).join('') : empty('购物车还是空的', '先去商城挑选商品吧。', '<a class="button" href="#catalog">去逛逛 ↗</a>')}</div><aside class="panel summary"><h2>订单结算</h2><label for="checkout-address">收货地址</label><select id="checkout-address" ${locked ? 'disabled' : ''}>${savedAddresses.map(a => `<option value="${h(a.id)}" ${String(a.id) === String(preferred) ? 'selected' : ''}>${h(a.receiverName)} · ${h(a.city + a.district + a.detailAddress)}</option>`).join('') || '<option value="">请先添加地址</option>'}</select><a class="text-button" href="#addresses">管理收货地址 →</a><div class="summary-line"><span>已选商品</span><span>${selected.reduce((n, r) => n + r.quantity, 0)} 件</span></div><div class="summary-line summary-total"><span>商品参考金额</span><strong>${knownPrices ? money(estimate) : '待确认'}</strong></div><p class="hint">下单时会重新确认价格和库存，订单金额以服务端结算为准。</p><button class="button primary full" data-action="checkout" ${pending?.cleanupIds || (!pending?.requestKey && (!selected.length || !savedAddresses.length || !knownPrices)) ? 'disabled' : ''}>${pending?.requestKey ? '重试原结算请求' : '提交订单 →'}</button><button class="text-button full" data-action="refresh">刷新购物车</button></aside></div>`;
  }
  async function addressPage(generation) {
    const data = await api('/users/addresses');
    if (generation === epoch) addresses = data;
    const locked = !!flow()?.requestKey;
    return heading('收货地址', '管理订单配送所需的收货信息。') + `<div class="two-col"><section class="panel"><h2>已保存的地址</h2>${data.map(a => `<article class="address-card"><h3>${h(a.receiverName)} ${a.isDefault ? '<span class="tag">默认地址</span>' : ''}</h3><p>${h(a.receiverPhone)}</p><p>${h(a.province + a.city + a.district + a.detailAddress)}</p><button class="text-button" data-action="delete-address" data-id="${h(a.id)}" ${locked ? 'disabled' : ''}>删除地址</button></article>`).join('') || '<p class="muted">还没有保存地址，在右侧添加一个吧。</p>'}<a class="text-button" href="#cart">返回购物车 →</a></section><section class="panel"><h2>添加新地址</h2><form id="address-form"><div class="form-grid"><label>收件人<input name="receiverName" required maxlength="64" autocomplete="shipping name"></label><label>手机号<input name="receiverPhone" type="tel" required pattern="[0-9+ ()-]{6,32}" maxlength="32" autocomplete="shipping tel"></label><label>省 / 直辖市<input name="province" required maxlength="64" autocomplete="shipping address-level1"></label><label>城市<input name="city" required maxlength="64" autocomplete="shipping address-level2"></label><label>区 / 县<input name="district" required maxlength="64" autocomplete="shipping address-level3"></label><label>邮政编码<input name="postalCode" maxlength="16" autocomplete="shipping postal-code"></label><label class="wide">详细地址<textarea name="detailAddress" required maxlength="255" rows="2" autocomplete="shipping street-address"></textarea></label></div><label class="checkbox-label"><input type="checkbox" name="isDefault"> 设为默认地址</label><button class="button primary full" type="submit">保存地址</button></form></section></div>`;
  }
  async function ordersPage(params) {
    const page = Math.max(1, Number(params.get('page')) || 1);
    const status = params.get('status') || '';
    const data = await api(`/orders?page=${page}&size=8${status ? `&status=${encodeURIComponent(status)}` : ''}`);
    return heading('我的订单', '查看订单进度、支付和物流信息。') + `<div class="section-top"><div class="button-row"><a class="button small" href="#orders">全部</a>${['PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'].map(s => `<a class="button small ${s === status ? 'primary' : ''}" href="#orders?status=${s}">${h(statusNames[s])}</a>`).join('')}</div><button class="text-button" data-action="refresh">刷新订单 ↻</button></div><div class="stack">${data.items.map(o => `<article class="panel order-card"><div class="order-top"><div><p>下单于 ${h(date(o.createdAt))}</p><span class="order-number">订单 ${h(o.orderNo)}</span></div>${badge(o.status)}</div><div class="item-row"><div class="mini-art" aria-hidden="true">${art}</div><div class="item-main"><h3>${h(o.firstItem?.productName || '订单商品')}</h3><p>${h(o.firstItem?.skuName || '')} · 共 ${h(o.itemCount)} 项商品</p></div></div><div class="order-bottom"><span>订单金额 <strong>${money(o.totalAmount)}</strong></span><a class="button small" href="#order?no=${pathId(o.orderNo)}">查看订单 →</a></div></article>`).join('') || empty('这里还没有订单', '浏览商城商品，开始你的第一笔模拟购买。', '<a class="button" href="#catalog">去逛逛</a>')}</div>${pagination('orders', page, data.hasNext, `&status=${encodeURIComponent(status)}`)}`;
  }
  async function orderPage(params, generation) {
    const o = await api(`/orders/${pathId(params.get('no'))}/detail`);
    if (generation === epoch) currentOrder = o;
    const a = o.address;
    return `<a class="back" href="#orders">← 返回我的订单</a><div class="page-heading"><div class="detail-title"><h1>订单详情</h1>${badge(o.status)}</div><p class="order-number">订单 ${h(o.orderNo)} · ${h(date(o.createdAt))}</p></div><div class="two-col"><div class="stack"><section class="panel"><h2>商品清单</h2>${o.items.map(i => `<div class="item-row"><div class="mini-art" aria-hidden="true">${art}</div><div class="item-main"><h3>${h(i.productName)}</h3><p>${h(i.skuName)} · ${h(Object.entries(i.specs || {}).map(([k, v]) => `${k}：${v}`).join(' / '))}</p><p>${money(i.unitPrice)} × ${h(i.quantity)}</p></div><strong>${money(i.amount)}</strong></div>`).join('')}</section><section class="panel"><h2>收货信息</h2>${a ? `<h3>${h(a.receiverName)} <span class="muted">${h(a.receiverPhone)}</span></h3><p class="muted">${h(a.province + a.city + a.district + a.detailAddress)}</p>` : '<p class="muted">这笔订单没有保存收货地址。</p>'}</section><section class="panel"><h2>订单时间线</h2>${o.history.length ? `<ol class="timeline">${o.history.map(e => `<li><strong>${h(eventNames[e.eventType] || e.eventType)}</strong><p>${h(date(e.occurredAt))} · ${e.actorType === 'SYSTEM' ? '系统处理' : '买家操作'}</p><p>${h(historyNote(e))}</p></li>`).join('')}</ol>` : '<p class="muted">暂无已保存的历史记录，当前状态以订单信息为准。</p>'}</section></div><aside class="stack"><section class="panel summary"><h2>订单金额</h2>${[['商品总额', o.productAmount], ['优惠', o.discountAmount], ['运费', o.freightAmount]].map(([label, value]) => `<div class="summary-line"><span>${label}</span><span>${money(value)}</span></div>`).join('')}<div class="summary-line summary-total"><span>应付总额</span><strong>${money(o.totalAmount)}</strong></div>${o.status === 'PENDING_PAYMENT' ? `<p class="hint">请于 ${h(date(o.paymentDeadline))} 前完成模拟支付。</p>` : ''}${o.cancelReason ? `<p class="hint">取消原因：${h(({ USER_CANCELLED: '买家主动取消', PAYMENT_TIMEOUT: '超出支付期限' })[o.cancelReason] || o.cancelReason)}</p>` : ''}<div class="stack">${o.actions.map(action => `<button class="button ${action.code === 'CANCEL' ? '' : 'primary'}" data-action="order-action" data-code="${h(action.code)}">${h(action.label)}</button>`).join('')}</div><button class="text-button full" data-action="refresh">刷新订单状态 ↻</button><p class="hint">支付、发货与收货均为演示，不产生真实交易。</p></section><section class="panel"><h2>支付记录</h2>${o.payments.map(p => `<div class="record">${p.simulated ? '<span class="tag mock">模拟支付</span>' : ''} ${badge(p.status)}<p>${h(p.paymentNo)}</p><p>${money(p.amount)} · ${h(date(p.paidAt))}</p></div>`).join('') || '<p class="muted">暂无已保存的支付记录。</p>'}</section><section class="panel"><h2>物流记录</h2>${o.shipments.map(s => `<div class="record">${s.simulated ? '<span class="tag mock">模拟物流</span>' : h(s.company)} ${badge(s.status)}<p>${h(s.trackingNo)}</p><p>发货 ${h(date(s.shippedAt))}</p><p>送达 ${h(date(s.deliveredAt))}</p></div>`).join('') || '<p class="muted">暂无已保存的物流记录。</p>'}</section></aside></div>`;
  }

  async function checkout() {
    let pending = flow();
    if (pending?.cleanupIds) throw new Error('购物车正在同步，请稍后刷新。');
    if (!pending) {
      const addressId = $('#checkout-address')?.value;
      const selected = cart.filter(r => r.selected);
      if (!addressId || !selected.length) throw new Error('请选择商品并添加收货地址。');
      if (!await confirmAction('提交模拟订单', `即将结算 ${selected.reduce((n, r) => n + r.quantity, 0)} 件商品。提交后查看最终金额，再选择是否模拟支付。`)) return;
      pending = { requestKey: crypto.randomUUID(), addressId, itemIds: selected.map(r => String(r.id)) };
      saveFlow(pending);
    }
    try {
      const order = await api('/orders/checkout', 'POST', { requestKey: pending.requestKey, addressId: pending.addressId });
      saveFlow({ cleanupIds: pending.itemIds, orderNo: order.orderNo });
      notify('订单已提交，接下来可以模拟支付。');
      go(`order?no=${pathId(order.orderNo)}`);
    } catch (error) {
      // A definitive rejection can be corrected. Ambiguous network/server failures
      // retain the original key, including across page reload and login expiry.
      if ([400, 403, 404, 422].includes(error.status)) saveFlow(null);
      await render();
      throw error;
    }
  }
  async function work(fn, element) {
    if (busy) return;
    busy = true;
    if (element) element.disabled = true;
    try { await fn(); } catch (error) { notify(error.message, true); }
    finally { busy = false; if (element?.isConnected) element.disabled = false; }
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const action = button.dataset.action;
    if (action === 'login') return showAuth();
    if (action === 'close-auth') return $('#auth-dialog').close();
    if (action === 'toggle-auth') {
      if (busy) return;
      registering = !registering;
      $('#auth-title').textContent = registering ? '注册账号' : '账号登录';
      $('#auth-submit').textContent = registering ? '注册并登录' : '登录';
      $('#auth-toggle').textContent = registering ? '已有账号？去登录' : '还没有账号？注册一个';
      $('#nickname-field').hidden = !registering;
      $('#nickname-field input').required = registering;
      $('#auth-form [name=password]').minLength = registering ? 8 : 1;
      $('#auth-form [name=password]').autocomplete = registering ? 'new-password' : 'current-password';
      $('#auth-error').textContent = '';
      return;
    }
    work(async () => {
      if (action === 'logout') { endSession(); await render(); return; }
      if (action === 'switch-admin') { endSession(); await render(); showAuth(); return; }
      if (action === 'refresh') { await render(); return; }
      if (action === 'admin-status') {
        if (!isAdmin()) throw new Error('请使用管理员账号。');
        const nextStatus = Number(button.dataset.status);
        const product = await api(`/products/${pathId(button.dataset.id)}/detail`);
        if (!await confirmAction(nextStatus === 1 ? '上架商品' : '下架商品', `${product.name}，${money(product.price)}。${nextStatus === 1 ? '上架后买家可查看和购买。' : '下架后将停止新的购买，历史订单保留。'}`)) return;
        await api(`/products/${pathId(product.id)}`, 'PUT', { categoryId: product.categoryId, name: product.name, subtitle: product.subtitle, price: product.price, status: nextStatus });
        await render(); notify('商品状态已更新。'); return;
      }
      if (action === 'add-cart') {
        if (!token) { showAuth(); return; }
        if (flow()) throw new Error('请先到购物车确认上一次结算或等待同步完成。');
        if (!selectedSku || currentProduct?.status !== 1) return;
        await api('/users/cart/items', 'POST', { skuId: selectedSku.skuId, quantity: 1 });
        notify('已加入购物车。'); return;
      }
      if (action === 'quantity' || action === 'remove-cart') {
        if (flow()) throw new Error('请先确认上一次结算结果。');
        const row = cart.find(r => String(r.id) === button.dataset.id);
        if (!row) return;
        if (action === 'remove-cart') await api(`/users/cart/items/${pathId(row.id)}`, 'DELETE');
        else await api(`/users/cart/items/${pathId(row.id)}`, 'PUT', { quantity: Math.max(1, Math.min(99, row.quantity + Number(button.dataset.step))) });
        await render(); return;
      }
      if (action === 'delete-address') {
        if (flow()?.requestKey) throw new Error('结算结果待确认，暂时不能删除地址。');
        if (await confirmAction('删除收货地址', '删除后，该地址将不再用于新的订单。历史订单中的地址快照会保留。')) {
          await api(`/users/addresses/${pathId(button.dataset.id)}`, 'DELETE'); await render();
        }
        return;
      }
      if (action === 'checkout') { await checkout(); return; }
      if (action === 'order-action') {
        const order = currentOrder;
        const code = button.dataset.code;
        const operation = order?.actions.find(a => a.code === code);
        const endpoints = { CANCEL: 'cancel', MOCK_PAY: 'pay', MOCK_SHIP: 'ship', MOCK_COMPLETE: 'complete' };
        if (!operation || !endpoints[code]) return;
        if (await confirmAction(operation.label, `订单 ${order.orderNo}，金额 ${money(order.totalAmount)}。${code === 'CANCEL' ? '取消后将释放预占库存。' : '此操作仅模拟状态变化，不产生真实扣款或物流。'}`)) {
          await api(`/orders/${pathId(order.orderNo)}/${endpoints[code]}`, 'POST');
          await render(); notify(`${operation.label}已完成。`);
        }
      }
    }, button);
  });
  document.addEventListener('change', event => {
    const checkbox = event.target.closest('[data-select]');
    if (!checkbox) return;
    if (busy || flow()) { checkbox.checked = !checkbox.checked; return; }
    work(async () => {
      try { await api(`/users/cart/items/${pathId(checkbox.dataset.select)}/selected?selected=${checkbox.checked}`, 'PUT'); }
      finally { await render(); }
    }, checkbox);
  });
  document.addEventListener('submit', event => {
    const form = event.target;
    if (!['global-search-form', 'search-form', 'auth-form', 'address-form', 'admin-search-form', 'admin-product-form', 'admin-category-form', 'admin-stock-form'].includes(form.id)) return;
    event.preventDefault();
    const values = Object.fromEntries(new FormData(form));
    work(async () => {
      if (form.id === 'search-form' || form.id === 'global-search-form') { go(`catalog?q=${encodeURIComponent(values.q.trim())}`); return; }
      if (form.id.startsWith('admin-')) {
        if (!isAdmin()) throw new Error('请使用管理员账号。');
        if (form.id === 'admin-search-form') { go(`admin?q=${encodeURIComponent(values.q.trim())}&status=${encodeURIComponent(values.status)}`); return; }
        if (form.id === 'admin-product-form') {
          const product = adminProduct;
          const request = { ...values, name: values.name.trim(), price: values.price, status: Number(values.status) };
          if (!request.name) throw new Error('请填写商品名称。');
          const saved = await api(product ? `/products/${pathId(product.id)}` : '/products', product ? 'PUT' : 'POST', request);
          notify(product ? '商品已保存。' : '商品已创建，请补充库存。');
          go(`admin?edit=${pathId(saved.id)}`); return;
        }
        if (form.id === 'admin-category-form') {
          const name = values.name.trim();
          if (!name) throw new Error('请填写分类名称。');
          if (adminCategories.some(c => c.name === name)) throw new Error('该分类已存在，请直接选择。');
          await api('/categories', 'POST', { name, parentId: 0, status: 1, sortOrder: 0 });
          await render(); notify('分类已创建，可在商品表单中选择。'); return;
        }
        if (form.id === 'admin-stock-form') {
          const product = adminProduct;
          const sku = adminSkus.find(s => String(s.skuId) === values.skuId);
          const quantity = Number(values.quantity);
          if (!product || !sku || !Number.isInteger(quantity) || quantity < 1 || quantity > 1000000) throw new Error('请选择规格，并输入 1–1000000 的整数数量。');
          if (!await confirmAction('确认增加库存', `${product.name} / ${sku.skuName}，本次增加 ${quantity} 件。`)) return;
          try {
            const stock = await api('/inventory', 'POST', { productId: product.id, skuId: sku.skuId, quantity });
            await render(); notify(`补库完成，该规格可用库存 ${stock.availableStock} 件。`);
          } catch (error) {
            await render();
            throw new Error(`${error.message} 补库结果不明确时，请先刷新库存确认，避免重复增加。`);
          }
          return;
        }
      }
      if (form.id === 'auth-form') {
        $('#auth-error').textContent = '';
        try {
          if (registering) {
            await api('/users/register', 'POST', values, false);
            registering = false;
            $('#nickname-field').hidden = true; $('#nickname-field input').required = false;
            $('#auth-title').textContent = '注册成功'; $('#auth-submit').textContent = '登录';
            $('#auth-toggle').textContent = '还没有账号？注册一个';
            $('#auth-form [name=password]').minLength = 1;
            $('#auth-form [name=password]').autocomplete = 'current-password';
          }
          const login = await api('/auth/login', 'POST', { username: values.username, password: values.password }, false);
          token = login.accessToken; username = values.username;
          sessionStorage.setItem('nova.token', token); sessionStorage.setItem('nova.username', username);
          form.reset(); $('#auth-dialog').close(); account(); await render();
        } catch (error) { $('#auth-error').textContent = error.message; }
        return;
      }
      if (form.id === 'address-form') {
        await api('/users/addresses', 'POST', { ...values, isDefault: values.isDefault === 'on' });
        notify('收货地址已保存。'); await render();
      }
    }, form.querySelector('[type=submit]'));
  });
  document.addEventListener('error', event => {
    if (event.target instanceof HTMLImageElement) event.target.parentElement.innerHTML = art;
  }, true);
  window.addEventListener('hashchange', render);
  account(); render();
})();

