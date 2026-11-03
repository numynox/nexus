-- Abeona, #38 phase 5: what a late arrival is worth, kept as data.
--
-- Passenger rights in Germany follow EU Regulation 2021/782 and DB's
-- conditions of carriage. The figures change (the minimum payout, the
-- Deutschlandticket's flat rate, the taxi cap), so they are rows a migration
-- can correct, not code in two clients and an APK that lags behind
-- (decisions/2026-11-03-abeona-compensation-rules-as-data.md). Every figure
-- here was taken from fahrgastrechte.info and bahn.de in November 2026 and
-- must be checked against them before it is relied on.
--
-- abeona_compensation(trip_id) applies the rows to one trip and its real
-- arrival (abeona_trip_arrival). It writes nothing and runs as the caller.
--
-- Only additions: older APKs keep reading what they read before.

-- ── The trip's side ────────────────────────────────────────────────────────

-- Set by the traveller: the delay was caused by extreme weather, a natural
-- disaster or a person on the track — "force majeure", which excludes
-- compensation since June 2023. A strike by railway staff is not.
alter table public.abeona_trips
  add column if not exists force_majeure boolean not null default false;

-- ── Rules ──────────────────────────────────────────────────────────────────

create table if not exists public.abeona_compensation_rules (
  id text primary key,
  kind text not null check (kind in ('tier', 'flat', 'minimum', 'deadline', 'night', 'force_majeure')),
  -- 'flat' only: the fare type the flat rate replaces the percentages for,
  -- and the class it is for (null for both classes).
  fare_type text check (fare_type is null or fare_type in ('flex', 'saver', 'regional', 'deutschlandticket', 'bahncard100', 'other')),
  travel_class smallint check (travel_class is null or travel_class in (1, 2)),
  -- 'tier', 'flat', 'night': the arrival delay at the destination from which the rule applies.
  min_delay_minutes integer check (min_delay_minutes is null or min_delay_minutes >= 0),
  -- 'tier': the share of the fare paid back.
  percent numeric(5, 2) check (percent is null or percent between 0 and 100),
  -- 'flat': per case; 'minimum': the smallest payout; 'night': the taxi cap.
  amount_cents integer check (amount_cents is null or amount_cents >= 0),
  -- 'flat': a case pays at most this share of the ticket's value …
  cap_percent numeric(5, 2) check (cap_percent is null or cap_percent between 0 and 100),
  -- … which is the trip's price, else this (the Deutschlandticket's monthly price).
  ticket_value_cents integer check (ticket_value_cents is null or ticket_value_cents >= 0),
  -- 'deadline': months after the trip to claim in.
  months integer check (months is null or months > 0),
  -- 'night': the planned arrival window in local time.
  night_from time,
  night_to time,
  -- English, shown as is by both clients: a reason for the verdict …
  note text not null check (btrim(note) <> ''),
  -- … and, for some rules, advice shown under it.
  hint text,
  updated_at timestamptz not null default now()
);

comment on table public.abeona_compensation_rules is
  'Passenger-rights figures for abeona_compensation(): verify against fahrgastrechte.info and bahn.de.';

insert into public.abeona_compensation_rules
  (id, kind, fare_type, travel_class, min_delay_minutes, percent, amount_cents, cap_percent, ticket_value_cents, months, night_from, night_to, note, hint)
