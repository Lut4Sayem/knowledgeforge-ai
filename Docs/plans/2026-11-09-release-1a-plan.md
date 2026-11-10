
# Release 1a (Wiki Foundation) Implementation Plan

Date: 2026-11-09 · Status: APPROVED 2026-11-09 (scope decisions D1-D5 accepted as recommended) · Owner: MD. Lutfur Rahman

**Goal:** Turn the flat Team -> Space -> Document app into a working wiki: nested pages in a tree, a BlockNote block
editor with autosave, status lozenges, tags, version history with compare/restore, trash/archive, page lock,
Ctrl+K palette with Atlas Search, files and bookmarks in pages, and a safe migration of all existing documents.

**Functional first.** Every screen in this plan is built to the wireframes' STRUCTURE with plain, token-based CSS.
The visual design pass happens AFTER 1a works, screen by screen (Phase 10 is only the hand-off list, not the pass).

**Architecture:** Spring Boot stays the single owner of data and rules (ADR 0004). A new React + TypeScript (Vite) app
in `frontend/` replaces the 9 plain-JS pages and is built into Spring's static resources (one deployable, ADR 0001/0006).
Page content is a Yjs document edited by BlockNote **in the browser**; in 1a there is no collab server, so the browser
sends the encoded Yjs state plus extracted plain text to Spring on autosave (the Node collab service arrives in 1b).
A one-off Node script converts legacy markdown to BlockNote/Yjs during migration (ADR 0007).

**Tech stack:** Java 17, Spring Boot 4.1, Spring Data MongoDB, Spring Security (session + CSRF), MongoDB Atlas +
Atlas Search, Testcontainers (`mongodb/mongodb-atlas-local`), React 18 + TypeScript + Vite, BlockNote (MPL core only),
Yjs, TanStack Query, React Router, dnd-kit, cmdk, jsdiff, idb-keyval, Vitest + Testing Library, Playwright, GitHub Actions.

**Specs this plan implements (read them with it):**
- `Docs/PRD.md` v1.1, section 6 (US-1 ... US-10) and section 7 (NFRs)
- `Docs/specs/2026-11-09-domain-model-design.md` (rules: permissions 4.1, trash 4.5, draft 4.7, lock 4.8, versions 5, migration 6)
- `Docs/specs/2026-11-09-physical-schema.md` (collections, fields, limits, indexes)
- `Docs/adr/0001` ... `0007` (0008 is Release 2)
- `Docs/wireframes/` (Main, Editing, History, Palette, Trash, Sidebar; Share and MoveToTeam: see Scope decisions)

---

## Scope decisions (approved 2026-11-09)

| # | Question | Recommendation |
|---|---|---|
| D1 | Share dialog + page restrictions UI + public links are in the 1a wireframe set, but the PRD puts them in **1b**. | Follow the PRD: **no Share UI, no share links in 1a.** The permission resolver still understands `restriction` (tested) so 1b only adds the UI and endpoints. |
| D2 | Personal spaces: the migration creates one per user (domain spec 6), so they exist in 1a. Without "Move to team" a private page is stuck there. | **Build Move-to-team in 1a** as the last feature (Phase 8, Should). If time runs short, ship 1a with it hidden. |
| D3 | History wireframe colours changes per author. Without real-time (1b) a session with two authors can't attribute each word. | 1a colours a version's changes by the version's author; a multi-author version uses one shared colour labelled with all names. Exact per-author marks come in 1b from Yjs. |
| D4 | Plain text for search comes from the browser in 1a (no collab server yet). | Accept: it is only the user's own editable content, capped at 1 MB, and only used for search/diff. In 1b the collab service extracts it server-side. |
| D5 | Page-level comments and AI Summarize/Ask exist today and the migration must preserve comments. | Keep both working on nodes with the **existing** UI behaviour (comment list under the page; Summarize button; Ask on the project page). No inline comments (1b). |
| D6 | A stale save (409) offered "Reload latest / Keep mine": one click threw away one side, with no version to go back to. | **Approved 2026-11-10: merge, guarded by an epoch.** Both copies are Yjs documents of one lineage, so on a 409 the client merges the server state into its document and saves again; the banner and both buttons are gone. `node_contents.contentEpoch` (int, default 0) goes up ONLY when the state is replaced wholesale (version restore, migration conversion), never on a normal save. A copy based on another epoch is NOT merged (it would bring back what the restore removed): it is stored as a private version (`kind RECOVERED`, `privateTo` = the user) through `POST /api/nodes/{id}/versions/recovered`, the user sees "Your unsaved changes were kept as a private version because this page was restored", and the page loads the server state. The local copy is cleared only after that save succeeded. RECOVERED versions are never merged with other versions. |

---

## Global Constraints

- Scope: PRD v1.1 section 5 "Release 1a" only. Primary user = student project teams; tie-breaks go to them.
- Backend first, then frontend, inside every phase.
- Package-by-feature under `com.knowledgeforge.knowledgeforge`; constructor injection; `ResponseStatusException` + `GlobalExceptionHandler` JSON `{"message": ...}`.
- Spring Boot 4 property names: `spring.mongodb.*`. Secrets only from env vars.
- New collections: `snake_case` plural; fields `camelCase`; `createdAt`/`updatedAt` UTC Dates; `schemaVersion: 1`; references stored as ObjectId (`@Field(targetType = FieldType.OBJECT_ID)` on `String` fields).
- Size limits (physical schema): title 0-200; tags max 20, each 1-40, lower-cased; ancestors max 50 (tree depth 50); rank 1-64 chars; content `state` max 4 MB; `text` max 1 MB; version name max 100; icon max 16; `recentNodes` max 20.
- Every main query uses an index; integration tests assert it with `explain()`.
- BlockNote: only MPL-2.0 packages (`@blocknote/core`, `@blocknote/react`, `@blocknote/mantine`). **Never `@blocknote/xl-*`.**
- Attachments: existing rules unchanged (extension allowlist, 5 MB/file, 300 MB total, download as attachment + nosniff).
- Trash auto-purge after **30 days**; soft lock expires **~60 s** after last heartbeat (heartbeat every **20 s**); autosave **~1 s** after typing stops.
- Version job: every 1 min; AUTO version after **5 min** idle, or session **>= 15 min** AND **~30 s** pause; merge if same single author, **< 1 h** after previous AUTO, change **< ~200 chars**; skip when `contentHash` unchanged.
- Performance budgets: page opens < 2 s; typing < 50 ms; 10,000 pages seeded for measurement.
- Accessibility: keyboard-usable editor, tree and palette; visible focus; labels for screen readers.
- Responsive down to 360 px (read + light editing).
- Git: small commits with clear messages; follow the repo's local git rules; never commit local-only notes (`Docs/PROJECT_CONTEXT.md`, `Docs/FEATURE_PLAN.md`).
- Each phase ends green in CI and updates `Docs/PROJECT_CONTEXT.md` (progress log + roadmap tick).

