// =====================================================================
// RESTAURANT CORE — Kiểm thử đồng thời v4.1 (cần PostgreSQL server thật, nhiều kết nối)
//
//   npm i pg
//   node 06-test-concurrency-v4.1.mjs postgresql://user:pass@host:5432/restaurant_core
//
// Điều kiện: database đã nạp 04-schema-v4.1.sql. Script tự tạo dữ liệu (mã có tiền tố riêng mỗi lần chạy) nên chạy lại
// được; nên dùng database thử nghiệm. Hai kiểu kiểm tra:
//   * XÁC ĐỊNH (lock)  — giao dịch A giữ khóa (chưa commit), B phải BỊ CHẶN (đang chờ khóa) rồi chạy tiếp sau khi A commit.
//     Chứng minh khóa tồn tại và có đúng thứ tự, không phụ thuộc may rủi.
//   * ĐUA TỰ DO (race) — nhiều kết nối bắn cùng lúc, lặp nhiều vòng; kiểm tra kết quả luôn nhất quán, không deadlock (40P01),
//     không lỗi 23514/23505 lọt ra ngoài.
// Thoát mã 0 nếu mọi ca đạt, 1 nếu có ca sai.
// =====================================================================
import pg from 'pg';

const URL = process.argv[2] || process.env.DATABASE_URL;
if (!URL) { console.error('Thiếu connection string. Ví dụ: node 06-test-concurrency-v4.1.mjs postgresql://postgres@127.0.0.1:5432/restaurant_core'); process.exit(2); }
const RUN = Date.now().toString(36);
const ITER = Number(process.env.ITER || 15);          // số vòng cho các ca đua tự do
setTimeout(() => { console.error('WATCHDOG: quá 10 phút'); process.exit(3); }, 600000).unref();

// ---------- hạ tầng nhỏ
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const open = async () => { const c = new pg.Client({ connectionString: URL }); await c.connect(); return c; };
const results = [];
let deadlocks = 0, unexpected = 0;
const check = (name, cond, detail = '') => {
  results.push({ name, ok: !!cond, detail });
  console.log(`${cond ? '  ✓' : '  ✗ FAIL'} ${name}${cond ? '' : detail ? ' — ' + detail : ''}`);
};
const wrap = async (p) => { try { return { ok: true, rows: (await p).rows }; } catch (e) { if (e.code === '40P01') deadlocks++; return { ok: false, code: e.code, msg: e.message.split('\n')[0] }; } };
const val = (r) => (r.ok && r.rows[0] ? Object.values(r.rows[0])[0] : undefined);
const group = (t) => console.log(`\n${t}`);

async function waitBlocked(obs, pid, ms = 3000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    const r = await obs.query('select wait_event_type from pg_stat_activity where pid = $1', [pid]);
    if (r.rows[0] && r.rows[0].wait_event_type === 'Lock') return true;
    await sleep(20);
  }
  return false;
}
// A giữ giao dịch mở sau khi chạy aSql; B chạy bSql (autocommit) song song; trả về kết quả cả hai và B có bị chặn không
async function hold(aSql, bSql, { aAfter } = {}) {
  const a = await open(), b = await open(), obs = await open();
  try {
    const bPid = (await b.query('select pg_backend_pid() pid')).rows[0].pid;
    await a.query('BEGIN');
    const ra = await wrap(a.query(aSql));
    if (aAfter) await aAfter(a);
    const pb = wrap(b.query(bSql));
    const blocked = await waitBlocked(obs, bPid);
    await a.query(ra.ok ? 'COMMIT' : 'ROLLBACK');
    return { a: ra, b: await pb, blocked };
  } finally { await a.end(); await b.end(); await obs.end(); }
}
// n kết nối bắn cùng lúc
async function race(fns) {
  const cs = await Promise.all(fns.map(() => open()));
  try { return await Promise.all(fns.map((f, i) => wrap(f(cs[i])))); } finally { await Promise.all(cs.map((c) => c.end())); }
}

// ---------- dữ liệu nền (idempotent) và các nhà máy tạo dữ liệu
const db = await open();
const q = (sql, p) => db.query(sql, p);
const one = async (sql, p) => Object.values((await db.query(sql, p)).rows[0] || {})[0];
let seq = 0;
const code = (p) => `${p}-${RUN}-${++seq}`;
let E = {};
async function base() {
  await q(`INSERT INTO departments (department_name) VALUES ('Vận hành') ON CONFLICT DO NOTHING`);
  const dept = await one(`SELECT id FROM departments WHERE department_name = 'Vận hành'`);
  for (const [c, n, role] of [['CM2', 'Quản lý 2', 'MANAGER2'], ['CM', 'Quản lý', 'MANAGER'], ['CW', 'Phục vụ', 'WAITER'], ['CK', 'Bếp', 'KITCHEN'], ['CC', 'Thu ngân', 'CASHIER']]) {
    await q(`INSERT INTO employees (employee_code, full_name, department_id) VALUES ($1, $2, $3) ON CONFLICT (employee_code) DO NOTHING`, [c, n, dept]);
    const id = await one(`SELECT id FROM employees WHERE employee_code = $1`, [c]);
    await q(`INSERT INTO accounts (employee_id, username, password_hash) VALUES ($1, $2, 'x') ON CONFLICT (employee_id) DO NOTHING`, [id, c.toLowerCase()]);
    await q(`INSERT INTO account_roles (account_id, role_id) SELECT a.id, r.id FROM accounts a, roles r WHERE a.employee_id = $1 AND r.role_code = $2 ON CONFLICT DO NOTHING`, [id, role.replace(/\d$/, '')]);
    E[role] = id;
  }
  await q(`INSERT INTO menu_categories (name) VALUES ('Concurrency') ON CONFLICT DO NOTHING`);
  E.cat = await one(`SELECT id FROM menu_categories WHERE name = 'Concurrency'`);
  await q(`INSERT INTO daily_menus (business_date, status) VALUES (rc_business_date(now()), 'DRAFT') ON CONFLICT (business_date) DO NOTHING`);
  E.menu = await one(`SELECT id FROM daily_menus WHERE business_date = rc_business_date(now())`);
}
// Món 1 đơn vị nguyên liệu/suất với hạn mức và tồn bếp cho trước
async function newDish({ quota = null, stock = 100, per = 1, share = null } = {}) {
  const name = code('Dish');
  if (share) {                                         // món khác nhưng dùng chung loại nguyên liệu + lô bếp của `share`
    const item2 = await one(`INSERT INTO menu_items (menu_category_id, name, price) VALUES ($1, $2, 30000) RETURNING id`, [E.cat, name]);
    const rec2 = await one(`INSERT INTO recipes (menu_item_id, version, is_current) VALUES ($1, 1, true) RETURNING id`, [item2]);
    await q(`INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES ($1, $2, $3)`, [rec2, share.type, per]);
    const dmi2 = await one(`INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) VALUES ($1, $2, $3, $4) RETURNING id`, [E.menu, item2, rec2, quota]);
    return { dmi: dmi2, lot: share.lot, ing: share.ing, type: share.type, name };
  }
  const ing = await one(`INSERT INTO ingredients (name, base_unit, is_expiry_tracked) VALUES ($1, 'u', false) RETURNING id`, [name]);
  const type = await one(`INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type) VALUES ($1, $2, 'DIRECT') RETURNING id`, [ing, name]);
  const rcp = await one(`INSERT INTO stock_receipts (created_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
  await q(`INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit) VALUES ($1, $2, $3, 'u', 1, 1)`, [rcp, ing, stock]);
  await q(`SELECT rc_confirm_receipt($1, $2)`, [rcp, E.MANAGER]);
  const iss = await one(`INSERT INTO stock_issues (issued_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
  await q(`INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) SELECT $1, b.id, $3, 'DIRECT' FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = $2`, [iss, rcp, stock]);
  await q(`SELECT rc_issue_stock($1, $2)`, [iss, E.MANAGER]);
  await q(`SELECT rc_receive_issue($1, $2)`, [iss, E.KITCHEN]);
  const lot = await one(`SELECT id FROM kitchen_batches WHERE kitchen_ingredient_type_id = $1`, [type]);
  const item = await one(`INSERT INTO menu_items (menu_category_id, name, price) VALUES ($1, $2, 30000) RETURNING id`, [E.cat, name]);
  const rec = await one(`INSERT INTO recipes (menu_item_id, version, is_current) VALUES ($1, 1, true) RETURNING id`, [item]);
  await q(`INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES ($1, $2, $3)`, [rec, type, per]);
  const dmi = await one(`INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) VALUES ($1, $2, $3, $4) RETURNING id`, [E.menu, item, rec, quota]);
  await q(`UPDATE daily_menus SET status = 'PUBLISHED', published_at = now() WHERE id = $1 AND status = 'DRAFT'`, [E.menu]);
  return { dmi, lot, ing, type, name };
}
async function newOrder() {
  const t = await one(`INSERT INTO dining_tables (code, capacity) VALUES ($1, 4) RETURNING id`, [code('T')]);
  const s = await one(`SELECT rc_open_session($1, $2)`, [t, E.WAITER]);
  const o = await one(`SELECT id FROM orders WHERE table_session_id = $1`, [s]);
  return { order: o, session: s, table: t };
}
let keySeq = 0;
async function send(order, dmi, qty = 1) {
  const key = code('K') + '-' + (++keySeq);
  const b = await one(`SELECT (rc_submit_batch($1, $2, $3, jsonb_build_array(jsonb_build_object('daily_menu_item_id', $4::bigint, 'quantity', $5::int))) ->> 'batch_id')::bigint`, [order, E.WAITER, key, dmi, qty]);
  return one(`SELECT id FROM order_items WHERE order_batch_id = $1 ORDER BY id LIMIT 1`, [b]);
}
const status = (item) => one(`SELECT status FROM order_items WHERE id = $1`, [item]);
async function serveItem(item) {
  await q(`SELECT rc_accept_item($1, $2)`, [item, E.KITCHEN]);
  await q(`SELECT rc_start_cooking($1, $2)`, [item, E.KITCHEN]);
  await q(`SELECT rc_finish_dish($1, $2)`, [item, E.KITCHEN]);
  await q(`SELECT rc_serve_item($1, $2)`, [item, E.WAITER]);
}
// Đơn có một dòng đã phục vụ và hóa đơn PAYMENT_PENDING
async function pendingInvoice(dish) {
  const o = await newOrder();
  const it = await send(o.order, dish.dmi);
  await serveItem(it);
  const inv = await one(`SELECT rc_checkout_invoice($1, $2)`, [o.order, E.CASHIER]);
  return { ...o, item: it, inv };
}
const lotState = async (lot) => (await q(`SELECT quantity_on_hand::float q, reserved_quantity::float r FROM kitchen_batches WHERE id = $1`, [lot])).rows[0];

await base();
console.log(`Kiểm thử đồng thời v4.1 · tiền tố ${RUN} · ${ITER} vòng cho mỗi ca đua tự do`);

// =====================================================================
group('C1 · QĐ-8 Hai Kitchen cùng nhận suất cuối (quota 1)');
{
  const d = await newDish({ quota: 1 });
  const o1 = await newOrder(), o2 = await newOrder();
  const i1 = await send(o1.order, d.dmi), i2 = await send(o2.order, d.dmi);
  const r = await hold(`SELECT rc_accept_item(${i1}, ${E.KITCHEN})`, `SELECT rc_accept_item(${i2}, ${E.KITCHEN})`);
  check('A giữ khóa dòng thực đơn ngày → B bị chặn chờ khóa', r.blocked);
  check('A nhận được suất cuối (ACCEPTED), B bị từ chối (REJECTED) sau khi A commit', val(r.a) === 'ACCEPTED' && val(r.b) === 'REJECTED', `${val(r.a)}/${val(r.b)}`);
  check('Số suất đã chiếm = 1', Number(await one(`SELECT rc_quota_used($1)`, [d.dmi])) === 1);
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const dd = await newDish({ quota: 1 });
    const a = await newOrder(), b = await newOrder();
    const x = await send(a.order, dd.dmi), y = await send(b.order, dd.dmi);
    const res = await race([(c) => c.query(`SELECT rc_accept_item($1, $2)`, [x, E.KITCHEN]), (c) => c.query(`SELECT rc_accept_item($1, $2)`, [y, E.KITCHEN])]);
    const vs = res.map((z) => val(z)).sort();
    if (!(res.every((z) => z.ok) && vs[0] === 'ACCEPTED' && vs[1] === 'REJECTED' && Number(await one(`SELECT rc_quota_used($1)`, [dd.dmi])) === 1)) { bad++; unexpected++; }
  }
  check(`Đua tự do ${ITER} vòng: luôn đúng 1 ACCEPTED + 1 REJECTED, quota không vượt, không lỗi`, bad === 0, `${bad} vòng sai`);
}

