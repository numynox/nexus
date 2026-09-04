import { createClient } from "@supabase/supabase-js";

const supabaseUrl = import.meta.env.PUBLIC_SUPABASE_URL || "";
const supabaseAnonKey =
  import.meta.env.PUBLIC_SUPABASE_ANON_KEY ||
  import.meta.env.PUBLIC_SUPABASE_KEY ||
  "";

// The schema generics are deliberately permissive: this project has no
// generated database types, and `data.ts` hand-writes the row interfaces it
// needs. Without them, every `.from(...)` insert/update resolves to `never` and
// `astro check` rejects the whole data layer.
let cachedClient: ReturnType<typeof createClient<any, "public", any>> | null =
  null;

export function getSupabaseClient() {
  if (!supabaseUrl || !supabaseAnonKey) {
    throw new Error(
      "Missing PUBLIC_SUPABASE_URL or PUBLIC_SUPABASE_ANON_KEY (or PUBLIC_SUPABASE_KEY).",
    );
  }

  if (!cachedClient) {
    cachedClient = createClient<any, "public", any>(supabaseUrl, supabaseAnonKey, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
      },
    });
  }

  return cachedClient;
}

export const supabase = getSupabaseClient();