## Review Focus

1. **Big page vs. unload flush.** `fetch(..., {keepalive: true})` bodies are capped at 64 KB by browsers, so a large page's flush on tab close can fail silently. Expected: nothing lost; the pending state stays in IndexedDB and is replayed on next open. Test in Task 4.3 (`pendingStateSurvivesFailedFlush`).
2. **Two tabs, same user, same page.** Both pass the soft lock (same holder) and race on `revision`. Expected (changed by D6): second save gets 409, the client merges the server state into its document and saves again; both tabs' edits are in the page. Test in Task 3.1 (`staleRevisionReturns409`) and Task 4.3 (`sameEpochMerges`, Playwright two-tab test).
3. **Move a page under its own descendant.** Expected: 400 "Can't move a page inside itself", tree unchanged. Test in Task 2.3 (`moveUnderOwnDescendantRejected`).
4. **Bookmark URL pointing at the server's own network** (`http://localhost`, `169.254.169.254`, `10.x`, redirects to them). Expected: no request leaves to private/loopback/link-local addresses; preview simply falls back to the bare URL. Test in Task 6.2 (`linkPreviewBlocksPrivateAddresses`).
5. **Restore a branch whose parent was deleted forever / is in trash.** Expected: branch restored at the space top level with a notice, `ancestors` rewritten for every node. Test in Task 5.1 (`restoreWithMissingParentGoesToTop`).

---

## File structure (decided)

Backend, new packages (each: entity, repository, service, controller, DTOs):

| Package | Responsibility |
|---|---|
| `access` | `NodeAccessService`: the one permission resolver (domain 4.1). Everything else calls it. |
| `node` | `Node` entity (`nodes`), tree reads, create/rename/tags/icon, move + `RankKeys`, publish, lock, trash/archive/restore/purge |
| `nodecontent` | `NodeContent` (`node_contents`): load/save with revision check, size caps, hash, edit session |
| `nodeversion` | `NodeVersion` (`node_versions`): session job, merge rule, named versions, list, restore (replaces `documentVersion`) |
| `editlock` | `EditLock` (`edit_locks`, TTL): soft lock acquire/heartbeat/release (deleted in 1b) |
| `linkpreview` | Server-side bookmark metadata fetch with SSRF guard |
| `migration` | `MigrationRunner`, `MigrationStep` interface, steps, `migrations` collection, internal content API for the Node script |
| `space`, `user`, `comment`, `attachment`, `search`, `ai`, `dashboard`, `activity` | existing; changed as tasks say |
| `documentPkg`, `documentVersion` | **deleted** in Phase 9 after migration |

Frontend (`frontend/`, Vite root):

```
frontend/src/
  api/          client.ts (fetch + CSRF header + 401 redirect), types.ts, one file per resource (nodes.ts, content.ts, ...)
  app/          App.tsx (routes), Shell.tsx (sidebar + top bar + outlet), queryClient.ts
  tree/         Sidebar.tsx, PageTree.tsx, buildTree.ts
  page/         PageView.tsx, DraftBar.tsx, Breadcrumbs.tsx, TagEditor.tsx, PageMenu.tsx
  editor/       Editor.tsx, schema.ts (custom blocks), StatusBlock.tsx, BookmarkBlock.tsx, slashItems.ts,
                autosave.ts, pendingStore.ts, SaveIndicator.tsx, SoftLockBanner.tsx
  history/      HistoryPanel.tsx, groupVersions.ts, DiffView.tsx
  trash/        TrashPage.tsx, ConfirmTrashDialog.tsx
  palette/      CommandPalette.tsx
  move/         MoveToTeamDialog.tsx
  pages/        Login, Register, Join, Dashboard, Team, Profile, Search (ported old pages)
  styles/       tokens.css (copied from DESIGN.md section 0), base.css (plain layout only)
frontend/e2e/   Playwright specs
collab/         Node package; in 1a only `migrate-content.ts` (ADR 0007). Hocuspocus is added here in 1b.
```

---

## Phase 0: Foundation (test infra, CI, CSRF, React shell)

