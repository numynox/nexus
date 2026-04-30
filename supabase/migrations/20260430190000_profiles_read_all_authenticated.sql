-- Allow all authenticated users to read all profiles
-- (needed so collaborative features like item logs can show user names)
DROP POLICY IF EXISTS "profiles_select_own" ON public.profiles;

CREATE POLICY "profiles_select_authenticated"
  ON public.profiles
  FOR SELECT
  TO authenticated
  USING (true);
