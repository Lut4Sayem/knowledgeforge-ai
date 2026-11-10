import { expect, test, type Page } from '@playwright/test'

// Needs the full app on E2E_BASE_URL (default :8080) with Mongo behind it. Each test registers a fresh user.
test.setTimeout(90_000)

async function signUp(page: Page) {
  const email = `trash-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`
  expect((await page.request.post('/api/auth/register', { data: { fullName: 'Trash Tester', email, password: 'password123' } })).ok()).toBeTruthy()
  expect((await page.request.post('/api/auth/login', { data: { email, password: 'password123' } })).ok()).toBeTruthy()
}

/** API call as the signed-in user, with the CSRF header the app needs on writes. */
async function call(page: Page, method: 'get' | 'post', url: string, data?: unknown) {
  const token = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const res = await page.request[method](url, { data, headers: { 'X-XSRF-TOKEN': token } })
  expect(res.ok(), `${method} ${url} -> ${res.status()}`).toBeTruthy()
  return res.json().catch(() => null)
}

async function privateSpaceWithBranch(page: Page) {
  await signUp(page)
  const space = (await call(page, 'get', '/api/me/spaces')).find((s: { ownerType: string }) => s.ownerType === 'USER')
  const page1 = (title: string, parentId?: string) => call(page, 'post', `/api/spaces/${space.id}/nodes`, { title, parentId })
  const parent = await page1('Parent')
  await page1('Child one', parent.id)
  await page1('Child two', parent.id)
  return { space, parent }
}

test('trash a parent with 2 children, restore it, the tree is back', async ({ page }) => {
  const { space, parent } = await privateSpaceWithBranch(page)
  await page.goto(`/app/p/${parent.id}`)
  const menuButton = page.getByRole('button', { name: 'Page actions' })
  const dialog = page.getByRole('dialog', { name: 'Move "Parent" to trash?' })

  // The question names the sub-pages. Esc closes it, nothing is trashed, focus goes back to the menu button.
  await menuButton.click()
  await page.getByRole('menuitem', { name: 'Move to trash' }).click()
  await expect(dialog).toContainText('This also moves its 2 sub-pages. You can restore them from the trash for 30 days.')
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(menuButton).toBeFocused()
  expect(await call(page, 'get', `/api/spaces/${space.id}/tree`)).toHaveLength(3)

  // Tab stays inside the dialog.
  await menuButton.click()
  await page.getByRole('menuitem', { name: 'Move to trash' }).click()
  await expect(dialog).toBeVisible()
  await page.keyboard.press('Tab')
  await page.keyboard.press('Tab')
  await page.keyboard.press('Tab')
  expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true)
  await dialog.getByRole('button', { name: 'Move to trash' }).click()

  await page.waitForURL(/\/app\/?$/)
  expect(await call(page, 'get', `/api/spaces/${space.id}/tree`)).toHaveLength(0)
  await expect(page.locator('details.space').first().getByRole('link', { name: 'Parent' })).toHaveCount(0)

  // Trash page: one row for the branch, then restore.
  await page.locator('details.space').first().getByRole('link', { name: 'Trash' }).click()
  const row = page.getByRole('row', { name: /Parent/ })
  await expect(row).toContainText('+ 2 sub-pages')
  await expect(row).toContainText('30 days')
  await row.getByRole('button', { name: 'Restore' }).click()
  await expect(page.locator('p.banner[role="status"]')).toContainText('"Parent" was restored.')
  expect(await call(page, 'get', `/api/spaces/${space.id}/tree`)).toHaveLength(3)
  await expect(page.locator('details.space').first().getByRole('link', { name: 'Parent' })).toBeVisible()
})

test('a page trashed while it is open keeps the typed text and shows the server message', async ({ page }) => {
  const { parent } = await privateSpaceWithBranch(page)
  await page.goto(`/app/p/${parent.id}`)
  await page.locator('.bn-editor[contenteditable="true"]').click()
  await page.keyboard.type('Words that must survive')
  await call(page, 'post', `/api/nodes/${parent.id}/trash`) // someone trashes the page before the autosave fires

  await expect(page.getByText('Not saved: This page is in the trash')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.bn-editor')).toContainText('Words that must survive') // still on screen
  const kept = await page.evaluate(
    () =>
      new Promise<string[]>((resolve, reject) => {
        const open = indexedDB.open('keyval-store')
        open.onerror = () => reject(open.error)
        open.onsuccess = () => {
          const get = open.result.transaction('keyval').objectStore('keyval').getAll()
          get.onsuccess = () => resolve(get.result.map((v) => JSON.stringify(v)))
        }
      }),
  )
  expect(kept.join('')).toContain('Words that must survive') // and on the device
})
