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
PSQL_IMAGE="${PSQL_IMAGE:-postgres:17-alpine}"

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

# Unlike the local restore, the schema here comes from the backup snapshot: the
# point of a remote restore is to put the database back exactly as it was.
docker run --rm -v "$BACKUP_DIR:/backup:ro" "$PSQL_IMAGE" \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

docker run --rm -v "$BACKUP_DIR:/backup:ro" "$PSQL_IMAGE" \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /backup/schema.sql

docker run --rm -v "$BACKUP_DIR:/backup:ro" "$PSQL_IMAGE" \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /backup/data.sql

# The signup trigger lives on auth.users, so a public-only dump cannot contain
# it and dropping public CASCADE removes it. This is the one thing the snapshot
# cannot restore by itself.
docker run --rm -v "$ROOT_DIR:/repo:ro" "$PSQL_IMAGE" \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f /repo/scripts/sql/recreate_auth_profile_trigger.sql

# Dumps can carry stale sequence values; without this the first insert after a
# restore fails on a duplicate key.
docker run --rm -v "$ROOT_DIR:/repo:ro" "$PSQL_IMAGE" \
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -q -f /repo/scripts/sql/resync_sequences.sql

echo "Remote restore complete."
echo
echo "The schema is now the one captured in the backup. If that backup predates"
echo "the latest migrations, bring it up to date with:  npm run db:push"
