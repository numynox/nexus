-- Vibilia: the assumed speed curve goes by a drive's duration, not its
-- distance.
--
-- 20261020120000 read the owner's curve as average speed over drive
-- distance. It was meant over duration: a 20-minute drive averages
-- 50 km/h. Only the curve changes. Everything that converts drives calls
-- vibilia_assumed_drive_km, and the speed factor scales the new curve as
-- it scaled the old one.
-- Reasoning: .agents/brain/decisions/2026-10-20-assumed-speed-until-learned.md

-- Kilometres of one drive of the given length, at this average speed for the
-- whole drive:
--
--     up to 10 min   30 km/h
--     10 → 20 min    rising linearly to 50 km/h
--     20 → 100 min   rising linearly to 100 km/h
--     beyond         100 km/h
--
-- The Android and web apps carry copies for a single drive (DriveCurve.kt,
-- assumedDriveKm in data.ts); this is the original.
create or replace function public.vibilia_assumed_drive_km(p_hours numeric)
returns numeric
language sql
immutable
set search_path = public
as $$
  select case
    when p_hours is null or p_hours <= 0 then 0
    else p_hours * case
      when p_hours * 60 <= 10 then 30
      when p_hours * 60 <= 20 then 30 + 2 * (p_hours * 60 - 10)
      when p_hours * 60 <= 100 then 50 + 0.625 * (p_hours * 60 - 20)
      else 100
    end
  end;
$$;
