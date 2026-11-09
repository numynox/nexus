-- Abeona: a broken connection lifts a bound ticket's train binding.
--
-- DB lifts the binding of a Sparpreis and the like at 20 minutes' expected
-- delay or a cancellation. A connection that cannot be made breaks the plan
-- just as surely, so it now lifts it too:
--   * a change with no time at all: the train to catch leaves at or before
--     the arriving one is in (0 minutes or less, by the live times, rounded
--     to whole minutes like both clients' transfer rule);
--   * a change the traveller's phone saw them miss (their position, still at
--     the change station after the train left) when it had less than 5
--     minutes. Only the phone knows that, so the client says so with
--     p_connection_missed; the abeona-transit alternatives action passes it on.
-- binding_lifted_reason says which: 'delay', 'cancelled',
-- 'impossible_connection' or 'missed_connection'; null while it holds.
--
-- Only additions to the JSON: older APKs and the deployed Edge Function keep
-- calling abeona_allowed_products(p_trip_id) and get what they got before,
-- plus the new reasons. Mirrored in transfer.ts / Transfer.kt (trainBindingLifted).

drop function if exists public.abeona_allowed_products(uuid);

create or replace function public.abeona_allowed_products(p_trip_id uuid, p_connection_missed boolean default false)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with trip as (
    select t.id, t.fare_type, t.train_binding
    from public.abeona_trips t
    where t.id = p_trip_id
  ),
  rides as (
    select l.idx, l.line_name, l.cancelled, l.planned_arrival, l.arrival,
      l.planned_departure, l.departure,
      lead(coalesce(l.departure, l.planned_departure)) over (order by l.idx) as next_departure
    from public.abeona_legs l
    join trip on trip.id = l.trip_id
    where l.mode <> 'walk'
  ),
  facts as (
    select trip.*,
      coalesce((
        select round(extract(epoch from (coalesce(r.arrival, r.planned_arrival) - r.planned_arrival)) / 60)::integer
        from rides r order by r.idx desc limit 1
      ), 0) as delay,
      exists (select 1 from rides r where r.cancelled) as any_cancelled,
      exists (
        select 1 from rides r
        where r.next_departure is not null
          and round(extract(epoch from (r.next_departure - coalesce(r.arrival, r.planned_arrival))) / 60) <= 0
      ) as impossible_connection,
      coalesce((select jsonb_agg(r.line_name order by r.idx) from rides r where r.line_name is not null), '[]'::jsonb) as lines
    from trip
  ),
  judged as (
    select f.*, r.fare_type as rule_fare_type, r.long_distance, r.excluded_products, r.hint,
      coalesce(r.train_bound, false) and f.train_binding and coalesce(r.binding_liftable, false) as liftable,
      coalesce(r.train_bound, false) and f.train_binding as bound,
      case
        when public.abeona_train_binding_lifted(f.fare_type, f.train_binding, f.delay) then 'delay'
        when f.any_cancelled then 'cancelled'
        when f.impossible_connection then 'impossible_connection'
        when coalesce(p_connection_missed, false) then 'missed_connection'
      end as reason
    from facts f
    left join public.abeona_fare_rules r on r.fare_type = f.fare_type
  )
  select jsonb_build_object(
    'trip_id', j.id,
    'fare_type', j.fare_type,
    'known', j.rule_fare_type is not null,
    'long_distance', coalesce(j.long_distance, true),
    'excluded_products', to_jsonb(coalesce(j.excluded_products, '{}'::text[])),
    'train_bound', j.bound and not (j.liftable and j.reason is not null),
    'binding_lifted', j.liftable and j.reason is not null,
    'binding_lifted_reason', case when j.liftable then j.reason end,
    'arrival_delay_minutes', j.delay,
    'booked_lines', j.lines,
    'hint', coalesce(j.hint, 'Abeona does not know this ticket''s terms. Check them before you board another train.')
  )
  from judged j;
$$;

revoke all on function public.abeona_allowed_products(uuid, boolean) from public, anon;
grant execute on function public.abeona_allowed_products(uuid, boolean) to authenticated;