group('C2 · Hai giao dịch cùng đặt giữ nguyên liệu cuối (tồn bếp còn 1, quota không giới hạn)');
{
  const d = await newDish({ quota: null, stock: 1 });
  const i1 = await send((await newOrder()).order, d.dmi), i2 = await send((await newOrder()).order, d.dmi);
  const r = await hold(`SELECT rc_accept_item(${i1}, ${E.KITCHEN})`, `SELECT rc_accept_item(${i2}, ${E.KITCHEN})`);
  check('B bị chặn chờ khóa lô bếp', r.blocked);
  check('Một ACCEPTED, một REJECTED (thiếu nguyên liệu)', [val(r.a), val(r.b)].sort().join() === 'ACCEPTED,REJECTED', `${val(r.a)}/${val(r.b)}`);
  const st = await lotState(d.lot);
  check('reserved ≤ on_hand, đang giữ đúng 1', st.r === 1 && st.q === 1);
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const dd = await newDish({ quota: null, stock: 1 });
    const x = await send((await newOrder()).order, dd.dmi), y = await send((await newOrder()).order, dd.dmi);
    const res = await race([(c) => c.query(`SELECT rc_accept_item($1, $2)`, [x, E.KITCHEN]), (c) => c.query(`SELECT rc_accept_item($1, $2)`, [y, E.KITCHEN])]);
    const s2 = await lotState(dd.lot);
    if (!(res.every((z) => z.ok) && res.map(val).sort().join() === 'ACCEPTED,REJECTED' && s2.r === 1 && s2.q === 1)) { bad++; unexpected++; }
  }
  check(`Đua tự do ${ITER} vòng: không bán vượt tồn, không tồn âm, không lỗi CHECK`, bad === 0, `${bad} vòng sai`);
  // hai món KHÁC NHAU (hai dòng thực đơn ngày khác nhau → không chung khóa quota) cùng cần lô bếp cuối: chỉ khóa lô bảo vệ
  {
    const base = await newDish({ quota: null, stock: 1 });
    const other = await newDish({ quota: null, share: base });
    const x = await send((await newOrder()).order, base.dmi), y = await send((await newOrder()).order, other.dmi);
    const r2 = await hold(`SELECT rc_accept_item(${x}, ${E.KITCHEN})`, `SELECT rc_accept_item(${y}, ${E.KITCHEN})`);
    const st2 = await lotState(base.lot);
    check('Hai món KHÁC NHAU cùng lô cuối: B bị chặn; một ACCEPTED, một REJECTED (không lỗi CHECK); đang giữ đúng 1',
      r2.blocked && r2.a.ok && r2.b.ok && [val(r2.a), val(r2.b)].sort().join() === 'ACCEPTED,REJECTED' && st2.r === 1 && st2.q === 1, JSON.stringify(r2));
    let bad2 = 0;
    for (let k = 0; k < ITER; k++) {
      const b1 = await newDish({ quota: null, stock: 1 }), b2 = await newDish({ quota: null, share: b1 });
      const p = await send((await newOrder()).order, b1.dmi), s = await send((await newOrder()).order, b2.dmi);
      const res = await race([(c) => c.query(`SELECT rc_accept_item($1, $2)`, [p, E.KITCHEN]), (c) => c.query(`SELECT rc_accept_item($1, $2)`, [s, E.KITCHEN])]);
      const s3 = await lotState(b1.lot);
      if (!(res.every((z) => z.ok) && res.map(val).sort().join() === 'ACCEPTED,REJECTED' && s3.r === 1 && s3.q === 1)) { bad2++; unexpected++; }
    }
    check(`Đua tự do ${ITER} vòng với hai món khác nhau cùng lô cuối: không bán vượt tồn, không lỗi CHECK`, bad2 === 0, `${bad2} vòng sai`);
  }
}

