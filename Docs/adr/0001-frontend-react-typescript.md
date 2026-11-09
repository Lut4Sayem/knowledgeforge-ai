# ADR 0001: Frontend moves to React + TypeScript

Date: 2026-11-09 · Status: Accepted

## Context
The roadmap (PRD v1.1) needs a Notion-style block editor with `/` menu and drag handles, a drag-and-drop page tree,
live presence and real-time co-editing (1b), and an infinite whiteboard (Release 2). The current frontend is 9 hand-built
HTML pages + `app.js` with no components, state management or build step. The mature block-editor and whiteboard
libraries are React-first; whiteboard libraries in particular are React-based.

## Options
- A. React + TypeScript (Vite build)
- B. Vue or Svelte
- C. Stay with plain HTML/JS

## Decision
A. React + TypeScript, built with Vite. Spring Boot remains the backend (REST + real-time endpoints).

## Consequences
- + Every planned feature has mature library options; strong typing catches bugs early; strong CV/job-market fit.
- + DESIGN.md tokens and style.css carry over (CSS variables work in React).
- - Learning curve (React, TypeScript, Vite).
- - The 9 existing pages are rebuilt as React components; plain-JS pages are retired as their React versions ship.
- Build: the React app is built to static files and served by Spring Boot (one deployable) unless the hosting ADR decides otherwise.
