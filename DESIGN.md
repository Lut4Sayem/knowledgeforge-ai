---
name: KnowledgeForge design system
based-on: Notion design analysis (VoltAgent/awesome-design-md, MIT), trimmed to app UI
status: font + primary color chosen 2026-11-03 (see Overrides)
---

# KnowledgeForge Design System

## 0. KnowledgeForge overrides (highest priority)
Filled in during the redesign. Anything here wins over the rest of this file.

- **Font (UI + headings):** **Instrument Sans** 400/500/600 (Google Fonts, `display=swap`).
  Stack: `"Instrument Sans", ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`. One face for everything except code.
- **Font (code / markdown code blocks):** **Fragment Mono** 400 (+ italic). A neo-grotesque mono drawn to sit next to a grotesk.
  Stack: `"Fragment Mono", ui-monospace, "Cascadia Code", Consolas, monospace`. Only in `<pre>`, `<code>`, `<kbd>` and the markdown editor.
- **Primary color:** **#1b6e66** (deep slate-teal). White text 6.05:1; on `--color-surface` 5.56:1.
- **Primary pressed / deep:** **#18615a** (-12%, white 7.25:1) / **#14534d** (-25%, white 8.85:1). Soft: **#e8f1f0** (primary at 10% on white; primary text on it 5.27:1).
- **Art direction:** "Reading room." The app is a quiet, warm-paper room where the documents are the brightest thing:
  a warm stone frame (sidebar + top bar on `--color-surface`, no rule between them) holds a white sheet where the work happens.
  One deep teal, like a library binding, marks the single action and the current place, and nothing else takes colour except tags and people.
  Type is one plain grotesk at regular weight. Headings are sized up a little, never made bold for show. Figures are tabular. The dashboard opens with a sentence built from real data, not "Welcome back".

Contrast fixes (DESIGN.md §9 says text must reach 4.5:1; these replace the values further down):
- `--color-steel` **#6b6964** (was #787671: 4.17:1 on surface). 5.48 on canvas, 5.04 on surface, 4.73 on hover. Placeholders use steel, not muted.
- `--color-stone` / `--color-muted` are for disabled text and decorative icons only, never for words someone must read.
- `--color-hairline-strong` **#8f8b84** (was #c8c4be: 1.74:1). Input borders need 3:1 (3.39 on white).
- `--color-link` **= primary-pressed #18615a**, always underlined. A blue link next to a teal button would mean two accents competing.
- Semantic colour as **text** uses the deep variant: success **#136c27**, warning **#92400e**, error **#b42318** (6.57:1). The bright values (#1aae39, #dd5b00, #e03131) are only for icons and borders.
- Error soft pair: **#fdecea** background with **#7a1d14** text (9.12:1). The old #fde0e0/#e03131 pair was 3.64:1.

Priority when sources disagree: **this section > rest of DESIGN.md > taste-skill > frontend-design.**
Layout: designed fresh. The old mockups and the current pages show WHAT each page must contain, not how it should look.

## 1. Overview
KnowledgeForge is a calm, document-first workspace: Teams -> Spaces -> Markdown documents.
The look borrows Notion's app feel: warm off-white surfaces, warm-gray text, hairline borders instead of heavy shadows,
8px buttons, 12px cards, quiet chrome so the content is the focus. Color is used sparingly: one primary for the main action,
soft pastel tints for tags/status/categories, semantic colors only for meaning.

Principles:
- Content first: documents are the hero, the UI frames them and steps back.
- One primary action per view. Everything else is secondary, ghost or link.
- Flat by default: borders separate, shadows only for things that float (menus, modals, toasts).
- Dense where it helps (tables, lists, sidebar), roomy where you read (document view).
- Every screen has a designed empty, loading and error state.

## 2. Colors (CSS variables live in style.css `:root`)

### Primary
| Token | Value | Use |
|---|---|---|
| `--color-primary` | TBD (overrides) | Main CTA, focus ring, active nav marker, selected states |
| `--color-primary-pressed` | TBD | Pressed / hover of primary |
| `--color-primary-soft` | primary at ~10% on white | Selected rows, active nav background |
| `--color-on-primary` | #ffffff | Text on primary |
| `--color-link` | #0075de | Inline text links only, never buttons |

### Surfaces & lines
| Token | Value | Use |
|---|---|---|
| `--color-canvas` | #ffffff | Main content area, cards, inputs |
| `--color-surface` | #f6f5f4 | Sidebar, page background behind cards, search field rest |
| `--color-surface-soft` | #fafaf9 | Table header, quiet sections |
| `--color-hover` | #efeeec | Hover on rows, nav items, ghost buttons |
| `--color-hairline` | #e5e3df | Default 1px borders, dividers |
| `--color-hairline-soft` | #ede9e4 | Row separators |
| `--color-hairline-strong` | #c8c4be | Input borders, secondary button border |

### Text
| Token | Value | Use |
|---|---|---|
| `--color-ink` | #1a1a1a | Headings, primary text |
| `--color-charcoal` | #37352f | Document body text (warm) |
| `--color-slate` | #5d5b54 | Secondary text, labels |
| `--color-steel` | #787671 | Meta text (dates, counts), icons |
| `--color-stone` | #a4a097 | Placeholder-ish labels, disabled icons |
| `--color-muted` | #bbb8b1 | Disabled text, placeholders |

