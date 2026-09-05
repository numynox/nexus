-- invoke_refresh_fuel_prices() read FUEL_PRICE_API_KEY from the vault and never
-- used it: the key is only needed inside the Edge Function, which reads it from
-- its own environment. The unused lookup made every cron tick fail in any
-- environment where the key was not also duplicated into the vault.
--
-- Recreated here without that lookup. Signature, security context and schedule
-- are unchanged.

set check_function_bodies = off;

create or replace function public.invoke_refresh_fuel_prices()
returns void
language plpgsql
security definer
set search_path = pg_catalog
as $function$
declare
  v_invoke_secret text;
  v_function_url text;
begin
  select decrypted_secret
    into v_invoke_secret
  from vault.decrypted_secrets
  where name = 'FUEL_PRICE_INVOKE_SECRET'
  limit 1;

  if v_invoke_secret is null then
    raise exception 'FUEL_PRICE_INVOKE_SECRET not found in vault';
  end if;

  select decrypted_secret
    into v_function_url
  from vault.decrypted_secrets
  where name = 'REFRESH_FUEL_PRICES_FUNCTION_URL'
  limit 1;

  if v_function_url is null then
    raise exception 'REFRESH_FUEL_PRICES_FUNCTION_URL not found in vault';
  end if;

  perform net.http_post(
    url := v_function_url,
    headers := jsonb_build_object(
      'x-invoke-secret', v_invoke_secret,
      'Content-Type', 'application/json'
    ),
    body := '{}'::jsonb
  );
end;
$function$;
