# KnowledgeForge: Product Requirements Document (PRD)

Version 1.2 · 2026-11-14 (1.2: US-10 migration dropped by D7; 1.1: concurrent editors 50 -> 100) · Owner: MD. Lutfur Rahman
Status: APPROVED. Feature priorities come from `MoSCoW (fixed).md`.
Next phases after approval: domain model & schema -> wireframes -> architecture & tool decisions (ADRs) -> build.

---

## 1. Vision
**KnowledgeForge is the shared workspace where student project teams write, plan and keep everything about their project in one place: docs, wikis and (later) whiteboards, edited together in real time.**

## 2. Problem
Student project teams (course projects, thesis groups, study groups) spread their work across WhatsApp, Google Docs,
Drive folders, screenshots and someone's laptop. Two pains, seen together:
1. **Knowledge gets lost**: decisions, notes and resources are scattered; before a deadline nobody can find the latest version.
2. **Planning together is clumsy**: brainstorming and diagrams happen in a separate tool (or on paper) and never connect back to the docs.

## 3. Users
**Primary persona: the student project team**
- 3-6 students, one course project or thesis, lasting a semester (~4 months).
- Mostly laptops; phones for reading and quick edits.
- Work in bursts: kickoff, weekly meetings, the week before a deadline.
- Mixed tech comfort; no one wants to read a manual.
- Need: one place for project docs and decisions, editing together live, finding things fast, sharing a read-only page with a supervisor.

