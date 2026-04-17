#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_ROOT="${1:-$ROOT_DIR/backups/supabase}"
SCHEMAS="${SUPABASE_BACKUP_SCHEMAS:-public}"
STAMP="$(date +"%Y-%m-%d_%H-%M-%S")"
DEST="$BACKUP_ROOT/$STAMP"

mkdir -p "$DEST"

cd "$ROOT_DIR"

echo "Creating Supabase backup in: $DEST"
echo "Schemas: $SCHEMAS"

npx supabase db dump --linked --schema "$SCHEMAS" --file "$DEST/schema.sql"
npx supabase db dump --linked --data-only --schema "$SCHEMAS" --use-copy --file "$DEST/data.sql"
npx supabase db dump --linked --role-only --file "$DEST/roles.sql"

cat > "$DEST/manifest.txt" <<EOF
created_at=$STAMP
schemas=$SCHEMAS
project_linked=true
schema_file=schema.sql
data_file=data.sql
roles_file=roles.sql
EOF

cat > "$DEST/RESTORE_NOTES.txt" <<'EOF'
Local restore (safe test):
  npm run db:restore:local -- backups/supabase/<timestamp>

Remote restore (destructive, requires DB URL):
  SUPABASE_DB_URL='postgresql://...' bash scripts/db-restore-remote.sh backups/supabase/<timestamp> --confirm-remote-restore
EOF

echo "Backup complete: $DEST"
