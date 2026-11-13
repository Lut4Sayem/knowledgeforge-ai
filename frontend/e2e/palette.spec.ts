import { expect, test, type Page } from '@playwright/test'

// Needs the full app on E2E_BASE_URL (default :8080) with Atlas-local or Atlas behind it (content search needs the index).
test.setTimeout(90_000)

async function signUp(page: Page) {
  const email = `palette-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`
  expect((await page.request.post('/api/auth/register', { data: { fullName: 'Palette Tester', email, password: 'password123' } })).ok()).toBeTruthy()
  expect((await page.request.post('/api/auth/login', { data: { email, password: 'password123' } })).ok()).toBeTruthy()
}

async function call(page: Page, method: 'get' | 'post', url: string, data?: unknown) {
  const token = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const res = await page.request[method](url, { data, headers: { 'X-XSRF-TOKEN': token } })
  expect(res.ok(), `${method} ${url} -> ${res.status()}`).toBeTruthy()
  return res.json().catch(() => null)
}

test('Ctrl+K finds a page by title, opens it, and Esc gives focus back', async ({ page }) => {
  await signUp(page)
  const space = (await call(page, 'get', '/api/me/spaces')).find((s: { ownerType: string }) => s.ownerType === 'USER')
  const word = `Quokka${Date.now()}`
  const node = await call(page, 'post', `/api/spaces/${space.id}/nodes`, { title: `${word} plan` })
  await page.goto('/app/')

  const trigger = page.getByRole('button', { name: /^Search/ })
  await trigger.focus()
  await page.keyboard.press('Control+k')
  const palette = page.getByRole('dialog', { name: 'Search pages and actions' })
  await expect(palette).toBeVisible()

  await page.keyboard.press('Escape')
  await expect(palette).toBeHidden()
  await expect(trigger).toBeFocused()

  await page.keyboard.press('Control+k')
  await expect(palette).toBeVisible()
  await palette.getByRole('combobox').fill(word.toLowerCase())
  await expect(palette.getByRole('option', { name: `${word} plan` })).toBeVisible()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(new RegExp(`/app/p/${node.id}$`))
  await expect(palette).toBeHidden()
})