group('C3 · Trừ một lô bếp: nấu trùng một món / hai món cùng lô');
{
  const d = await newDish({ quota: null, stock: 10 });
  const it = await send((await newOrder()).order, d.dmi);
  await q(`SELECT rc_accept_item($1, $2)`, [it, E.KITCHEN]);
  const r = await hold(`SELECT rc_start_cooking(${it}, ${E.KITCHEN})`, `SELECT rc_start_cooking(${it}, ${E.KITCHEN})`);
  check('B chờ khóa dòng món của A', r.blocked);
  check('A nấu được; B bị từ chối RC409 (không nấu lần hai)', r.a.ok && !r.b.ok && r.b.code === 'RC409', JSON.stringify(r.b));
  const st = await lotState(d.lot);
  check('Tồn chỉ bị trừ 1 lần (9) và đúng 1 dòng COOK', st.q === 9 && Number(await one(`SELECT count(*) FROM stock_movements WHERE order_item_id = $1 AND movement_type = 'COOK'`, [it])) === 1);
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const dd = await newDish({ quota: null, stock: 2 });
    const x = await send((await newOrder()).order, dd.dmi), y = await send((await newOrder()).order, dd.dmi);
    await q(`SELECT rc_accept_item($1, $2)`, [x, E.KITCHEN]); await q(`SELECT rc_accept_item($1, $2)`, [y, E.KITCHEN]);
    const res = await race([(c) => c.query(`SELECT rc_start_cooking($1, $2)`, [x, E.KITCHEN]), (c) => c.query(`SELECT rc_start_cooking($1, $2)`, [y, E.KITCHEN])]);
    const s2 = await lotState(dd.lot);
    const ledger = Number(await one(`SELECT COALESCE(SUM(quantity_delta), 0) FROM stock_movements WHERE kitchen_batch_id = $1`, [dd.lot]));
    if (!(res.every((z) => z.ok) && s2.q === 0 && s2.r === 0 && ledger === 0)) { bad++; unexpected++; }
  }
  check(`Hai món cùng lô nấu đồng thời ${ITER} vòng: tồn về 0, sổ khớp, không âm`, bad === 0, `${bad} vòng sai`);
}

group('C4 · QĐ-2/8 Hủy ↔ bắt đầu nấu cùng lúc');
{
  let cancelWins = 0, cookWins = 0, bad = 0;
  for (let k = 0; k < ITER * 2; k++) {
    const d = await newDish({ quota: 1, stock: 5 });
    const it = await send((await newOrder()).order, d.dmi);
    await q(`SELECT rc_accept_item($1, $2)`, [it, E.KITCHEN]);
    const res = await race([(c) => c.query(`SELECT rc_cancel_item($1, $2, 'đua')`, [it, E.KITCHEN]), (c) => c.query(`SELECT rc_start_cooking($1, $2)`, [it, E.KITCHEN])]);
    const st = await status(it), lot = await lotState(d.lot), cooks = Number(await one(`SELECT count(*) FROM stock_movements WHERE order_item_id = $1 AND movement_type = 'COOK'`, [it]));
    const used = Number(await one(`SELECT rc_quota_used($1)`, [d.dmi]));
    if (st === 'CANCELLED' && cooks === 0 && lot.q === 5 && lot.r === 0 && used === 0 && res[0].ok && !res[1].ok && res[1].code === 'RC409') cancelWins++;
    else if (st === 'COOKING' && cooks === 1 && lot.q === 4 && lot.r === 0 && used === 1 && res[1].ok && !res[0].ok && res[0].code === 'RC409') cookWins++;
    else { bad++; unexpected++; }
  }
  check(`Luôn đúng một bên thắng (hủy ${cancelWins} / nấu ${cookWins}); hủy thắng → suất được giải phóng, nấu thắng → giữ suất; ${ITER * 2} vòng`, bad === 0, `${bad} vòng sai`);
  // chiều nào thắng cũng phải đúng: kiểm tra xác định cả hai chiều (đua tự do thường thiên về bên nhẹ hơn)
  {
    const d1 = await newDish({ quota: 1, stock: 5 });
    const a = await send((await newOrder()).order, d1.dmi); await q(`SELECT rc_accept_item($1, $2)`, [a, E.KITCHEN]);
    const r = await hold(`SELECT rc_cancel_item(${a}, ${E.KITCHEN}, 'giữ khóa')`, `SELECT rc_start_cooking(${a}, ${E.KITCHEN})`);
    const lot = await lotState(d1.lot);
    check('Hủy giữ khóa trước: nấu bị chặn rồi bị từ chối RC409; suất được giải phóng, không có sổ COOK, tồn nguyên',
      r.blocked && r.a.ok && !r.b.ok && r.b.code === 'RC409' && (await status(a)) === 'CANCELLED' && Number(await one(`SELECT rc_quota_used($1)`, [d1.dmi])) === 0
      && lot.q === 5 && lot.r === 0 && Number(await one(`SELECT count(*) FROM stock_movements WHERE order_item_id = $1`, [a])) === 0, JSON.stringify(r));
    const d2 = await newDish({ quota: 1, stock: 5 });
    const b = await send((await newOrder()).order, d2.dmi); await q(`SELECT rc_accept_item($1, $2)`, [b, E.KITCHEN]);
    const r2 = await hold(`SELECT rc_start_cooking(${b}, ${E.KITCHEN})`, `SELECT rc_cancel_item(${b}, ${E.KITCHEN}, 'muộn')`);
    const lot2 = await lotState(d2.lot);
    check('Nấu giữ khóa trước: hủy bị chặn rồi bị từ chối RC409 (chỉ Manager hủy ngoại lệ); quota giữ nguyên, tồn đã trừ 1 lần',
      r2.blocked && r2.a.ok && !r2.b.ok && r2.b.code === 'RC409' && (await status(b)) === 'COOKING' && Number(await one(`SELECT rc_quota_used($1)`, [d2.dmi])) === 1
      && lot2.q === 4 && lot2.r === 0, JSON.stringify(r2));
  }
}

group('C5 · QĐ-4 Gọi món ↔ chốt hóa đơn; Kitchen nhận món ↔ Cashier chốt hóa đơn');
{
  const d = await newDish({ quota: null, stock: 100 });
  const o = await newOrder(); const a = await send(o.order, d.dmi); await serveItem(a);
  const r = await hold(`SELECT rc_checkout_invoice(${o.order}, ${E.CASHIER})`, `SELECT rc_submit_batch(${o.order}, ${E.WAITER}, '${code('K')}', jsonb_build_array(jsonb_build_object('daily_menu_item_id', ${d.dmi}::bigint, 'quantity', 1)))`);
  check('Waiter gửi món bị chặn chờ khóa đơn khi Cashier đang chốt', r.blocked);
  check('Chốt thành công; gửi món bị từ chối RC409 (đang thanh toán)', r.a.ok && !r.b.ok && r.b.code === 'RC409', JSON.stringify(r.b));
  let bad = 0, oneOk = 0;
  for (let k = 0; k < ITER; k++) {
    const oo = await newOrder(); const x = await send(oo.order, d.dmi); await serveItem(x);
    const key = code('K');
    const res = await race([
      (c) => c.query(`SELECT rc_checkout_invoice($1, $2)`, [oo.order, E.CASHIER]),
      (c) => c.query(`SELECT rc_submit_batch($1, $2, $3, jsonb_build_array(jsonb_build_object('daily_menu_item_id', $4::bigint, 'quantity', 1)))`, [oo.order, E.WAITER, key, d.dmi]),
    ]);
    const succ = res.filter((z) => z.ok).length;
    const inv = (await q(`SELECT id, status FROM invoices WHERE order_id = $1`, [oo.order])).rows[0];
    const pend = Number(await one(`SELECT count(*) FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id WHERE b.order_id = $1 AND oi.status = 'PENDING'`, [oo.order]));
    const served = Number(await one(`SELECT count(*) FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id WHERE b.order_id = $1 AND oi.status = 'SERVED'`, [oo.order]));
    const lines = inv ? Number(await one(`SELECT count(*) FROM invoice_items WHERE invoice_id = $1`, [inv.id])) : 0;
    const consistent = succ === 1 && (inv ? (inv.status === 'PAYMENT_PENDING' && pend === 0 && lines === served) : pend === 1);
    if (consistent) oneOk++; else { bad++; unexpected++; }
  }
  check(`Đua tự do ${ITER} vòng: luôn đúng một bên thành công; không có món lọt ra ngoài hóa đơn`, bad === 0, `${bad} vòng sai`);
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const oo = await newOrder(); const x = await send(oo.order, d.dmi); await serveItem(x);
    const y = await send(oo.order, d.dmi);                       // y PENDING
    const res = await race([(c) => c.query(`SELECT rc_accept_item($1, $2)`, [y, E.KITCHEN]), (c) => c.query(`SELECT rc_checkout_invoice($1, $2)`, [oo.order, E.CASHIER])]);
    const inv = await one(`SELECT count(*) FROM invoices WHERE order_id = $1`, [oo.order]);
    if (!(res[0].ok && !res[1].ok && res[1].code === 'RC409' && Number(inv) === 0)) { bad++; unexpected++; }
  }
  check(`Kitchen nhận món ↔ chốt hóa đơn ${ITER} vòng: chốt luôn bị từ chối khi còn món chưa phục vụ, không tạo hóa đơn thiếu`, bad === 0, `${bad} vòng sai`);
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const oo = await newOrder(); const x = await send(oo.order, d.dmi); await serveItem(x);
    const y = await send(oo.order, d.dmi);
    await q(`SELECT rc_accept_item($1, $2)`, [y, E.KITCHEN]); await q(`SELECT rc_start_cooking($1, $2)`, [y, E.KITCHEN]); await q(`SELECT rc_finish_dish($1, $2)`, [y, E.KITCHEN]);
    const res = await race([(c) => c.query(`SELECT rc_serve_item($1, $2)`, [y, E.WAITER]), (c) => c.query(`SELECT rc_checkout_invoice($1, $2)`, [oo.order, E.CASHIER])]);
    const inv = (await q(`SELECT id FROM invoices WHERE order_id = $1`, [oo.order])).rows[0];
    const lines = inv ? Number(await one(`SELECT count(*) FROM invoice_items WHERE invoice_id = $1`, [inv.id])) : 0;
    // phục vụ xong trước → chốt đủ 2 dòng; chốt trước → bị từ chối (món còn READY), không bao giờ có hóa đơn thiếu dòng
    const ok = res[0].ok && ((res[1].ok && lines === 2) || (!res[1].ok && res[1].code === 'RC409' && !inv));
    if (!ok) { bad++; unexpected++; }
  }
  check(`Phục vụ món cuối ↔ chốt hóa đơn ${ITER} vòng: hoặc chốt đủ 2 dòng hoặc bị từ chối, không bao giờ thiếu dòng`, bad === 0, `${bad} vòng sai`);
}

