import { expect, test } from '@playwright/test'

// Needs the full app on E2E_BASE_URL (default :8080) with Mongo behind it. Registers a fresh user through the UI.
test.setTimeout(90_000)

test('register, create team, create space, create page, comment', async ({ page }) => {
  const email = `portal-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`
  await page.goto('/app/register')
  await page.getByLabel('Full name').fill('Portal Tester')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('password123')
  await page.getByLabel('Confirm password').fill('password123')
  await page.getByRole('button', { name: 'Create account' }).click()

  // Register sends them to the login page; logging in lands on the React dashboard.
  await expect(page).toHaveURL(/\/app\/login/)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('password123')
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/app\/?$/)
  await page.getByRole('button', { name: 'New team' }).click()
  await page.getByLabel('Team name').fill('Portal Team')
  await page.getByRole('button', { name: 'Create a team' }).click()

  await expect(page.getByRole('heading', { name: 'Portal Team' })).toBeVisible()
  await page.getByLabel('Space name').fill('Portal Space')
  await page.getByRole('button', { name: 'New space' }).click()
  await expect(page.getByRole('link', { name: 'Portal Space' })).toBeVisible()

  await page.getByRole('button', { name: 'New page in Portal Space' }).click()
  await expect(page).toHaveURL(/\/app\/p\//)

  await page.getByLabel('Add a comment').fill('First comment')
  await page.getByRole('button', { name: 'Comment', exact: true }).click()
  await expect(page.getByText('First comment')).toBeVisible()
})
