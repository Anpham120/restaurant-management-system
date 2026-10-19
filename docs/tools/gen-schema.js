#!/usr/bin/env node
// Sinh lược đồ PostgreSQL của phân hệ 2–5 từ ERD tổng thể và các khối SQL trong README phân hệ.
//
// Cách chạy (từ thư mục gốc dự án, Node.js 18+, không cần cài thêm gói):
//   node docs/tools/gen-schema.js          # ghi docs/database/schema-phan-he-2-5.sql
//   node docs/tools/gen-schema.js --check  # chỉ so với tệp hiện có; mã thoát 1 nếu tệp cũ hoặc nguồn có lỗi
// check-docs.js gọi generate() để báo lỗi khi tệp SQL không còn khớp ERD và README.
//
// Nguồn và cách dịch:
//   - Bảng, cột, kiểu, NOT NULL (*), PK, UK, FK lấy từ docs/erd-tong-the.puml; bảng thuộc phân hệ nào lấy theo ERD phân hệ
//     vẽ bảng đó viền liền. Phân hệ 1 có lược đồ viết tay (01-quan-ly-nhan-vien/schema-postgresql.sql), không sinh ở đây.
//   - Ghi chú dưới đường chấm: UK (...) [NULLS NOT DISTINCT], PARTIAL UK (...) WHERE ..., FK (...) → bảng (...),
//     CHECK cột IN (...), CHECK cột, cột >= số, CHECK <biểu thức SQL>; ghi chú CHECK viết bằng lời dịch theo bảng LOI.
//     Ghi chú TRIGGER: phải có CREATE TRIGGER trên bảng đó trong README phân hệ sở hữu.
//   - README: câu ALTER TABLE ... ADD CONSTRAINT và CREATE UNIQUE INDEX phải trùng định nghĩa một ràng buộc sinh từ ERD
//     (lấy tên đặt trong README); CREATE FUNCTION, CREATE TRIGGER chép nguyên văn.
'use strict';
const fs = require('fs');
const path = require('path');

const OUT_REL = 'database/schema-phan-he-2-5.sql';

// Ghi chú CHECK viết bằng lời trong ERD → [hậu tố tên ràng buộc, biểu thức SQL]
const LOI = {
  'CHECK change_amount = GREATEST(cash_received - amount_due, 0)': ['change', 'change_amount = GREATEST(cash_received - amount_due, 0)'],
  'CHECK change_amount có khi và chỉ khi có cash_received': ['change_presence', '(cash_received IS NULL) = (change_amount IS NULL)'],
  'CHECK payment_reference chỉ khi QR; bắt buộc khi QR ở WAITING_TRANSFER, PAID': ['payment_reference',
    "(payment_reference IS NULL OR method = 'QR') AND (method <> 'QR' OR status NOT IN ('WAITING_TRANSFER', 'PAID') OR payment_reference IS NOT NULL)"],
  'CHECK QR: không có cash_received, cash_counted, change_amount': ['qr_no_cash',
    "method <> 'QR' OR (cash_received IS NULL AND cash_counted IS NULL AND change_amount IS NULL)"],
  'CHECK CASH đã PAID: bắt buộc cash_counted = cash_received >= amount_due': ['cash_paid',
    "method <> 'CASH' OR status <> 'PAID' OR (cash_received IS NOT NULL AND cash_counted IS NOT NULL AND cash_counted = cash_received AND cash_received >= amount_due)"],
  'CHECK CANCELLED: bắt buộc cancel_reason': ['cancel_reason', "status <> 'CANCELLED' OR cancel_reason IS NOT NULL"],
  'CHECK payment_webhook_event_id bắt buộc khi QR, trống khi CASH': ['qr_webhook', "(method = 'QR') = (payment_webhook_event_id IS NOT NULL)"],
  'CHECK PROCESSED: bắt buộc payment_request_id': ['processed_request', "processing_result <> 'PROCESSED' OR payment_request_id IS NOT NULL"],
  'CHECK CONFIRMED: bắt buộc confirmed_by_id, confirmed_at': ['confirmed',
    "status <> 'CONFIRMED' OR (confirmed_by_id IS NOT NULL AND confirmed_at IS NOT NULL)"],
  'CHECK NOT is_usable: bắt buộc unusable_reason': ['unusable_reason', 'is_usable OR unusable_reason IS NOT NULL'],
  'CHECK nguồn khớp movement_type': ['source',
    "(movement_type = 'CONSUMPTION' AND order_item_id IS NOT NULL AND stock_count_item_id IS NULL AND quantity_delta < 0)"
    + " OR (movement_type = 'ADJUSTMENT' AND stock_count_item_id IS NOT NULL AND order_item_id IS NULL)"
    + " OR (movement_type = 'RECEIPT' AND order_item_id IS NULL AND stock_count_item_id IS NULL AND quantity_delta > 0)"],
  'CHECK RELEASED: bắt buộc release_reason': ['released', "status <> 'RELEASED' OR release_reason IS NOT NULL"],
  'CHECK CONSUMED, RELEASED: bắt buộc closed_at': ['closed_at', "status NOT IN ('CONSUMED', 'RELEASED') OR closed_at IS NOT NULL"],
  'CHECK fund_source = COUNTER chỉ khi payment_method = CASH': ['counter_cash', "fund_source <> 'COUNTER' OR payment_method = 'CASH'"],
  'CHECK stock_receipt_id chỉ khi category = INGREDIENT': ['receipt_ingredient', "stock_receipt_id IS NULL OR category = 'INGREDIENT'"],
  'CHECK VOIDED: bắt buộc void_reason, voided_by_id, voided_at': ['voided',
    "status <> 'VOIDED' OR (void_reason IS NOT NULL AND voided_by_id IS NOT NULL AND voided_at IS NOT NULL)"],
  'CHECK difference <> 0: bắt buộc reason': ['reason', 'difference = 0 OR reason IS NOT NULL'],
  'CHECK version > 1: bắt buộc revision_reason': ['revision_reason', 'version = 1 OR revision_reason IS NOT NULL'],
};