group('C6 · Tiền mặt ↔ webhook QR; hủy QR ↔ webhook; thu trùng');
{
  const d = await newDish({ quota: null, stock: 200 });
  // (a) QR đã hủy → webhook đến muộn đua với thu tiền mặt
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const p = await pendingInvoice(d);
    const ref = await one(`SELECT rc_start_qr($1, $2)`, [p.inv, E.CASHIER]);
    await q(`SELECT rc_cancel_payment_request((SELECT id FROM payment_requests WHERE payment_reference = $1), $2, 'đổi sang tiền mặt')`, [ref, E.CASHIER]);
    const res = await race([
      (c) => c.query(`SELECT rc_pay_cash($1, $2, 100000)`, [p.inv, E.CASHIER]),
      (c) => c.query(`SELECT rc_process_verified_qr($1, $2, 30000, '{}')`, [code('FT'), ref]),
    ]);
    const pays = (await q(`SELECT method FROM payments WHERE invoice_id = $1`, [p.inv])).rows;
    const ok = res[0].ok && res[1].ok && val(res[1]) === 'AFTER_CANCEL' && pays.length === 1 && pays[0].method === 'CASH'
      && (await one(`SELECT status FROM invoices WHERE id = $1`, [p.inv])) === 'PAID';
    if (!ok) { bad++; unexpected++; }
  }
  check(`QR đến muộn ↔ tiền mặt ${ITER} vòng: đúng 1 payment (CASH), webhook = AFTER_CANCEL, hóa đơn PAID`, bad === 0, `${bad} vòng sai`);
  // (b) webhook hợp lệ ↔ hủy yêu cầu QR
  let qrWins = 0, cancelWins = 0; bad = 0;
  for (let k = 0; k < ITER; k++) {
    const p = await pendingInvoice(d);
    const ref = await one(`SELECT rc_start_qr($1, $2)`, [p.inv, E.CASHIER]);
    const reqId = await one(`SELECT id FROM payment_requests WHERE payment_reference = $1`, [ref]);
    const res = await race([
      (c) => c.query(`SELECT rc_process_verified_qr($1, $2, 30000, '{}')`, [code('FT'), ref]),
      (c) => c.query(`SELECT rc_cancel_payment_request($1, $2, 'khách hủy')`, [reqId, E.CASHIER]),
    ]);
    const pays = Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv]));
    const rs = await one(`SELECT status FROM payment_requests WHERE id = $1`, [reqId]);
    if (val(res[0]) === 'PROCESSED' && !res[1].ok && res[1].code === 'RC409' && pays === 1 && rs === 'PAID') qrWins++;
    else if (val(res[0]) === 'AFTER_CANCEL' && res[1].ok && pays === 0 && rs === 'CANCELLED') cancelWins++;
    else { bad++; unexpected++; }
  }
  check(`Webhook ↔ hủy QR ${ITER} vòng (QR ${qrWins} / hủy ${cancelWins}): luôn nhất quán, không vừa thu vừa hủy`, bad === 0, `${bad} vòng sai`);
  {
    const p = await pendingInvoice(d);
    const ref = await one(`SELECT rc_start_qr($1, $2)`, [p.inv, E.CASHIER]);
    const reqId = await one(`SELECT id FROM payment_requests WHERE payment_reference = $1`, [ref]);
    const r = await hold(`SELECT rc_process_verified_qr('${code('FT')}', '${ref}', 30000, '{}')`, `SELECT rc_cancel_payment_request(${reqId}, ${E.CASHIER}, 'khách hủy')`);
    check('Webhook hợp lệ giữ khóa trước: hủy QR bị chặn rồi bị từ chối RC409; thanh toán QR thành công, hóa đơn PAID',
      r.blocked && val(r.a) === 'PROCESSED' && !r.b.ok && r.b.code === 'RC409' && (await one(`SELECT status FROM invoices WHERE id = $1`, [p.inv])) === 'PAID', JSON.stringify(r));
    const p2 = await pendingInvoice(d);
    const ref2 = await one(`SELECT rc_start_qr($1, $2)`, [p2.inv, E.CASHIER]);
    const req2 = await one(`SELECT id FROM payment_requests WHERE payment_reference = $1`, [ref2]);
    const r2 = await hold(`SELECT rc_cancel_payment_request(${req2}, ${E.CASHIER}, 'khách hủy')`, `SELECT rc_process_verified_qr('${code('FT')}', '${ref2}', 30000, '{}')`);
    check('Hủy QR giữ khóa trước: webhook bị chặn rồi trả AFTER_CANCEL (sự cố đối soát), không tạo payment, hóa đơn vẫn PAYMENT_PENDING',
      r2.blocked && r2.a.ok && val(r2.b) === 'AFTER_CANCEL' && Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p2.inv])) === 0
      && (await one(`SELECT status FROM invoices WHERE id = $1`, [p2.inv])) === 'PAYMENT_PENDING', JSON.stringify(r2));
  }
  // (c) hai lần thu tiền mặt cùng lúc
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const p = await pendingInvoice(d);
    const res = await race([(c) => c.query(`SELECT rc_pay_cash($1, $2, 100000)`, [p.inv, E.CASHIER]), (c) => c.query(`SELECT rc_pay_cash($1, $2, 100000)`, [p.inv, E.CASHIER])]);
    const pays = Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv]));
    const tbl = await one(`SELECT status FROM dining_tables WHERE id = $1`, [p.table]);
    if (!(res.filter((z) => z.ok).length === 1 && res.find((z) => !z.ok).code === 'RC409' && pays === 1 && tbl === 'AVAILABLE')) { bad++; unexpected++; }
  }
  check(`Thu tiền mặt hai lần cùng lúc ${ITER} vòng: đúng 1 payment, bàn đóng 1 lần`, bad === 0, `${bad} vòng sai`);
}

group('C7 · Webhook QR trùng / đến cùng lúc (idempotent)');
{
  const d = await newDish({ quota: null, stock: 100 });
  const p = await pendingInvoice(d);
  const ref = await one(`SELECT rc_start_qr($1, $2)`, [p.inv, E.CASHIER]);
  const ft = code('FT');
  const r = await hold(`SELECT rc_process_verified_qr('${ft}', '${ref}', 30000, '{}')`, `SELECT rc_process_verified_qr('${ft}', '${ref}', 30000, '{}')`);
  check('B (cùng mã giao dịch) bị chặn chờ A', r.blocked);
  check('Cả hai trả PROCESSED, không lỗi 23505 (v4.1 trả kết quả đã lưu)', val(r.a) === 'PROCESSED' && val(r.b) === 'PROCESSED', `${JSON.stringify(r.a)} ${JSON.stringify(r.b)}`);
  check('Chỉ 1 payment, 1 sự kiện webhook', Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv])) === 1 && Number(await one(`SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_code = $1`, [ft])) === 1);
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const pp = await pendingInvoice(d);
    const rf = await one(`SELECT rc_start_qr($1, $2)`, [pp.inv, E.CASHIER]);
    const f = code('FT');
    const res = await race([1, 2, 3, 4].map(() => (c) => c.query(`SELECT rc_process_verified_qr($1, $2, 30000, '{}')`, [f, rf])));
    const pays = Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [pp.inv]));
    if (!(res.every((z) => z.ok && val(z) === 'PROCESSED') && pays === 1)) { bad++; unexpected++; }
  }
  check(`4 webhook cùng mã đến cùng lúc ${ITER} vòng: cả 4 trả PROCESSED, 1 payment`, bad === 0, `${bad} vòng sai`);
  // mã tham chiếu lạ, hai webhook cùng mã đồng thời
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const f = code('FT');
    const res = await race([1, 2, 3].map(() => (c) => c.query(`SELECT rc_process_verified_qr($1, 'KHONGCOMA', 1000, '{}')`, [f])));
    const ev = Number(await one(`SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_code = $1`, [f]));
    if (!(res.every((z) => z.ok && val(z) === 'MISMATCH') && ev === 1)) { bad++; unexpected++; }
  }
  check(`Webhook mã tham chiếu lạ trùng đồng thời ${ITER} vòng: cùng kết quả MISMATCH, 1 sự kiện`, bad === 0, `${bad} vòng sai`);
}

