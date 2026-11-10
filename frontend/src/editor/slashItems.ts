import { insertOrUpdateBlockForSlashMenu } from '@blocknote/core'
import { getDefaultReactSlashMenuItems, type DefaultReactSuggestionItem } from '@blocknote/react'
import type { KfEditor } from './schema'

export type KfSlashItem = DefaultReactSuggestionItem & { key: string }

/** BlockNote's default items (text, headings, lists, quote, divider, code, image, file, table, ...) + ours. */
export function getKfSlashItems(editor: KfEditor): KfSlashItem[] {
  return [
    ...(getDefaultReactSlashMenuItems(editor) as KfSlashItem[]),
    {
      key: 'status',
      title: 'Status',
      subtext: 'In progress, blocked, done…',
      aliases: ['status', 'lozenge', 'label', 'state'],
      group: 'Others',
      onItemClick: () => editor.insertInlineContent([{ type: 'status', props: { label: 'Status', color: 'gray' } }, ' ']),
    },
    {
      key: 'bookmark',
      title: 'Bookmark',
      subtext: 'Paste a link',
      aliases: ['bookmark', 'link', 'url', 'embed'],
      group: 'Others',
      onItemClick: () => insertOrUpdateBlockForSlashMenu(editor, { type: 'bookmark' }),
    },
  ]
}
