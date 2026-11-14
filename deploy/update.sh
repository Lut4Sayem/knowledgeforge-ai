#!/bin/sh
# Pull the latest image; if it changed, restart and health-check. On failure, roll back to the previous image.
set -u
cd /home/azureuser/kf || exit 1
IMG=ghcr.io/lut4sayem/knowledgeforge-ai:latest
log() { echo "$(date -u +%FT%TZ) $*"; }

old=$(docker image inspect -f '{{.Id}}' "$IMG" 2>/dev/null || echo none)
docker compose pull -q app || { log "pull failed"; exit 1; }
new=$(docker image inspect -f '{{.Id}}' "$IMG")
[ "$old" = "$new" ] && [ -n "$(docker compose ps -q app)" ] && exit 0

log "new image $new (was $old), deploying"
[ "$old" != none ] && docker tag "$old" kf-previous
docker compose up -d

for i in $(seq 1 24); do
  sleep 5
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then log "healthy, deploy ok"; exit 0; fi
done

log "health check FAILED"
if [ "$old" != none ]; then
  docker tag kf-previous "$IMG"
  docker compose up -d
  log "rolled back to $old"
fi
exit 1
