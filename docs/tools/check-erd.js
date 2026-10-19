#!/usr/bin/env node
// Kiểm tra ERD PlantUML của Restaurant Core (RMS) và tài liệu đi kèm.
//
// Cách chạy (từ thư mục gốc dự án, cần Node.js 18+, không cần cài thêm gói):
//   node docs/tools/check-erd.js            # kiểm tra thư mục docs cạnh công cụ
//   node docs/tools/check-erd.js <docs>     # kiểm tra thư mục docs khác
// Mã thoát 1 khi có LỖI; CẢNH BÁO và THÔNG TIN không làm hỏng lệnh.
//
// Nội dung kiểm tra:
//   1. Sở hữu bảng: mỗi bảng vẽ viền liền ở đúng một ERD phân hệ (phân hệ sở hữu); ERD tổng thể đủ bảng,
//      định nghĩa giống hệt bản gốc; bảng phân hệ khác vẽ viền nét đứt, chỉ gồm cột có trong bản gốc.
//   2. Khóa: mọi bảng có id bigint PK; cột *_id là FK bigint, trỏ tới bảng có thật; FK ghép
//      "FK (a, b) → bảng (x, y)" trỏ tới PK/UK đúng cột, cùng kiểu; UK/PARTIAL UK dùng cột có thật.
//   3. Đường nối: mỗi đường chân chim phải ứng với một khóa ngoại thật (không nối giả); bản số đúng
//      NOT NULL / UNIQUE của cột khóa ngoại; cột FK hiển thị mà bảng đích có mặt thì phải có đường nối.
//   4. Mũi tên nét đứt "..>": chỉ dùng cho liên kết tổng hợp, nhãn phải ghi "không phải FK".
//   5. Cụm bảng rời trong từng ERD, phân loại: thiếu liên kết FK thật (lỗi); nối bằng liên kết tổng hợp
//      hoặc được khai báo độc lập hợp lệ (thông tin); chỉ nối được qua bảng chưa vẽ (cảnh báo, gợi ý đường).
//      Khai báo nhóm độc lập hợp lệ trong .puml:  ' check-erd: doc-lap bang_a, bang_b : lý do
//   6. README: từ điển dữ liệu khớp ERD (cột, kiểu, bắt buộc, FK, UK); bảng "Bảng của phân hệ khác trong ERD"
//      khớp các bảng nét đứt; UK ghép và FK ghép được nhắc trong README; danh sách sở hữu ở docs/README.md.
//   7. Ảnh: mỗi .puml có .png; nguồn nhúng trong PNG khớp tệp .puml và _style.puml hiện tại; link ảnh hợp lệ.
//   8. Sơ đồ hoạt động, use case: tên snake_case phải là bảng hoặc cột có thật; mã IN HOA phải có trong README.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const DOCS = path.resolve(process.argv[2] || path.join(__dirname, '..'));
const errors = [];
const warnings = [];
const infos = [];
const err = (m) => errors.push(m);
const warn = (m) => warnings.push(m);
const info = (m) => infos.push(m);
const rel = (f) => path.relative(DOCS, f).replace(/\\/g, '/');
const read = (f) => fs.readFileSync(f, 'utf8').replace(/\r/g, '');
const plural = (s) => (s.endsWith('y') ? s.slice(0, -1) + 'ies' : /(ch|sh|x|s)$/.test(s) ? s + 'es' : s + 's');
// Cột khóa ngoại mang tên vai trò (người thực hiện) tham chiếu employees.id
const isRoleFk = (c) => /_by_id$/.test(c) || ['waiter_id', 'cashier_id', 'actor_id'].includes(c);

// ---------------------------------------------------------------- đọc ERD
const COL_RE = /^(\*\s*)?([a-z_][a-z0-9_]*)\s*:\s*([a-z]+(?:\(\d+(?:,\d+)?\))?)\s*(?:<<([^>]+)>>)?$/;
// Đầu mút quan hệ theo ký hiệu IE của PlantUML: [min, max] của bảng ở đầu đó
const END = { '||': [1, 1], '|o': [0, 1], 'o|': [0, 1], '}o': [0, 'N'], 'o{': [0, 'N'], '}|': [1, 'N'], '|{': [1, 'N'] };
const endText = (e) => `${e[0]}..${e[1]}`;

