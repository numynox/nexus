import type { Component, Snippet } from "svelte";

export interface NexusTheme {
  name: string;
  label: string;
  dark: boolean;
}

export declare const THEMES: NexusTheme[];
export declare const AUTO: "auto";
export declare const AUTO_DARK_THEME: string;
export declare const AUTO_LIGHT_THEME: string;

export declare function isKnownTheme(value: string): boolean;
export declare function resolveTheme(theme: string): string;
export declare function getTheme(storageKey: string): string;
export declare function setTheme(storageKey: string, theme: string): void;
export declare function applyTheme(
  theme: string,
  options?: { animated?: boolean },
): void;
export declare function themeBootstrapScript(storageKey: string): string;

export declare const ThemePicker: Component<{
  current: string;
  onSelect: (theme: string) => void;
}>;

export declare const LoginPanel: Component<{
  siteTitle: string;
  logoSrc?: string;
  signIn: (email: string, password: string) => Promise<unknown>;
  onSignedIn?: () => void;
}>;

export declare const AppShell: Component<{
  getSession: () => Promise<any>;
  onAuthStateChange: (
    cb: (event: string, session: any) => void,
  ) => { data: { subscription: { unsubscribe: () => void } } };
  signIn: (email: string, password: string) => Promise<unknown>;
  siteTitle: string;
  logoSrc?: string;
  onSignedIn?: (session: any) => void | Promise<void>;
  sidebar?: Snippet<[any]>;
  children?: Snippet;
}>;
