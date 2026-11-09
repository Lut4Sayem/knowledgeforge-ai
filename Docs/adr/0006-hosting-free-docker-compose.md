# ADR 0006: Hosting at $0/month: Docker Compose everywhere, Azure for Students first, Oracle Always Free later

Date: 2026-11-09 · Status: Accepted
(Replaces the first draft that proposed paid Render instances; rejected by the owner: budget is $0/month.)

## Context
Production needs the Spring Boot app, the Node collab service (WebSockets, ADR 0004), MongoDB Atlas, CI and daily
backups. Hard constraint: **$0 per month**. Render's free tier is too slow and sleeps; two always-awake services exceed
its free hours. Checked 2026-11-09:
- Oracle Cloud Always Free: ARM VM with 2 OCPU / 12 GB RAM free forever, but needs a real (non-prepaid, non-virtual)
  card for verification, can hit "out of capacity", and reclaims VMs that look idle for 7 days. The owner has no card of
  his own (a friend's card is possible but risky: verification mismatch, one-account-per-person, suspension risk).
- Azure for Students (GitHub Student Pack): $100 credit, no card for 18+, plus free-tier services; normally renewable
  yearly while a student.
- Heroku (Student Pack): $13/month credit for 24 months; not enough for two always-on services.
- Vercel: free static/front-end hosting; cannot run Spring Boot or long-lived WebSocket servers.
- DigitalOcean Student Pack offer ended 2026-07-31.

## Decision
1. **Portability first:** the whole backend runs with **Docker Compose** (Spring Boot, Node collab service, reverse proxy
   with automatic HTTPS). The same compose file runs on a laptop, Azure or Oracle; changing hosts is a redeploy, not a rebuild.
2. **Start on Azure for Students** (own identity, no card): one small Linux VM running the compose stack, sized and
   memory-tuned to stay inside free services + the $100 credit. Exact VM size verified at setup against the account's offers.
3. **Frontend:** served by Spring (one origin, simplest) unless Vercel is clearly better at setup time.
4. **Database:** MongoDB Atlas free tier (Student Pack $50 Atlas credit available if needed).
5. **Move to Oracle Always Free** when the owner has his own international card (more RAM, free forever).
6. **CI:** GitHub Actions (free for public repos) runs tests/builds on every PR and deploys `main`.
7. **Backups:** nightly GitHub Actions `mongodump`, encrypted, short retention; restore tested once per release.
8. **Monitoring:** Spring Actuator health + a free external uptime monitor.

## What this means for the owner
$0/month. Some setup work up front. Credits must be watched and renewed; the host can change later
without touching the app.

## Consequences
- + No monthly cost; vendor-independent thanks to Docker Compose; good DevOps learning.
- - A small VM: Java and Node memory must be tuned and measured.
- - Free credits expire; renewal and a possible move to Oracle are planned work.
- - The PRD's "99% uptime, no sleeping" is a goal on free infrastructure, not a guarantee.