function parseErd(file) {
  const src = read(file);
  const entities = new Map();
  const entRe = /^entity\s+"([^"]+)"\s+as\s+(\w+)([^{\n]*)\{\n([\s\S]*?)\n\}/gm;
  let m;
  while ((m = entRe.exec(src))) {
    const [, name, alias, attrs, body] = m;
    if (alias !== name) warn(`${rel(file)}: bảng ${name} dùng alias ${alias} khác tên bảng`);
    const cols = [];
    const notes = [];
    let inNotes = false;
    for (const raw of body.split('\n')) {
      const line = raw.trim();
      if (!line || line === '--') continue;
      if (line === '..') { inNotes = true; continue; }
      if (inNotes) { notes.push(line); continue; }
      const c = line.match(COL_RE);
      if (!c) { err(`${rel(file)}: ${name}: không đọc được dòng "${line}"`); continue; }
      cols.push({ name: c[2], required: !!c[1], type: c[3], markers: (c[4] || '').split(',').map((s) => s.trim()).filter(Boolean) });
    }
    if (entities.has(name)) err(`${rel(file)}: bảng ${name} khai báo hai lần`);
    entities.set(name, { name, dashed: /line\.dashed/.test(attrs), cols, notes });
  }
  const rels = [];
  const aggs = [];
  const directives = [];
  const relRe = /^(\w+)\s+(\|\||\|o|o\||\}o|\}\|)-+(?:(?:up|down|left|right)-+)?(\|\||o\||\|o|o\{|\|\{)\s+(\w+)(?:\s*:\s*(.+))?$/;
  const aggRe = /^(\w+)\s+\.+(?:(?:up|down|left|right)\.+)?>\s+(\w+)(?:\s*:\s*(.+))?$/;
  src.split('\n').forEach((raw, i) => {
    const line = raw.trim();
    const d = line.match(/^'\s*check-erd:\s*doc-lap\s+([a-z_, ]+?)\s*:\s*(.+)$/);
    if (d) { directives.push({ tables: d[1].split(',').map((s) => s.trim()).filter(Boolean), reason: d[2], lineNo: i + 1 }); return; }
    let r = line.match(relRe);
    if (r) { rels.push({ a: r[1], aSym: r[2], bSym: r[3], b: r[4], label: (r[5] || '').trim(), lineNo: i + 1 }); return; }
    r = line.match(aggRe);
    if (r) { aggs.push({ from: r[1], to: r[2], label: (r[3] || '').replace(/^"|"$/g, '').replace(/\\n/g, ' ').trim(), lineNo: i + 1 }); return; }
    if (/^\w+\s+[-.|}{o<>]+(\[[^\]]*\][-.]*>?)?\s+\w+/.test(line) && !/hidden/.test(line)) warn(`${rel(file)}:${i + 1}: không nhận dạng được đường nối "${line}"`);
  });
  return { file, src, entities, rels, aggs, directives };
}

// ---------------------------------------------------------------- nạp ERD
const moduleDirs = fs.readdirSync(DOCS, { withFileTypes: true })
  .filter((d) => d.isDirectory() && /^\d\d-/.test(d.name) && fs.existsSync(path.join(DOCS, d.name, 'erd.puml')))
  .map((d) => d.name).sort();
if (!moduleDirs.length) { console.error('Không tìm thấy ERD phân hệ trong ' + DOCS); process.exit(2); }
const ERD = {};
for (const mod of moduleDirs) ERD[mod] = parseErd(path.join(DOCS, mod, 'erd.puml'));
const totalFile = path.join(DOCS, 'erd-tong-the.puml');
const TOTAL = fs.existsSync(totalFile) ? parseErd(totalFile) : null;
if (!TOTAL) err('thiếu erd-tong-the.puml');

// ---------------------------------------------------------------- 1. sở hữu bảng
const GLOBAL = new Map(); // tên bảng -> định nghĩa gốc (bảng viền liền ở phân hệ sở hữu)
const OWNER = new Map();
for (const mod of moduleDirs) {
  for (const e of ERD[mod].entities.values()) {
    if (e.dashed) continue;
    if (OWNER.has(e.name)) { err(`bảng ${e.name} vẽ viền liền ở cả ${OWNER.get(e.name)} và ${mod} (chỉ phân hệ sở hữu vẽ viền liền)`); continue; }
    OWNER.set(e.name, mod);
    GLOBAL.set(e.name, e);
  }
}
for (const mod of moduleDirs) for (const e of ERD[mod].entities.values()) {
  if (e.dashed && !GLOBAL.has(e.name)) err(`${mod}: bảng nét đứt ${e.name} không có định nghĩa gốc (viền liền) ở phân hệ nào`);
  if (!/^[a-z][a-z0-9_]*s$/.test(e.name)) warn(`${mod}: tên bảng ${e.name} không phải snake_case số nhiều`);
}
const sameCol = (a, b) => a.type === b.type && a.required === b.required && a.markers.join(',') === b.markers.join(',');
if (TOTAL) {
  for (const name of GLOBAL.keys()) if (!TOTAL.entities.has(name)) err(`ERD tổng thể thiếu bảng ${name}`);
  for (const e of TOTAL.entities.values()) {
    const g = GLOBAL.get(e.name);
    if (!g) { err(`ERD tổng thể có bảng ${e.name} không thuộc phân hệ nào`); continue; }
    if (e.dashed) err(`ERD tổng thể: ${e.name} không được vẽ nét đứt`);
    const colsSame = e.cols.length === g.cols.length && e.cols.every((c, i) => c.name === g.cols[i].name && sameCol(c, g.cols[i]));
    if (!colsSame) err(`ERD tổng thể: cột của ${e.name} khác định nghĩa gốc ở ${OWNER.get(e.name)}`);
    if (e.notes.join('\n') !== g.notes.join('\n')) err(`ERD tổng thể: ràng buộc của ${e.name} khác định nghĩa gốc ở ${OWNER.get(e.name)}`);
  }
}
for (const mod of moduleDirs) for (const e of ERD[mod].entities.values()) {
  if (!e.dashed) continue;
  const g = GLOBAL.get(e.name);
  if (!g) continue;
  if (OWNER.get(e.name) === mod) err(`${mod}: ${e.name} thuộc phân hệ này nhưng vẽ nét đứt`);
  if (!e.cols.some((c) => c.name === 'id')) err(`${mod}: bảng tham chiếu ${e.name} thiếu cột id`);
  for (const c of e.cols) {
    const gc = g.cols.find((x) => x.name === c.name);
    if (!gc) err(`${mod}: bảng tham chiếu ${e.name} có cột ${c.name} không tồn tại ở bản gốc (${OWNER.get(e.name)})`);
    else if (!sameCol(c, gc)) err(`${mod}: ${e.name}.${c.name} khác bản gốc (kiểu, bắt buộc hoặc đánh dấu)`);
  }
  if (e.notes.length) warn(`${mod}: bảng tham chiếu ${e.name} ghi ràng buộc; ràng buộc chỉ ghi ở phân hệ sở hữu`);
}

// ---------------------------------------------------------------- 2. khóa
const UK_RE = /^UK \(([^)]+)\)$/;
const PUK_RE = /^PARTIAL UK \(([^)]+)\) WHERE .+$/;
const CFK_RE = /^FK \(([^)]+)\) → (\w+) \(([^)]+)\)$/;
const splitCols = (s) => s.split(',').map((x) => x.trim());
const FKS = []; // {child, col, parent, required, unique}
const COMPOSITE = []; // {table, cols, parent, pcols}
function uniqueSets(def) {
  const sets = [['id']];
  for (const c of def.cols) if (c.markers.includes('UK')) sets.push([c.name]);
  for (const n of def.notes) { const m = n.match(UK_RE); if (m) sets.push(splitCols(m[1])); }
  return sets;
}
for (const [name, def] of GLOBAL) {
  const id = def.cols.find((c) => c.name === 'id');
  if (!id || id.type !== 'bigint' || !id.required || !id.markers.includes('PK')) err(`${name}: cần cột * id : bigint <<PK>>`);
  if (def.cols.filter((c) => c.markers.includes('PK')).length !== 1) err(`${name}: phải có đúng một cột PK`);
  for (const c of def.cols) {
    const isFk = c.markers.includes('FK');
    if (c.name.endsWith('_id') && !isFk) err(`${name}.${c.name}: cột *_id phải đánh dấu FK`);
    if (isFk && !c.name.endsWith('_id')) err(`${name}.${c.name}: cột FK phải có hậu tố _id`);
    if (!isFk) continue;
    let parent = plural(c.name.slice(0, -3));
    if (!GLOBAL.has(parent) && isRoleFk(c.name)) parent = 'employees';
    if (!GLOBAL.has(parent)) { err(`${name}.${c.name}: không xác định được bảng đích (${parent} không tồn tại)`); continue; }
    const pk = GLOBAL.get(parent).cols.find((x) => x.name === 'id');
    if (c.type !== 'bigint' || (pk && pk.type !== c.type)) err(`${name}.${c.name}: kiểu ${c.type} không khớp khóa chính ${parent}.id (${pk && pk.type})`);
    const unique = c.markers.includes('UK') || def.notes.some((n) => n === `UK (${c.name})`);
    FKS.push({ child: name, col: c.name, parent, required: c.required, unique });
  }
  for (const n of def.notes) {
    let m;
    if ((m = n.match(UK_RE)) || (m = n.match(PUK_RE))) {
      for (const x of splitCols(m[1])) if (!def.cols.some((c) => c.name === x)) err(`${name}: "${n}" dùng cột không có: ${x}`);
    } else if ((m = n.match(CFK_RE))) {
      const cols = splitCols(m[1]);
      const parent = m[2];
      const pcols = splitCols(m[3]);
      COMPOSITE.push({ table: name, cols, parent, pcols });
      const p = GLOBAL.get(parent);
      if (!p) { err(`${name}: FK ghép trỏ tới bảng không có: ${parent}`); continue; }
      if (cols.length !== pcols.length) err(`${name}: FK ghép "${n}" lệch số cột`);
      cols.forEach((x, i) => {
        const a = def.cols.find((c) => c.name === x);
        const b = p.cols.find((c) => c.name === pcols[i]);
        if (!a) err(`${name}: FK ghép dùng cột không có: ${x}`);
        if (!b) err(`${name}: FK ghép trỏ tới cột không có: ${parent}.${pcols[i]}`);
        if (a && b && a.type !== b.type) err(`${name}: FK ghép ${x} (${a.type}) khác kiểu ${parent}.${pcols[i]} (${b.type})`);
      });
      const key = [...pcols].sort().join(',');
      if (!uniqueSets(p).some((s) => [...s].sort().join(',') === key)) err(`${name}: FK ghép cần ${parent} có PK/UK đúng trên (${pcols.join(', ')})`);
    } else if (/^FK\b/.test(n)) err(`${name}: không đọc được ràng buộc FK "${n}" (dạng đúng: FK (a, b) → bảng (x, y))`);
    else if (/^UK\b|^PARTIAL UK\b/.test(n)) err(`${name}: không đọc được ràng buộc "${n}"`);
  }
}
const expectedEnds = (fk) => ({ parentEnd: fk.required ? [1, 1] : [0, 1], childEnd: fk.unique ? [0, 1] : [0, 'N'] });
const sameEnd = (a, b) => a[0] === b[0] && a[1] === b[1];