group('C8 · QĐ-3 Khách bỏ về ↔ thanh toán; thứ tự khóa orders → invoices');
{
  const d = await newDish({ quota: null, stock: 300 });
  let paid = 0, unc = 0, bad = 0;
  for (let k = 0; k < ITER * 2; k++) {
    const p = await pendingInvoice(d);
    const res = await race([(c) => c.query(`SELECT rc_pay_cash($1, $2, 100000)`, [p.inv, E.CASHIER]), (c) => c.query(`SELECT rc_abandon_session($1, $2, 'bỏ về')`, [p.session, E.MANAGER])]);
    const inv = (await q(`SELECT status FROM invoices WHERE id = $1`, [p.inv])).rows[0].status;
    const pays = Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv]));
    if (inv === 'PAID' && pays === 1 && res[0].ok && !res[1].ok && res[1].code === 'RC409') paid++;
    else if (inv === 'UNCOLLECTED' && pays === 0 && res[1].ok && !res[0].ok && res[0].code === 'RC409') unc++;
    else { bad++; unexpected++; }
  }
  check(`Thanh toán ↔ bỏ về ${ITER * 2} vòng (PAID ${paid} / UNCOLLECTED ${unc}): luôn một trong hai, không vừa PAID vừa bỏ về, không deadlock`, bad === 0, `${bad} vòng sai`);
  {
    const p = await pendingInvoice(d);
    const r = await hold(`SELECT rc_abandon_session(${p.session}, ${E.MANAGER}, 'bỏ về')`, `SELECT rc_pay_cash(${p.inv}, ${E.CASHIER}, 100000)`);
    check('Bỏ về giữ khóa trước: thu tiền mặt bị chặn rồi bị từ chối RC409; hóa đơn UNCOLLECTED, không có payments',
      r.blocked && r.a.ok && !r.b.ok && r.b.code === 'RC409' && (await one(`SELECT status FROM invoices WHERE id = $1`, [p.inv])) === 'UNCOLLECTED'
      && Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv])) === 0, JSON.stringify(r));
    const p2 = await pendingInvoice(d);
    const r2 = await hold(`SELECT rc_pay_cash(${p2.inv}, ${E.CASHIER}, 100000)`, `SELECT rc_abandon_session(${p2.session}, ${E.MANAGER}, 'bỏ về')`);
    check('Thu tiền mặt giữ khóa trước: bỏ về bị chặn rồi bị từ chối RC409; hóa đơn PAID, 1 payment',
      r2.blocked && r2.a.ok && !r2.b.ok && r2.b.code === 'RC409' && (await one(`SELECT status FROM invoices WHERE id = $1`, [p2.inv])) === 'PAID'
      && Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p2.inv])) === 1, JSON.stringify(r2));
  }
  // chứng minh xác định: các hàm khóa orders TRƯỚC invoices. Giữ khóa orders ở A; nếu B đã giữ invoices thì NOWAIT ở A sẽ lỗi 55P03.
  const probes = [
    ['rc_pay_cash', (p) => `SELECT rc_pay_cash(${p.inv}, ${E.CASHIER}, 100000)`],
    ['rc_start_qr', (p) => `SELECT rc_start_qr(${p.inv}, ${E.CASHIER})`],
    ['rc_reopen_invoice', (p) => `SELECT rc_reopen_invoice(${p.inv}, ${E.CASHIER}, 'kiểm thử')`],
    ['rc_process_verified_qr', (p) => `SELECT rc_process_verified_qr('${code('FT')}', 'KHONGCOMA', 1, '{}')`],
    ['rc_abandon_session', (p) => `SELECT rc_abandon_session(${p.session}, ${E.MANAGER}, 'kiểm thử')`],
    ['rc_cancel_item_exception', (p) => `SELECT rc_cancel_item_exception(${p.item}, ${E.MANAGER}, 'kiểm thử')`],
    ['rc_checkout_invoice', (p) => `SELECT rc_checkout_invoice(${p.order}, ${E.CASHIER})`],
    ['rc_waive_invoice', (p) => `SELECT rc_waive_invoice(${p.order}, ${E.MANAGER}, 'kiểm thử')`],
  ];
  for (const [name, build] of probes) {
    const p = await pendingInvoice(d);
    if (name === 'rc_process_verified_qr') p.ref = await one(`SELECT rc_start_qr($1, $2)`, [p.inv, E.CASHIER]);   // cần yêu cầu QR để hàm phải khóa chuỗi hóa đơn
    const text = name === 'rc_process_verified_qr' ? `SELECT rc_process_verified_qr('${code('FT')}', '${p.ref}', 1, '{}')` : build(p);
    const a = await open(), b = await open(), obs = await open();
    try {
      const bPid = (await b.query('select pg_backend_pid() pid')).rows[0].pid;
      await a.query('BEGIN');
      await a.query(`SELECT 1 FROM orders WHERE id = ${p.order} FOR UPDATE`);                   // A giữ khóa orders (mức 1)
      const pb = wrap(b.query(text));
      const blocked = await waitBlocked(obs, bPid);
      const nowait = await wrap(a.query(`SELECT 1 FROM invoices WHERE order_id = ${p.order} FOR UPDATE NOWAIT`));   // B đã giữ invoices chưa?
      await a.query('ROLLBACK');
      const rb = await pb;
      check(`${name}: bị chặn ở khóa orders và CHƯA giữ khóa invoices (orders → invoices)`, blocked && nowait.ok, `blocked=${blocked} nowait=${JSON.stringify(nowait)}`);
      if (!rb.ok && rb.code === '40P01') unexpected++;
    } finally { await a.end(); await b.end(); await obs.end(); }
  }
}

group('C8b · QĐ-2/QĐ-3 Hủy ngoại lệ ↔ chốt hóa đơn; bỏ về ↔ gửi món');
{
  const d = await newDish({ quota: null, stock: 400 });
  // Hủy ngoại lệ món SERVED ↔ chốt hóa đơn: hóa đơn không bao giờ chứa dòng đã CANCELLED
  const mk = async () => { const o = await newOrder(); const x = await send(o.order, d.dmi), y = await send(o.order, d.dmi); await serveItem(x); await serveItem(y); return { ...o, x, y }; };
  {
    const o = await mk();
    const r = await hold(`SELECT rc_checkout_invoice(${o.order}, ${E.CASHIER})`, `SELECT rc_cancel_item_exception(${o.x}, ${E.MANAGER}, 'miễn phí')`);
    check('Chốt hóa đơn giữ khóa trước: hủy ngoại lệ bị chặn rồi bị từ chối RC409 (phải mở lại hóa đơn); hóa đơn đủ 2 dòng',
      r.blocked && r.a.ok && !r.b.ok && r.b.code === 'RC409' && (await status(o.x)) === 'SERVED' && Number(await one(`SELECT count(*) FROM invoice_items WHERE invoice_id = (SELECT id FROM invoices WHERE order_id = $1)`, [o.order])) === 2, JSON.stringify(r));
    const o2 = await mk();
    const r2 = await hold(`SELECT rc_cancel_item_exception(${o2.x}, ${E.MANAGER}, 'miễn phí')`, `SELECT rc_checkout_invoice(${o2.order}, ${E.CASHIER})`);
    const inv = (await q(`SELECT id, total::float t FROM invoices WHERE order_id = $1`, [o2.order])).rows[0];
    check('Hủy ngoại lệ giữ khóa trước: chốt hóa đơn bị chặn rồi chỉ gồm món còn hiệu lực (1 dòng, 30.000)',
      r2.blocked && r2.a.ok && r2.b.ok && (await status(o2.x)) === 'CANCELLED' && inv && inv.t === 30000
      && Number(await one(`SELECT count(*) FROM invoice_items WHERE invoice_id = $1`, [inv.id])) === 1, JSON.stringify(r2));
  }
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const o = await mk();
    const res = await race([(c) => c.query(`SELECT rc_checkout_invoice($1, $2)`, [o.order, E.CASHIER]), (c) => c.query(`SELECT rc_cancel_item_exception($1, $2, 'đua')`, [o.x, E.MANAGER])]);
    const inv = (await q(`SELECT id FROM invoices WHERE order_id = $1`, [o.order])).rows[0];
    const lines = inv ? (await q(`SELECT ii.order_item_id id FROM invoice_items ii WHERE ii.invoice_id = $1`, [inv.id])).rows.map((z) => Number(z.id)) : [];
    const stX = await status(o.x);
    const allServed = (await q(`SELECT status FROM order_items WHERE id = ANY($1)`, [lines])).rows.every((z) => z.status === 'SERVED');
    // hoặc: chốt trước (2 dòng, X vẫn SERVED, hủy bị RC409) hoặc: hủy trước (X CANCELLED, hóa đơn 1 dòng chỉ Y)
    const ok = (res[0].ok && !res[1].ok && res[1].code === 'RC409' && stX === 'SERVED' && lines.length === 2 && allServed)
            || (res[0].ok && res[1].ok && stX === 'CANCELLED' && lines.length === 1 && lines[0] === Number(o.y) && allServed);
    if (!ok) { bad++; unexpected++; }
  }
  check(`Hủy ngoại lệ ↔ chốt hóa đơn ${ITER} vòng: hóa đơn luôn là bản chụp đúng các dòng SERVED còn hiệu lực`, bad === 0, `${bad} vòng sai`);
  // Bỏ về ↔ gửi món: không bao giờ còn dòng PENDING trong đơn đã CLOSED
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const o = await newOrder(); const s = await send(o.order, d.dmi); await serveItem(s);
    const res = await race([
      (c) => c.query(`SELECT rc_abandon_session($1, $2, 'bỏ về')`, [o.session, E.MANAGER]),
      (c) => c.query(`SELECT rc_submit_batch($1, $2, $3, jsonb_build_array(jsonb_build_object('daily_menu_item_id', $4::bigint, 'quantity', 1)))`, [o.order, E.WAITER, code('K'), d.dmi]),
    ]);
    const open = Number(await one(`SELECT count(*) FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id WHERE b.order_id = $1 AND oi.status IN ('PENDING','ACCEPTED','COOKING','READY')`, [o.order]));
    const ost = await one(`SELECT status FROM orders WHERE id = $1`, [o.order]);
    const inv = await one(`SELECT status FROM invoices WHERE order_id = $1`, [o.order]);
    const tbl = await one(`SELECT status FROM dining_tables WHERE id = $1`, [o.table]);
    const ok = res[0].ok && ost === 'CLOSED' && inv === 'UNCOLLECTED' && open === 0 && tbl === 'AVAILABLE' && (res[1].ok || res[1].code === 'RC409');
    if (!ok) { bad++; unexpected++; }
  }
  check(`Bỏ về ↔ gửi món ${ITER} vòng: đơn CLOSED không còn dòng mở (món gửi chen vào được hủy cùng; hoặc bị từ chối RC409)`, bad === 0, `${bad} vòng sai`);
}

