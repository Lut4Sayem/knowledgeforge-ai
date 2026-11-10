import { expect, test, type Page } from '@playwright/test'

// Needs the full app on E2E_BASE_URL (default :8080) with Mongo behind it. Each test registers a fresh user.
async function openNewPage(page: Page): Promise<string> {
  const email = `autosave-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`
  const register = await page.request.post('/api/auth/register', { data: { fullName: 'Autosave Tester', email, password: 'password123' } })
  expect(register.ok()).toBeTruthy()
  const login = await page.request.post('/api/auth/login', { data: { email, password: 'password123' } })
  expect(login.ok()).toBeTruthy()
  await page.goto('/app')
  await page.getByRole('button', { name: /new page/i }).first().click() // a new user only has the private space
  await page.waitForURL(/\/app\/p\//)
  await expect(editor(page)).toBeVisible()
  return page.url()
}

const editor = (page: Page) => page.locator('.bn-editor')
const saveState = (page: Page) => page.locator('.save-indicator')

async function typeInEditor(page: Page, text: string) {
  await editor(page).click()
  await page.keyboard.type(text)
}

test('type, wait for "Saved", reload, text is there', async ({ page }) => {
  const saves: string[] = []
  page.on('request', (r) => r.method() === 'PUT' && r.url().endsWith('/content') && saves.push(r.url()))
  await openNewPage(page)
  await expect(saveState(page)).toHaveText('Saved')
  await page.waitForTimeout(1500)
  expect(saves).toHaveLength(0) // just opening a page must not save it

  await typeInEditor(page, 'Hello autosave')
  await expect(saveState(page)).toHaveText('Saving…')
  await expect(saveState(page)).toHaveText('Saved')
  expect(saves).toHaveLength(1) // one save for the whole burst of typing

  await page.reload()
  await expect(editor(page)).toContainText('Hello autosave')
  await expect(saveState(page)).toHaveText('Saved')
})

test('offline edits are kept on the device and replayed on the next open', async ({ page, context }) => {
  const url = await openNewPage(page)
  await typeInEditor(page, 'Before offline')
  await expect(saveState(page)).toHaveText('Saved')

  await context.setOffline(true)
  await page.keyboard.type(' and typed offline')
  await expect(saveState(page)).toHaveText('Offline: changes kept on this device')
  await page.close() // the tab dies without ever reaching the server

  await context.setOffline(false)
  const again = await context.newPage()
  await again.goto(url)
  await expect(editor(again)).toContainText('Before offline and typed offline')
  await expect(saveState(again)).toHaveText('Saved')
  await again.reload() // and now it really is on the server
  await expect(editor(again)).toContainText('Before offline and typed offline')
})

test('two tabs on one page: the second save is a conflict, nothing is lost', async ({ page, context }) => {
  const url = await openNewPage(page)
  const second = await context.newPage()
  await second.goto(url)
  await expect(saveState(second)).toHaveText('Saved')

  await typeInEditor(page, 'From the first tab')
  await expect(saveState(page)).toHaveText('Saved')

  await typeInEditor(second, 'From the second tab')
  const banner = second.getByRole('alert').filter({ hasText: 'This page changed somewhere else.' })
  await expect(banner).toBeVisible()
  await expect(editor(second)).toContainText('From the second tab') // still on screen

  await second.reload() // even a reload keeps my copy and the question
  await expect(editor(second)).toContainText('From the second tab')
  await second.getByRole('button', { name: 'Keep mine' }).click()
  await expect(saveState(second)).toHaveText('Saved')

  await page.reload()
  await expect(editor(page)).toContainText('From the second tab')
})

test('slash menu inserts a status chip and Ctrl+Z removes it', async ({ page }) => {
  await openNewPage(page)
  await typeInEditor(page, '/status')
  await page.getByRole('option', { name: /Status/ }).click()
  await expect(editor(page).locator('.kf-status')).toHaveText('Status')

  await editor(page).locator('.kf-status button').click()
  await page.getByRole('radio', { name: 'green' }).click()
  await expect(editor(page).locator('span.kf-status--green')).toBeVisible()

  await editor(page).click()
  await page.keyboard.press('Control+z')
  await page.keyboard.press('Control+z')
  await expect(editor(page).locator('.kf-status')).toHaveCount(0)
})
