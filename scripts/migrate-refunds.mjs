// Additive local migration. Take a full backup before any schema change.
import { spawnSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
if (!process.argv.includes('--apply')) {
  console.log('Run node scripts/migrate-refunds.mjs --apply [--returns] to back up the local mall database and apply 011 [and 012]. Stop order/product writes first.');
  process.exit(0);
}
function docker(args, input) {
  const result = spawnSync('docker', args, { input, windowsHide: true, maxBuffer: 128 * 1024 * 1024 });
  if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr.toString());
  return result.stdout;
}
mkdirSync(resolve(root, 'logs'), { recursive: true });
const backup = resolve(root, 'logs', `pre-refunds-${new Date().toISOString().replace(/[:.]/g, '-')}.sql`);
const dump = docker(['exec', 'novamall-mysql', 'sh', '-c',
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF mall']);
if (dump.length < 100) throw new Error('Backup unexpectedly empty; migration stopped.');
writeFileSync(backup, dump, { flag: 'wx' });
docker(['exec', '-i', 'novamall-mysql', 'sh', '-c',
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 mall'],
  readFileSync(resolve(root, 'database/init/011-refunds.sql')));
console.log(`Migration 011 applied. Backup: ${backup}`);
if(process.argv.includes('--returns')) {
  docker(['exec','-i','novamall-mysql','sh','-c','exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 mall'],
    readFileSync(resolve(root,'database/init/012-return-refunds.sql')));
  console.log('Migration 012 applied.');
}
