import { confirmDialog } from '../ui/ConfirmDialog'

const pages = (n: number) => `${n} sub-page${n === 1 ? '' : 's'}`

/** "Move "X" to trash?": names how many sub-pages go with it (wireframe Trash). */
export function confirmTrash(title: string, subPages: number): Promise<boolean> {
  return confirmDialog({
    title: `Move "${title || 'Untitled'}" to trash?`,
    body:
      subPages > 0
        ? `This also moves its ${pages(subPages)}. You can restore ${subPages === 1 ? 'it' : 'them'} from the trash for 30 days.`
        : 'You can restore it from the trash for 30 days.',
    confirmLabel: 'Move to trash',
    danger: true,
  })
}

export function confirmDeleteForever(title: string, subPages: number): Promise<boolean> {
  return confirmDialog({
    title: `Delete "${title || 'Untitled'}" forever?`,
    body: `This permanently deletes the page${subPages > 0 ? `, its ${pages(subPages)}` : ''}, and their versions, comments and files. This cannot be undone.`,
    confirmLabel: 'Delete forever',
    danger: true,
  })
}
