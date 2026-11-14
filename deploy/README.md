# Deploying to the VM

Image is built by `.github/workflows/deploy.yml` (push to `main`) and pushed to GHCR.
The VM pulls it every 5 minutes (`kf-update.timer` runs `update.sh`); a failed health check rolls back to the previous image.

## One-time VM setup (Ubuntu 24.04, 1 GB RAM)
```sh
# ssh: key-only (PasswordAuthentication no) + fail2ban
sudo apt-get install -y fail2ban
printf '[sshd]
enabled = true
maxretry = 5
bantime = 1h
' | sudo tee /etc/fail2ban/jail.d/sshd.local
sudo systemctl enable --now fail2ban
# swap
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
# docker
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker azureuser   # then log out and in
# files
mkdir -p ~/kf     # copy deploy/docker-compose.yml, Caddyfile, update.sh here, then: chmod +x ~/kf/update.sh
sudo cp kf-update.service kf-update.timer /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now kf-update.timer
```

## ~/kf/.env (chmod 600, never in git)
- `SPRING_DATA_MONGODB_URI` : Atlas URI ending in the NEW database name, e.g. `.../knowledgeforge_v1?retryWrites=true&w=majority`
- `AI_BASE_URL`, `AI_MODEL`, `AI_API_KEY` : only if the AI assistant is used

## Backup secrets (GitHub > repo > Settings > Secrets and variables > Actions)
- `BACKUP_MONGODB_URI` : URI of the new database (a read-only user is enough)
- `AGE_PUBLIC_KEY` : `age-keygen -o kf-backup.key` locally; paste the `age1...` public key. Keep the key file offline.
Restore: `age -d -i kf-backup.key kf-DATE.archive.age > kf.archive`, then `mongorestore --uri=... --gzip --archive=kf.archive`.

## Uptime monitor
Create a free HTTPS monitor (for example UptimeRobot, 5-minute interval) on
`https://knowledgeforge-lrs.eastasia.cloudapp.azure.com/actuator/health`, keyword `UP`, with e-mail alerts.
