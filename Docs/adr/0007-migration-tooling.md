# ADR 0007: Data migration = small Spring migration runner + one-off Node conversion script

Date: 2026-11-09 · Status: Accepted

## Context
The domain-model spec requires a one-time, re-runnable, verified migration: spaces -> team spaces, personal spaces for
every user, documents -> nodes (same ids), versions -> node versions, comments -> page-level comments, and markdown
content -> BlockNote blocks stored as Yjs document state. BlockNote's markdown parser and Yjs are JavaScript libraries,
so the content conversion cannot run in Java.

## Options
- A. Mongock (Java migration library): established, but Spring Boot 4 compatibility is not confirmed, and it still
  can't do the JavaScript content conversion.
- B. **Custom minimal runner in Spring + one-off Node script for content**.
- C. Everything as one Node script writing directly to MongoDB: bypasses Spring's rules and validation.

## Decision
B.
- Spring: ordered migration steps (Java classes) recorded in a `migrations` collection, skipped when already done;
  handles structure (spaces, personal spaces, nodes with preserved ids, versions metadata, comments, publishState) and
  count verification.
- Node (lives in the collab service repo): reads `legacyMarkdown` of nodes/versions via Spring's internal API,
  converts markdown -> BlockNote blocks -> Yjs state, sends it back; idempotent per node.
- Always: backup first, dry run on a copy of production data, verification report, abort loudly on mismatch.

## What this means for you
One safe, repeatable migration you can rehearse before touching real data.

## Consequences
- + No dependency whose Boot 4 support is unknown; all rules stay in Spring.
- - We maintain a small runner ourselves (a few dozen lines) and a one-off script.
- Revisit Mongock if migrations become frequent.