### Task 0.1: Integration test base with Atlas-local in Testcontainers
**Files:** Modify `pom.xml` (add `org.testcontainers:mongodb`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`). Create `src/test/java/.../support/IntegrationTest.java`, `src/test/java/.../support/ExplainAssert.java`.
**Produces:** `abstract class IntegrationTest` (`@SpringBootTest`, static `MongoDBAtlasLocalContainer` with `@ServiceConnection`, image `mongodb/mongodb-atlas-local:8.0`), `ExplainAssert.usesIndex(MongoTemplate t, String collection, Query q, String indexName)`.
- [x] Test `IntegrationTestSmokeTest.containerStartsAndAppConnects`: saves and reads a `User`.
- [x] Test `ExplainAssertTest.detectsCollectionScan`: query without index -> assertion fails; with index -> passes.
- [x] Make `KnowledgeforgeApplicationTests.contextLoads` extend `IntegrationTest` (no more Atlas env var needed for tests).
- [x] `./mvnw -q test` green. Commit `test: add Atlas-local integration test base`.

### Task 0.2: CI on every PR
**Files:** Create `.github/workflows/ci.yml`.
- [x] Jobs: `backend` (Temurin 17, `./mvnw -B verify`, Docker available for Testcontainers), `frontend` (Node 20, `npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build`). Required on PR to `main`.
- [ ] Open a test PR; both jobs green. Commit `ci: run backend and frontend checks on PRs`.

### Task 0.3: Turn CSRF protection on
PRD 7 requires CSRF protection; it is disabled today.
**Files:** Modify `SecurityConfig.java`; old `static/app.js` `KF.api` (send header) so old pages keep working until retired.
**Produces:** cookie `XSRF-TOKEN` (`CookieCsrfTokenRepository.withHttpOnlyFalse()`), header `X-XSRF-TOKEN` required on POST/PUT/PATCH/DELETE under `/api/**`; exempt only `/api/auth/login`, `/api/auth/register` and (Phase 9) `/internal/**` (protected by its own secret).
- [x] Test `CsrfTest.postWithoutTokenIs403` and `postWithTokenSucceeds` (MockMvc).
- [x] Manual: log in on the old pages, edit a document. Commit `feat: enable CSRF protection`.

### Task 0.4: React + TS app scaffold served by Spring
**Files:** Create `frontend/` (Vite React-TS template, ESLint, Vitest, Playwright config), `frontend/src/api/client.ts`, `frontend/src/app/App.tsx`, `frontend/src/styles/tokens.css`. Modify `pom.xml` (`com.github.eirslett:frontend-maven-plugin`: install Node 20, `npm ci`, `npm run build` in `generate-resources`; Vite `build.outDir` = `../target/classes/static/app`, `base: '/app/'`). Create `src/main/java/.../SpaController.java`. Modify `Dockerfile` (no change needed if Maven builds the frontend; verify).
**Produces:**
- `api<T>(path: string, init?: RequestInit): Promise<T>`: JSON, sends `X-XSRF-TOKEN` from cookie, throws `ApiError {status, message}`, on 401 navigates to `/app/login?next=<path>`.
- `SpaController`: forwards `GET /app/**` (paths without a dot) to `/app/index.html`.
- Dev: `npm run dev` on :5173 with proxy `/api`, `/logout`, `/internal` -> :8080.
- [x] Vitest `client.test.ts`: `sendsCsrfHeaderOnPost`, `redirectsToLoginOn401`, `throwsApiErrorWithServerMessage`.
- [x] Integration test `SpaControllerTest.deepLinkServesIndex` (`/app/p/123` -> 200 HTML).
- [x] `./mvnw -q package` produces a jar that serves `/app/`. Commit `feat: add React app scaffold served by Spring`.

### Task 0.5: Port auth pages (login, register, join) to React
Needed first so the new app is usable on its own. Same behaviour as the current pages (PROJECT_CONTEXT progress log), plain styling.
**Files:** `frontend/src/pages/Login.tsx`, `Register.tsx`, `Join.tsx`; routes `/app/login`, `/app/register`, `/app/join/:code`.
- [x] Vitest: `loginShowsServerErrorOnBadCredentials`, `loginRedirectsToSafeNextOnly` (`next=//evil.com` -> dashboard), `registerValidatesPasswordLength8`.
- [ ] Commit `feat: React login, register and join pages`.

---

## Phase 1: Data model (new collections, personal spaces)

### Task 1.1: Node, NodeContent, NodeVersion, EditLock, Migration entities + indexes + validators
**Files:** Create `node/Node.java`, `node/NodeRepository.java`, `nodecontent/NodeContent.java`, `nodecontent/NodeContentRepository.java`, `nodeversion/NodeVersion.java`, `nodeversion/NodeVersionRepository.java`, `editlock/EditLock.java`, `editlock/EditLockRepository.java`, `migration/MigrationRecord.java`, `common/UserRef.java`, `common/SchemaSetup.java`; enums `NodeType {PAGE, BOARD}`, `NodeStatus {ACTIVE, ARCHIVED, TRASHED}`, `PublishState {DRAFT, PUBLISHED}`, `ContentFormat {YJS_BLOCKNOTE_V1, YJS_BOARD_V1}`, `VersionKind {AUTO, NAMED, PUBLISHED, PUBLISHED_TO_TEAM, RESTORED, MIGRATED}`.
Fields exactly as physical schema 3.4-3.7 and 3.14. `Node.restriction` is `Restriction {List<String> viewers, editors}` or null.
`SchemaSetup` (`ApplicationRunner`): creates every index from physical schema 3.4-3.7 by name, and applies `$jsonSchema` (strict/error) to the new collections. Keep `auto-index-creation` off for new entities (indexes are explicit).
- [x] Integration test `SchemaSetupTest.createsAllIndexes`: list index names per collection equals the schema tables.
- [x] `SchemaSetupTest.rejectsTitleOver200`, `rejectsMoreThan20Tags`, `rejectsStateOver4MB` (validator rejects direct inserts).
- [x] Commit `feat: add node, content, version and lock collections`.

### Task 1.2: Space ownerType + personal space per user
**Files:** Modify `space/Space.java` (`ownerType`, `ownerUserId`, `icon`, `status`), `space/SpaceService.java`, `auth/AuthService.java` (register creates the personal space), `space/SpaceController.java`.
**Produces:** `SpaceService.createPersonalSpace(String userId): Space` (idempotent; unique partial index guarantees one); `GET /api/me/spaces` -> `[{id, name, icon, ownerType, teamId, teamName, myRole}]` (team spaces where I'm a member + my personal space; personal space named "Private").
- [x] Tests: `registerCreatesPersonalSpace`, `createPersonalSpaceTwiceKeepsOne`, `meSpacesListsTeamAndPersonal`, `meSpacesExcludesOtherTeams`.
- [x] Commit `feat: personal space per user`.

---

## Phase 2: Permissions and the page tree (US-1)

### Task 2.1: The permission resolver
**Files:** Create `access/NodeAccessService.java`, `access/Access.java` (`NONE, TITLE_ONLY, VIEW, EDIT`). Test `access/NodeAccessServiceTest.java` (Mockito, table-driven).
**Produces:**
- `Access resolve(String userId, Node node)` implementing domain spec 4.1 steps 2-6 (personal = owner only; team role base; restriction walk over `ancestors` then node, rights only go down; ADMIN not listed = `TITLE_ONLY`; ARCHIVED caps at VIEW; TRASHED = NONE here (trash views use `resolveForTrash`); `locked` caps at VIEW).
- `Access resolveForTrash(String userId, Node node)` (ignores TRASHED status, used by trash list/restore).
- `Node requireView(String nodeId)`, `Node requireEdit(String nodeId)`, `Node requireEditIgnoringLock(String nodeId)` (for unlock): load node, 404 if missing, 403 if insufficient. Current user from `SecurityContextHolder`.
- `boolean canEditSpace(String userId, Space space)`.
Shortcut allowed: no per-request cache in 1a (`shortcut: no cache; add a short (user,node) cache if the permission check shows up in profiling`).
- [x] Table test `resolveMatrix` covering: space type (TEAM/USER) x role (ADMIN/EDITOR/VIEWER/non-member) x restriction (none / on ancestor as viewer / as editor / not listed) x status (ACTIVE/ARCHIVED/TRASHED) x locked. At least these rows: `viewerIsCappedAtView`, `nonMemberIsNone`, `personalSpaceOtherUserIsNone`, `ancestorRestrictionAppliesToChild`, `childCannotWidenBeyondParent`, `adminNotListedSeesTitleOnly`, `archivedCapsAtView`, `lockedCapsEditorAtView`.
- [x] Commit `feat: node permission resolver`.

### Task 2.2: Rank keys
**Files:** Create `node/RankKeys.java`, `node/RankKeysTest.java`.
**Produces:** `static String between(String before, String after)` (either may be null = open end), base-62 digits `0-9A-Za-z`, result strictly between, never ends in `0`, length <= 64 (throw `IllegalStateException` to trigger `rebalance`); `static List<String> spread(int n)` for rebalancing a sibling list.
- [x] Tests: `betweenNullsGivesMiddle`, `betweenIsStrictlyOrdered` (property: 1,000 random inserts keep sort order), `repeatedInsertAtFrontStaysUnder64` for 200 inserts, `spreadIsSorted`.
- [x] Commit `feat: fractional rank keys`.

### Task 2.3: Tree, create, rename, tags, move
**Files:** Create `node/NodeService.java`, `node/NodeController.java`, DTOs `NodeSummaryDTO`, `NodeDTO`, `CreateNodeRequest`, `UpdateNodeRequest`, `MoveNodeRequest`. Integration test `node/NodeServiceIT.java`.
**Produces (endpoints):**
- `GET /api/spaces/{spaceId}/tree` -> `NodeSummaryDTO[] {id, parentId, rank, title, icon, type, publishState, locked, titleOnly}` for ACTIVE nodes the user can at least see the title of (ordered by `parentId, rank`). One query on `{spaceId, status, parentId, rank}`.
- `POST /api/spaces/{spaceId}/nodes {parentId?, title?, afterId?}` -> `NodeDTO` (type PAGE, DRAFT, empty `NodeContent` created in the same call with `format YJS_BLOCKNOTE_V1`, `revision 0`).
- `GET /api/nodes/{id}` -> `NodeDTO {id, spaceId, parentId, title, icon, tags, publishState, locked, lockedBy, status, access, breadcrumbs: [{id,title}], createdBy, updatedBy, contentUpdatedAt}`. Also pushes `{nodeId, at}` into `users.recentNodes` (`$push` + `$slice: -20`, removing an older entry for the same node first).
- `PATCH /api/nodes/{id} {title?, icon?, tags?}` (tags trimmed, lower-cased, de-duplicated; limits from Global Constraints -> 400).
- `POST /api/nodes/{id}/move {parentId (nullable), beforeId?, afterId?}`: same space only in 1a (cross-space is Move-to-team); requires edit on node and on destination parent (or space edit for top level); rewrites `ancestors` of the node and all descendants with one `updateMany` per depth change (use `{ancestors: id}` query); depth > 50 -> 400.
- [x] IT tests: `treeUsesIndex` (ExplainAssert), `createUnderParentSetsAncestors`, `moveRewritesDescendantAncestors`, `moveUnderOwnDescendantRejected`, `moveDeeperThan50Rejected`, `viewerCannotCreateOrMove` (403), `tagsAreNormalized`, `recentNodesCappedAt20`.
- [x] Commit `feat: page tree API`.

### Task 2.4: App shell, sidebar tree and page route (frontend)
Wireframes `Sidebar` and `Main`. Plain layout.
**Files:** `app/Shell.tsx`, `tree/Sidebar.tsx`, `tree/PageTree.tsx`, `tree/buildTree.ts`, `page/PageView.tsx`, `page/Breadcrumbs.tsx`, `api/nodes.ts`, `api/spaces.ts`. Routes: `/app` (dashboard placeholder until Phase 9), `/app/p/:nodeId`.
**Produces:** `buildTree(nodes: NodeSummary[]): TreeNode[]` (hash map id -> node, children sorted by `rank` then `id`). Sidebar: team switcher, "Search Ctrl K" button, PROJECTS = team spaces of the selected team, PRIVATE = personal space, each with expandable tree, `+` new page under a node and at top, "Archived" and "Trash" links per project. Drag-and-drop with dnd-kit (reorder + nest, keyboard sensor enabled) calls `move` with `beforeId/afterId`. Expanded state remembered per space in `localStorage` (try/catch).
- [x] Vitest: `buildTreeNestsAndSorts`, `buildTreeHandlesOrphansAtTop` (parent not in list -> top level), `newPageButtonCreatesAndNavigates`.
- [ ] Playwright `tree.spec.ts`: create page, create sub-page, drag sub-page to top level, breadcrumbs update.
- [x] Commit `feat: sidebar page tree and page route`.

---

## Phase 3: Content storage and soft lock

### Task 3.1: Load/save content with revision check
**Files:** `nodecontent/NodeContentService.java`, `nodecontent/NodeContentController.java`, DTOs `ContentDTO`, `SaveContentRequest`. IT `NodeContentServiceIT.java`.
**Produces:**
- `GET /api/nodes/{id}/content` -> `{format, state: base64, revision}` (view access).
- `PUT /api/nodes/{id}/content {baseRevision, state: base64, text}` -> `{revision, savedAt}`. Edit access. Atomic `findAndModify` on `{_id, revision: baseRevision}`; no match -> **409** `{message, currentRevision}`. Caps: state > 4 MB or text > 1 MB -> 413. Sets `contentHash = sha256(state)`, copies `search {title, tags, status, publishState}` from the node, updates `editSession` (`startedAt` if null, `lastEditAt = now`, add user to `authors` max 100), sets node `updatedBy`.
- `NodeService` keeps `node_contents.search` in sync on rename/tags/status/publish changes (one helper `syncSearchFields(nodeId)`).
- [x] IT: `saveIncrementsRevision`, `staleRevisionReturns409`, `stateOver4MBRejected`, `viewerCannotSave`, `lockedPageCannotBeSaved`, `renameUpdatesSearchCopy`.
- [x] Commit `feat: page content load and save`.

### Task 3.2: Soft lock (Release 1a only)
**Files:** `editlock/EditLockService.java`, `editlock/EditLockController.java`. IT `EditLockServiceIT.java`.
**Produces:** `POST /api/nodes/{id}/edit-lock {force: boolean}` -> `{mine: boolean, holder: UserRef|null, expiresAt}`: acquire if free/expired/mine (renew `expiresAt = now + 60 s`); held by someone else and `force=false` -> `{mine:false, holder}`; `force=true` -> take over. `DELETE /api/nodes/{id}/edit-lock` releases if mine. Reads check `expiresAt > now`.
- [x] IT: `acquireFreeLock`, `secondUserSeesHolder`, `expiredLockCanBeTaken`, `forceTakesOver`, `releaseOnlyOwn`.
- [x] Commit `feat: soft edit lock`.

---

## Phase 4: Block editor (US-2, US-3, autosave, no lost edits)

### Task 4.1: BlockNote on a local Y.Doc (spike, half a day, throwaway allowed)
Decides one thing: how BlockNote edits a `Y.XmlFragment` with **no provider**, and how a server-free conversion blocks <-> Y.Doc works (needed for migration). Check the current BlockNote docs first (APIs move fast).
- [x] Prove: editor bound to `ydoc.getXmlFragment('document-store')`; `Y.encodeStateAsUpdate(ydoc)` -> base64 -> new doc -> same blocks.
- [x] Prove: a Node script can turn markdown into a Y.Doc (BlockNote markdown parse + blocks-to-Y.Doc helper, MPL packages only; `@blocknote/server-util` if MPL).
- [x] Write the result as 5-10 lines in `Docs/PROJECT_CONTEXT.md` (decisions). If either proof fails, stop and write a short ADR before Task 4.2.

### Task 4.2: Editor with slash menu and custom blocks
**Files:** `editor/schema.ts`, `editor/Editor.tsx`, `editor/StatusBlock.tsx`, `editor/BookmarkBlock.tsx` (renders from props; fetching comes in Phase 6), `editor/slashItems.ts`, `editor/extractText.ts`.
**Produces:** `kfSchema` = default blocks + inline content `status {label: string, color: 'gray'|'blue'|'green'|'yellow'|'red'|'purple'}` + block `bookmark {url, title, description, imageUrl}`. Slash menu contains at least: text, H1-H3, bulleted/numbered/to-do list, quote, divider, code, image, file, table, status, bookmark (US-2). `extractText(blocks): string` (takes `editor.document`, so the Node migration script can use it too) = plain text of all blocks, newline-separated, status labels included.
- [x] Vitest: `slashMenuHasRequiredItems` (the 14 names above), `undoRedoWithCtrlZY` (Yjs undo manager, not the browser's), `statusChipEditableInPlace` (change label + colour), `extractTextIncludesStatusAndTable`.
- [x] Commit `feat: block editor with status and bookmark blocks`.

### Task 4.3: Autosave, local pending copy, flush, save states
**Files:** `editor/autosave.ts`, `editor/pendingStore.ts`, `editor/SaveIndicator.tsx`, `api/content.ts`. Modify `page/PageView.tsx`.
**Produces:**
- `pendingStore`: `put(nodeId, slot, {state, text, baseRevision, at})`, `all(nodeId)`, `clear(nodeId, slot)` on idb-keyval. One slot per editing session (tab), so two tabs on one page cannot overwrite each other's unsaved copy.
- `useAutosave(nodeId, ydoc, editor)`: on Y.Doc update -> write pending immediately -> debounce 1 s -> `PUT`; states `saved | saving | offline | retrying | conflict`; retry with backoff 1, 2, 5, 10, 30 s; `online` event retries; on `visibilitychange=hidden`/`pagehide` -> `fetch keepalive` PUT (ignore failure, pending copy remains); on open: if pending exists with `baseRevision == server revision` apply pending state then save; if server is newer -> `conflict`; a pending copy identical to the server's state is just dropped (the flush on close had worked). **Changed by D6:** a pending copy of the same epoch is always merged into the server state and saved (no conflict state); a copy of an older epoch is kept as a private RECOVERED version. Copies are scoped to the user, owned per tab through a Web Lock (a live tab's copy is never taken over), dropped after 7 days untouched, quarantined when unreadable, and removed on logout. A page with no saved state starts from one fixed state (`EMPTY_PAGE_STATE`), otherwise two tabs could not be merged.
- `beforeunload` prompt only while state is `saving | offline | retrying | conflict` (a conflict is unsaved too).
- `SaveIndicator` texts exactly: "Saved", "Saving…", "Offline: changes kept on this device", "Couldn't save, retrying".
- Stale save (D6, replaces the conflict banner): same `contentEpoch` -> merge the server state into the local Y.Doc and save on the new revision; different epoch -> `POST /api/nodes/{id}/versions/recovered {state, text, baseEpoch}`, notice "Your unsaved changes were kept as a private version because this page was restored", load the server state; if that save fails the local copy stays, the error is shown and it is retried with the usual backoff. A 409 whose server state equals what was just sent is our own save with a lost answer. Saves time out after 15 s; 400/403/404/413 stop the retry loop and show the server message.
- [x] Vitest (fake timers, mocked API): `debouncesOneSecond`, `pendingStateSurvivesFailedFlush`, `replaysPendingOnOpen`, `sameEpochMerges`, `restoreThenStaleClientDoesNotResurrectContent`, `recoveryFailureKeepsLocalCopy`, `offlineShowsOfflineText`. Java IT `RecoveredVersionIT` (`recoveredVersionIsPrivate`, `restoreThenStaleClientDoesNotResurrectContent` with a test helper standing in for restore).
- [x] Playwright `autosave.spec.ts`: type, wait for "Saved", reload, text is there; go offline (context.setOffline), type, reload online, text is there.
- [x] Commit `feat: autosave with local pending copy`.

### Task 4.4: Soft-lock banner and read-only mode
**Files:** `editor/SoftLockBanner.tsx`, modify `page/PageView.tsx`.
Behaviour: on open with edit access, `POST edit-lock {force:false}`; held by other -> banner "<name> is editing this page right now." [Open read-only] [Edit anyway]; while editing, heartbeat every 20 s; release on unmount/pagehide. Read-only also when `access=VIEW` or `locked`.
- [x] Vitest `showsBannerWhenOtherHolds`, `editAnywayForces`, `heartbeatEvery20s`. Playwright `softlock.spec.ts` (two users).
- [x] Commit `feat: soft lock banner`.

---

## Phase 5: Draft/publish, lock, tags filter, trash and archive (US-3 done, US-4, US-6, US-8)

### Task 5.1: Trash, archive, restore, purge (backend)
**Files:** Modify `node/NodeService.java`, `node/NodeController.java`; create `node/TrashPurgeJob.java`. IT `TrashIT.java`.
**Produces:**
- `GET /api/nodes/{id}/subtree-count` -> `{count}` (descendants, for the confirm dialog).
- `POST /api/nodes/{id}/trash` -> `{count}`: node + ACTIVE descendants -> TRASHED, `trashedAt/By`, `trashRootId = id`. Edit access.
- `GET /api/spaces/{id}/trash` -> `[{trashRootId, title, path, descendantCount, trashedBy, trashedAt, purgeAt, parentGone}]` (roots only).
- `POST /api/trash/{trashRootId}/restore`: whole branch -> ACTIVE; parent missing/TRASHED/ARCHIVED -> move to top level (rewrite `ancestors`), response `{movedToTop: true}`.
- `DELETE /api/trash/{trashRootId}`: team ADMIN (personal: owner) only; deletes nodes, contents, versions, comments, attachments + GridFS files, edit locks, AI caches.
- `TrashPurgeJob` (`@Scheduled` daily 03:00 UTC): same delete for roots with `trashedAt < now - 30 days`, batched 100 roots per run loop.
- Archive: `POST /api/nodes/{id}/archive`, `POST /api/nodes/{id}/unarchive`, `GET /api/spaces/{id}/archived`, same branch logic with ARCHIVED (no purge).
- Every action writes an `ActivityLog` (`TRASH_NODE`, `RESTORE_NODE`, `PURGE_NODE`, `ARCHIVE_NODE`, `UNARCHIVE_NODE`).
- [ ] IT: `trashMovesWholeBranch`, `restoreBringsBranchBack`, `restoreWithMissingParentGoesToTop`, `cannotRestoreInsideTrashedBranch` (restore by a non-root id -> 400), `onlyAdminDeletesForever`, `purgeDeletesAfter30DaysWithFiles`, `archivedIsReadOnlyAndSearchable`, `purgeQueryUsesIndex`.
- [ ] Commit `feat: trash, archive and purge`.

### Task 5.2: Publish, lock, tag listing (backend)
**Produces:** `POST /api/nodes/{id}/publish` / `unpublish` (edit access; only flips `publishState` here. Task 6.1 adds the `PUBLISHED` version on publish), `POST /api/nodes/{id}/lock` / `unlock` (page editors only; unlock uses `requireEditIgnoringLock`), `GET /api/tags/{tag}/nodes` -> nodes with that tag across all my spaces that I can VIEW (index `{tags}`).
- [ ] IT: `publishFlipsState`, `lockedPageRejectsSaveAndRename`, `unlockOnlyByEditor`, `tagListingHidesInaccessiblePages`.
- [ ] Commit `feat: publish, lock and tag listing`.

### Task 5.3: Frontend: draft bar, page menu, tags, trash/archive pages
Wireframes `Main` (draft bar, tags, page menu) and `Trash`.
**Files:** `page/DraftBar.tsx`, `page/PageMenu.tsx` (Lock/Unlock, Archive, Move to trash, Version history, Move to team (Phase 8)), `page/TagEditor.tsx`, `trash/TrashPage.tsx` (tabs Trash / Archived, table: Page, Was in, Deleted by, Deleted forever in, Actions), `trash/ConfirmTrashDialog.tsx` ("Move "<title>" to trash?" / "This also moves its N sub-pages. You can restore them from the trash for 30 days."), route `/app/s/:spaceId/trash`, `/app/tags/:tag`.
- [ ] Vitest: `draftBarHiddenWhenPublished`, `confirmShowsSubpageCount`, `deleteForeverOnlyForAdmins`, `restoreToTopShowsNotice`.
- [ ] Playwright `trash.spec.ts`: trash a parent with 2 children, restore, tree is back.
- [ ] Commit `feat: draft bar, tags, trash and archive screens`.

---

## Phase 6: Versions, compare, restore (US-5) + files and bookmarks (US-9)

### Task 6.1: Version sessions, merge rule, named versions (backend)
**Files:** `nodeversion/NodeVersionService.java`, `nodeversion/VersionSessionJob.java`, `nodeversion/NodeVersionController.java`, DTOs. Unit test `VersionRulesTest.java` (pure logic, injected `Clock`), IT `NodeVersionIT.java`.
**Produces:**
- `NodeVersion createVersion(String nodeId, VersionKind kind, String name)`: snapshot `state`, `text`, `title`, `contentHash`, `authors` (from `editSession`, else current user); clears `editSession`; sets node `contentUpdatedAt`.
- `VersionSessionJob` every 60 s: for contents with `editSession.lastEditAt` matching the rules in Global Constraints -> `closeSession(nodeId)`; `closeSession` skips when hash equals latest version's hash; applies merge rule (update latest AUTO in place, `updatedAt = now`) or inserts AUTO. NAMED/PUBLISHED/PUBLISHED_TO_TEAM/RESTORED/MIGRATED are never merged.
- `POST /api/nodes/{id}/session/end` (called on leaving the page; `sendBeacon`-friendly, empty body) -> `closeSession`.
- `GET /api/nodes/{id}/versions` -> `[{id, kind, name, authors, createdAt, updatedAt, private}]` newest first, excluding `privateTo` other users.
- `GET /api/versions/{vid}` -> `{..., state: base64, text}`; `POST /api/nodes/{id}/versions {name}` (named: if content unchanged since latest version, name that one instead); `POST /api/versions/{vid}/restore` -> sets content to snapshot (`revision+1`, closes current session first so nothing is lost) and creates a `RESTORED` version.
- Restore (D6 epoch rule): `POST /api/versions/{vid}/restore` replaces the state wholesale, so it must increment `node_contents.contentEpoch` together with `revision`. RECOVERED versions (already written by `POST /api/nodes/{id}/versions/recovered`, Phase 4) are never merged with other versions and never chosen as the "latest version" by the merge rule or the hash check.
- [ ] IT `restoreThenStaleClientDoesNotResurrectContent` against the REAL restore (today `RecoveredVersionIT` uses `ContentTestHelper.replaceWholesale`; switch it to the restore endpoint and delete the helper), plus `restoreIncrementsContentEpoch`, `recoveredNeverMerged`.
- [ ] Unit: `idleFiveMinutesCloses`, `longSessionClosesOnlyAfterPause`, `sameAuthorSmallChangeMerges`, `differentAuthorDoesNotMerge`, `namedNeverMerged`, `unchangedHashSkipped`.
- [ ] Wire publish (Task 5.2) to `createVersion(id, PUBLISHED, null)`.
- [ ] IT: `publishCreatesPublishedVersion`, `restoreCreatesNewVersionAndKeepsOld`, `privateVersionsHiddenFromOthers`, `versionListUsesIndex`.
- [ ] Commit `feat: version sessions, named versions and restore`.

### Task 6.2: Files in pages + bookmark previews (backend)
**Files:** Modify `attachment/*` (`documentId` -> `nodeId`, endpoints `POST /api/nodes/{id}/attachments/upload`, `GET /api/nodes/{id}/attachments`; download/delete unchanged; access via `NodeAccessService`). Create `linkpreview/LinkPreviewService.java`, `linkpreview/LinkPreviewController.java`.
**Produces:** `GET /api/link-preview?url=` -> `{url, title, description, imageUrl}` (all may be null). Rules: http/https only; resolve DNS and reject loopback, private, link-local, multicast, CGNAT, IPv6 ULA (check every redirect hop, max 3 redirects); timeouts connect 3 s / read 5 s; read at most 512 KB; parse `og:title`, `og:description`, `og:image`, `<title>`; per-user limit 30/min; result cached 24 h in memory (`shortcut: in-memory cache, move to Mongo TTL if multiple instances`).
- [ ] Tests: `linkPreviewBlocksPrivateAddresses` (localhost, 127.0.0.1, 10.0.0.1, 169.254.169.254, [::1], redirect to private), `parsesOpenGraph` (local test server), `stopsAfter512KB`, `uploadRequiresEditOnNode`.
- [ ] Commit `feat: node attachments and link previews`.

### Task 6.3: History panel and compare (frontend)
Wireframe `History`.
**Files:** `history/HistoryPanel.tsx`, `history/groupVersions.ts`, `history/DiffView.tsx`.
**Produces:** `groupVersions(v: VersionSummary[]): DayGroup[]` (Today / Yesterday / date; consecutive AUTO in one session collapsed as "▸ N checkpoints in this session"; private versions grouped as "N private drafts · Only you can see these"; RECOVERED versions are listed in that private group, labelled "Recovered unsaved changes", with "Restore this version" available to their owner). Panel: "Named versions only" filter, "Name the current version…" input, select a version -> main area shows the diff against the version before (jsdiff `diffWords` on `text`, insertions coloured by the version's authors per D3, deletions struck through), "Back to current", "Restore this version" (editors).
- [ ] Vitest: `groupsByDayAndSession`, `namedOnlyFilter`, `diffMarksInsertAndDelete`.
- [ ] Commit `feat: version history panel with compare`.

### Task 6.4: Image, file and bookmark blocks wired (frontend)
`/image` and `/file` and drag-drop upload through `POST /api/nodes/{id}/attachments/upload`; blocks store the attachment id and render via the download URL (images inline). Pasting a lone URL offers "Create bookmark"; the bookmark block calls `link-preview` and shows title, description, image; falls back to the URL.
- [ ] Vitest: `pasteUrlOffersBookmark`, `bookmarkFallsBackToUrl`, `rejectedFileShowsServerMessage` (413 text).
- [ ] Commit `feat: files, images and bookmarks in pages`.

---

## Phase 7: Search and command palette (US-4 filter, US-7)

### Task 7.1: Atlas Search index and search API
**Files:** Create `src/main/resources/search/nodes_search.json` (index definition), modify `common/SchemaSetup.java` (create the search index if missing), rewrite `search/SearchService.java`, `search/SearchController.java`, `search/SearchResultDTO.java`.
**Produces:** index `nodes_search` on `node_contents`: `search.title` (string + autocomplete), `text` (string), `search.tags` (token), filters `spaceId` (objectId), `search.status`, `search.publishState` (token). `GET /api/search?q=&tag=&publishState=&spaceId=&limit=20` -> `[{nodeId, spaceId, spaceName, title, path, snippetHtml, tags, publishState, archived}]`: `$search` compound (fuzzy `maxEdits 1` on title and text, title boosted x3), filter `spaceId in <my accessible space ids>` and `search.status in [ACTIVE, ARCHIVED]`, then `NodeAccessService.resolve` on each hit (drop below VIEW and TITLE_ONLY content snippets), highlights escaped server-side and only `<mark>` added. Teams and spaces are still matched by name (existing behaviour, simple regex over my teams/spaces).
- `GET /api/me/recent` -> recent nodes (from `recentNodes`) the user can still VIEW, newest first.
- [ ] IT (Atlas-local): `findsByTitleWithTypo`, `findsByContent`, `neverReturnsOtherTeamsPages`, `restrictedPageNotReturnedToUnlisted`, `snippetIsEscaped` (`<script>` in text), `archivedIsReturnedAndMarked`.
- [ ] Commit `feat: Atlas Search for pages`.

### Task 7.2: Ctrl+K palette and search page (frontend)
Wireframe `Palette`.
**Files:** `palette/CommandPalette.tsx` (cmdk), `pages/Search.tsx` (port of current search page onto the new API).
Behaviour: Ctrl+K / Cmd+K anywhere (also the sidebar button); empty box = recent pages; typing = instant title prefix match over loaded trees + debounced (200 ms) server search for "IN PAGE CONTENT"; ACTIONS: "New page in <current project>", "New project", "Open trash", "Go to settings"; ↑ ↓ / Enter / Esc; focus returns to the trigger on close.
- [ ] Vitest: `opensOnCtrlK`, `emptyShowsRecent`, `enterOpensPage`, `escRestoresFocus`, `staleResponsesIgnored`.
- [ ] Playwright `palette.spec.ts`.
- [ ] Commit `feat: command palette and search page`.

---

## Phase 8: Move private page to a team project (D2, Should)
Wireframe `MoveToTeam`; domain spec 4.4.
**Files:** `node/MoveToTeamService.java`, endpoint `POST /api/nodes/{id}/move-to-team {spaceId, parentId|null, beforeId?, afterId?}`; `move/MoveToTeamDialog.tsx`.
Rules: source in my personal space; destination a team space where I'm EDITOR/ADMIN and I can edit the parent; whole branch moves (`spaceId`, `ancestors`, `restriction = null`); attachments, tags and comments come along; all existing versions **and comments** get `privateTo = me` (physical schema 3.9 and section 5). New `PUBLISHED_TO_TEAM` version per moved node; activity for the branch in the personal space not copied. One-way: team -> personal move is rejected (400 "Copy it instead").
- [ ] IT: `moveToTeamMakesOldVersionsPrivate`, `teamSeesOnlyPublishedToTeamVersion`, `viewerCannotMoveIntoTeam`, `teamToPersonalRejected`.
- [ ] Vitest: dialog lists only editable parents ("(you can't edit here)" disabled).
- [ ] Commit `feat: move private pages to a team project`.

---

## Phase 9: Migration, remaining pages, cutover (US-10)

### Task 9.1: Migration runner and structural steps (Spring)
**Files:** `migration/MigrationStep.java` (`String id(); Map<String,Long> run(boolean dryRun)`), `migration/MigrationRunner.java` (runs steps in order, records `migrations` `{_id, startedAt, finishedAt, status, counts}`, skips DONE, aborts on exception, enabled only with `kf.migration.enabled=true`, `kf.migration.dry-run`), steps:
1. `S01_ConvertIdsToObjectId` (string references in spaces, team_members, comments, attachments, activity_logs, document_versions -> ObjectId); rename `teamInvites` -> `team_invites`.
2. `S02_SpacesOwnerTypeTeam` + personal space for every user.
3. `S03_DocumentsToNodes` (same `_id`; top-level PAGE ordered by `updatedAt` desc via `RankKeys.spread`; status DRAFT/PUBLISHED -> `publishState`; tags lower-cased; `node_contents` with `legacyMarkdown`, empty `state`, `format YJS_BLOCKNOTE_V1`).
4. `S04_VersionsToNodeVersions` (`kind MIGRATED`, authors = saver, not private; `legacyMarkdown` kept in `text` until conversion).
5. `S05_CommentsAndAttachments` (`documentId` -> `nodeId`, `anchor null`, `threadId = _id`).
6. `S06_ClearAiCaches`.
7. `S07_Verify`: counts in = counts out for documents/nodes, versions, comments, attachments; every user has one personal space; mismatch -> FAILED + loud log + non-zero exit.
- [ ] IT on a seeded legacy dataset (`src/test/resources/migration/legacy-*.json`): `migrationPreservesCounts`, `idsArePreserved`, `rerunIsNoOp`, `dryRunWritesNothing`, `mismatchAbortsLoudly`.
- [ ] Commit `feat: migration runner and structural steps`.

### Task 9.2: Content conversion script (Node) + internal API
**Files:** `migration/InternalContentController.java` (`GET /internal/migration/pending?limit=50` -> `[{nodeId|versionId, markdown}]`, `PUT /internal/migration/content/{nodeId}` and `/version/{versionId}` `{state, text}`); header `X-Internal-Secret` = env `KF_INTERNAL_SECRET` (constant-time compare; endpoints disabled when unset). `collab/migrate-content.ts` (loop: fetch pending -> markdown -> BlockNote blocks with `kfSchema` -> Y.Doc -> base64 + `extractText` -> PUT; idempotent: only rows with empty `state`; report converted/failed counts). The conversion replaces the state wholesale, so the internal `PUT .../content/{nodeId}` sets `contentEpoch` to 1 (D6 epoch rule): a browser that still holds a pre-conversion copy keeps it as a RECOVERED version instead of merging it.
- [ ] Spring tests: `internalApiRejectsWrongSecret`, `internalApiDisabledWithoutSecret`.
- [ ] Node test (Vitest): `convertsHeadingsListsCodeTablesLinks` against a fixture of real markdown from production docs; `rerunSkipsConverted`.
- [ ] Commit `feat: markdown to block content conversion`.

### Task 9.3: Port remaining pages, comments, AI, dashboard to nodes
Port Dashboard, Team, Profile pages to React (same behaviour as today, plain CSS). Comments: `/api/nodes/{id}/comments` (page-level list under the page, VIEWERs may comment). AI: Summary reads `node_contents.text` (`POST /api/nodes/{id}/ai/summary`), Ask uses Atlas Search hits instead of keyword scan. Dashboard "recently updated" from `nodes` `{spaceId, status, contentUpdatedAt}`.
- [ ] Existing tests updated (`AiServiceTest`, `DashboardServiceTest`, `AccessRulesTest`) + `commentsRespectNodeAccess`.
- [ ] Commit per page/feature.

### Task 9.4: Cutover
- Old URLs: `document.html?id=X` -> `/app/p/X`; `space.html?id=` -> `/app/s/:id`; `/` and other old pages -> `/app` equivalents (`HomeController` redirects).
- Delete `static/*.html`, `static/app.js`, `static/style.css`, `documentPkg`, `documentVersion`, old search code; remove `legacyMarkdown` only after the production verification report is reviewed (separate small step S08).
- [ ] Rehearsal: `mongodump` production -> restore into a local Atlas-local -> run migration + script -> verification report -> click through 10 real pages. Write the result in PROJECT_CONTEXT.
- [ ] Commit `chore: retire legacy pages and document model`.

---

## Phase 10: Release 1a ship

### Task 10.1: Hosting (ADR 0006), backups, monitoring
**Files:** `deploy/docker-compose.yml` (app + Caddy with automatic HTTPS; collab service joins in 1b), `deploy/Caddyfile`, `.github/workflows/deploy.yml` (on `main`: build image, push to GHCR, SSH deploy to the Azure VM), `.github/workflows/backup.yml` (nightly `mongodump`, encrypted with `age`, kept 7 days as artifact), Spring Actuator `/actuator/health` (public, no details).
- [ ] JVM tuned for the VM (`-XX:MaxRAMPercentage=60`), measured memory written in PROJECT_CONTEXT.
- [ ] Restore test from a nightly backup into Atlas-local. Uptime monitor on `/actuator/health`.

### Task 10.2: Release checks
- [ ] Seed 10,000 pages (`kf.seed` dev profile): sidebar tree, page open < 2 s, search measured and recorded.
- [ ] Security pass (OWASP Top 10): every `/api/nodes/**` endpoint has a test for non-member 403/404; rate limit login/register (10/min per IP, `shortcut: in-memory bucket`); XSS check on titles, tags, bookmark data, snippets.
- [ ] Accessibility pass: keyboard-only walk through tree, editor, palette, history, trash; axe check in Playwright (no serious violations).
- [ ] 360 px check of every screen (no horizontal scroll).
- [ ] Production migration (backup first), verification report, `legacyMarkdown` removal step, README update, tag `v1.0-1a`.

### Hand-off to the visual design pass (after 1a works)
Screen order: Sidebar (owner's ideas: per-page colour dot, parent-child guide lines with ticks) -> Page view -> Editing -> History -> Palette -> Trash -> Move to team -> ported pages. Source of truth: `DESIGN.md`. Each screen gets its own small design and the owner's approval before code.

---

## Requirement coverage

| Requirement | Task(s) |
|---|---|
| US-1 nested pages, drag, breadcrumbs | 2.2, 2.3, 2.4 |
| US-2 block editor, `/` menu, undo, autosave + states | 4.1-4.3 |
| US-3 status lozenges (6 colours) | 4.2 |
| US-4 tags + filter across spaces | 2.3, 5.2, 5.3, 7.1 |
| US-5 history, compare, restore | 6.1, 6.3 |
| US-6 trash (30 d), archive, permanent delete | 5.1, 5.3 |
| US-7 Ctrl+K palette | 7.1, 7.2 |
| US-8 page lock | 5.2, 5.3 |
| US-9 files and bookmarks | 6.2, 6.4 |
| US-10 migration | 9.1-9.4 |
| Draft/publish (domain 4.7) | 5.2, 5.3 |
| Soft lock (domain 5.4) | 3.2, 4.4 |
| No lost edits (domain 5.3) | 4.3 |
| Personal spaces + move to team (domain 1, 4.4) | 1.2, 8 |
| NFR CSRF, rate limit, access checks, XSS | 0.3, 2.1, 10.2 |
| NFR CI, real MongoDB tests, indexes via explain | 0.1, 0.2, every IT |
| NFR backups, health, uptime, $0 hosting | 10.1 |
| NFR performance, accessibility, 360 px | 10.2 |
| Deferred to 1b (PRD): page permissions UI, share links, inline comments, notifications, real-time | D1, D5 |