### Semantic
| Token | Value | Soft background |
|---|---|---|
| `--color-success` | #1aae39 | #d9f3e1 |
| `--color-warning` | #dd5b00 | #ffe8d4 |
| `--color-error` | #e03131 | #fde0e0 |
| `--color-info` | #0075de | #dcecfa |

### Tints (tags, avatars, team/space icons, category accents)
| Token | Background | Text on it |
|---|---|---|
| peach | #ffe8d4 | #793400 |
| rose | #fde0ec | #a02e6d |
| mint | #d9f3e1 | #136c27 |
| lavender | #e6e0f5 | #391c57 |
| sky | #dcecfa | #004a8c |
| yellow | #fef7d6 | #6b5200 |
| gray | #f0eeec | #37352f |
Assign a tint to a tag/team deterministically (hash of its name -> one of the 7) so the same tag always has the same color.
Never put white text on a tint; always use the paired dark text.

## 3. Typography
Font family from Overrides. Sizes are app-scaled (no marketing display sizes).

| Token | Size | Weight | Line height | Letter spacing | Use |
|---|---|---|---|---|---|
| `title-xl` | 32px | 600 | 1.2 | -0.5px | Document title, auth page heading |
| `title-lg` | 24px | 600 | 1.25 | -0.3px | Page titles (Dashboard, Team name) |
| `title-md` | 20px | 600 | 1.3 | 0 | Section headings, modal titles |
| `title-sm` | 16px | 600 | 1.4 | 0 | Card titles, list group headings |
| `body-lg` | 16px | 400 | 1.65 | 0 | Document reading text (markdown body) |
| `body` | 14px | 400 | 1.5 | 0 | Default UI text |
| `body-medium` | 14px | 500 | 1.5 | 0 | Nav items, emphasized UI text, buttons |
| `caption` | 13px | 400 | 1.4 | 0 | Meta text: dates, authors, counts |
| `caption-bold` | 12px | 600 | 1.4 | 0 | Badges, tags |
| `overline` | 11px | 600 | 1.4 | 0.8px uppercase | Sidebar section labels ("TEAMS") |

Rules: 600 for headings, 500 for buttons/nav, 400 for body. Use tabular numbers for counts and stats.
Markdown body: max width 720px, paragraphs 16px/1.65, headings h1-h3 map to title-xl/lg/md, code blocks on `--color-surface` with 6px radius.

## 4. Layout & spacing
Base unit 4px. Tokens: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.

App shell (all logged-in pages):
- **Sidebar** 248px, `--color-surface`, right hairline. Top: app brand. Then nav (Dashboard, Search), then overline "TEAMS" with the user's teams (tint icon + name), then footer with user avatar + name + logout. Active item: `--color-primary-soft` background + primary text or 2px primary left marker. Items 32px tall, 6px radius.
- **Top bar** 56px inside the content area: breadcrumbs (Team / Space / Document) on the left, page actions on the right. Bottom hairline.
- **Content** max-width 1120px for dashboards/lists, 720px for reading a document. 32px page padding desktop, 16px mobile.
- Auth pages (login, register, join): no sidebar. Centered card max 400px on `--color-surface` background, app brand above it.

## 5. Shapes & elevation
Radius: 4px tags/chips · 6px nav items, small buttons, code · 8px buttons, inputs · 12px cards, modals · 9999px status badges, avatars.

| Level | Shadow | Use |
|---|---|---|
| 0 | none, 1px hairline border | Cards, tables, panels (default) |
| 1 | `0 1px 2px rgba(15,15,15,.04)` | Hovered clickable cards |
| 2 | `0 4px 12px rgba(15,15,15,.08)` | Dropdowns, popovers |
| 3 | `0 16px 48px -8px rgba(15,15,15,.16)` | Modals, toasts |

## 6. Components

### Buttons (height 36px, 8px radius, `body-medium`, padding 0 14px; small variant 30px)
- **primary**: `--color-primary` bg, white text. One per view.
- **secondary**: transparent, `--color-ink` text, 1px `--color-hairline-strong` border.
- **ghost**: transparent, `--color-slate` text, `--color-hover` on hover. Toolbars, icon buttons.
- **danger**: `--color-error` text on transparent; filled red only inside a confirm dialog.
- **link**: `--color-link` text, no padding.
- Disabled: `--color-hairline` bg, `--color-muted` text. Loading: spinner replaces label, width stays fixed.
- Icon-only buttons need `aria-label` and a tooltip.

### Inputs
- Text input: 40px, `--color-canvas`, 1px `--color-hairline-strong`, 8px radius, padding 0 12px. Label above (13px, 500, `--color-slate`).
- Focus: 2px primary ring (`box-shadow: 0 0 0 2px` primary at 35%) + primary border. Never remove focus outlines without replacing them.
- Error: `--color-error` border + 13px error text under the field.
- Search field: `--color-surface` bg, search icon left, `Ctrl+K` hint right.
- Textarea / markdown editor: monospace font, 14px/1.6, `--color-canvas`, min-height 320px.
- Select, checkbox, radio: native elements styled to match (accent-color: primary).

