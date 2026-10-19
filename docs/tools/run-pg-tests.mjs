#!/usr/bin/env node
// Chạy bộ kiểm thử PostgreSQL của tài liệu trên PGlite (PostgreSQL biên dịch WebAssembly, chạy trong Node.js).
//
// Cách chạy (từ thư mục gốc dự án, Node.js 18+):
//   node docs/tools/run-pg-tests.mjs --pglite <thư mục gói @electric-sql/pglite>
//   (hoặc đặt biến môi trường PGLITE_DIR; gói đã cài trong node_modules thì bỏ tham số được)
//   --verbose: in mọi ca; mặc định chỉ in ca thất bại và tổng kết từng bộ.
// Các bộ, mỗi bộ một cơ sở dữ liệu mới:
//   - Phân hệ 1: 01-quan-ly-nhan-vien/schema-postgresql.sql + test-postgresql.sql, lặp ở ba múi giờ phiên
//     (mặc định của máy, UTC, America/New_York) để kiểm tra không phụ thuộc múi giờ.
//   - Liên phân hệ: lược đồ phân hệ 1 + database/schema-phan-he-2-5.sql + database/test-lien-phan-he.sql.
// Tệp kiểm thử viết cho psql (chạy được bằng psql -v ON_ERROR_STOP=1 -f ...). Công cụ này làm thay hai việc của psql
// mà tệp dùng: dòng "\set tên giá_trị" và thay ":tên" bằng giá trị (ngoài chuỗi, chú thích, thân $$).
// Dòng "-- @@CHECK@@" tách phần chạy ca kiểm thử với phần kiểm tra cuối (DO ... RAISE khi có ca thất bại).
// Mã thoát 0 khi mọi ca đạt; 1 khi có ca thất bại hoặc lỗi; 2 khi không nạp được PGlite.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DOCS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const verbose = args.includes('--verbose');
const pgliteArg = args.includes('--pglite') ? args[args.indexOf('--pglite') + 1] : process.env.PGLITE_DIR;

async function loadPglite() {
  try {
    if (pgliteArg) {
      const base = fs.existsSync(path.join(pgliteArg, 'dist', 'index.js')) ? path.join(pgliteArg, 'dist') : pgliteArg;
      const { PGlite } = await import(pathToFileURL(path.join(base, 'index.js')).href);
      const { btree_gist } = await import(pathToFileURL(path.join(base, 'contrib', 'btree_gist.js')).href);
      return { PGlite, btree_gist };
    }
    const { PGlite } = await import('@electric-sql/pglite');
    const { btree_gist } = await import('@electric-sql/pglite/contrib/btree_gist');
    return { PGlite, btree_gist };
  } catch (e) {
    console.error('Không nạp được PGlite (' + e.message + ').');
    console.error('Cài gói @electric-sql/pglite ở một thư mục bất kỳ (npm install @electric-sql/pglite) rồi chạy:');
    console.error('  node docs/tools/run-pg-tests.mjs --pglite <thư mục>/node_modules/@electric-sql/pglite');
    process.exit(2);
  }
}

// Làm thay psql: xử lý "\set tên giá_trị" và thay ":tên" ngoài chuỗi '...', "...", thân $tag$...$tag$ và chú thích.
function psqlVars(text) {
  const vars = {};
  let out = '';
  let i = 0;
  const lineStart = () => i === 0 || text[i - 1] === '\n';
  while (i < text.length) {
    if (lineStart() && text.startsWith('\\set ', i)) {
      const end = text.indexOf('\n', i) < 0 ? text.length : text.indexOf('\n', i);
      const m = text.slice(i + 5, end).trim().match(/^(\w+)\s+(.*)$/);
      if (!m) throw new Error('không đọc được dòng ' + text.slice(i, end));
      let v = m[2].trim();
      if (v.startsWith("'")) v = v.slice(1, v.lastIndexOf("'")).replace(/''/g, "'");
      vars[m[1]] = v;
      i = end;
      continue;
    }
    if (text.startsWith('--', i)) { const e = text.indexOf('\n', i); const end = e < 0 ? text.length : e; out += text.slice(i, end); i = end; continue; }
    if (text.startsWith('/*', i)) { const e = text.indexOf('*/', i + 2); const end = e < 0 ? text.length : e + 2; out += text.slice(i, end); i = end; continue; }
    if (text[i] === "'" || text[i] === '"') { const q = text[i]; let j = i + 1; while (j < text.length && !(text[j] === q && text[j + 1] !== q)) j += text[j] === q ? 2 : 1; out += text.slice(i, j + 1); i = j + 1; continue; }
    const d = text.slice(i, i + 64).match(/^\$(\w*)\$/);
    if (d) { const e = text.indexOf(d[0], i + d[0].length); const end = e < 0 ? text.length : e + d[0].length; out += text.slice(i, end); i = end; continue; }
    if (text.startsWith('::', i)) { out += '::'; i += 2; continue; }
    const v = text[i] === ':' && text.slice(i + 1, i + 65).match(/^\w+/);
    if (v && Object.prototype.hasOwnProperty.call(vars, v[0])) { out += vars[v[0]]; i += 1 + v[0].length; continue; }
    out += text[i++];
  }
  return out;
}