group('C9 · Mở bàn, chứng từ kho: xác nhận trùng đồng thời');
{
  const t = await one(`INSERT INTO dining_tables (code, capacity) VALUES ($1, 4) RETURNING id`, [code('T')]);
  const res = await race([(c) => c.query(`SELECT rc_open_session($1, $2)`, [t, E.WAITER]), (c) => c.query(`SELECT rc_open_session($1, $2)`, [t, E.WAITER])]);
  check('Hai Waiter mở cùng một bàn: đúng 1 phiên OPEN, người kia nhận RC409', res.filter((z) => z.ok).length === 1 && res.find((z) => !z.ok).code === 'RC409'
    && Number(await one(`SELECT count(*) FROM table_sessions WHERE dining_table_id = $1 AND status = 'OPEN'`, [t])) === 1);
  const d = await newDish({ quota: null, stock: 50 });
  // nhập kho: hai lần xác nhận
  const rcp = await one(`INSERT INTO stock_receipts (created_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
  await q(`INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit) VALUES ($1, $2, 10, 'u', 1, 1)`, [rcp, d.ing]);
  const r1 = await race([(c) => c.query(`SELECT rc_confirm_receipt($1, $2)`, [rcp, E.MANAGER]), (c) => c.query(`SELECT rc_confirm_receipt($1, $2)`, [rcp, E.MANAGER])]);
  check('Xác nhận phiếu nhập hai lần đồng thời: 1 thành công, 1 RC409; chỉ 1 lô RAW +10 và 1 dòng sổ RECEIPT',
    r1.filter((z) => z.ok).length === 1 && Number(await one(`SELECT count(*) FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = $1`, [rcp])) === 1
    && Number(await one(`SELECT count(*) FROM stock_movements WHERE event_key LIKE 'RECEIPT:%' AND raw_batch_id = (SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = $1)`, [rcp])) === 1);
  // xuất kho: hai phiếu cùng cần hết lô cuối
  const lotRaw = await one(`SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = $1`, [rcp]);
  const mk = async () => { const i = await one(`INSERT INTO stock_issues (issued_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]); await q(`INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES ($1, $2, 10, 'DIRECT')`, [i, lotRaw]); return i; };
  const i1 = await mk(), i2 = await mk();
  const r2 = await race([(c) => c.query(`SELECT rc_issue_stock($1, $2)`, [i1, E.MANAGER]), (c) => c.query(`SELECT rc_issue_stock($1, $2)`, [i2, E.MANAGER])]);
  check('Hai phiếu xuất cùng cần hết lô RAW cuối: 1 thành công, 1 từ chối; RAW = 0, không âm',
    r2.filter((z) => z.ok).length === 1 && Number(await one(`SELECT quantity_on_hand FROM inventory_batches WHERE id = $1`, [lotRaw])) === 0);
  // nhận bàn giao hai lần
  const okIssue = [i1, i2][r2.findIndex((z) => z.ok)];
  const r3 = await race([(c) => c.query(`SELECT rc_receive_issue($1, $2)`, [okIssue, E.KITCHEN]), (c) => c.query(`SELECT rc_receive_issue($1, $2)`, [okIssue, E.KITCHEN])]);
  check('Nhận bàn giao hai lần đồng thời: 1 thành công, 1 RC409; chỉ 1 lô bếp DIRECT',
    r3.filter((z) => z.ok).length === 1 && Number(await one(`SELECT count(*) FROM kitchen_batches k JOIN stock_issue_items l ON l.id = k.direct_issue_item_id WHERE l.stock_issue_id = $1`, [okIssue])) === 1);
}

