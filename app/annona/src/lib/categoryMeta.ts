import {
  Apple,
  Beef,
  Candy,
  Carrot,
  Cherry,
  Coffee,
  Cookie,
  Egg,
  Fish,
  Flame,
  Grape,
  IceCreamCone,
  Leaf,
  Milk,
  Nut,
  Package,
  Pizza,
  Sandwich,
  ShoppingBasket,
  Snowflake,
  Soup,
  Wheat,
  Wine,
} from "lucide-svelte";
import type { Component } from "svelte";

export interface CategoryColor {
  key: string;
  label: string;
  value: string;
}

export interface CategoryIconDef {
  key: string;
  label: string;
  component: Component;
}

export const CATEGORY_COLORS: CategoryColor[] = [
  { key: "rose", label: "Rose", value: "hsl(350 60% 55%)" },
  { key: "coral", label: "Coral", value: "hsl(15 70% 55%)" },
  { key: "amber", label: "Amber", value: "hsl(38 80% 50%)" },
  { key: "yellow", label: "Yellow", value: "hsl(50 85% 48%)" },
  { key: "lime", label: "Lime", value: "hsl(80 55% 45%)" },
  { key: "green", label: "Green", value: "hsl(140 50% 45%)" },
  { key: "teal", label: "Teal", value: "hsl(175 55% 42%)" },
  { key: "sky", label: "Sky", value: "hsl(200 65% 52%)" },
  { key: "blue", label: "Blue", value: "hsl(220 60% 55%)" },
  { key: "violet", label: "Violet", value: "hsl(265 55% 55%)" },
  { key: "pink", label: "Pink", value: "hsl(320 60% 57%)" },
  { key: "brown", label: "Brown", value: "hsl(25 40% 45%)" },
  { key: "slate", label: "Slate", value: "hsl(215 20% 50%)" },
];

export const CATEGORY_ICONS: CategoryIconDef[] = [
  { key: "package", label: "Default", component: Package },
  { key: "shopping-basket", label: "Basket", component: ShoppingBasket },
  { key: "apple", label: "Apple", component: Apple },
  { key: "beef", label: "Meat", component: Beef },
  { key: "candy", label: "Candy", component: Candy },
  { key: "carrot", label: "Carrot", component: Carrot },
  { key: "cherry", label: "Cherry", component: Cherry },
  { key: "coffee", label: "Coffee", component: Coffee },
  { key: "cookie", label: "Bakery", component: Cookie },
  { key: "egg", label: "Egg", component: Egg },
  { key: "fish", label: "Seafood", component: Fish },
  { key: "flame", label: "Spices", component: Flame },
  { key: "grape", label: "Grape", component: Grape },
  { key: "ice-cream-cone", label: "Frozen", component: IceCreamCone },
  { key: "leaf", label: "Herbs", component: Leaf },
  { key: "milk", label: "Dairy", component: Milk },
  { key: "nut", label: "Nuts", component: Nut },
  { key: "pizza", label: "Pizza", component: Pizza },
  { key: "sandwich", label: "Sandwich", component: Sandwich },
  { key: "snowflake", label: "Frozen", component: Snowflake },
  { key: "soup", label: "Soup", component: Soup },
  { key: "wheat", label: "Grains", component: Wheat },
  { key: "wine", label: "Wine", component: Wine },
];

export function getCategoryBorderColor(
  colorKey: string | null | undefined,
): string {
  if (!colorKey) return "";
  return CATEGORY_COLORS.find((c) => c.key === colorKey)?.value ?? "";
}

export function getCategoryIconComponent(
  iconKey: string | null | undefined,
): Component {
  if (!iconKey) return Package;
  return CATEGORY_ICONS.find((i) => i.key === iconKey)?.component ?? Package;
}
