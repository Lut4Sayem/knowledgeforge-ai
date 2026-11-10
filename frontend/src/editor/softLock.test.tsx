import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SoftLockBanner, { useEditLock } from './SoftLockBanner'

const contentApi = vi.hoisted(() => ({ acquireEditLock: vi.fn(), releaseEditLock: vi.fn() }))
vi.mock('../api/content', () => contentApi)

const mine = { mine: true, holder: { id: 'me', name: 'Me' }, expiresAt: '' }
const rafi = { mine: false, holder: { id: 'u2', name: 'Rafi' }, expiresAt: '' }

function Harness({ canEdit = true }: { canEdit?: boolean }) {
  const lock = useEditLock('n1', canEdit)
  return (
    <>
      {lock.mode === 'blocked' && <SoftLockBanner holder={lock.holder} onReadOnly={lock.openReadOnly} onEditAnyway={lock.editAnyway} />}
      <p data-testid="mode">{lock.mode}</p>
    </>
  )
}
const mode = () => screen.getByTestId('mode').textContent
const tick = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms))

beforeEach(() => {
  vi.useFakeTimers()
  contentApi.releaseEditLock.mockResolvedValue(null)
})
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('soft lock', () => {
  it('showsBannerWhenOtherHolds', async () => {
    contentApi.acquireEditLock.mockResolvedValue(rafi)
    render(<Harness />)
    expect(mode()).toBe('checking') // not editable until the lock is answered
    await tick(0)

    expect(contentApi.acquireEditLock).toHaveBeenCalledWith('n1', false)
    expect(screen.getByRole('alert').textContent).toContain('Rafi is editing this page right now.')
    expect(mode()).toBe('blocked')

    fireEvent.click(screen.getByRole('button', { name: 'Open read-only' }))
    expect(screen.queryByRole('alert')).toBeNull()
    expect(mode()).toBe('readonly')
    await tick(60_000)
    expect(contentApi.acquireEditLock).toHaveBeenCalledTimes(1) // a reader neither forces nor heartbeats
  })

  it('editAnywayForces', async () => {
    contentApi.acquireEditLock.mockImplementation(async (_id: string, force: boolean) => (force ? mine : rafi))
    render(<Harness />)
    await tick(0)

    fireEvent.click(screen.getByRole('button', { name: 'Edit anyway' }))
    await tick(0)
    expect(contentApi.acquireEditLock).toHaveBeenLastCalledWith('n1', true)
    expect(mode()).toBe('editing')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('heartbeatEvery20s', async () => {
    contentApi.acquireEditLock.mockResolvedValue(mine)
    const view = render(<Harness />)
    await tick(0)
    expect(mode()).toBe('editing')
    expect(contentApi.acquireEditLock).toHaveBeenCalledTimes(1)

    await tick(19_999)
    expect(contentApi.acquireEditLock).toHaveBeenCalledTimes(1)
    await tick(1)
    expect(contentApi.acquireEditLock).toHaveBeenCalledTimes(2)
    await tick(20_000)
    expect(contentApi.acquireEditLock).toHaveBeenCalledTimes(3)
    expect(contentApi.acquireEditLock).toHaveBeenLastCalledWith('n1', false) // a heartbeat never forces

    view.unmount() // leaving the page releases the lock and stops the heartbeat
    expect(contentApi.releaseEditLock).toHaveBeenCalledWith('n1', false)
    await tick(60_000)
    expect(contentApi.acquireEditLock).toHaveBeenCalledTimes(3)
  })

  it('releasesWithARequestThatSurvivesTheTabClosing', async () => {
    contentApi.acquireEditLock.mockResolvedValue(mine)
    render(<Harness />)
    await tick(0)
    window.dispatchEvent(new Event('pagehide'))
    expect(contentApi.releaseEditLock).toHaveBeenCalledWith('n1', true)
  })

  it('someoneTakingOverMakesMeReadOnlyWithTheBanner', async () => {
    contentApi.acquireEditLock.mockResolvedValueOnce(mine).mockResolvedValue(rafi)
    render(<Harness />)
    await tick(0)
    expect(mode()).toBe('editing')
    await tick(20_000) // my heartbeat finds Rafi holding it
    expect(mode()).toBe('blocked')
    expect(screen.getByRole('alert').textContent).toContain('Rafi is editing this page right now.')
  })

  it('viewersNeverAskForTheLock', async () => {
    const view = render(<Harness canEdit={false} />)
    await tick(60_000)
    view.unmount()
    expect(contentApi.acquireEditLock).not.toHaveBeenCalled()
    expect(contentApi.releaseEditLock).not.toHaveBeenCalled()
  })

  it('aFailedLockCheckDoesNotBlockEditing', async () => {
    contentApi.acquireEditLock.mockRejectedValue(new Error('network'))
    render(<Harness />)
    await tick(0)
    expect(mode()).toBe('editing')
  })
})
