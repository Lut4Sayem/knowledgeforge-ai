import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { ConfirmHost, confirmDialog } from './ConfirmDialog'

afterEach(cleanup)

function setup() {
  render(
    <>
      <button type="button">Trigger</button>
      <ConfirmHost />
    </>,
  )
  const trigger = screen.getByRole('button', { name: 'Trigger' })
  const ask = () => {
    trigger.focus()
    let answer!: Promise<boolean>
    act(() => {
      answer = confirmDialog({ title: 'Move "Plan" to trash?', body: 'This also moves its 2 sub-pages.', confirmLabel: 'Move to trash', danger: true })
    })
    return answer
  }
  return { trigger, ask }
}

describe('confirmDialog', () => {
  it('showsTitleBodyAndNamedButtons', async () => {
    const { ask } = setup()
    void ask()
    const dialog = await screen.findByRole('dialog', { name: 'Move "Plan" to trash?' })
    expect(document.getElementById(dialog.getAttribute('aria-describedby')!)?.textContent).toBe('This also moves its 2 sub-pages.')
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Move to trash' })).toBeTruthy()
  })

  it('startsOnTheSafeButton', async () => {
    const { ask } = setup()
    void ask()
    await screen.findByRole('dialog')
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Cancel' }))
  })

  it('confirmResolvesTrueAndReturnsFocus', async () => {
    const { ask, trigger } = setup()
    const answer = ask()
    await userEvent.click(await screen.findByRole('button', { name: 'Move to trash' }))
    expect(await answer).toBe(true)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })

  it('escapeResolvesFalseAndReturnsFocus', async () => {
    const { ask, trigger } = setup()
    const answer = ask()
    await screen.findByRole('dialog')
    await userEvent.keyboard('{Escape}')
    expect(await answer).toBe(false)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })

  it('cancelResolvesFalse', async () => {
    const { ask } = setup()
    const answer = ask()
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }))
    expect(await answer).toBe(false)
  })

  it('tabStaysInsideTheDialog', async () => {
    const { ask } = setup()
    void ask()
    await screen.findByRole('dialog')
    const cancel = screen.getByRole('button', { name: 'Cancel' })
    const confirm = screen.getByRole('button', { name: 'Move to trash' })
    expect(document.activeElement).toBe(cancel)
    await userEvent.tab()
    expect(document.activeElement).toBe(confirm)
    await userEvent.tab() // past the last button: back to the first, not out to the page
    expect(document.activeElement).toBe(cancel)
    await userEvent.tab({ shift: true }) // before the first: to the last
    expect(document.activeElement).toBe(confirm)
  })

  it('aSecondQuestionWhileOneIsOpenIsRefused', async () => {
    const { ask } = setup()
    void ask()
    await screen.findByRole('dialog')
    let second!: Promise<boolean>
    act(() => {
      second = confirmDialog({ title: 'Other', confirmLabel: 'OK' })
    })
    expect(await second).toBe(false)
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
  })

  it('noHostMeansNo', async () => {
    expect(await confirmDialog({ title: 'x', confirmLabel: 'y' })).toBe(false) // never destructive without anyone to answer
  })
})
