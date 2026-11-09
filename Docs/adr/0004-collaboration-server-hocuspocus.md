# ADR 0004: Real-time sync runs in a separate Node.js service (Hocuspocus); Spring Boot owns data and permissions

Date: 2026-11-09 · Status: Accepted

## Context
Yjs (ADR 0002) needs a WebSocket sync server for live co-editing (Release 1b) and live boards (Release 2), sized for
100 concurrent editors. All business rules (auth, permissions, versions, trash, publish state) already live in Spring Boot.

## Options
- A. Node.js service using Hocuspocus (open-source Yjs server), with Spring Boot as the source of truth.
- B. Implement Yjs sync inside Spring Boot (Java): one service, but little Java support for the Yjs protocol; high risk.
- C. Hosted real-time service (e.g. Liveblocks, Y-Sweet): little to operate, paid at scale, vendor dependency.

## Decision
A. A small Node.js "collab" service running Hocuspocus. Spring Boot remains the single owner of the database rules.

## How the two services work together
1. The browser asks Spring for a short-lived signed **collab token** for a page (Spring checks the user's rights first).
2. The browser opens a WebSocket to the collab service with that token.
3. Hocuspocus `onAuthenticate` verifies the token (signature + expiry + page id) and the right level (view = read-only connection).
4. `onLoadDocument`: collab service fetches the page's Yjs state from Spring's internal API.
5. `onStoreDocument` (debounced): collab service sends the updated Yjs state to Spring's internal API; Spring saves it,
   updates `textForSearch` and the editing session used for versions.
6. Internal API is not public: shared secret / network-restricted. Permission changes (restriction edits, removal from
   team, trash) make Spring tell the collab service to close affected connections.

## Consequences
- + Standard, proven architecture; clear separation of concerns; good portfolio/interview story.
- + All validation, versioning and permission rules stay in one place (Spring).
- - Two deployables (Spring + Node) to build, deploy and monitor (hosting ADR).
- - Token and internal-API security must be designed carefully and tested.
