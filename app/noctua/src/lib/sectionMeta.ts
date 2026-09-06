/**
 * Icons for sections — the Noctua counterpart to Annona's categoryMeta.
 *
 * Sections used to carry an emoji, which is why Noctua read as the older
 * sibling: the other two apps use lucide throughout. Icons are stored as a key
 * so the rendering can change without touching the database, and unknown keys
 * (including the emoji this replaced) fall back to the default rather than
 * rendering nothing.
 *
 * Sections briefly had a colour as well. It is gone — see
 * `.agents/brain/decisions/2026-09-06-sections-have-no-colour.md`.
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

export interface SectionIconDef {
  key: string;
  label: string;
  component: Component;
}

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

export function getSectionIconComponent(
  iconKey: string | null | undefined,
): Component {
  if (!iconKey) return Newspaper;
  return SECTION_ICONS.find((i) => i.key === iconKey)?.component ?? Newspaper;
}
