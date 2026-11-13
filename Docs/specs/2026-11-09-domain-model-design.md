# Domain Model Design: Pages, Boards, Permissions, Versions (Releases 1-2)

Date: 2026-11-09 · Status: APPROVED 2026-11-09 · Process: requirements -> design review -> written spec
Inputs: `Docs/PRD.md` v1.1, `MoSCoW (fixed).md`
Scope: the CONCEPTUAL model and rules. The PHYSICAL schema (exact content format, editor/CRDT storage, search engine)
is finalized after the tool decisions (ADRs). Nothing here depends on a specific editor library.

---

## 1. Decisions log
| # | Decision | Chosen |
|---|---|---|
| 1 | Where pages live | Team spaces **and** one personal space per user; pages can move personal -> team |
| 2 | What a project is | **A Space is a project.** A team has many spaces. A user can be in many teams. No extra Project layer. |
| 3 | Base access | Team roles (ADMIN / EDITOR / VIEWER) are the base layer; page permissions only adjust within them |
| 4 | Pages vs boards | One shared tree; pages and boards share comments, versions, trash, permissions |
| 5 | Outsiders | "Anyone with the link" = view without an account. Commenting or more = account + team invite (VIEWER). Space-scoped guest stays a Could. |
| 6 | Permission inheritance | **Inherit, bounded by the parent**: a page's viewers/editors can be any subset of those who can access its parent, widened or narrowed any time, never beyond the parent |
| 7 | Restriction rights | View **and** edit lists per page |
| 8 | Admin vs restricted page | Admins see that a restricted page exists (title only) and can remove the restriction; removal is logged |
| 9 | Versions | One version per editing session + named versions; checkpoints at natural pauses; small same-author changes merged; grouped history view |
| 10 | No lost edits | Fast autosave, flush on tab close, local IndexedDB copy until confirmed |
| 11 | Concurrent editing in Release 1a | **Soft lock**: "Rafi is editing. Open read-only or edit anyway?" Replaced by real-time co-editing in 1b |
| 12 | Delete / archive | The whole branch goes together, with a confirm dialog showing the count; restorable |
| 13 | Personal -> team move | Earlier versions become private to the mover; team history starts at a "Published to team" version |
| 14 | Overall shape | **Approach 1**: one `nodes` collection (pages + boards) with parent + ancestors + rank; content in a separate collection |
| 15 | Draft / published | Page-level `publishState` DRAFT / PUBLISHED (kept as a real concept) |
| 16 | Scale target | 500 users, **100 concurrent editors**, 10,000 pages |

Rejected alternatives (for the record): separate page/board collections (doubles every feature); whole tree embedded in the
space document (write contention, 16 MB limit, every reorder rewrites everything); permissions without inheritance (new
sub-pages start unprotected); Notion-style "open up beyond parent" (hard to reason about and secure).

## 2. Access patterns & data structures
| # | Operation | Frequency | Structure | Cost |
|---|---|---|---|---|
| 1 | Load sidebar tree of a space | every page load | All light node records of the space (no content) in one query; tree built client-side with a hash map (id -> node) | 1 indexed query + O(n) |
| 2 | Open a page | constant | One content record per node, looked up by `nodeId` | 1 indexed read |
| 3 | List/reorder siblings | often | `parentId` + fractional `rank` keys (insert between two keys) | reorder = 1 write |
| 4 | Breadcrumbs, subtree ops, permission walk | constant | `ancestors` array (materialized path), multikey index | 0 extra lookups / 1 query |
| 5 | Ctrl+K jump | very often | Per-user capped recent list (~20) + client-side title prefix match over the loaded tree | no server round trip while typing |
| 6 | Full-text search | often | Inverted index (Mongo text index or Atlas Search; tools phase) over `textForSearch` + title + tags | indexed |
| 7 | Real-time co-editing (1b) | while editing | CRDT document (tools phase) | small incremental updates |
| 8 | Compare versions | occasional | Full snapshots; diff computed on demand (Myers) | on demand |
| 9 | Notification badge | every page load | Index `(userId, read, createdAt)` | 1 indexed count |
| 10 | Permission check | every request | Walk node `ancestors` restrictions; short per-user+node cache | O(depth) |

Rule: every main query must use an index (verified with `explain()` in integration tests).

## 3. Entities
Existing and unchanged: User, Team, TeamMember (role), TeamInvite, TeamJoinRequest, Attachment (GridFS), ActivityLog, AI usage.

### Space (changed) = a project
| Field | Notes |
|---|---|
| `ownerType` | `TEAM` or `USER` |
| `teamId` / `ownerUserId` | exactly one is set |
| `name`, `description`, `icon` | |
| `status` | `ACTIVE` / `ARCHIVED` |
| `createdBy`, timestamps | |
Every user gets exactly one personal space at sign-up (and existing users at migration).

