// Removing files in abeona-tickets that no trip or card points at any more
// (public.abeona_orphaned_files). Postgres may not delete Storage objects,
// so the scheduled job does it through the Storage API, a few per run.
// Kept apart from index.ts so the test can hand it fakes.

/** At most this many files per run; the job runs every 15 minutes. */
export const ORPHANS_PER_RUN = 20;

export interface OrphanDatabase {
  rpc(fn: string, args: Record<string, unknown>): PromiseLike<{ data: unknown; error: { message: string } | null }>;
}

export interface OrphanStorage {
  remove(paths: string[]): PromiseLike<{ error: { message: string } | null }>;
}

/** Returns how many files were removed; throws when the listing or removal fails. */
export async function removeOrphanedFiles(db: OrphanDatabase, bucket: OrphanStorage, limit = ORPHANS_PER_RUN): Promise<number> {
  const { data, error } = await db.rpc("abeona_orphaned_files", { p_limit: limit });
  if (error) throw new Error(`abeona_orphaned_files: ${error.message}`);
  const names = (Array.isArray(data) ? data : [])
    .map((row) => (typeof row === "string" ? row : (row as Record<string, unknown>)?.abeona_orphaned_files))
    .filter((name): name is string => typeof name === "string" && name.length > 0);
  if (names.length === 0) return 0;
  const removed = await bucket.remove(names);
  if (removed.error) throw new Error(`remove: ${removed.error.message}`);
  return names.length;
}
