# Release 1a wireframes (approved 2026-11-09)

Low-fidelity layout blueprints: they fix STRUCTURE (which screens exist, what is on each, where it sits).
The look (colors, type, spacing, motion) comes from `DESIGN.md` and a later design pass.

| File | Screen |
|---|---|
| `Main.dc.html` | 1 · Page view and app shell (sidebar, top bar, draft bar, page blocks) |
| `Editing.dc.html` | 2 · Editing: `/` block menu, block handles, save states, Release 1a soft-lock banner |
| `History.dc.html` | 3 · Version history side panel + per-author change highlights + restore |
| `Palette.dc.html` | 4 · Ctrl+K command palette (pages, content matches, actions) |
| `Trash.dc.html` | 5 · Trash and archive per project + delete confirmation |
| `Share.dc.html` | 6 · Share dialog: page access (view/edit per person) + public link |
| `MoveToTeam.dc.html` | 7 · Move a private page into a team project |
| `Sidebar.dc.html` | Shared sidebar component used by every screen |

Layout decisions: sidebar left and always visible on desktop; history opens as a right-side panel; share, Ctrl+K and
move are dialogs; trash is a full page per project; the Draft/Published bar sits under the top bar.
Files are HTML markup with inline styles: read them for layout and content, do not ship them.

## Design-pass ideas (owner, 2026-11-09)
Visual design is done screen by screen AFTER Release 1a works. First idea, for the sidebar page tree:
- a small coloured marker (dot) before each page, colour picked per page;
- thin vertical guide lines connecting a parent to its children, with a short tick into each child.