const read = (f) => fs.readFileSync(f, 'utf8').replace(/\r/g, '');
const plural = (s) => (s.endsWith('y') ? s.slice(0, -1) + 'ies' : /(ch|sh|x|s)$/.test(s) ? s + 'es' : s + 's');
const isRoleFk = (c) => /_by_id$/.test(c) || ['waiter_id', 'cashier_id', 'actor_id'].includes(c);
const short = (c) => c.replace(/_id$/, '');
const splitCols = (s) => s.split(',').map((x) => x.trim());
const norm = (s) => s.replace(/\s+/g, ' ').replace(/\( /g, '(').replace(/ \)/g, ')').trim().toLowerCase();
// Giá trị trạng thái IN HOA sau = hoặc <> thành chuỗi SQL; bỏ qua từ khóa và tên hàm (ROUND(, GREATEST()
const quoteEnums = (e) => e.replace(/(=|<>)\s*(?!(?:AND|OR|NOT|NULL|TRUE|FALSE)\b)([A-Z][A-Z_]+)\b(?!\s*\()/g, "$1 '$2'");
const sqlType = (t) => {
  const m = t.match(/^(varchar|decimal)\((\d+(?:,\d+)?)\)$/);
  if (m) return `${m[1].toUpperCase()}(${m[2]})`;
  return { bigint: 'BIGINT', int: 'INTEGER', integer: 'INTEGER', boolean: 'BOOLEAN', date: 'DATE', time: 'TIME', timestamptz: 'TIMESTAMPTZ', text: 'TEXT' }[t];
};

function parseEntities(src) {
  const out = [];
  for (const m of src.matchAll(/^entity\s+"([^"]+)"\s+as\s+\w+([^{\n]*)\{\n([\s\S]*?)\n\}/gm)) {
    const cols = [];
    const notes = [];
    let inNotes = false;
    for (const raw of m[3].split('\n')) {
      const line = raw.trim();
      if (!line || line === '--') continue;
      if (line === '..') { inNotes = true; continue; }
      if (inNotes) { notes.push(line); continue; }
      const c = line.match(/^(\*\s*)?([a-z_][a-z0-9_]*)\s*:\s*([a-z]+(?:\(\d+(?:,\d+)?\))?)\s*(?:<<([^>]+)>>)?$/);
      if (c) cols.push({ name: c[2], required: !!c[1], type: c[3], markers: (c[4] || '').split(',').map((s) => s.trim()).filter(Boolean) });
    }
    out.push({ name: m[1], dashed: /line\.dashed/.test(m[2]), cols, notes });
  }
  return out;
}

