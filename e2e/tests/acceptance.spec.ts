import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test'

// The demo accounts of docker-compose.yml; override for another environment.
const PASSWORD = process.env.E2E_PASSWORD ?? '123456'
const SEPAY_KEY = process.env.E2E_SEPAY_KEY ?? 'dev-sepay-key'

async function tokenOf(request: APIRequestContext, username: string): Promise<string> {
  const response = await request.post('/api/auth/login', { data: { username, password: PASSWORD } })
  expect(response.ok(), `sign in as ${username}`).toBeTruthy()
  return (await response.json()).token
}

const bearer = (token: string) => ({ Authorization: `Bearer ${token}` })

/** A new browser session signed in through the login page. */
async function signIn(browser: Browser, username: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage()
  await page.goto('/login')
  await page.getByLabel('Tên đăng nhập').fill(username)
  await page.getByLabel('Mật khẩu').fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  return page
}

/**
 * Acceptance criterion 1 (docs-core/01, §1.5): a guest orders by QR, a waiter confirms, the kitchen cooks, the
 * guest's phone shows "Xong", the guest pays by transfer, the bank's webhook confirms it and the table is free.
 */
test('the whole QR ordering and bank transfer flow', async ({ browser, request }) => {
  // A table and a dish of its own, so the test does not depend on what else is in the database.
  const manager = bearer(await tokenOf(request, 'quanly'))
  const suffix = Date.now() % 100_000
  const tableName = `E2E-${suffix}`
  const dishName = `Món E2E ${suffix}`
  const table = await (await request.post('/api/tables', { headers: manager, data: { name: tableName, area: 'E2E', seats: 4 } })).json()
  const categories = await (await request.get('/api/categories', { headers: manager })).json()
  const dish = await request.post('/api/menu-items', { headers: manager, data: { categoryId: categories[0].id, name: dishName, price: 45_000 } })
  expect(dish.ok()).toBeTruthy()

  // The guest scans the QR code on a phone and orders.
  const guest = await (await browser.newContext({ viewport: { width: 390, height: 844 } })).newPage()
  await guest.goto(`/q/${table.qrToken}`)
  await expect(guest.getByText(`Bàn ${tableName}`)).toBeVisible()
  await guest.getByRole('button', { name: `Thêm ${dishName}` }).click()
  await guest.getByRole('button', { name: /Xem 1 món đã chọn/ }).click()
  await guest.getByRole('button', { name: /Gửi món \(1\)/ }).click()
  await expect(guest.getByText('Chờ xác nhận', { exact: true })).toBeVisible()

  // The waiter sees the table waiting and confirms the dish (BR-10).
  const waiter = await signIn(browser, 'phucvu')
  const card = waiter.locator('.table-card', { hasText: tableName })
  await expect(card.getByText('1 món QR chờ xác nhận')).toBeVisible()
  await card.click()
  await waiter.getByRole('button', { name: 'Xác nhận tất cả' }).click()
  await expect(waiter.getByText('Chờ làm', { exact: true })).toBeVisible()

  // The kitchen cooks it, and the guest's phone follows without reloading (NFR-01).
  const chef = await signIn(browser, 'bep')
  const ticket = () => chef.locator('.kitchen-column .ant-card', { hasText: dishName })
  await ticket().getByRole('button', { name: 'Bắt đầu' }).click()
  await ticket().getByRole('button', { name: 'Xong' }).click()
  await expect(guest.getByText('Xong', { exact: true })).toBeVisible()

  // The guest pays by VietQR; the bank's webhook confirms the transfer (BR-15).
  const [paymentResponse] = await Promise.all([
    guest.waitForResponse((r) => r.url().endsWith(`/api/public/tables/${table.qrToken}/payment`) && r.request().method() === 'POST'),
    guest.getByRole('button', { name: 'Thanh toán chuyển khoản' }).click(),
  ])
  const payment = await paymentResponse.json()
  await expect(guest.getByText(payment.reference)).toBeVisible()
  const webhook = await request.post('/api/webhooks/sepay', {
    headers: { Authorization: `Apikey ${SEPAY_KEY}` },
    data: { id: Date.now(), gateway: 'Vietcombank', content: `Thanh toan ${payment.reference}`, transferType: 'in', transferAmount: payment.amount },
  })
  expect(webhook.ok()).toBeTruthy()
  await expect(guest.getByText('Đã thanh toán')).toBeVisible()

  // The table is free again (BR-04).
  const tables = await (await request.get('/api/tables', { headers: manager })).json()
  expect(tables.find((t: { id: number }) => t.id === table.id).status).toBe('AVAILABLE')
})

/** Acceptance criterion 2: each role can only do its own work; the API answers 403 otherwise. */
test('each role is limited to its own work', async ({ request }) => {
  const waiter = bearer(await tokenOf(request, 'phucvu'))
  const chef = bearer(await tokenOf(request, 'bep'))
  const manager = bearer(await tokenOf(request, 'quanly'))
  expect((await request.get('/api/employees', { headers: waiter })).status()).toBe(403)
  expect((await request.get('/api/inventory-items', { headers: chef })).status()).toBe(403)
  expect((await request.get('/api/payrolls', { headers: manager })).status()).toBe(403)
  expect((await request.get('/api/kitchen/items', { headers: chef })).status()).toBe(200)
})

/** Every employee reaches their own page for clocking in, leave and payslips (FR-13.4, FR-14.1, FR-15.4). */
test('every employee has a "Của tôi" page', async ({ browser }) => {
  const cashier = await signIn(browser, 'thungan')
  await cashier.getByRole('menuitem', { name: 'Của tôi' }).click()
  await expect(cashier.getByRole('heading', { name: 'Của tôi' })).toBeVisible()
  await expect(cashier.getByText('Chấm công hôm nay', { exact: true })).toBeVisible()
  await expect(cashier.getByText('Phiếu lương', { exact: true })).toBeVisible()
})
