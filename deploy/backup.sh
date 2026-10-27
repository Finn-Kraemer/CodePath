#!/usr/bin/env bash
# Sichert die CodePath-Datenbank in eine komprimierte Datei.
#   ./deploy/backup.sh                → backups/codepath-JJJJ-MM-TT_HHMM.sql.gz
# Wiederherstellen:
#   gunzip -c backups/<datei>.sql.gz | docker compose exec -T postgres psql -U codepath codepath
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/codepath-$(date +%Y-%m-%d_%H%M).sql.gz"
docker compose exec -T postgres pg_dump -U codepath --clean --if-exists codepath | gzip > "$file"
echo "Backup geschrieben: $file"
