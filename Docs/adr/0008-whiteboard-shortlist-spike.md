# ADR 0008: Whiteboard engine: shortlist Excalidraw and the GazBoard engine; decide by a spike at the start of Release 2

Date: 2026-11-09 · Status: Accepted (final engine choice deferred to the spike; it gets its own ADR)

## Context
Release 2 Musts: infinite canvas (pan/zoom/minimap; mouse/trackpad/touch), shapes, sticky notes, connectors, frames,
layering/alignment, locking, media, live cursors/presence, in-board search, board history/trash, public links, boards in
the page tree and embedded in pages. Must use React (ADR 0001), Yjs + Hocuspocus (ADR 0002/0004), $0 cost (ADR 0006).
Student boards hold hundreds of objects, not hundreds of thousands, so Canvas 2D / DOM rendering is enough.

Reviewed 2026-11-09: GazBoard (by MD. Fakhruddin Gazzali, faculty; MIT license): offline Microsoft-Whiteboard-style app,
plain JS ES modules (~12.6k lines core), Canvas 2D rendering (no WebGL/PixiJS), camera/surface/hit-test/ink modules,
op-based store (add/del/set/order + undo) with an `applyRemote` seam for future sync; notes, shapes, connectors, lock,
groups, layering, lasso, laser, pressure ink, maths/graphs, PDF/Word/PowerPoint import. No multiplayer, no minimap.

## Options
- Excalidraw: MIT, ready React component, Canvas 2D; frames/arrows/text/images/lock; no first-class sticky notes;
  Yjs sync via community bindings (risk).
- tldraw: best Miro-like fit and official multiplayer, but commercially licensed (free hobby license with unclear
  production terms). **Rejected**: conflicts with the $0 rule and a public product.
- GazBoard engine adapted: MIT, pen/maths/document-import strengths for students; needs a React wrapper, TypeScript
  typings, and a Yjs sync layer built on its op store. High effort, high depth.
- Build our own (Canvas 2D or WebGL/PixiJS): **Rejected**: months of engine work already done by the options above.

## Decision
Shortlist **Excalidraw** and the **GazBoard engine**. At the start of Release 2, run a time-boxed spike (2-3 days)
testing both against: (1) live sync through Yjs/Hocuspocus with presence, (2) sticky notes + connectors + frames,
(3) embedding a board inside a page and storing it as a Node of type BOARD. Record results and the final pick in a new ADR.

## Consequences
- + Decision made with evidence at the last responsible moment; no lock-in during Releases 1a/1b.
- + Domain model already supports boards (Node type BOARD, shared versions/trash/permissions).
- If GazBoard is chosen: keep its MIT copyright notice, credit the author, and ask the author as a courtesy (possible mentor).
- Spike code is throwaway.
