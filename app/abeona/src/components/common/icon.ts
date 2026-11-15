import type { Component } from "svelte";

/** An icon a shared component is handed: a lucide icon (still a class component) or a Svelte 5 component. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type IconComponent = Component<any> | (new (...args: any[]) => any);