// Tách câu lệnh SQL: tôn trọng chuỗi '...', thân hàm $$...$$ và chú thích -- (dấu ; trong đó không tách câu)
function splitSql(text) {
  const out = [];
  let cur = '';
  let i = 0;
  while (i < text.length) {
    if (text.startsWith('--', i)) { const e = text.indexOf('\n', i); const end = e < 0 ? text.length : e; cur += text.slice(i, end); i = end; continue; }
    if (text[i] === "'") { const e = text.indexOf("'", i + 1); const end = e < 0 ? text.length : e + 1; cur += text.slice(i, end); i = end; continue; }
    const d = text.slice(i).match(/^\$(\w*)\$/);
    if (d) { const e = text.indexOf(d[0], i + d[0].length); const end = e < 0 ? text.length : e + d[0].length; cur += text.slice(i, end); i = end; continue; }
    if (text[i] === ';') { if (cur.trim()) out.push(cur.trim() + ';'); cur = ''; i++; continue; }
    cur += text[i++];
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}
const stripComments = (s) => s.split('\n').filter((l) => !l.trim().startsWith('--')).join('\n').trim();

function generate(docs) {
  const problems = [];
  const moduleDirs = fs.readdirSync(docs).filter((d) => /^\d\d-/.test(d) && fs.existsSync(path.join(docs, d, 'erd.puml'))).sort();
  const owner = new Map();
  for (const mod of moduleDirs) for (const e of parseEntities(read(path.join(docs, mod, 'erd.puml')))) if (!e.dashed) owner.set(e.name, mod);
  const titles = {};
  for (const m of read(path.join(docs, 'README.md')).matchAll(/^\| (\d) \| ([^|]+?) \| \[(\d\d-[a-z-]+)\]/gm)) titles[m[3]] = `Phân hệ ${m[1]} – ${m[2]}`;
  const all = parseEntities(read(path.join(docs, 'erd-tong-the.puml')));
  const known = new Set(all.map((e) => e.name));
  const gen = all.filter((e) => owner.has(e.name) && !owner.get(e.name).startsWith('01-'));
  gen.sort((a, b) => owner.get(a.name).localeCompare(owner.get(b.name)));

  const cons = []; // {table, kind: pk|uq|ck|fk|ux, name, def | cols, where}
  const triggerNotes = new Map();
  for (const e of gen) {
    const t = e.name;
    const add = (kind, suffix, def, extra) => cons.push({ table: t, kind, name: `${kind}_${t}${suffix ? '_' + suffix : ''}`, def, ...extra });
    add('pk', '', 'PRIMARY KEY (id)');
    for (const c of e.cols) if (c.markers.includes('UK')) add('uq', short(c.name), `UNIQUE (${c.name})`);
    for (const n of e.notes) {
      let m;
      if ((m = n.match(/^UK \(([a-z_, ]+)\)( NULLS NOT DISTINCT)?$/))) add('uq', splitCols(m[1]).map(short).join('_'), `UNIQUE${m[2] || ''} (${m[1]})`);
      else if ((m = n.match(/^PARTIAL UK \(([a-z_, ]+)\) WHERE (.+)$/))) add('ux', splitCols(m[1]).map(short).join('_'), null, { cols: m[1], where: quoteEnums(m[2]) });
      else if ((m = n.match(/^FK \(([a-z_, ]+)\) → (\w+) \(([a-z_, ]+)\)$/))) {
        add('fk', splitCols(m[1]).map(short).join('_'), `FOREIGN KEY (${m[1]}) REFERENCES ${m[2]} (${m[3]}) ON DELETE RESTRICT`);
      } else if ((m = n.match(/^CHECK ([a-z_]+(?:, [a-z_]+)*) IN \(([A-Z][A-Z0-9_]*(?:, [A-Z][A-Z0-9_]*)*)\)$/))) {
        const vals = splitCols(m[2]).map((v) => `'${v}'`).join(', ');
        for (const c of splitCols(m[1])) add('ck', c, `CHECK (${c} IN (${vals}))`);
      } else if ((m = n.match(/^CHECK ([a-z_]+(?:, [a-z_]+)+) (>=|>) (-?\d+)$/))) {
        for (const c of splitCols(m[1])) add('ck', c, `CHECK (${c} ${m[2]} ${m[3]})`);
      } else if (LOI[n]) add('ck', LOI[n][0], `CHECK (${LOI[n][1]})`);
      else if ((m = n.match(/^CHECK ([A-Za-z_0-9 ()*+\-<>=,.]+)$/))) add('ck', m[1].match(/[a-z_][a-z0-9_]*/)[0], `CHECK (${quoteEnums(m[1])})`);
      else if (/^TRIGGER:/.test(n)) triggerNotes.set(t, (triggerNotes.get(t) || 0) + 1);
      else problems.push(`${t}: chưa dịch được ghi chú "${n}" sang SQL (thêm vào bảng LOI của gen-schema.js)`);
    }
    for (const c of e.cols) {
      if (!c.markers.includes('FK')) continue;
      let p = plural(short(c.name));
      if (!known.has(p) && isRoleFk(c.name)) p = 'employees';
      if (!known.has(p)) { problems.push(`${t}.${c.name}: không xác định được bảng đích`); continue; }
      add('fk', short(c.name), `FOREIGN KEY (${c.name}) REFERENCES ${p} (id) ON DELETE RESTRICT`);
    }
    for (const c of e.cols) if (!sqlType(c.type)) problems.push(`${t}.${c.name}: kiểu ${c.type} chưa có ánh xạ SQL`);
  }

  // README: dùng tên ràng buộc đặt trong README, chép hàm và trigger
  const routines = [];
  const readmeTriggers = new Map();
  for (const mod of moduleDirs.filter((d) => !d.startsWith('01-'))) {
    const md = read(path.join(docs, mod, 'README.md'));
    for (const block of md.matchAll(/```sql\n([\s\S]*?)```/g)) {
      for (const stmt of splitSql(block[1])) {
        const body = stripComments(stmt);
        let m;
        if ((m = body.match(/^ALTER TABLE\s+(\w+)\s+ADD CONSTRAINT\s+(\w+)\s+([\s\S]+?);?$/))) {
          const c = cons.find((x) => x.table === m[1] && x.def && norm(x.def) === norm(m[3]));
          if (!c) problems.push(`${mod}/README.md: ${m[2]} không khớp ràng buộc nào sinh từ ERD: ${m[3].replace(/\s+/g, ' ')}`);
          else c.name = m[2];
        } else if ((m = body.match(/^CREATE UNIQUE INDEX\s+(\w+)\s+ON\s+(\w+)\s*\(([^)]+)\)\s+WHERE\s+([\s\S]+?);?$/))) {
          const c = cons.find((x) => x.kind === 'ux' && x.table === m[2] && norm(x.cols) === norm(m[3]) && norm(x.where) === norm(m[4]));
          if (!c) problems.push(`${mod}/README.md: chỉ mục ${m[1]} không khớp PARTIAL UK nào trong ERD`);
          else c.name = m[1];
        } else if (/^CREATE (OR REPLACE )?(FUNCTION|TRIGGER)\b/.test(body)) {
          routines.push({ mod, sql: stmt });
          if ((m = body.match(/^CREATE TRIGGER\s+\w+\s+[\s\S]*?\bON\s+(\w+)/))) readmeTriggers.set(m[1], (readmeTriggers.get(m[1]) || 0) + 1);
        } else if (/^(CREATE|ALTER|DROP)\b/.test(body)) problems.push(`${mod}/README.md: câu DDL chưa hỗ trợ: ${body.split('\n')[0]}`);
      }
    }
  }
  for (const [t, n] of triggerNotes) if ((readmeTriggers.get(t) || 0) !== n) problems.push(`${t}: ERD ghi ${n} TRIGGER, README phân hệ có ${readmeTriggers.get(t) || 0} CREATE TRIGGER`);
  for (const [t] of readmeTriggers) if (!triggerNotes.has(t)) problems.push(`${t}: README có CREATE TRIGGER nhưng ERD không ghi TRIGGER`);
  const names = new Map();
  for (const c of cons) {
    if (c.name.length > 63) problems.push(`tên ${c.name} dài quá 63 ký tự`);
    if (names.has(c.name)) problems.push(`trùng tên ràng buộc ${c.name}`);
    names.set(c.name, c);
  }

  // Ghép tệp SQL
  const L = [];
  L.push(`-- Phân hệ 2–5: lược đồ PostgreSQL (${gen.length} bảng). TỆP SINH TỰ ĐỘNG – không sửa tay.`);
  L.push('-- Nguồn: docs/erd-tong-the.puml (bảng, cột, khóa, ghi chú ràng buộc) và khối SQL trong README phân hệ 2–5');
  L.push('-- (tên ràng buộc, hàm, trigger). Sinh lại: node docs/tools/gen-schema.js; check-docs.js báo lỗi khi tệp này cũ.');
  L.push('-- Chạy sau 01-quan-ly-nhan-vien/schema-postgresql.sql (bảng employees). Cần PostgreSQL 15 trở lên (UNIQUE NULLS NOT DISTINCT).');
  L.push('-- Quy ước: trạng thái VARCHAR + CHECK (JPA @Enumerated(EnumType.STRING)); mọi khóa ngoại ON DELETE RESTRICT.');
  let mod = null;
  for (const e of gen) {
    if (owner.get(e.name) !== mod) { mod = owner.get(e.name); L.push('', `-- ===== ${titles[mod] || mod} =====`); }
    L.push('', `CREATE TABLE ${e.name} (`);
    const items = e.cols.map((c) => `    ${c.name} ${sqlType(c.type)}${c.markers.includes('PK') ? ' GENERATED BY DEFAULT AS IDENTITY' : c.required ? ' NOT NULL' : ''}`);
    for (const c of cons.filter((x) => x.table === e.name && ['pk', 'uq', 'ck'].includes(x.kind))) items.push(`    CONSTRAINT ${c.name} ${c.def}`);
    L.push(items.join(',\n'), ');');
  }
  L.push('', '-- ===== Khóa ngoại (bảng đích có thể tạo sau nên thêm sau cùng) =====');
  for (const c of cons.filter((x) => x.kind === 'fk')) L.push(`ALTER TABLE ${c.table} ADD CONSTRAINT ${c.name}\n    ${c.def};`);
  L.push('', '-- ===== Chỉ mục duy nhất một phần (PARTIAL UK trong ERD) =====');
  for (const c of cons.filter((x) => x.kind === 'ux')) L.push(`CREATE UNIQUE INDEX ${c.name} ON ${c.table} (${c.cols}) WHERE ${c.where};`);
  L.push('', '-- ===== Hàm và trigger (chép nguyên văn từ README phân hệ) =====');
  let src = null;
  for (const r of routines) {
    if (r.mod !== src) { src = r.mod; L.push('', `-- Nguồn: ${r.mod}/README.md`); }
    L.push(r.sql);
  }
  return { sql: L.join('\n') + '\n', problems, file: path.join(docs, OUT_REL), tables: gen.map((e) => e.name), constraints: cons };
}

module.exports = { generate, splitSql, OUT_REL };

if (require.main === module) {
  const docs = path.resolve(__dirname, '..');
  const { sql, problems, file, tables, constraints } = generate(docs);
  for (const p of problems) console.error('LỖI: ' + p);
  const rel = path.relative(process.cwd(), file);
  if (process.argv.includes('--check')) {
    const cur = fs.existsSync(file) ? read(file) : null;
    if (cur !== sql) console.error(`LỖI: ${rel} không khớp ERD và README, chạy: node docs/tools/gen-schema.js`);
    process.exit(problems.length || cur !== sql ? 1 : 0);
  }
  if (problems.length) process.exit(1);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, sql, 'utf8');
  const count = (k) => constraints.filter((c) => c.kind === k).length;
  console.log(`Đã ghi ${rel}: ${tables.length} bảng, ${count('fk')} khóa ngoại, ${count('uq')} UNIQUE, ${count('ux')} chỉ mục duy nhất một phần, ${count('ck')} CHECK`);
}
