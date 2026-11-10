import { expect, test } from '@playwright/test'

// Needs the full app on E2E_BASE_URL (default :8080) with Mongo behind it. Registers a fresh user per run.
test('create page, create sub-page, drag it to top level, breadcrumbs update', async ({ page }) => {
  const email = `tree-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`
  expect((await page.request.post('/api/auth/register', { data: { fullName: 'Tree Tester', email, password: 'password123' } })).ok()).toBeTruthy()
  expect((await page.request.post('/api/auth/login', { data: { email, password: 'password123' } })).ok()).toBeTruthy()
  await page.goto('/app')

  // A new user only has the private space.
  const priv = page.locator('details.space').first()
  const crumbs = page.getByRole('navigation', { name: 'Breadcrumb' })
  await priv.getByRole('button', { name: /new page/i }).click()
  await page.waitForURL(/\/app\/p\//)
  await page.getByLabel('Page title').fill('Parent')
  await page.getByLabel('Page title').blur()
  await expect(priv.getByRole('link', { name: 'Parent' })).toBeVisible()

  const parentUrl = page.url()
  await priv.getByRole('button', { name: 'New sub-page under Parent' }).click()
  await page.waitForURL((url) => url.href !== parentUrl)
  await page.getByLabel('Page title').fill('Child')
  await page.getByLabel('Page title').blur()
  await expect(priv.getByRole('link', { name: 'Child' })).toBeVisible()
  await expect(crumbs).toContainText('Parent')

  // Drag Child (keyboard sensor on its grip) down onto the "top level" drop zone.
  await priv.getByRole('button', { name: 'Move Child' }).focus()
  await page.keyboard.press('Space')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Space')

  await expect(crumbs).not.toContainText('Parent')
  await expect(priv.getByRole('button', { name: 'New sub-page under Child' })).toBeVisible() // still in the tree, now a sibling
})