const read = (f) => fs.readFileSync(path.join(DOCS, f), 'utf8').replace(/\r/g, '');
const SCHEMA1 = '01-quan-ly-nhan-vien/schema-postgresql.sql';
const SUITES = [
  ...['', 'UTC', 'America/New_York'].map((tz) => ({
    name: `Phân hệ 1, múi giờ phiên ${tz || 'mặc định'}`, schemas: [SCHEMA1], test: '01-quan-ly-nhan-vien/test-postgresql.sql', tz,
  })),
  { name: 'Liên phân hệ 1–5', schemas: [SCHEMA1, 'database/schema-phan-he-2-5.sql'], test: 'database/test-lien-phan-he.sql', tz: '' },
];

const { PGlite, btree_gist } = await loadPglite();
let failedSuites = 0;
for (const s of SUITES) {
  const pg = new PGlite({ extensions: { btree_gist } });
  await pg.waitReady;
  console.log(`\n== ${s.name} ==`);
  try {
    if (s.tz) await pg.exec(`SET TimeZone = '${s.tz}'`);
    const env = (await pg.query("SELECT version() AS v, current_setting('TimeZone') AS tz")).rows[0];
    console.log(`Máy chủ: ${env.v.split(' on ')[0]}; múi giờ phiên: ${env.tz}`);
    for (const f of s.schemas) await pg.exec(read(f));
    const c = (await pg.query(`SELECT
        (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE')::int AS bang,
        (SELECT count(*) FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype = 'f')::int AS fk,
        (SELECT count(*) FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype = 'u')::int AS uq,
        (SELECT count(*) FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype = 'c')::int AS ck,
        (SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexdef LIKE 'CREATE UNIQUE INDEX%WHERE%')::int AS ux,
        (SELECT count(*) FROM pg_trigger t JOIN pg_class r ON r.oid = t.tgrelid WHERE r.relnamespace = 'public'::regnamespace AND NOT t.tgisinternal)::int AS trg`)).rows[0];
    console.log(`Lược đồ: ${c.bang} bảng, ${c.fk} khóa ngoại, ${c.uq} UNIQUE, ${c.ux} chỉ mục duy nhất một phần, ${c.ck} CHECK, ${c.trg} trigger`);
    const full = psqlVars(read(s.test));
    const cut = full.indexOf('-- @@CHECK@@');
    const results = await pg.exec(cut < 0 ? full : full.slice(0, cut));
    let summary = null;
    for (const r of results) {
      const names = (r.fields || []).map((f) => f.name);
      if (names.includes('result')) {
        for (const row of r.rows) if (verbose || row.result !== 'PASS') console.log(`${row.result} ${row.case_no.padEnd(8)} ${row.descr} | mong đợi ${row.expected}, thực tế ${row.actual}`);
      } else if (names.includes('passed_count')) summary = r.rows[0];
    }
    if (cut >= 0) await pg.exec(full.slice(cut));
    console.log(`Kết quả: ${summary.passed_count}/${Number(summary.passed_count) + Number(summary.failed_count)} ca đạt`);
    if (Number(summary.failed_count)) failedSuites++;
  } catch (e) {
    console.log(`LỖI: ${e.message}${e.code ? ` (SQLSTATE ${e.code})` : ''}`);
    failedSuites++;
  }
  await pg.close();
}
console.log(failedSuites ? `\n${failedSuites} bộ có ca thất bại hoặc lỗi` : '\nMọi bộ kiểm thử đều đạt');
process.exit(failedSuites ? 1 : 0);
