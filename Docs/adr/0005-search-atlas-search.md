# ADR 0005: Full-text search uses MongoDB Atlas Search

Date: 2026-11-09 · Status: Accepted

## Context
Ctrl+K and the search page need fast full-text search over page titles, content (`textForSearch`) and tags across
up to 10,000 pages, ranked by relevance, ideally typo-tolerant with highlighted matches. Later, the AI assistant should
move from keyword matching to meaning-based (semantic) search. Data already lives in MongoDB Atlas.
Today's search is a keyword scan in Java (`SearchService`).

## Options
- A. MongoDB text index: built into any MongoDB; basic word matching, no typo tolerance, weak ranking/highlighting.
- B. **Atlas Search** (Lucene engine built into Atlas): relevance ranking, typo tolerance (fuzzy), highlighting,
  autocomplete; same database, no extra service; Atlas Vector Search (semantic search) lives in the same place later.
- C. Separate search engine (Meilisearch / Elasticsearch): excellent search, but another service to host, sync and secure.

## Decision
B. Atlas Search, queried from Spring with `$search` aggregation; permission filtering applied in the same query
(only spaces/nodes the user can access) plus the standard permission check on results.

## What this means for you
Search gets "Google-like" (typos forgiven, best matches first, matched words highlighted) without running anything new.

## Consequences
- + No new service; one place for data, search, and future semantic search for the AI.
- - Ties the app to MongoDB Atlas (not plain self-hosted MongoDB). Acceptable: already on Atlas.
- - Free (M0) clusters allow at most 3 search + vector indexes in total. Planned use: 1 search index (pages) + 1 vector index later (AI). GitHub Student Pack includes $50 Atlas credit if a bigger tier is ever needed.
- - Local development and integration tests need an Atlas-compatible local deployment
  (Atlas CLI `atlas deployments setup --type local`) or a test Atlas cluster.