**Secondary users** (served, but don't win tie-breaks): supervisors/teachers (read, comment), study groups, clubs, small startups.

**Tie-break rule:** when two features compete for time, the one that helps a student project team more wins.

## 4. Goals & success metrics
| Goal | Metric | Target |
|---|---|---|
| Real users | Student teams actively using it | 3 teams by end of Release 1b, 10 by end of Release 2 |
| Engagement | Weekly active users | 20+ by end of Release 1b |
| Usefulness | Pages created per active team per week | 5+ |
| Reliability | Uptime / data loss | 99% monthly uptime, zero data loss (backups) |
| Engineering depth (portfolio) | CI green, test coverage on core services, documented ADRs | CI on every PR, ADR per major decision |

## 5. Scope by release
Design rule: **build the wiki first, but design the data model for both pages and boards from day one.**

### Release 1a: Wiki foundation (no real-time yet)
| Feature | MoSCoW | Notes |
|---|---|---|
| Spaces | Must | Exists; keep. |
| Hierarchical page tree (nested pages) | Must | Replaces the flat Space -> Document model. |
| Block-based editor + `/` menu | Must | Replaces the markdown textarea. |
| Status lozenges | Must | A block type. |
| Labels & tags | Must | Tags exist; extend to filtering across spaces. |
| Version history, compare & restore | Must | History + restore exist; add compare (diff). |
| Trash & archive | Must | New. |
| Command palette & global search (Ctrl+K) | Must | Search exists; add the palette. |
| Locking (pages) | Must | |
| Media & files in pages | Should | Upload backend (GridFS) exists. |
| Web bookmarks | Should | |
| **Migration** | dropped (D7) | Not needed: the 1a deploy starts on an empty database. |

### Release 1b: Collaboration
| Feature | MoSCoW | Notes |
|---|---|---|
| Real-time co-editing + presence (carets, who's here) | Must | The shared real-time engine; reused by boards in Release 2. |
| Page permissions | Must | Per page, on top of team roles. |
| Public link sharing | Must | Read-only (optionally editable) link without login. |
| Comments with @mentions | Should | Inline comments on text; replace page-level comments. |
| Notifications (in-app, then email) | Should | |

### Release 2: Whiteboard
| Feature | MoSCoW |
|---|---|
| Boards as pages in the tree + embed a board in a page | Must (product-level) |
| Infinite canvas (pan, zoom, minimap; mouse/trackpad/touch) | Must |
| Shapes, sticky notes, connectors & arrows | Must |
| Frames, layering & alignment, locking items | Must |
| Media on canvas | Must |
| Live cursors & presence on boards | Must |
| In-board search; board version history & trash; public board links | Must |
| Pen/highlighter/eraser/lasso, object hyperlinks, docs on the board, tables, board export | Should |

### Release 3: Work tracking & polish
| Feature | MoSCoW |
|---|---|
| Kanban / task board (assignee, due date, status) | Should |
| Tables with sort/filter (pages and boards) | Should |
| Space blueprints, colour-coded history, audit log | Should |
| Could-list items, chosen by real user feedback | Could |

### Out of scope (Won't)
Clustering, Miro cards, wireframe library, synced copies, webpage capture, live reactions, music player, MCP server, live iFrame embeds, diagram plugins.

## 6. User stories: Release 1a (with acceptance criteria)
Format: *As a team member, I want ..., so that ...* · AC = acceptance criteria.

**US-1 Nested pages**: As a member, I want to create pages inside pages, so that our project is organized like a real wiki.
- AC: create a sub-page from a page or the sidebar; tree shows unlimited depth; drag to re-order or move under another parent; moving a page moves its children; breadcrumbs show the full path.

**US-2 Block editor**: As a member, I want to write with blocks and a `/` menu, so that formatting is fast without markdown syntax.
- AC: `/` opens a filterable menu with at least: text, H1-H3, bulleted/numbered/to-do list, quote, divider, code, image, file, table, status, bookmark; blocks can be dragged to re-order and turned into another type; Ctrl+Z/Ctrl+Y work; content autosaves (no "Save" button), with a visible saved/saving state.

**US-3 Status lozenges**: As a member, I want to drop a coloured status into a page, so that everyone sees what's in progress or blocked.
- AC: inline status chip with a label and one of 5+ colours; editable in place.

**US-4 Labels**: As a member, I want to tag pages and filter by tag across my spaces, so that related pages are easy to find.
- AC: add/remove tags on a page; clicking a tag lists all pages with it (only pages I can access).

**US-5 History & compare**: As a member, I want to see who changed what and compare two versions, so that I can understand and undo changes.
- AC: version list with author and time; side-by-side or inline diff between any two versions; restore creates a new version (nothing is lost).

**US-6 Trash & archive**: As a member, I want deleted pages to go to trash and old pages to be archived, so that nothing is lost by accident.
- AC: delete moves a page (and children) to the space trash; restore puts it back in place; trash auto-empties after 30 days; editors can permanently delete; archived pages leave the tree but stay searchable and restorable.

**US-7 Command palette**: As a member, I want Ctrl+K to jump anywhere, so that I never hunt through the sidebar.
- AC: Ctrl+K opens a palette; shows recent pages first; typing searches titles and content across my spaces; Enter opens; also offers actions (new page, new space, go to settings).

**US-8 Page lock**: As an editor, I want to lock a finished page, so that nobody changes it by accident.
- AC: locked pages are read-only for everyone until an editor unlocks; lock state is visible.

**US-9 Files & bookmarks in pages**: As a member, I want to drop files and links into a page, so that resources live next to the notes.
- AC: drag/drop or `/file` uploads (existing limits: allowlist, 5 MB); images render inline; pasting a URL offers a bookmark card (title, description, image when available).

**US-10 Migration (dropped by D7, 2026-11-14: all existing data is dummy data and there are no real users, so nothing needs protecting)**: As an existing user, I want my old documents to keep working, so that the upgrade loses nothing.
- AC: every existing document becomes a page with equivalent blocks; versions, comments, attachments and tags are preserved; a one-time migration is logged and re-runnable safely.

(User stories for Releases 1b, 2 and 3 are written when each release starts.)

## 7. Non-functional requirements
| Area | Requirement |
|---|---|
| Performance | Page opens < 2 s on a normal connection; typing feels instant (< 50 ms local); real-time changes reach others < 500 ms (Release 1b). |
| Scale target | 500 users, 100 people editing at once, 10,000 pages (sized for university use, not a global SaaS). |
| Availability | No cold-start sleeping in production; 99% monthly uptime; health check + uptime monitor. |
| Data safety | Automated daily backups with a tested restore; nothing deleted without trash/undo. |
| Security | Session auth with CSRF protection; rate limiting on login/register; per-page access checks on every API call (including real-time channels); all user content escaped/sanitized; OWASP Top 10 review per release. |
| Privacy | Privacy page; delete-my-account (removes or anonymizes personal data); AI features clearly labelled as sending text to a provider. |
| Accessibility | WCAG 2.1 AA: keyboard-usable editor and palette, visible focus, contrast, screen-reader labels. |
| Compatibility | Latest Chrome, Edge, Firefox, Safari; responsive down to 360 px (phones: read + light editing). |
| Quality | CI runs tests on every PR; unit + integration tests (real MongoDB in tests) for core services; no merge on red. |
| Maintainability | Architecture decisions recorded as ADRs; DESIGN.md is the UI source of truth. |
| Observability | Structured logs, error tracking, basic usage metrics for the success metrics above. |

## 8. Constraints & assumptions
- Solo developer, ~3-4 months.
- Tools are chosen by fit, not by price; cost is handled separately (student credits etc.).
- Existing stack (Spring Boot 4, MongoDB, plain JS frontend) may change where a feature requires it; any change gets an ADR.
- Releases ship to real users; each release must be usable on its own.

## 9. Open questions (to settle in the next phases)
1. Editor technology and whether the frontend moves to a framework (decide in the tools phase, ADR).
2. Real-time engine (CRDT vs. operational transform, and which library) (ADR).
3. Page permission model details: inherit from parent page? (schema phase)
4. Hosting without cold starts (tools phase).
5. Exact AI features for pages/boards beyond existing Summarize and Ask (later release).
