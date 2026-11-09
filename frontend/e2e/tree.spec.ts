import { expect, test } from '@playwright/test'

// Needs the full app on E2E_BASE_URL (default :8080) with Mongo behind it. Registers a fresh user per run.
test('create page, create sub-page, drag it to top level, breadcrumbs update', async ({ page }) => {
  const email = `tree-${Date.now()}@example.test`
  await page.goto('/app/register')
  await page.getByLabel(/name/i).first().fill('Tree Tester')
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('password123')
  await page.getByLabel(/confirm/i).fill('password123')
  await page.getByRole('button', { name: /create|register|sign up/i }).click()
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel(/password/i).fill('password123')
  await page.getByRole('button', { name: /log in|sign in/i }).click()
  await page.goto('/app')

  // A new user only has the private space.
  const priv = page.locator('details.space').first()
  await priv.getByRole('button', { name: /new page/i }).click()
  await page.getByLabel('Page title').fill('Parent')
  await page.getByLabel('Page title').blur()
  await expect(priv.getByRole('link', { name: 'Parent' })).toBeVisible()

  await priv.getByRole('button', { name: 'New sub-page under Parent' }).click()
  await page.getByLabel('Page title').fill('Child')
  await page.getByLabel('Page title').blur()
  await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).toContainText('Parent')

  // Drag Child (keyboard sensor on its grip) to the top-level drop zone.
  await priv.getByRole('button', { name: 'Move Child' }).focus()
  await page.keyboard.press('Space')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Space')

  await expect(page.getByRole('navigation', { name: 'Breadcrumb' })).not.toContainText('Parent')
})