### Node (new): any tree item
| Field | Notes |
|---|---|
| `spaceId` | |
| `type` | `PAGE` (R1), `BOARD` (R2) |
| `parentId` | null = top level of the space |
| `ancestors` | `[topLevelId, ..., parentId]` |
| `rank` | fractional ordering key among siblings |
| `title`, `icon`, `tags[]` | |
| `status` | `ACTIVE` / `ARCHIVED` / `TRASHED` |
| `publishState` | `DRAFT` / `PUBLISHED` (pages; new pages start DRAFT; boards decided in the Release 2 spec) |
| `trashedAt`, `trashedBy`, `trashRootId` | `trashRootId` = the node the delete started from |
| `locked`, `lockedBy` | |
| `restriction` | `null` (inherit) or `{ viewers: [userId], editors: [userId] }` |
| `editSession` | `{ startedAt, lastEditAt, authors[] }` for version sessions; plus 1a soft-lock holder + heartbeat |
| `createdBy`, `updatedBy`, timestamps | |

### NodeContent (new): one per node
`nodeId`, `format` (set in tools phase), `data`, `revision` (incremented on each save), `textForSearch`, `updatedAt`.

### NodeVersion (replaces DocumentVersion)
`nodeId`, `title`, `content snapshot`, `contentHash`, `authors[]`, `name` (named versions), `kind` (`AUTO` / `NAMED` / `PUBLISHED_TO_TEAM` / `PUBLISHED` / `RESTORED`), `privateTo` (userId or null), `createdAt`, `updatedAt` (for merged versions).

### ShareLink (new)
`nodeId`, `token` (random, unguessable), `access` (`VIEW`), `createdBy`, `createdAt`, `expiresAt` (optional), `revoked`.

### Comment (upgraded)
`nodeId`, `anchor` (null = page-level; text range for pages; object id for boards), `threadId`, `parentCommentId`, `body`, `resolved`, `resolvedBy`, `createdBy`, timestamps.

### Notification (new)
`userId`, `type` (`MENTION`, `REPLY`, `INVITE`, ...), `nodeId`, `actorId`, `read`, `createdAt`.

### Removed
`Document` -> Node + NodeContent (same ids). `DocumentVersion` -> NodeVersion.

## 4. Rules

### 4.1 Permission check (every API request and every real-time connection)
1. Load the node (it carries `ancestors`, `spaceId`).
2. Space: personal -> owner only (others denied unless via share link, 4.6). Team -> must be a member; starting right from role: ADMIN/EDITOR = edit, VIEWER = view.
3. Walk restrictions top-down over `ancestors` then the node: not in viewers or editors -> deny; only in viewers -> cap at view. Rights only go down.
4. Team ADMIN not listed on a restriction: sees the title only, may remove the restriction (logged), cannot read content otherwise.
5. Status: ARCHIVED -> view at most; TRASHED -> only via the trash view.
6. Locked -> view for everyone; page editors may unlock.
7. Result none / view / edit; cached briefly per (user, node); cache cleared on any restriction, role, membership or move change.

### 4.2 Restrictions
- `viewers`/`editors` must be within the users who can access the parent with at least that right; team VIEWERs can never be page editors.
- Saving a restriction outside those bounds is rejected with a clear message.

### 4.3 Move
- Moves the node and its whole subtree; rewrites `ancestors` of all of them; `rank` places it among new siblings.
- Requires edit on the node and on the destination. No moves between different teams. No team -> personal moves (copy instead).
- Restrictions are re-checked against the new parent; people no longer allowed are removed and the mover is warned before confirming.

### 4.4 Personal -> team ("publish my draft to the team")
- Destination must be a team space where the mover is EDITOR or ADMIN.
- Comes along: content, attachments, tags, comments (only the owner could comment in a personal space).
- Reset: restrictions (inherit from destination), share links (revoked).
- History: every existing version of the moved nodes gets `privateTo = mover`; a new `PUBLISHED_TO_TEAM` version is created and visible to the team. Only the mover sees/compares/restores private versions; restoring one creates a new normal version. Personal-space activity on those nodes stays hidden from the team.