group('C10 · Sơ chế / hoàn trả / hủy hàng / kiểm kê đồng thời; không deadlock');
{
  // Mỗi lô thuộc một nguyên liệu riêng (tránh ràng buộc FEFO giữa các lô cùng nguyên liệu)
  const mkLot = async (qty, withType = false) => {
    const ing = await one(`INSERT INTO ingredients (name, base_unit, is_expiry_tracked) VALUES ($1, 'kg', false) RETURNING id`, [code('Raw')]);
    const type = withType ? await one(`INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type) VALUES ($1, $2, 'PREPARED') RETURNING id`, [ing, code('Pre')]) : null;
    const rc = await one(`INSERT INTO stock_receipts (created_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
    await q(`INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit) VALUES ($1, $2, $3, 'kg', 1, 1)`, [rc, ing, qty]);
    await q(`SELECT rc_confirm_receipt($1, $2)`, [rc, E.MANAGER]);
    const lot = await one(`SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = $1`, [rc]);
    return { lot, ing, type };
  };
  // Sơ chế: hai phiếu đầu vào 0.6 trên WIP 1 → một phiếu thắng, WIP còn 0.4
  const L0 = await mkLot(10, true);
  const lot = L0.lot, typePre = L0.type;
  const iss = await one(`INSERT INTO stock_issues (issued_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
  const line = await one(`INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES ($1, $2, 1, 'PREPARE') RETURNING id`, [iss, lot]);
  await q(`SELECT rc_issue_stock($1, $2)`, [iss, E.MANAGER]); await q(`SELECT rc_receive_issue($1, $2)`, [iss, E.KITCHEN]);
  const prep = async (inQty) => {
    const p = await one(`INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, prepared_by_id, request_key) VALUES ($1, $2, $3, $4) RETURNING id`, [line, inQty, E.KITCHEN, code('P')]);
    await q(`INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity) VALUES ($1, $2, $3)`, [p, typePre, inQty - 0.05]);
    return p;
  };
  const p1 = await prep(0.6), p2 = await prep(0.6);
  const r = await race([(c) => c.query(`SELECT rc_confirm_preparation($1, $2)`, [p1, E.KITCHEN]), (c) => c.query(`SELECT rc_confirm_preparation($1, $2)`, [p2, E.KITCHEN])]);
  const wip = Number(await one(`SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = $1`, [line]));
  check('Hai phiếu sơ chế cùng vượt WIP: đúng 1 thắng, bên kia RC409; WIP còn 0,4', r.filter((z) => z.ok).length === 1 && r.find((z) => !z.ok).code === 'RC409' && Math.abs(wip - 0.4) < 1e-9, `wip=${wip} ${JSON.stringify(r)}`);
  const pOk = [p1, p2][r.findIndex((z) => z.ok)];
  const r2 = await race([(c) => c.query(`SELECT rc_confirm_preparation($1, $2)`, [pOk, E.KITCHEN]), (c) => c.query(`SELECT rc_confirm_preparation($1, $2)`, [pOk, E.KITCHEN])]);
  check('Xác nhận lặp cùng phiếu sơ chế: cả hai bị từ chối RC409, không trừ WIP lần hai', r2.every((z) => !z.ok && z.code === 'RC409') && Math.abs(Number(await one(`SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = $1`, [line])) - 0.4) < 1e-9);

  // Deadlock hunt: hai phiếu hoàn trả/hủy hàng/kiểm kê chạm cùng tập lô theo thứ tự ngược nhau
  const big = 20 * (2 * ITER + 6);
  const lots = [(await mkLot(big)).lot, (await mkLot(big)).lot, (await mkLot(big)).lot];
  const mkIssueLines = async () => {
    const i = await one(`INSERT INTO stock_issues (issued_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
    const ids = [];
    for (const l of lots) ids.push(await one(`INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES ($1, $2, 20, 'PREPARE') RETURNING id`, [i, l]));
    await q(`SELECT rc_issue_stock($1, $2)`, [i, E.MANAGER]); await q(`SELECT rc_receive_issue($1, $2)`, [i, E.KITCHEN]);
    return ids;
  };
  let bad = 0;
  for (let k = 0; k < ITER; k++) {
    const ids = await mkIssueLines();
    const mkReturn = async (order) => {
      const rt = await one(`INSERT INTO stock_returns (requested_by_id, reason, request_key) VALUES ($1, 'đua', $2) RETURNING id`, [E.KITCHEN, code('R')]);
      for (const idx of order) await q(`INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, quantity) VALUES ($1, $2, 1)`, [rt, ids[idx]]);
      return rt;
    };
    const ra = await mkReturn([0, 1, 2]), rb = await mkReturn([2, 1, 0]);
    const res = await race([(c) => c.query(`SELECT rc_confirm_return($1, $2)`, [ra, E.MANAGER]), (c) => c.query(`SELECT rc_confirm_return($1, $2)`, [rb, E.MANAGER])]);
    const wipSum = Number(await one(`SELECT SUM(wip_remaining_quantity) FROM stock_issue_items WHERE id = ANY($1)`, [ids]));
    if (!(res.every((z) => z.ok) && Math.abs(wipSum - (60 - 6)) < 1e-9)) { bad++; unexpected++; }
  }
  check(`Hai phiếu hoàn trả chạm cùng ${3} lô theo thứ tự ngược nhau ${ITER} vòng: không deadlock, WIP khớp`, bad === 0, `${bad} vòng sai`);
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const ids = await mkIssueLines();
    const mkAdj = async (order) => {
      const a = await one(`INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key) VALUES ('DAMAGE', 'đua', $1, $2) RETURNING id`, [E.KITCHEN, code('A')]);
      for (const idx of order) await q(`INSERT INTO stock_adjustment_items (stock_adjustment_id, wip_issue_item_id, quantity_delta) VALUES ($1, $2, -1)`, [a, ids[idx]]);
      return a;
    };
    const aa = await mkAdj([0, 1, 2]), ab = await mkAdj([2, 1, 0]);
    const res = await race([(c) => c.query(`SELECT rc_confirm_adjustment($1, $2)`, [aa, E.MANAGER]), (c) => c.query(`SELECT rc_confirm_adjustment($1, $2)`, [ab, E.MANAGER])]);
    if (!res.every((z) => z.ok)) { bad++; unexpected++; }
  }
  check(`Hai phiếu hủy hàng chạm cùng lô theo thứ tự ngược nhau ${ITER} vòng: không deadlock`, bad === 0, `${bad} vòng sai`);
  // kiểm kê trùng
  const ids = await mkIssueLines();
  const cnt = await one(`INSERT INTO stock_counts (created_by_id, request_key, request_hash) VALUES ($1, gen_random_uuid()::text, repeat('0', 64)) RETURNING id`, [E.MANAGER]);
  await q(`INSERT INTO stock_count_items (stock_count_id, wip_issue_item_id, book_quantity, actual_quantity) VALUES ($1, $2, 20, 19)`, [cnt, ids[0]]);
  const rc = await race([(c) => c.query(`SELECT rc_confirm_count($1, $2)`, [cnt, E.MANAGER]), (c) => c.query(`SELECT rc_confirm_count($1, $2)`, [cnt, E.MANAGER])]);
  check('Xác nhận phiếu kiểm kê hai lần đồng thời: 1 thành công, 1 RC409; điều chỉnh một lần',
    rc.filter((z) => z.ok).length === 1 && Number(await one(`SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = $1`, [ids[0]])) === 19);
}

group('C11 · Đóng băng thiết kế: miễn thu ↔ thanh toán, bỏ về ↔ webhook trễ, request_key đồng thời, hai Manager cùng xác nhận');
{
  const d = await newDish({ quota: null, stock: 400 });
  // Miễn thu ↔ thu tiền mặt: không bao giờ cùng thành công
  let paid = 0, waived = 0, bad = 0;
  for (let k = 0; k < ITER * 2; k++) {
    const p = await pendingInvoice(d);
    const res = await race([(c) => c.query(`SELECT rc_pay_cash($1, $2, 100000)`, [p.inv, E.CASHIER]), (c) => c.query(`SELECT rc_waive_invoice($1, $2, 'miễn thu')`, [p.order, E.MANAGER])]);
    const inv = await one(`SELECT status FROM invoices WHERE id = $1`, [p.inv]);
    const pays = Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv]));
    if (inv === 'PAID' && pays === 1 && res[0].ok && !res[1].ok && res[1].code === 'RC409') paid++;
    else if (inv === 'WAIVED' && pays === 0 && res[1].ok && !res[0].ok && res[0].code === 'RC409') waived++;
    else { bad++; unexpected++; }
  }
  check(`Miễn thu ↔ thu tiền mặt ${ITER * 2} vòng (PAID ${paid} / WAIVED ${waived}): đúng một bên thành công, không deadlock`, bad === 0, `${bad} vòng sai`);
  {
    const p = await pendingInvoice(d);
    const r = await hold(`SELECT rc_waive_invoice(${p.order}, ${E.MANAGER}, 'miễn thu')`, `SELECT rc_pay_cash(${p.inv}, ${E.CASHIER}, 100000)`);
    check('Miễn thu giữ khóa trước: thu tiền bị chặn rồi RC409; hóa đơn WAIVED, không payments',
      r.blocked && r.a.ok && !r.b.ok && r.b.code === 'RC409' && (await one(`SELECT status FROM invoices WHERE id = $1`, [p.inv])) === 'WAIVED'
      && Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv])) === 0, JSON.stringify(r));
  }
  // Miễn thu / bỏ về ↔ webhook QR đến cùng lúc: hoặc PAID (bên kia RC409), hoặc WAIVED/UNCOLLECTED + webhook AFTER_CANCEL lưu sự cố
  for (const [label, fn, endStatus] of [['Miễn thu', (p) => [`SELECT rc_waive_invoice($1, $2, 'miễn thu')`, [p.order, E.MANAGER]], 'WAIVED'],
                                        ['Bỏ về', (p) => [`SELECT rc_abandon_session($1, $2, 'bỏ về')`, [p.session, E.MANAGER]], 'UNCOLLECTED']]) {
    let pd = 0, other = 0; bad = 0;
    for (let k = 0; k < ITER; k++) {
      const p = await pendingInvoice(d);
      const ref = await one(`SELECT rc_start_qr($1, $2)`, [p.inv, E.CASHIER]);
      const total = Number(await one(`SELECT total FROM invoices WHERE id = $1`, [p.inv]));
      const ft = code('FT');
      const [sql, args] = fn(p);
      const res = await race([(c) => c.query(sql, args), (c) => c.query(`SELECT rc_process_verified_qr($1, $2, $3, '{}')`, [ft, ref, total])]);
      const inv = await one(`SELECT status FROM invoices WHERE id = $1`, [p.inv]);
      const pays = Number(await one(`SELECT count(*) FROM payments WHERE invoice_id = $1`, [p.inv]));
      const ev = (await q(`SELECT processing_result, resolution_status FROM payment_webhook_events WHERE provider_transaction_code = $1`, [ft])).rows[0];
      if (inv === 'PAID' && pays === 1 && ev && ev.processing_result === 'PROCESSED' && !res[0].ok && res[0].code === 'RC409') pd++;
      else if (inv === endStatus && pays === 0 && res[0].ok && ev && ev.processing_result === 'AFTER_CANCEL' && ev.resolution_status === 'OPEN') other++;
      else { bad++; unexpected++; }
    }
    check(`${label} ↔ webhook QR ${ITER} vòng (PAID ${pd} / ${endStatus} ${other}): không vừa thu vừa ${endStatus}; webhook trễ luôn được lưu để đối chiếu`, bad === 0, `${bad} vòng sai`);
  }
  // request_key đồng thời (QĐ-ID)
  const payload = (qty) => JSON.stringify({ items: [{ ingredient_id: Number(d.ing), input_quantity: qty, input_unit: 'u', conversion_factor: 1, unit_price_per_input_unit: 1 }] });
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const key = code('RK');
    const res = await race([1, 2].map(() => (c) => c.query(`SELECT rc_create_stock_receipt($1, $2, $3::jsonb)`, [E.MANAGER, key, payload(5)])));
    const n = Number(await one(`SELECT count(*) FROM stock_receipts WHERE request_key = $1`, [key]));
    if (!(res.every((z) => z.ok) && val(res[0]) === val(res[1]) && n === 1)) { bad++; unexpected++; }
  }
  check(`Hai lần gửi cùng request_key + cùng payload đồng thời ${ITER} vòng: cùng một phiếu nhập, không nháp trùng`, bad === 0, `${bad} vòng sai`);
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const key = code('RK');
    const res = await race([(c) => c.query(`SELECT rc_create_stock_receipt($1, $2, $3::jsonb)`, [E.MANAGER, key, payload(5)]),
                            (c) => c.query(`SELECT rc_create_stock_receipt($1, $2, $3::jsonb)`, [E.MANAGER, key, payload(6)])]);
    const n = Number(await one(`SELECT count(*) FROM stock_receipts WHERE request_key = $1`, [key]));
    if (!(res.filter((z) => z.ok).length === 1 && res.find((z) => !z.ok).code === 'RC409' && n === 1)) { bad++; unexpected++; }
  }
  check(`Cùng request_key khác payload đồng thời ${ITER} vòng: đúng một phiếu, yêu cầu kia RC409`, bad === 0, `${bad} vòng sai`);
  {
    const key = code('RK');
    const r = await hold(`SELECT rc_create_stock_receipt(${E.MANAGER}, '${key}', '${payload(7)}'::jsonb)`, `SELECT rc_create_stock_receipt(${E.MANAGER}, '${key}', '${payload(7)}'::jsonb)`);
    check('request_key: lần gửi thứ hai chờ lần đầu commit rồi trả đúng phiếu đó', r.blocked && r.a.ok && r.b.ok && val(r.a) === val(r.b), JSON.stringify(r));
  }
  // Hai Manager khác nhau cùng xác nhận một phiếu xuất: một thành công, RAW chỉ trừ một lần
  bad = 0;
  for (let k = 0; k < ITER; k++) {
    const rc = await one(`SELECT rc_create_stock_receipt($1, $2, $3::jsonb)`, [E.MANAGER, code('RK'), payload(10)]);
    await q(`SELECT rc_confirm_receipt($1, $2)`, [rc, E.MANAGER]);
    const lotRaw = await one(`SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.stock_receipt_id = $1`, [rc]);
    const before = Number(await one(`SELECT SUM(quantity_on_hand) FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.ingredient_id = $1`, [d.ing]));
    const iss = await one(`SELECT rc_create_stock_issue($1, $2, $3::jsonb)`, [E.MANAGER, code('RK'),
      JSON.stringify({ items: [{ inventory_batch_id: Number(lotRaw), quantity: 10, fulfillment_mode: 'PREPARE' }] })]);   // xuất hết lô: FEFO không chặn vòng sau
    const res = await race([(c) => c.query(`SELECT rc_issue_stock($1, $2)`, [iss, E.MANAGER]), (c) => c.query(`SELECT rc_issue_stock($1, $2)`, [iss, E.MANAGER2])]);
    const after = Number(await one(`SELECT SUM(quantity_on_hand) FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.ingredient_id = $1`, [d.ing]));
    const outs = Number(await one(`SELECT count(*) FROM stock_movements m JOIN stock_issue_items l ON l.id = m.ref_issue_item_id WHERE l.stock_issue_id = $1 AND m.movement_type = 'ISSUE_OUT'`, [iss]));
    const okN = res.filter((z) => z.ok).length;
    if (okN === 1 && res.find((z) => !z.ok).code === 'RC409' && outs === 1 && Math.abs(before - after - 10) < 1e-9) continue;
    bad++; unexpected++;
  }
  check(`Hai Manager cùng xác nhận một phiếu xuất ${ITER} vòng: tối đa một lần trừ RAW, một bút toán ISSUE_OUT`, bad === 0, `${bad} vòng sai`);
}

group('Tổng hợp');
{
  const inv = await q(`SELECT count(*)::int n FROM stock_movements`);       // chạm DB để chắc kết nối còn tốt
  void inv;
  // bất biến sau toàn bộ các ca đua (docs/02 §9)
  const checks = {
    'I-1 RAW = sổ': `SELECT b.id FROM inventory_batches b LEFT JOIN stock_movements m ON m.raw_batch_id = b.id GROUP BY b.id HAVING b.quantity_on_hand <> COALESCE(SUM(m.quantity_delta), 0)`,
    'I-2 WIP = sổ': `SELECT i.id FROM stock_issue_items i LEFT JOIN stock_movements m ON m.wip_issue_item_id = i.id GROUP BY i.id HAVING i.wip_remaining_quantity <> COALESCE(SUM(m.quantity_delta), 0)`,
    'I-3 bếp = sổ, giữ = reservation ACTIVE': `SELECT k.id FROM kitchen_batches k WHERE k.quantity_on_hand <> (SELECT COALESCE(SUM(m.quantity_delta), 0) FROM stock_movements m WHERE m.kitchen_batch_id = k.id) OR k.reserved_quantity <> (SELECT COALESCE(SUM(r.quantity), 0) FROM stock_reservations r WHERE r.kitchen_batch_id = k.id AND r.status = 'ACTIVE')`,
    'I-4 hóa đơn = Σ dòng': `SELECT v.id FROM invoices v LEFT JOIN invoice_items ii ON ii.invoice_id = v.id GROUP BY v.id HAVING v.total <> COALESCE(SUM(ii.line_total), 0)`,
    'I-5 trạng thái món = sự kiện cuối': `SELECT oi.id FROM order_items oi JOIN LATERAL (SELECT e.to_status FROM order_item_events e WHERE e.order_item_id = oi.id ORDER BY e.id DESC LIMIT 1) l ON true WHERE l.to_status <> oi.status`,
    'I-6 bàn OCCUPIED ⇔ phiên OPEN': `SELECT t.id FROM dining_tables t WHERE (t.status = 'OCCUPIED') <> EXISTS (SELECT 1 FROM table_sessions s WHERE s.dining_table_id = t.id AND s.status = 'OPEN')`,
    'I-7 PAID ⇔ đúng một payment': `SELECT v.id FROM invoices v LEFT JOIN payments p ON p.invoice_id = v.id WHERE (v.status = 'PAID') <> (p.id IS NOT NULL)`,
    'I-8 không vượt quota': `SELECT d.id FROM daily_menu_items d WHERE d.quota_total IS NOT NULL AND rc_quota_used(d.id) > d.quota_total`,
    'I-11 UNCOLLECTED/WAIVED không có payment': `SELECT v.id FROM invoices v WHERE v.status IN ('UNCOLLECTED', 'WAIVED') AND EXISTS (SELECT 1 FROM payments p WHERE p.invoice_id = v.id)`,
    'I-16 không hóa đơn treo của đơn đã kết thúc': `SELECT v.id FROM invoices v JOIN orders o ON o.id = v.order_id WHERE o.status <> 'OPEN' AND v.status IN ('DRAFT', 'PAYMENT_PENDING')`,
    'I-18 mỗi request_key một chứng từ': `SELECT request_key FROM stock_receipts GROUP BY request_key HAVING count(*) > 1`,
    'I-13 CONSUMED ⇔ có sổ COOK': `SELECT r.id FROM stock_reservations r WHERE (r.status = 'CONSUMED') <> EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = 'COOK:' || r.id)`,
  };
  for (const [name, sql] of Object.entries(checks)) check(`Bất biến sau mọi ca đua — ${name}`, (await q(sql)).rows.length === 0);
  check('Không có deadlock (40P01) nào trong toàn bộ các ca', deadlocks === 0, `${deadlocks} deadlock`);
  check('Không có kết quả bất thường nào ngoài dự kiến trong các vòng đua', unexpected === 0, `${unexpected} vòng`);
}
await db.end();
const failed = results.filter((r) => !r.ok);
console.log(`\nKẾT QUẢ: ${results.length - failed.length}/${results.length} kiểm tra đạt${failed.length ? ' — SAI: ' + failed.map((f) => f.name).join(' | ') : ''}`);
process.exit(failed.length ? 1 : 0);