// ---------------------------------------------------------------- 3–5. đường nối, liên kết tổng hợp, cụm rời
// Đồ thị khóa ngoại toàn hệ thống (vô hướng, có trọng số): khóa ngoại người thực hiện (*_by_id, waiter_id...)
// nặng hơn để đường gợi ý ưu tiên liên kết nghiệp vụ (ví dụ order_batches → orders thay vì → employees).
const fkPathGraph = new Map();
for (const t of GLOBAL.keys()) fkPathGraph.set(t, new Map());
for (const fk of FKS) {
  const w = isRoleFk(fk.col) ? 1.5 : 1;
  for (const [a, b] of [[fk.child, fk.parent], [fk.parent, fk.child]]) {
    const cur = fkPathGraph.get(a).get(b);
    if (cur === undefined || w < cur) fkPathGraph.get(a).set(b, w);
  }
}
function shortestPath(fromSet, toSet) {
  const dist = new Map();
  const prev = new Map();
  const done = new Set();
  for (const t of fromSet) { dist.set(t, 0); prev.set(t, null); }
  for (;;) {
    let t = null;
    for (const [x, d] of dist) if (!done.has(x) && (t === null || d < dist.get(t))) t = x;
    if (t === null) return null;
    if (toSet.has(t)) { const p = []; for (let x = t; x !== null; x = prev.get(x)) p.unshift(x); return p; }
    done.add(t);
    for (const [n, w] of fkPathGraph.get(t) || []) {
      const nd = dist.get(t) + w;
      if (!dist.has(n) || nd < dist.get(n)) { dist.set(n, nd); prev.set(n, t); }
    }
  }
}
const labelsByFk = new Map(); // khóa ngoại -> nhãn ở từng ERD, để so nhất quán
const summary = [];
function checkDiagram(name, d, isTotal) {
  const drawn = d.entities;
  const shownCol = (t, c) => drawn.has(t) && drawn.get(t).cols.some((x) => x.name === c);
  for (const r of d.rels) for (const t of [r.a, r.b]) if (!drawn.has(t)) err(`${name}:${r.lineNo}: đường nối tới bảng chưa khai báo: ${t}`);
  // ghép đường nối với khóa ngoại thật theo từng cặp bảng
  const pairs = new Map();
  const pairKey = (x, y) => [x, y].sort().join('|');
  for (const r of d.rels) {
    const k = pairKey(r.a, r.b);
    if (!pairs.has(k)) pairs.set(k, { lines: [], fks: [] });
    pairs.get(k).lines.push(r);
  }
  for (const fk of FKS) {
    if (!drawn.has(fk.child) || !drawn.has(fk.parent)) continue;
    const k = pairKey(fk.child, fk.parent);
    if (!pairs.has(k)) pairs.set(k, { lines: [], fks: [] });
    pairs.get(k).fks.push(fk);
  }
  const edges = [];
  let fkLines = 0;
  for (const { lines, fks } of pairs.values()) {
    const freeFks = [...fks];
    const unmatched = [];
    for (const r of lines) {
      if (!fks.length) { err(`${name}:${r.lineNo}: đường nối ${r.a} – ${r.b} không ứng với khóa ngoại nào (nối giả)`); continue; }
      const fit = freeFks.find((fk) => {
        const parentIsA = fk.parent === r.a;
        const pe = END[parentIsA ? r.aSym : r.bSym];
        const ce = END[parentIsA ? r.bSym : r.aSym];
        const ex = expectedEnds(fk);
        return sameEnd(pe, ex.parentEnd) && sameEnd(ce, ex.childEnd);
      });
      if (fit) { freeFks.splice(freeFks.indexOf(fit), 1); edges.push([r.a, r.b]); fkLines++; record(fit, r); }
      else unmatched.push(r);
    }
    for (const r of unmatched) {
      const fk = freeFks.shift();
      if (!fk) { err(`${name}:${r.lineNo}: thừa đường nối ${r.a} – ${r.b} (không còn khóa ngoại nào chưa vẽ)`); continue; }
      const ex = expectedEnds(fk);
      const parentIsA = fk.parent === r.a;
      err(`${name}:${r.lineNo}: bản số sai cho ${fk.child}.${fk.col} → ${fk.parent}: vẽ ${fk.parent} ${endText(END[parentIsA ? r.aSym : r.bSym])} – ${fk.child} ${endText(END[parentIsA ? r.bSym : r.aSym])}, theo NOT NULL/UNIQUE phải là ${fk.parent} ${endText(ex.parentEnd)} – ${fk.child} ${endText(ex.childEnd)}`);
      edges.push([r.a, r.b]);
      fkLines++;
      record(fk, r);
    }
    for (const fk of freeFks) {
      if (shownCol(fk.child, fk.col)) err(`${name}: thiếu đường nối cho khóa ngoại ${fk.child}.${fk.col} → ${fk.parent} (cả hai bảng đều có trong sơ đồ)`);
    }
  }
  function record(fk, r) {
    if (!shownCol(fk.child, fk.col)) warn(`${name}:${r.lineNo}: đường nối ${fk.parent} – ${fk.child} dựa trên cột ${fk.col} không hiển thị trong bảng`);
    const k = `${fk.child}.${fk.col}`;
    if (!labelsByFk.has(k)) labelsByFk.set(k, new Map());
    labelsByFk.get(k).set(name, r.label || '(không nhãn)');
  }
  // liên kết tổng hợp
  for (const a of d.aggs) {
    if (!drawn.has(a.from) || !drawn.has(a.to)) err(`${name}:${a.lineNo}: liên kết tổng hợp tới bảng chưa khai báo`);
    if (!/không phải FK|không phải khóa ngoại/i.test(a.label)) err(`${name}:${a.lineNo}: mũi tên nét đứt ${a.from} ..> ${a.to} phải ghi rõ "không phải FK" trong nhãn`);
    if (FKS.some((fk) => (fk.child === a.from && fk.parent === a.to) || (fk.child === a.to && fk.parent === a.from))) warn(`${name}:${a.lineNo}: ${a.from} và ${a.to} có khóa ngoại thật; dùng đường chân chim thay cho mũi tên nét đứt`);
  }
  // cụm bảng theo đường khóa ngoại
  const parent = new Map([...drawn.keys()].map((t) => [t, t]));
  const find = (x) => (parent.get(x) === x ? x : (parent.set(x, find(parent.get(x))), parent.get(x)));
  for (const [x, y] of edges) if (drawn.has(x) && drawn.has(y)) parent.set(find(x), find(y));
  const comps = new Map();
  for (const t of drawn.keys()) { const r = find(t); if (!comps.has(r)) comps.set(r, new Set()); comps.get(r).add(t); }
  const list = [...comps.values()];
  const ownedCount = (s) => [...s].filter((t) => !drawn.get(t).dashed).length;
  list.sort((p, q) => ownedCount(q) - ownedCount(p) || q.size - p.size); // cụm chính: nhiều bảng của phân hệ nhất
  for (const c of list.slice(1)) {
    const names = [...c].join(', ');
    const crossing = FKS.filter((fk) => drawn.has(fk.child) && drawn.has(fk.parent) && c.has(fk.child) !== c.has(fk.parent));
    // khóa ngoại thật có hiển thị cột mà không vẽ đường: thiếu liên kết thực tế
    const shownFk = crossing.filter((fk) => shownCol(fk.child, fk.col));
    if (shownFk.length) { err(`${name}: cụm {${names}} tách rời dù có khóa ngoại thật ${shownFk.map((fk) => `${fk.child}.${fk.col} → ${fk.parent}`).join(', ')}: thiếu liên kết, cần vẽ`); continue; }
    // khóa ngoại thật nhưng cột cố ý không hiển thị ở bảng tham chiếu
    const hidden = crossing.length ? `; khóa ngoại thật không hiển thị cột (không dùng ở phân hệ này): ${crossing.map((fk) => `${fk.child}.${fk.col} → ${fk.parent}`).join(', ')}` : '';
    const agg = d.aggs.filter((a) => c.has(a.from) !== c.has(a.to));
    if (agg.length) { info(`${name}: cụm {${names}} nối với phần còn lại bằng liên kết tổng hợp (không phải FK): ${agg.map((a) => `${a.from} ..> ${a.to}`).join('; ')}${hidden}`); continue; }
    const dir = d.directives.find((x) => [...c].every((t) => x.tables.includes(t)));
    if (dir) { info(`${name}: nhóm độc lập hợp lệ {${names}}: ${dir.reason}${hidden}`); continue; }
    if (crossing.length) { warn(`${name}: cụm tách rời {${names}}${hidden}; nếu liên kết này cần cho phân hệ thì hiển thị cột và vẽ đường nối`); continue; }
    const p = shortestPath(c, new Set([...drawn.keys()].filter((t) => !c.has(t))));
    const onlyRef = [...c].every((t) => drawn.get(t).dashed);
    const missing = p ? p.filter((t) => !drawn.has(t)) : [];
    warn(`${name}: cụm tách rời {${names}}${p ? `; trong CSDL nối qua ${p.join(' → ')} (bảng chưa vẽ: ${missing.join(', ') || 'không'})` : '; không có đường khóa ngoại nào tới phần còn lại'}${onlyRef ? '; nếu không cần cho phân hệ này thì bỏ các bảng tham chiếu này khỏi sơ đồ' : ''}`);
  }
  for (const dir of d.directives) for (const t of dir.tables) if (!drawn.has(t)) warn(`${name}:${dir.lineNo}: khai báo độc lập cho bảng không có trong sơ đồ: ${t}`);
  const owned = [...drawn.values()].filter((e) => !e.dashed).length;
  summary.push({ name, tables: drawn.size, owned, refs: drawn.size - owned, fk: fkLines, agg: d.aggs.length, comps: list.length });
  if (isTotal) {
    const missingFk = FKS.filter((fk) => !d.rels.some((r) => [r.a, r.b].includes(fk.child) && [r.a, r.b].includes(fk.parent)));
    if (missingFk.length) err(`ERD tổng thể thiếu ${missingFk.length} khóa ngoại: ${missingFk.map((f) => `${f.child}.${f.col}`).join(', ')}`);
  }
}
for (const mod of moduleDirs) checkDiagram(mod, ERD[mod], false);
if (TOTAL) checkDiagram('erd-tong-the', TOTAL, true);
for (const [k, byErd] of labelsByFk) {
  const labels = new Set(byErd.values());
  if (labels.size > 1) warn(`nhãn đường nối của ${k} khác nhau giữa các ERD: ${[...byErd].map(([e, l]) => `${e}=${l}`).join('; ')}`);
}

