# ADR 0003: Block editor is BlockNote

Date: 2026-11-09 · Status: Accepted

## Context
Release 1a needs a Notion-style block editor (`/` menu, drag handles, headings/lists/to-dos/quote/divider/code/image/
file/table, custom blocks: status lozenge, bookmark) in React (ADR 0001) with Yjs collaboration in 1b (ADR 0002).

## Options
- A. BlockNote: ready Notion-style React editor built on ProseMirror + TipTap; custom blocks; core MPL-2.0.
- B. TipTap: lower-level; full control but the whole block UI must be built.
- C. Lexical (Meta): fast, Yjs support, no ready Notion-style UI.

## Decision
A. BlockNote.

## Consequences
- + Release 1a editor quickly; effort goes to the product-specific parts (tree, permissions, versioning, real-time, migration).
- + TipTap/ProseMirror underneath is available when a limit is hit.
- - License: use only MPL-2.0 core packages; do NOT use `@blocknote/xl-*` packages (GPL-3.0 / commercial).
  If BlockNote's own source files are modified, those changes must be published (MPL-2.0).
- - Migration needs a markdown -> BlockNote blocks converter (BlockNote provides markdown parsing; verify fidelity on real data).
