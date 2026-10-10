// Node 20+. Run from the checkout with local services running.
// Default: import/update the named catalog only. --replace-existing also soft-deletes
// other products and their now-unused categories, after a mandatory database backup.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const gateway = (process.env.NOVAMALL_GATEWAY || 'http://127.0.0.1:18080').replace(/\/$/, '');
if (!['127.0.0.1', 'localhost'].includes(new URL(gateway).hostname)) {
  throw new Error('This data replacement script is for a local demo gateway only.');
}
const replace = process.argv.includes('--replace-existing');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'database/seed/demo-catalog.json'), 'utf8'));
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const backupDir = path.join(root, 'logs', `catalog-backup-${stamp}`);
fs.mkdirSync(backupDir, { recursive: true });
let token = '';

// Keep Snowflake identifiers exact, including JSON numeric IDs from legacy endpoints.
function parseJson(text) {
  return JSON.parse(text.replace(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g, value => {
    if (/^-?\d+$/.test(value) && (BigInt(value) > 9007199254740991n || BigInt(value) < -9007199254740991n)) return `"${value}"`;
    return value;
  }));
}
async function api(method, endpoint, body) {
  const response = await fetch(`${gateway}/api${endpoint}`, {
    method, signal: AbortSignal.timeout(30000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const payload = parseJson(await response.text());
  if (!response.ok || !payload.success) throw new Error(`${method} ${endpoint}: ${response.status} ${payload.message || 'failed'}`);
  return payload.data;
}
async function allProducts() {
  const items = [];
  for (let page = 1; ; page++) {
    const result = await api('GET', `/products?page=${page}&size=100`);
    items.push(...result.items);
    if (items.length >= result.total) return items;
    if (!result.items.length) throw new Error('Product pagination ended before total was reached.');
  }
}
function dockerMysql(command) {
  const result = spawnSync('docker', ['exec', 'novamall-mysql', 'sh', '-c', command], {
    windowsHide: true, timeout: 60000, maxBuffer: 64 * 1024 * 1024
  });
  if (result.error || result.status !== 0) throw new Error('Database backup/read failed; no automatic retry. Check the local MySQL container.');
  return result.stdout;
}
function orderCounts() {
  return dockerMysql('exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B mall -e "SELECT COUNT(*) FROM mall_order; SELECT COUNT(*) FROM mall_order_item;"').toString('utf8');
}
function journal(message) {
  fs.appendFileSync(path.join(backupDir, 'operations.log'), `${new Date().toISOString()} ${message}\n`);
}

if (!manifest.products.length || new Set(manifest.products.map(p => p.name)).size !== manifest.products.length) {
  throw new Error('Catalog must contain uniquely named products.');
}
for (const p of manifest.products) {
  if (!manifest.categories.includes(p.category) || !Number.isFinite(p.price) || p.price < 0
      || !Number.isInteger(p.initialStock) || p.initialStock <= 0 || !p.coverImageUrl.startsWith('https://')) {
    throw new Error(`Invalid catalog entry: ${p.name}`);
  }
}
const login = await api('POST', '/auth/login', {
  username: process.env.NOVAMALL_ADMIN_USERNAME || 'admin',
  password: process.env.NOVAMALL_ADMIN_PASSWORD || 'AdminPass123!'
});
token = login.accessToken;
const existing = await allProducts();
const categories = (await api('GET', '/categories')).items;
const beforeOrders = orderCounts();
const dump = dockerMysql('exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --default-character-set=utf8mb4 mall mall_product mall_product_sku mall_category mall_inventory');
if (!dump.length) throw new Error('Empty backup; refusing to mutate the catalog.');
fs.writeFileSync(path.join(backupDir, 'catalog.sql'), dump);
fs.writeFileSync(path.join(backupDir, 'before.json'), JSON.stringify({ existing, categories, orderCounts: beforeOrders }, null, 2));
console.log(`Backup saved: ${backupDir}`);

const categoryIds = new Map();
for (const [index, name] of manifest.categories.entries()) {
  const found = categories.find(c => c.name === name);
  const data = { parentId: 0, name, status: 1, sortOrder: index * 10 };
  const saved = await api(found ? 'PUT' : 'POST', found ? `/categories/${found.id}` : '/categories', data);
  categoryIds.set(name, String(saved.id));
  journal(`category ${saved.id} ${name}`);
}
const imported = [];
// Create secondary products first, making phones among the newest recommendations.
for (const p of [...manifest.products].reverse()) {
  const found = existing.find(item => item.name === p.name);
  const body = { categoryId: categoryIds.get(p.category), name: p.name, subtitle: p.subtitle,
    price: p.price, status: 1, coverImageUrl: p.coverImageUrl, description: p.description };
  const saved = await api(found ? 'PUT' : 'POST', found ? `/products/${found.id}` : '/products', body);
  if (saved.coverImageUrl !== p.coverImageUrl) throw new Error('Product service lacks image support. Rebuild/restart it before importing.');
  journal(`${found ? 'updated' : 'created'} product ${saved.id} ${p.name}`);
  const skus = await api('GET', `/products/${saved.id}/skus`);
  const sku = skus.find(s => s.skuCode === `P${saved.id}-DEFAULT`);
  if (!sku || sku.coverImageUrl !== p.coverImageUrl) throw new Error(`Default SKU image missing: ${p.name}`);
  let stock;
  try { stock = await api('GET', `/inventory/skus/${sku.skuId}`); }
  catch (error) {
    if (!error.message.includes('inventory not found for SKU:')) throw error;
    // A confirmed missing row is the only reason to initialize stock. Never refill
    // an existing row or blindly retry an ambiguous inventory write.
    stock = await api('POST', '/inventory', { productId: saved.id, skuId: sku.skuId, quantity: p.initialStock });
    journal(`initialized stock ${sku.skuId} ${p.initialStock}`);
  }
  imported.push({ id: String(saved.id), name: p.name, category: p.category, price: saved.price,
    skuId: sku.skuId, availableStock: stock.availableStock, coverImageUrl: saved.coverImageUrl });
  console.log(`Ready: ${p.name} / ${p.category} / CNY ${saved.price} / stock ${stock.availableStock}`);
}

// Remove the old catalog only after every replacement and stock row is ready.
const keepIds = new Set(imported.map(p => p.id));
let removed = 0;
let removedCategories = 0;
if (replace) {
  for (const p of existing.filter(p => !keepIds.has(String(p.id)))) {
    await api('DELETE', `/products/${p.id}`);
    removed++;
    journal(`soft-deleted product ${p.id} ${p.name}`);
  }
  const current = await allProducts();
  const usedCategories = new Set(current.map(p => String(p.categoryId)));
  let obsolete = categories.filter(c => !usedCategories.has(String(c.id)) && ![...categoryIds.values()].includes(String(c.id)));
  while (obsolete.length) {
    const leaves = obsolete.filter(c => !obsolete.some(child => String(child.parentId) === String(c.id)));
    if (!leaves.length) throw new Error('Unexpected category cycle; cleanup stopped.');
    for (const c of leaves) {
      await api('DELETE', `/categories/${c.id}`);
      removedCategories++;
      journal(`soft-deleted category ${c.id} ${c.name}`);
    }
    const leafIds = new Set(leaves.map(c => String(c.id)));
    obsolete = obsolete.filter(c => !leafIds.has(String(c.id)));
  }
}
const after = await allProducts();
if (replace && (after.length !== imported.length || after.some(p => !keepIds.has(String(p.id))))) {
  throw new Error('Catalog differs from the imported manifest; inspect the operation journal.');
}
const afterOrders = orderCounts();
const report = { imported, removed, removedCategories, orderCountsUnchanged: beforeOrders === afterOrders };
fs.writeFileSync(path.join(backupDir, 'result.json'), JSON.stringify(report, null, 2));
console.log(`Completed: ${imported.length} catalog products; ${removed} old products and ${removedCategories} old categories removed. Order counts unchanged: ${report.orderCountsUnchanged}.`);