// ---------------------------------------------------------------- 6. README
function parseDictionary(md) {
  const tables = new Map();
  let current = null;
  for (const line of md.split('\n')) {
    const h = line.match(/^\*\*([a-z_]+)\*\* [–-]/);
    if (h) { current = { cols: new Map(), composite: [] }; tables.set(h[1], current); continue; }
    if (!current) continue;
    if (!line.startsWith('|')) { if (line.trim()) current = null; continue; }
    const cells = line.split('|').slice(1, -1).map((s) => s.trim());
    if (cells.length < 3 || cells[0] === 'Cột' || /^-+$/.test(cells[0])) continue;
    if (cells[0].startsWith('(')) current.composite.push({ text: cells[0], cons: cells[2] });
    else current.cols.set(cells[0].replace(/`/g, ''), { type: cells[1], cons: cells[2] });
  }
  return tables;
}
const flat = (s) => s.replace(/`/g, '').replace(/\s+/g, ' ');
for (const mod of moduleDirs) {
  const readme = path.join(DOCS, mod, 'README.md');
  if (!fs.existsSync(readme)) { err(`${mod}: thiếu README.md`); continue; }
  const md = read(readme);
  const dict = parseDictionary(md);
  for (const e of ERD[mod].entities.values()) {
    if (e.dashed) continue;
    const dd = dict.get(e.name);
    if (!dd) { err(`${mod}: README thiếu từ điển dữ liệu cho ${e.name}`); continue; }
    for (const c of e.cols) {
      const r = dd.cols.get(c.name);
      if (!r) { err(`${mod}: từ điển thiếu cột ${e.name}.${c.name}`); continue; }
      if (r.type !== c.type) err(`${mod}: ${e.name}.${c.name} kiểu README ${r.type}, ERD ${c.type}`);
      const req = /^PK|(^|[,;]\s*)bắt buộc(?!\s+khi)/.test(r.cons); // "bắt buộc khi ..." là điều kiện, không phải NOT NULL
      if (req !== c.required) err(`${mod}: ${e.name}.${c.name} README ${req ? 'bắt buộc' : 'không bắt buộc'}, ERD ${c.required ? 'bắt buộc' : 'không bắt buộc'}`);
      const fk = r.cons.match(/FK → (\w+)/);
      const mfk = FKS.find((f) => f.child === e.name && f.col === c.name);
      if (!!fk !== !!mfk) err(`${mod}: ${e.name}.${c.name} README và ERD không thống nhất về FK`);
      else if (fk && mfk && fk[1] !== mfk.parent) err(`${mod}: ${e.name}.${c.name} README ghi FK → ${fk[1]}, ERD → ${mfk.parent}`);
      const uk = /(^|[ ,;])UK([ ,;]|$)/.test(r.cons);
      if (uk !== c.markers.includes('UK')) err(`${mod}: ${e.name}.${c.name} README ${uk ? 'có' : 'không có'} UK, ERD ${c.markers.includes('UK') ? 'có' : 'không có'}`);
    }
    for (const name of dd.cols.keys()) if (!e.cols.some((c) => c.name === name)) err(`${mod}: từ điển có cột ${e.name}.${name} không có trong ERD`);
    for (const n of e.notes) {
      let m;
      if ((m = n.match(UK_RE)) && splitCols(m[1]).length > 1) {
        if (!flat(md).includes(`(${splitCols(m[1]).join(', ')})`)) warn(`${mod}: README chưa nhắc ràng buộc ${n} của ${e.name}`);
      } else if ((m = n.match(CFK_RE))) {
        if (!flat(md).includes(`(${splitCols(m[1]).join(', ')})`)) err(`${mod}: README chưa mô tả khóa ngoại ghép ${n} của ${e.name}`);
      }
    }
  }
  for (const t of dict.keys()) if (!ERD[mod].entities.has(t)) err(`${mod}: README có từ điển cho ${t} nhưng ERD phân hệ không có bảng này`);
  // danh sách bảng của phân hệ khác
  const dashed = [...ERD[mod].entities.values()].filter((e) => e.dashed).map((e) => e.name).sort();
  const idx = md.search(/Bảng của phân hệ khác trong ERD/);
  if (idx < 0) { if (dashed.length) err(`${mod}: README thiếu mục "Bảng của phân hệ khác trong ERD"`); }
  else {
    const rows = [];
    let started = false;
    for (const line of md.slice(idx).split('\n').slice(1)) {
      if (line.startsWith('|')) { started = true; const c = line.split('|')[1].trim().replace(/`/g, ''); if (/^[a-z_]+$/.test(c)) rows.push(c); }
      else if (started) break;
    }
    const listed = rows.sort();
    if (listed.join(',') !== dashed.join(',')) err(`${mod}: README "Bảng của phân hệ khác trong ERD" [${listed}] khác các bảng nét đứt trong ERD [${dashed}]`);
  }
}
// danh sách bảng sở hữu ở docs/README.md
const rootReadme = path.join(DOCS, 'README.md');
if (fs.existsSync(rootReadme)) {
  const md = read(rootReadme);
  const rows = [...md.matchAll(/^\| (\d) – [^|]+ \| ([^|]+) \|$/gm)];
  if (!rows.length) warn('docs/README.md: không thấy bảng "Phân hệ | Bảng sở hữu"');
  for (const [, n, cell] of rows) {
    const mod = moduleDirs.find((m) => m.startsWith('0' + n + '-'));
    const listed = [...cell.matchAll(/`([a-z_]+)`/g)].map((x) => x[1]).sort().join(',');
    const owned = [...GLOBAL.keys()].filter((t) => OWNER.get(t) === mod).sort().join(',');
    if (mod && listed !== owned) err(`docs/README.md: phân hệ ${n} liệt kê [${listed}], ERD sở hữu [${owned}]`);
  }
}

// ---------------------------------------------------------------- 7. ảnh
function embeddedSource(png) {
  const b = fs.readFileSync(png);
  let i = 8;
  while (i + 8 <= b.length) {
    const len = b.readUInt32BE(i);
    const type = b.toString('ascii', i + 4, i + 8);
    if (type === 'iTXt') {
      const data = b.subarray(i + 8, i + 8 + len);
      const k = data.indexOf(0);
      if (data.toString('latin1', 0, k) === 'plantuml') {
        const compressed = data[k + 1] === 1;
        let p = data.indexOf(0, k + 3) + 1; // bỏ thẻ ngôn ngữ
        p = data.indexOf(0, p) + 1; // bỏ từ khóa dịch
        const raw = data.subarray(p);
        return compressed ? zlib.inflateSync(raw).toString('utf8') : raw.toString('utf8');
      }
    }
    if (type === 'IEND') break;
    i += 12 + len;
  }
  return null;
}
const norm = (s) => s.replace(/\r/g, '').replace(/[–—]/g, '-').trim();
const stylePath = path.join(DOCS, '_style.puml');
const styleLines = fs.existsSync(stylePath) ? read(stylePath).split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith("'")) : [];
const pumls = [];
for (const d of ['.', ...moduleDirs]) for (const f of fs.readdirSync(path.join(DOCS, d))) if (f.endsWith('.puml') && f !== '_style.puml') pumls.push(path.join(DOCS, d, f));
let stale = 0;
for (const f of pumls) {
  const png = f.replace(/\.puml$/, '.png');
  if (!fs.existsSync(png)) { err(`${rel(f)}: chưa có ảnh .png`); continue; }
  const emb = embeddedSource(png);
  if (emb === null) { warn(`${rel(png)}: không đọc được nguồn nhúng, không kiểm tra được ảnh cũ hay mới`); continue; }
  const src = read(f);
  if (!norm(emb).startsWith(norm(src))) { err(`${rel(png)}: ảnh cũ hơn nguồn ${rel(f)}, cần render lại`); stale++; continue; }
  if (/!include\s+\S*_style\.puml/.test(src)) {
    const expanded = norm(emb).slice(norm(src).length);
    const missing = styleLines.filter((l) => !expanded.includes(l));
    if (missing.length) { err(`${rel(png)}: render với _style.puml cũ (thiếu ${missing.length} dòng, ví dụ "${missing[0]}"), cần render lại`); stale++; }
  }
}
for (const md of [rootReadme, path.join(DOCS, 'bao-cao-tong-hop.md'), ...moduleDirs.map((m) => path.join(DOCS, m, 'README.md'))]) {
  if (!fs.existsSync(md)) continue;
  for (const m of read(md).matchAll(/!\[[^\]]*\]\(([^)]+\.png)\)/g)) if (!fs.existsSync(path.join(path.dirname(md), m[1]))) err(`${rel(md)}: link ảnh hỏng ${m[1]}`);
}

// ---------------------------------------------------------------- 8. sơ đồ hoạt động và use case
const allCols = new Set();
for (const def of GLOBAL.values()) for (const c of def.cols) allCols.add(c.name);
const readmeText = moduleDirs.map((m) => (fs.existsSync(path.join(DOCS, m, 'README.md')) ? read(path.join(DOCS, m, 'README.md')) : '')).join('\n');
const knownUpper = new Set(readmeText.match(/\b[A-Z][A-Z0-9_]{2,}\b/g) || []);
for (const mod of moduleDirs) for (const f of fs.readdirSync(path.join(DOCS, mod)).filter((x) => /^(activity|usecase).*\.puml$/.test(x))) {
  const src = read(path.join(DOCS, mod, f)).replace(/^\s*'.*$/gm, '').replace(/\\n/g, ' ').replace(/#[0-9A-Fa-f]{3,8}\b/g, ' ').replace(/^\s*(skinparam|!include|@start|@end|title).*$/gm, '');
  for (const t of new Set(src.match(/\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b/g) || [])) if (!GLOBAL.has(t) && !allCols.has(t)) warn(`${mod}/${f}: "${t}" không phải tên bảng hay cột trong ERD`);
  for (const t of new Set(src.match(/\b[A-Z][A-Z0-9_]{2,}\b/g) || [])) if (!knownUpper.has(t) && !/^UC\d+$/.test(t)) warn(`${mod}/${f}: mã "${t}" không xuất hiện trong README nào`);
}

// ---------------------------------------------------------------- báo cáo
console.log(`Kiểm tra ERD: ${DOCS}`);
console.log(`Bảng: ${GLOBAL.size}; khóa ngoại: ${FKS.length} (đơn) + ${COMPOSITE.length} (ghép); sơ đồ PlantUML: ${pumls.length}\n`);
console.log('ERD                      | bảng (liền/nét đứt) | đường FK | tổng hợp | cụm FK');
for (const s of summary) console.log(`${s.name.padEnd(24)} | ${String(s.tables).padStart(4)} (${s.owned}/${s.refs})`.padEnd(46) + ` | ${String(s.fk).padStart(8)} | ${String(s.agg).padStart(8)} | ${String(s.comps).padStart(6)}`);
const print = (title, list) => { console.log(`\n${title} (${list.length})`); for (const x of list) console.log('- ' + x); };
print('LỖI', errors);
print('CẢNH BÁO', warnings);
print('THÔNG TIN', infos);
process.exit(errors.length ? 1 : 0);
