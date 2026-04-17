#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <backup-dir>"
  echo "Example: $0 backups/supabase/2026-04-17_18-30-00"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$1"

if [[ "$BACKUP_DIR" != /* ]]; then
  BACKUP_DIR="$ROOT_DIR/$BACKUP_DIR"
fi

SCHEMA_FILE="$BACKUP_DIR/schema.sql"
DATA_FILE="$BACKUP_DIR/data.sql"

if [[ ! -f "$SCHEMA_FILE" || ! -f "$DATA_FILE" ]]; then
  echo "Backup files not found in: $BACKUP_DIR"
  echo "Required: schema.sql and data.sql"
  exit 1
fi

cd "$ROOT_DIR"

echo "Starting local Supabase if needed..."
npx supabase start >/dev/null

echo "Resolving local DB URL..."
DB_URL="$(npx supabase status -o env | awk -F= '/^DB_URL=/{gsub(/\"/, "", $2); print $2}')"

if [[ -z "$DB_URL" ]]; then
  echo "Could not determine local DB URL from 'supabase status -o env'."
  exit 1
fi

echo "Restoring backup into local Supabase database..."

docker run --rm --network host -v "$BACKUP_DIR:/backup:ro" postgres:15-alpine \
  psql "$DB_URL" -v ON_ERROR_STOP=1 -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

docker run --rm --network host -v "$BACKUP_DIR:/backup:ro" postgres:15-alpine \
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f /backup/schema.sql

docker run --rm --network host -v "$BACKUP_DIR:/backup:ro" postgres:15-alpine \
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f /backup/data.sql

docker run --rm --network host -v "$ROOT_DIR:/repo:ro" postgres:15-alpine \
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f /repo/scripts/sql/recreate_auth_profile_trigger.sql

docker run --rm --network host -v "$ROOT_DIR:/repo:ro" postgres:15-alpine \
  psql "$DB_URL" -v ON_ERROR_STOP=1 -f /repo/supabase/migrations/20260417235500_vibilia_profile_refs_and_reassign_upgrade.sql

echo "Local restore complete."
echo "Open Supabase Studio: http://127.0.0.1:54323"