values
  ('tier-60', 'tier', null, null, 60, 25, null, null, null, null, null, null,
   'From 60 minutes late at the destination, 25 % of the fare is paid back.', null),
  ('tier-120', 'tier', null, null, 120, 50, null, null, null, null, null, null,
   'From 120 minutes late at the destination, 50 % of the fare is paid back.', null),
  ('flat-deutschlandticket', 'flat', 'deutschlandticket', null, 60, null, 150, 25, 6300, null, null, null,
   'A Deutschlandticket gets €1.50 for each arrival 60 minutes or more late.',
   'At most 25 % of the ticket''s monthly price in all.'),
  ('flat-bahncard100-2', 'flat', 'bahncard100', 2, 60, null, 1000, 25, null, null, null, null,
   'A BahnCard 100 in 2nd class gets €10 for each arrival 60 minutes or more late.',
   'At most 25 % of the card''s value in all.'),
  ('flat-bahncard100-1', 'flat', 'bahncard100', 1, 60, null, 1500, 25, null, null, null, null,
   'A BahnCard 100 in 1st class gets €15 for each arrival 60 minutes or more late.',
   'At most 25 % of the card''s value in all.'),
  ('minimum', 'minimum', null, null, null, null, 400, null, null, null, null, null,
   'Amounts under €4 are not paid out.',
   'With a season ticket, collect several delays and claim them together once they add up to €4.'),
  ('deadline', 'deadline', null, null, null, null, null, null, null, 3, null, null,
   'Claim within three months of the trip.', null),
  ('night', 'night', null, null, 60, null, 12000, null, null, null, '00:00', '05:00',
   'Arriving between midnight and 5 am at least 60 minutes late, or stranded by the last train: DB pays a taxi up to €120, or a hotel if you cannot travel on that night.',
   'Keep the receipts and claim them with the form.'),
  ('force-majeure', 'force_majeure', null, null, null, null, null, null, null, null, null, null,
   'No compensation when the delay was caused by extreme weather, a natural disaster or a person on the track.',
   'A strike by railway staff does not count as such a cause.')
on conflict (id) do nothing;

alter table public.abeona_compensation_rules enable row level security;

-- Reference data, like abeona_fare_rules: read by everyone signed in,
-- written only by migrations.
drop policy if exists abeona_compensation_rules_select on public.abeona_compensation_rules;
create policy abeona_compensation_rules_select on public.abeona_compensation_rules
  for select to authenticated
  using (true);

revoke all on table public.abeona_compensation_rules from anon;
revoke insert, update, delete, truncate on table public.abeona_compensation_rules from authenticated;
grant select on table public.abeona_compensation_rules to authenticated;

-- ── The verdict ────────────────────────────────────────────────────────────