### 4.5 Trash, archive, restore
- Delete: node + subtree -> TRASHED, same `trashRootId`; confirm dialog shows the count.
- Restore: brings back that branch; if the old parent is gone or trashed, restore to the space top level with a notice (the branch's `ancestors` are rewritten, same as a move).
- Restoring a node inside a trashed branch alone is not allowed; restore happens per `trashRootId`.
- Auto-purge 30 days after `trashedAt`: deletes content, versions, comments, attachments (GridFS files), share links, notifications for those nodes.
- Permanent delete before 30 days: team ADMIN only (personal space: owner).
- Archive: same branch logic with ARCHIVED; shown in an "Archived" list; searchable; read-only; un-archive restores.

### 4.6 Share links
- Valid only if: exists, not revoked, not expired, node ACTIVE and **PUBLISHED**.
- Grants view of that node and its sub-pages (restricted sub-pages excluded), never edit, nothing outside the branch.
- Revoked automatically on personal -> team move; stops working while trashed/archived or unpublished.

### 4.7 Draft / published
- New pages start DRAFT. Anyone with edit rights can Publish / Unpublish; each change creates a `PUBLISHED` version entry (kind) when publishing.
- Drafts are visible to everyone with access, with a "Draft" badge; tree and search can filter by state.
- Personal-space pages also carry the state (it matters once moved to a team).

### 4.8 Lock
- Locked pages are read-only for everyone; only page editors can lock/unlock. Children are not locked automatically.

## 5. Versioning & no lost edits
### 5.1 Sessions -> versions
- Each change updates `editSession.lastEditAt` and adds the editor to `authors`.
- A job runs every minute and creates an AUTO version when: no edits for 5 minutes, or the session has run 15 minutes AND there has been a natural pause (no edits for ~30 s).
- When the last editor leaves (tab close/navigate), the version is created immediately.
- Skip if `contentHash` equals the latest version's hash.
- Merge rule: if the new AUTO version is by the same single author as the latest AUTO version, within 1 hour of it, and the change is small (under ~200 characters changed), update that version instead of adding one. NAMED, PUBLISHED and PUBLISHED_TO_TEAM versions are never merged or modified.
- Named versions are created instantly on request (or name the latest version if nothing changed).
- Retention: keep all; track storage per space; add thinning rules only if numbers require it.

### 5.2 History view
- Grouped by day, then session; checkpoints collapsed under their session; named versions highlighted; "Named versions only" filter; selecting a version shows changes highlighted per author colour.

### 5.3 No lost edits (client)
1. Changes apply on screen immediately; sent ~1 s after typing stops.
2. Unconfirmed changes are stored in IndexedDB per node until the server confirms.
3. On tab close/hide: flush with a request that survives page unload.
4. On open: replay unconfirmed local changes first.
5. Status indicator: Saved / Saving... / Offline: changes kept on this device / Couldn't save, retrying.
6. "Leave page?" prompt only when changes are truly unsaved.

### 5.4 Concurrency in Release 1a (soft lock)
- Saves carry the `revision` they are based on; a stale save is rejected (409) and its content kept locally.
- Opening a page someone is editing shows "<name> is editing. Open read-only / Edit anyway". The lock is held by heartbeat and expires ~60 s after the holder stops.
- Removed in Release 1b when real-time co-editing replaces it.

## 6. Migration (one-time, re-runnable)
> **Not used (decision D7, 2026-11-14):** there is no data migration; the 1a deploy starts on an empty database. Kept for history only.

1. Full backup (`mongodump`); run first on a copy of production data.
2. Migration steps recorded in a `migrations` collection; completed steps are skipped on re-run.
3. Mapping (ids preserved: node id = old document id):
   - spaces -> `ownerType: TEAM`, `ACTIVE`; every user gets a personal space;
   - documents -> top-level PAGE nodes ordered by last updated; status DRAFT/PUBLISHED -> `publishState`; tags kept; NodeContent from markdown (converter chosen with the editor) with `legacyMarkdown` kept;
   - document versions -> NodeVersions (`AUTO`, authors = saver, not private);
   - comments -> page-level comments (`anchor: null`); attachments unchanged;
   - AI summary/answer caches cleared.
4. Verification: counts in = counts out per collection; any mismatch stops the migration loudly.
5. Old `document.html?id=` URLs redirect to the new page URL.

## 7. Indexes
| Collection | Index |
|---|---|
| nodes | `{spaceId, status, parentId, rank}` · `{ancestors}` · `{tags}` · `{spaceId, updatedAt}` · `{trashedAt}` (purge job) |
| node_contents | `{nodeId}` unique |
| node_versions | `{nodeId, createdAt: -1}` |
| share_links | `{token}` unique · `{nodeId}` |
| comments | `{nodeId, threadId}` |
| notifications | `{userId, read, createdAt: -1}` |
| spaces | `{teamId}` · `{ownerUserId}` unique (personal) |
| users | recent pages as a capped array on the user record |
Full-text index chosen in the tools phase.

## 8. Testing
- Unit: permission resolver (table of role x restriction x space type x share link x status x lock), restriction bounds, rank generation, move rules, personal -> team privacy, version session/merge rules, share-link validity.
- Integration (real MongoDB via containers): migration on a realistic dataset with count verification; branch trash/restore/purge; queries use indexes (`explain()`).
- Performance: seed 10,000 pages; measure sidebar load, page open, search against PRD budgets. 100-editor load test in Release 1b.

## 9. Out of scope here (decided later)
Editor and content format, CRDT/real-time library, search engine, frontend framework, hosting, migration tooling: tools phase (ADRs).
Physical schema finalization follows those ADRs. Board content model: Release 2 spec.
