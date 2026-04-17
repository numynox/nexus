#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <backup-dir> --confirm-remote-restore"
  echo "Set SUPABASE_DB_URL with the remote Postgres connection string before running."
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$1"
CONFIRM_FLAG="$2"

if [[ "$BACKUP_DIR" != /* ]]; then
  BACKUP_DIR="$ROOT_DIR/$BACKUP_DIR"
fi

if [[ "$CONFIRM_FLAG" != "--confirm-remote-restore" ]]; then
  echo "Missing required confirmation flag: --confirm-remote-restore"
  exit 1
fi

if [[ -z "${SUPABASE_DB_URL:-}" ]]; then
  echo "SUPABASE_DB_URL is not set."
  exit 1
fi

SCHEMA_FILE="$BACKUP_DIR/schema.sql"
DATA_FILE="$BACKUP_DIR/data.sql"

if [[ ! -f "$SCHEMA_FILE" || ! -f "$DATA_FILE" ]]; then
  echo "Backup files not found in: $BACKUP_DIR"
  echo "Required: schema.sql and data.sql"
  exit 1
fi

cat <<'EOF'
WARNING: You are about to overwrite the remote database public schema.
This is destructive. Make sure you are restoring to the correct project.
EOF

read -r -p "Type RESTORE to continue: " ACK
if [[ "$ACK" != "RESTORE" ]]; then
  echo "Aborted."
  exit 1
fi

echo "Restoring backup into remote database..."

docker run --rm -v "$BACKUP_DIR:/backup:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

docker run --rm -v "$BACKUP_DIR:/backup:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /backup/schema.sql

docker run --rm -v "$BACKUP_DIR:/backup:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /backup/data.sql

docker run --rm -v "$ROOT_DIR:/repo:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /repo/scripts/sql/recreate_auth_profile_trigger.sql

docker run --rm -v "$ROOT_DIR:/repo:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /repo/supabase/migrations/20260417235500_vibilia_profile_refs_and_reassign_upgrade.sql

docker run --rm -v "$ROOT_DIR:/repo:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /repo/supabase/migrations/20260418001000_vibilia_sharing_full_access_rls.sql

docker run --rm -v "$ROOT_DIR:/repo:ro" postgres:15-alpine \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /repo/supabase/migrations/20260418004000_vibilia_share_by_email_and_members.sql

echo "Remote restore complete."