-- What a trip's delay is worth, as one JSON object:
--   trip_id
--   verdict          'eligible', 'not_eligible', 'below_minimum',
--                    'unknown_price' (eligible, but the fare is not known) or
--                    'unknown_arrival' (a ride was cancelled or missed and
--                    the trains taken instead are not recorded)
--   amount_cents     null without a price or without a claim
--   percent          the tier applied (25, 50), null for a flat rate
--   delay_minutes, delay_source, planned_arrival, actual_arrival
--                    from abeona_trip_arrival()
--   threshold_minutes  the delay from which this ticket gets anything
--   reasons[]        English sentences for the verdict
--   hints[]          English advice: the night taxi, pooling, caps
--   deadline         yyyy-mm-dd, the last day to claim
--   deadline_passed
--   poolable         a flat rate that may be collected and claimed together
--   summary_de       German text for DB's passenger-rights form, when
--                    there is something to claim
--   claim_url        DB's page about the form
-- Null for a trip the caller cannot see. Stable, writes nothing.
create or replace function public.abeona_compensation(p_trip_id uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = public
as $$
declare
  v_zone text := public.nexus_time_zone();
  t public.abeona_trips;
  v_arrival jsonb;
  v_delay integer;
  v_source text;
  v_flat public.abeona_compensation_rules;
  v_tier public.abeona_compensation_rules;
  v_minimum public.abeona_compensation_rules;
  v_night public.abeona_compensation_rules;
  v_fm public.abeona_compensation_rules;
  v_months integer;
  v_threshold integer;
  v_verdict text;
  v_amount integer;
  v_percent numeric;
  v_value integer;
  v_reasons text[] := '{}';
  v_hints text[] := '{}';
  v_deadline date;
  v_planned_local time;
  v_summary text;
  v_rides text;
  v_replaced text;
begin
  select * into t from public.abeona_trips where id = p_trip_id;
  if not found then
    return null;
  end if;

  v_arrival := public.abeona_trip_arrival(p_trip_id);
  v_delay := (v_arrival->>'delay_minutes')::integer;
  v_source := v_arrival->>'source';

  select * into v_flat from public.abeona_compensation_rules
  where kind = 'flat' and fare_type = t.fare_type
    and (travel_class is null or travel_class = t.travel_class)
  order by travel_class nulls last
  limit 1;
  select * into v_minimum from public.abeona_compensation_rules where kind = 'minimum' limit 1;
  select * into v_night from public.abeona_compensation_rules where kind = 'night' limit 1;
  select * into v_fm from public.abeona_compensation_rules where kind = 'force_majeure' limit 1;
  select months into v_months from public.abeona_compensation_rules where kind = 'deadline' limit 1;

  v_threshold := coalesce(
    v_flat.min_delay_minutes,
    (select min(min_delay_minutes) from public.abeona_compensation_rules where kind = 'tier')
  );
  v_deadline := (((v_arrival->>'planned')::timestamptz at time zone v_zone)::date
    + make_interval(months => coalesce(v_months, 3)))::date;

  if v_delay is null then
    v_verdict := 'unknown_arrival';
    v_reasons := array['A train was cancelled or missed. Record the trains you took instead to work out the delay.'];
  elsif v_threshold is null or v_delay < v_threshold then
    v_verdict := 'not_eligible';
    v_reasons := array[case
      when v_delay = 0 then 'Arrived on time.'
      else format('Arrived %s min late.', v_delay)
    end || coalesce(format(' Compensation starts at %s minutes.', v_threshold), '')];
  elsif t.force_majeure and v_fm.id is not null then
    v_verdict := 'not_eligible';
    v_reasons := array[v_fm.note];
    if v_fm.hint is not null then v_hints := v_hints || v_fm.hint; end if;
  elsif v_flat.id is not null then
    v_amount := v_flat.amount_cents;
    v_value := coalesce(t.price_cents, v_flat.ticket_value_cents);
    if v_flat.cap_percent is not null and v_value is not null then
      v_amount := least(v_amount, round(v_value * v_flat.cap_percent / 100.0)::integer);
    end if;
    v_reasons := array[v_flat.note];
    if v_flat.hint is not null then v_hints := v_hints || v_flat.hint; end if;
  else
    select * into v_tier from public.abeona_compensation_rules
    where kind = 'tier' and min_delay_minutes <= v_delay
    order by min_delay_minutes desc
    limit 1;
    v_percent := v_tier.percent;
    v_reasons := array[v_tier.note];
    if t.price_cents is null then
      v_verdict := 'unknown_price';
      v_reasons := v_reasons || 'Add what the ticket cost to work out the amount.'::text;
    else
      v_amount := round(t.price_cents * v_percent / 100.0)::integer;
    end if;
  end if;

  if v_verdict is null then
    if v_amount < coalesce(v_minimum.amount_cents, 0) then
      v_verdict := 'below_minimum';
      v_reasons := v_reasons || v_minimum.note;
      if v_flat.id is not null and v_minimum.hint is not null then
        v_hints := v_hints || v_minimum.hint;
      end if;
    else
      v_verdict := 'eligible';
    end if;
  end if;

  -- The night: a planned arrival in the small hours, an hour or more late.
  v_planned_local := ((v_arrival->>'planned')::timestamptz at time zone v_zone)::time;
  if v_night.id is not null and v_verdict <> 'not_eligible'
     and (v_delay is null or v_delay >= v_night.min_delay_minutes)
     and v_planned_local >= v_night.night_from and v_planned_local < v_night.night_to then
    v_hints := v_hints || v_night.note;
    if v_night.hint is not null then v_hints := v_hints || v_night.hint; end if;
  end if;

  -- The text for DB's form, in German, when there is something to claim.
  if v_verdict in ('eligible', 'below_minimum', 'unknown_price') then
    select string_agg(
      format('%s (%s %s → %s %s)',
        coalesce(l.line_name, 'Zug'),
        l.from_name, to_char(l.planned_departure at time zone v_zone, 'HH24:MI'),
        l.to_name, to_char(l.planned_arrival at time zone v_zone, 'HH24:MI')),
      ', ' order by l.idx)
    into v_rides
    from public.abeona_legs l
    where l.trip_id = t.id and l.mode <> 'walk';

    select string_agg(
      format('%s (%s → %s %s)',
        coalesce(nullif(e.leg->>'line_name', ''), 'Zug'),
        coalesce(e.leg->'from'->>'name', '?'),
        coalesce(e.leg->'to'->>'name', '?'),
        coalesce(to_char(coalesce(nullif(e.leg->'to'->>'current', ''), nullif(e.leg->'to'->>'planned', ''))::timestamptz at time zone v_zone, 'HH24:MI'), '')),
      ', ' order by e.n)
    into v_replaced
    from jsonb_array_elements(coalesce(t.replacement_legs, '[]'::jsonb)) with ordinality as e(leg, n)
    where coalesce(e.leg->>'mode', 'train') <> 'walk';

    v_summary := concat_ws(E'\n',
      'Fahrgastrechte: Verspätung am Zielbahnhof',
      format('Reise: %s → %s', t.origin_name, t.destination_name),
      format('Reisedatum: %s', to_char(t.planned_departure at time zone v_zone, 'DD.MM.YYYY')),
      format('Gebuchte Verbindung: %s', v_rides),
      case when t.missed_leg_idx is not null then
        format('Anschluss verpasst oder Zug ausgefallen: %s',
          (select coalesce(l.line_name, 'Zug') || ' ab ' || l.from_name
           from public.abeona_legs l where l.trip_id = t.id and l.idx = t.missed_leg_idx))
      end,
      case when v_replaced is not null then format('Stattdessen gefahren: %s', v_replaced) end,
      format('Planmäßige Ankunft: %s', to_char((v_arrival->>'planned')::timestamptz at time zone v_zone, 'DD.MM.YYYY HH24:MI')),
      format('Tatsächliche Ankunft: %s', to_char((v_arrival->>'actual')::timestamptz at time zone v_zone, 'DD.MM.YYYY HH24:MI')),
      format('Verspätung am Ziel: %s Minuten', v_delay),
      format('Fahrkarte: %s, %s. Klasse', case t.fare_type
        when 'flex' then 'Flexpreis'
        when 'saver' then 'Sparpreis'
        when 'regional' then 'Nahverkehrsfahrkarte'
        when 'deutschlandticket' then 'Deutschlandticket'
        when 'bahncard100' then 'BahnCard 100'
        else 'Fahrkarte'
      end, t.travel_class),
      case when t.price_cents is not null then
        format('Fahrpreis: %s €', replace(to_char(t.price_cents / 100.0, 'FM999990.00'), '.', ','))
      end
    );
  end if;

  return jsonb_build_object(
    'trip_id', t.id,
    'verdict', v_verdict,
    'amount_cents', case when v_verdict in ('not_eligible', 'unknown_arrival') then null else v_amount end,
    'percent', v_percent,
    'delay_minutes', v_delay,
    'delay_source', v_source,
    'planned_arrival', v_arrival->'planned',
    'actual_arrival', v_arrival->'actual',
    'threshold_minutes', v_threshold,
    'reasons', to_jsonb(v_reasons),
    'hints', to_jsonb(v_hints),
    'deadline', v_deadline,
    'deadline_passed', (now() at time zone v_zone)::date > v_deadline,
    'poolable', v_flat.id is not null,
    'summary_de', v_summary,
    'claim_url', 'https://www.bahn.de/service/informationen-buchung/fahrgastrechte'
  );
end;
$$;

revoke all on function public.abeona_compensation(uuid) from public, anon;
grant execute on function public.abeona_compensation(uuid) to authenticated;
