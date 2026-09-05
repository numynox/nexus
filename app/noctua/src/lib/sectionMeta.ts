/**
 * Icons and colours for sections — the Noctua counterpart to Annona's
 * categoryMeta.
 *
 * Sections used to carry an emoji, which is why Noctua read as the older
 * sibling: the other two apps use lucide throughout. Icons are stored as a key
 * so the rendering can change without touching the database, and unknown keys
 * (including the emoji this replaced) fall back to the default rather than
 * rendering nothing.
 */

import {
  Atom,
  Briefcase,
  Camera,
  Clapperboard,
  Cpu,
  Dumbbell,
  Gamepad2,
  Globe,
  GraduationCap,
  Heart,
  Landmark,
  Leaf,
  Lightbulb,
  Music,
  Newspaper,
  Plane,
  Rocket,
  Scale,
  Trophy,
  Utensils,
} from "lucide-svelte";
import type { Component } from "svelte";

export interface SectionColor {
  key: string;
  label: string;
  value: string;
}

export interface SectionIconDef {
  key: string;
  label: string;
  component: Component;
}

/** Same palette as Annona's categories, so the family looks related. */
export const SECTION_COLORS: SectionColor[] = [
  { key: "slate", label: "Slate", value: "hsl(215 20% 50%)" },
  { key: "rose", label: "Rose", value: "hsl(350 60% 55%)" },
  { key: "coral", label: "Coral", value: "hsl(15 70% 55%)" },
  { key: "amber", label: "Amber", value: "hsl(38 80% 50%)" },
  { key: "lime", label: "Lime", value: "hsl(80 55% 45%)" },
  { key: "green", label: "Green", value: "hsl(140 50% 45%)" },
  { key: "teal", label: "Teal", value: "hsl(175 55% 42%)" },
  { key: "sky", label: "Sky", value: "hsl(200 65% 52%)" },
  { key: "blue", label: "Blue", value: "hsl(220 60% 55%)" },
  { key: "violet", label: "Violet", value: "hsl(265 55% 55%)" },
  { key: "pink", label: "Pink", value: "hsl(320 60% 57%)" },
];

export const SECTION_ICONS: SectionIconDef[] = [
  { key: "newspaper", label: "News", component: Newspaper },
  { key: "globe", label: "World", component: Globe },
  { key: "landmark", label: "Politics", component: Landmark },
  { key: "cpu", label: "Technology", component: Cpu },
  { key: "atom", label: "Science", component: Atom },
  { key: "rocket", label: "Space", component: Rocket },
  { key: "briefcase", label: "Business", component: Briefcase },
  { key: "scale", label: "Law", component: Scale },
  { key: "trophy", label: "Sport", component: Trophy },
  { key: "dumbbell", label: "Fitness", component: Dumbbell },
  { key: "gamepad", label: "Games", component: Gamepad2 },
  { key: "clapperboard", label: "Film", component: Clapperboard },
  { key: "music", label: "Music", component: Music },
  { key: "camera", label: "Photography", component: Camera },
  { key: "utensils", label: "Food", component: Utensils },
  { key: "leaf", label: "Environment", component: Leaf },
  { key: "heart", label: "Health", component: Heart },
  { key: "graduation-cap", label: "Education", component: GraduationCap },
  { key: "plane", label: "Travel", component: Plane },
  { key: "lightbulb", label: "Ideas", component: Lightbulb },
];

export const DEFAULT_SECTION_ICON = "newspaper";
export const DEFAULT_SECTION_COLOR = "slate";

export function getSectionIconComponent(
  iconKey: string | null | undefined,
): Component {
  if (!iconKey) return Newspaper;
  return SECTION_ICONS.find((i) => i.key === iconKey)?.component ?? Newspaper;
}

export function getSectionColor(colorKey: string | null | undefined): string {
  if (!colorKey) return "";
  return SECTION_COLORS.find((c) => c.key === colorKey)?.value ?? "";
}