### Cards & lists
- **card**: canvas, 1px hairline, 12px radius, 20px padding. Clickable cards get level-1 shadow + `--color-hover` border on hover.
- **stat card** (dashboard counts): label `caption` steel, number `title-lg` tabular.
- **list row** (documents, members): 44px+, hairline-soft separators, hover `--color-hover`, title `body-medium`, meta `caption` steel on the right.
- **table**: header `--color-surface-soft`, `caption-bold` slate uppercase-free labels, rows as list rows.

### Badges & tags
- **Status badge** (pill, `caption-bold`, 2px 8px): DRAFT = gray tint, PUBLISHED = mint tint (success text).
- **Role badge** (pill): ADMIN = lavender, EDITOR = sky, VIEWER = gray.
- **Tag chip** (4px radius, `caption-bold`, 2px 8px): tint chosen by tag-name hash. Removable chips in the editor get a small x button with aria-label.
- **Count badge**: small pill, `--color-surface` bg, steel text.

### Navigation
- **Tabs** (team page: Spaces / Members / Requests; document: Comments / Versions / Files): underline tabs, inactive steel, active ink with 2px primary underline.
- **Breadcrumbs**: caption, steel, `/` separators, last item ink.
- **Pagination / load more**: secondary button, centered.

### Overlays & feedback
- **Modal**: canvas, 12px radius, level 3, max 480px, title-md header, actions right-aligned (secondary then primary). Closes on Esc and backdrop click; traps focus.
- **Dropdown menu**: canvas, 8px radius, level 2, items 32px, danger items red.
- **Toast**: bottom-right, level 3, 8px radius, icon + message, auto-hide 4s, success/error variants. Replaces `alert()`.
- **Confirm dialog**: for delete/remove actions (documents, members, attachments, comments).
- **Empty state**: centered, simple line icon, title-sm + one line of help text + one primary/secondary action.
- **Loading**: skeleton rows/cards (surface color, subtle pulse), not spinners, for lists. Spinner only inside buttons.
- **Error state**: inline message with retry button. 401 -> redirect to login.

### KnowledgeForge-specific
- **Avatar**: circle, 24/32/40px; image if profile picture, else initials on a name-hashed tint.
- **Team / Space icon**: 24px rounded-6 square with first letter on a tint.
- **Document editor**: title input as borderless `title-xl`; toolbar row (status select, tags input, save, export); split view markdown | live preview on desktop (toggle on mobile); unsaved-changes indicator; Ctrl+S saves.
- **Document view**: reading column 720px, meta line (author, updated, status, tags), right rail or tabs for Comments / Versions / Files.
- **Version history**: vertical list, version number + author + time, "Restore" secondary small button, current version marked.
- **Comments**: avatar + name + time (caption) + text; composer at the bottom; delete own via ghost icon.
- **File chips**: 8px radius, hairline border, file-type icon + name (truncated, full name in title) + size in caption; download on click; delete x for ADMIN/EDITOR. Links shown with a link icon.
- **Activity feed**: avatar + "**Name** updated **Doc title**" + relative time, grouped by day.
- **Search results**: filter chips row (type, tag, status), results grouped by type, matched term highlighted with a soft yellow (#fef7d6) background.
- **Invite link box**: read-only input + Copy button with "Copied" feedback.

## 7. Motion
150-200ms, ease-out for enter, ease-in for exit. Animate only opacity and transform. Hover color transitions 120ms.
Respect `prefers-reduced-motion: reduce` (turn off non-essential animation). No bouncing or attention-seeking motion.

## 8. Responsive
| Breakpoint | Behavior |
|---|---|
| < 640px | Sidebar becomes an off-canvas drawer (hamburger in top bar). Single column. Editor shows edit/preview toggle. Tables become stacked rows. |
| 640-1023px | Collapsible sidebar (icons only at 64px). Two-column grids. |
| >= 1024px | Full sidebar, split editor, up to 3-4 column card grids. |
Touch targets at least 40px on touch screens. No horizontal page scroll at 360px width.

## 9. Accessibility (non-negotiable)
- Text contrast at least 4.5:1 (3:1 for large text and UI borders that carry meaning).
- Visible focus on every interactive element; logical tab order; skip-to-content link.
- Real `<button>` / `<a>` elements, labels tied to inputs, `aria-label` on icon buttons, `aria-live` for toasts.
- Status never shown by color alone (badge text always present).
- Images and avatars have alt text; decorative icons `aria-hidden`.

## 10. Do / Don't
Do: hairlines over shadows · one primary action per view · warm grays · tints for categorization · generous reading width limits · designed empty states · consistent 8/12px radius.
Don't: gradients or glows · emoji as icons · purple-blue "AI app" look · heavy drop shadows on flat cards · pill-shaped primary buttons · more than one accent color competing · `alert()`/`confirm()` popups · raw user text inserted as HTML (always escape).
