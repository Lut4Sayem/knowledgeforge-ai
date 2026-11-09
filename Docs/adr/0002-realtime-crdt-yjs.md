# ADR 0002: Real-time editing engine is Yjs (CRDT)

Date: 2026-11-09 · Status: Accepted

## Context
Release 1b needs live co-editing for up to 100 concurrent editors, Release 2 needs live whiteboards, and the domain model
requires one real-time engine shared by pages and boards, plus "no lost edits" (offline/local copy, replay on reconnect).

## Options
- A. Yjs (CRDT): largest ecosystem of editor/whiteboard integrations, offline-capable, compact binary document updates.
- B. Automerge (CRDT): clean JSON-like model, far fewer editor/whiteboard integrations.
- C. Operational Transform (e.g. ShareDB): central server orders all edits, no offline editing, smaller ecosystem today.

## Decision
A. Yjs.

## Consequences
- + One engine for pages and boards; editors merge concurrent edits automatically; IndexedDB persistence (y-indexeddb)
  covers the "no lost edits" requirement; the 1a soft lock is removed in 1b.
- + Page content is stored as Yjs document state (binary), with `textForSearch` and version snapshots derived from it.
- - Yjs sync servers are typically Node.js (decided in the collaboration-server ADR); the Java backend must still own
  authentication and permission checks for every real-time connection.
- - Content is binary, so search text and history snapshots must be extracted explicitly.
