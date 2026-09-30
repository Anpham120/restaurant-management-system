// P3-03 (NFR-02): 30 people at once for 2 minutes on a busy evening. Guests order from the table QR code,
// waiters confirm and serve, the kitchen moves dishes along, cashiers watch the open orders, a manager reads the
// last 30 days of sales. The test fails when the API answers 5% of requests slower than 500 ms, or when more than
// 1% of requests fail. Load past sales first (perf/seed-history.sql) to test against a realistic database.
//
// Against the app started with Docker Compose (demo accounts on), from the repository root:
//   docker run --rm -i -v "$PWD/perf:/perf" grafana/k6:2.3.0 run -e BASE_URL=http://host.docker.internal:8080 /perf/load-test.js
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const PASSWORD = __ENV.DEMO_PASSWORD || '123456'
const DURATION = __ENV.DURATION || '2m'

// Every request is named, so the summary shows the 95th percentile of each endpoint.
const NAMES = [
  'GET /public/tables/{qr}',
  'GET /public/menu',
  'POST /public/tables/{qr}/items',
  'GET /tables',
  'GET /orders',
  'GET /orders/{id}',
  'POST /orders/{id}/confirm-pending',
  'GET /kitchen/items',
  'PATCH /order-items/{id}/status',
  'GET /reports/summary',
]

export const options = {
  scenarios: {
    guests: { executor: 'constant-vus', vus: 14, duration: DURATION, exec: 'guest' },
    waiters: { executor: 'constant-vus', vus: 10, duration: DURATION, exec: 'waiter' },
    kitchen: { executor: 'constant-vus', vus: 3, duration: DURATION, exec: 'kitchen' },
    cashiers: { executor: 'constant-vus', vus: 2, duration: DURATION, exec: 'cashier' },
    managers: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'manager' },
  },
  thresholds: Object.assign(
    { http_req_duration: ['p(95)<500'], http_req_failed: ['rate<0.01'] },
    ...NAMES.map((name) => ({ [`http_req_duration{name:${name}}`]: ['p(95)<500'] })),
  ),
}

// Two people acting on the same dish at once is normal: the second one gets 409 and moves on.
const okOrConflict = http.expectedStatuses({ min: 200, max: 299 }, 409)

const pick = (list) => list[Math.floor(Math.random() * list.length)]
const body = (res, fallback) => (res.status === 200 ? res.json() : fallback)
const staff = (token, name, extra = {}) =>
  Object.assign({ headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, tags: { name } }, extra)

function login(username) {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  })
  if (res.status !== 200) throw new Error(`Cannot sign in as ${username}: ${res.status} ${res.body}`)
  return res.json('token')
}

export function setup() {
  const tokens = { waiter: login('phucvu'), chef: login('bep'), cashier: login('thungan'), manager: login('quanly') }
  const tables = http.get(`${BASE}/api/tables`, staff(tokens.waiter, 'setup')).json()
  const dishes = http
    .get(`${BASE}/api/public/menu`)
    .json()
    .reduce((all, section) => all.concat(section.items), [])
    .filter((dish) => dish.available)
  return { tokens, qrTokens: tables.map((t) => t.qrToken), dishIds: dishes.map((d) => d.id) }
}

export function guest(data) {
  // Each guest stays at one table. Ordering on one visit in ten keeps a table well under its 10 requests a minute
  // (BR-30), as real guests do; a table that goes over gets 429 and counts as a failure.
  const qr = data.qrTokens[(__VU - 1) % data.qrTokens.length]
  const table = http.get(`${BASE}/api/public/tables/${qr}`, { tags: { name: 'GET /public/tables/{qr}' } })
  check(table, { 'guest page loads': (r) => r.status === 200 })
  http.get(`${BASE}/api/public/menu`, { tags: { name: 'GET /public/menu' } })
  sleep(2 + Math.random() * 2)
  if (Math.random() < 0.1) {
    const line = { menuItemId: pick(data.dishIds), quantity: 1 + Math.floor(Math.random() * 2) }
    const sent = http.post(`${BASE}/api/public/tables/${qr}/items`, JSON.stringify({ items: [line] }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /public/tables/{qr}/items' },
    })
    check(sent, { 'guest order accepted': (r) => r.status === 201 })
  }
}

export function waiter(data) {
  const token = data.tokens.waiter
  const tables = body(http.get(`${BASE}/api/tables`, staff(token, 'GET /tables')), [])
  const pending = tables.filter((t) => t.pendingCount > 0)
  if (pending.length > 0) {
    http.post(`${BASE}/api/orders/${pick(pending).openOrderId}/confirm-pending`, null,
      staff(token, 'POST /orders/{id}/confirm-pending', { responseCallback: okOrConflict }))
  }
  const ready = tables.filter((t) => t.readyCount > 0)
  if (ready.length > 0) {
    const order = body(http.get(`${BASE}/api/orders/${pick(ready).openOrderId}`, staff(token, 'GET /orders/{id}')), {})
    const dish = (order.items || []).find((i) => i.status === 'READY')
    if (dish) {
      http.patch(`${BASE}/api/order-items/${dish.id}/status`, JSON.stringify({ status: 'SERVED' }),
        staff(token, 'PATCH /order-items/{id}/status', { responseCallback: okOrConflict }))
    }
  }
  sleep(1 + Math.random() * 2)
}

export function kitchen(data) {
  const token = data.tokens.chef
  const items = body(http.get(`${BASE}/api/kitchen/items`, staff(token, 'GET /kitchen/items')), [])
  for (const [from, to] of [['WAITING', 'COOKING'], ['COOKING', 'READY']]) {
    // Oldest first, like the screen; three cooks rarely take the very same dish.
    const next = items.filter((i) => i.status === from).slice(0, 3)
    if (next.length > 0) {
      http.patch(`${BASE}/api/order-items/${pick(next).id}/status`, JSON.stringify({ status: to }),
        staff(token, 'PATCH /order-items/{id}/status', { responseCallback: okOrConflict }))
    }
  }
  sleep(2 + Math.random() * 2)
}

export function cashier(data) {
  const orders = http.get(`${BASE}/api/orders`, staff(data.tokens.cashier, 'GET /orders'))
  check(orders, { 'open orders load': (r) => r.status === 200 })
  sleep(3)
}

export function manager(data) {
  const day = (msAgo) => new Date(Date.now() - msAgo).toISOString().slice(0, 10)
  const url = `${BASE}/api/reports/summary?from=${day(30 * 24 * 3600 * 1000)}&to=${day(0)}`
  const report = http.get(url, staff(data.tokens.manager, 'GET /reports/summary'))
  check(report, { 'sales report loads': (r) => r.status === 200 })
  sleep(5)
}
