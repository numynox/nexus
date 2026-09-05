-- Sections carried an emoji as their icon, which is why Noctua looked older
-- than the other two apps: Annona and Vibilia use lucide throughout, and
-- Annona's categories already have an icon key plus a colour.
--
-- `icon` now holds a key from SECTION_ICONS (app/noctua/src/lib/sectionMeta.ts)
-- rather than an emoji, and sections gain a colour like categories have. The
-- rendering falls back to the default for anything unrecognised, so a stored
-- emoji does not break the sidebar — but the existing default is mapped anyway,
-- since every section carried the same owl.

alter table public.sections
  add column if not exists color text;

alter table public.sections
  alter column icon set default 'newspaper';

-- The old default, and anything else that is not a known key, becomes the new
-- default. Anyone who picked a different emoji loses that choice; nothing else
-- can be inferred from it, and the picker is one click.
update public.sections
set icon = 'newspaper'
where icon is null
   or icon !~ '^[a-z0-9-]+$';

update public.sections
set color = 'slate'
where color is null;
