<!--
  The owl at the end of the feed: a branch under a sky whose stars are today's
  reading, one per article, joined in the order they were read. The pull past
  the end is the owl's — its eyes widen and its ear tufts pop up at the
  threshold; let go and it flies off to look for news, and comes back shaking
  its head if there was none. By day it naps.

  The Android app's EndOfFeed is the fuller version of this and its source of
  truth: the shapes are the same SVG paths, in the same 360 × 470 units
  (lib/nightSky.ts). Positions and motion are eased towards targets each
  frame, so a new target taken mid-way never jerks.
-->
<script lang="ts">
  import { onMount } from "svelte";
  import type { TodayRead } from "../lib/data";
  import {
    constellation,
    isNight,
    MOON,
    moonLit,
    moonLitPath,
    moonPhase,
    moonPhaseName,
    seeded,
  } from "../lib/nightSky";

  interface Props {
    /** How far the end is pulled: 1 at the refresh threshold, up to 1.5. */
    pull?: number;
    refreshing?: boolean;
    /** Today's reads, oldest first: the stars. */
    reads?: TodayRead[];
    now?: Date;
  }

  let {
    pull = 0,
    refreshing = false,
    reads = [],
    now = new Date(),
  }: Props = $props();

  const uid = $props.id();
  const night = $derived(isNight(now));
  const phase = $derived(moonPhase(now));
  const stars = $derived(constellation(reads.map((r) => r.id)));

  const reducedQuery =
    typeof window !== "undefined"
      ? window.matchMedia("(prefers-reduced-motion: reduce)")
      : null;
  const reduced = reducedQuery?.matches ?? false;

  // ── Static sky ───────────────────────────────────────────────────────────
  const backgroundStars = (() => {
    const r = seeded(7);
    return Array.from({ length: 70 }, () => ({
      x: -200 + r() * 760,
      y: -400 + r() * 780,
      r: 0.5 + r() * 0.9,
      o: 0.35 + r() * 0.5,
      d: r() * 4,
      dur: 3 + r() * 3,
    }));
  })();

  const LEAVES: [number, number, number][] = [
    [38, -30, -1],
    [58, 24, 1],
    [74, -20, 1],
    [300, 210, -1],
    [318, 160, 1],
  ];

  function branchY(x: number, b: number): number {
    const t = (x + 220) / 800;
    return (1 - t) ** 2 * 436 + 2 * t * (1 - t) * (418 + 2 * b) + t * t * 402;
  }

  // ── Eased values ─────────────────────────────────────────────────────────
  type Value = { v: number; t: number; k: number };
  const A: Record<string, Value> = {};
  const value = (name: string, v: number, k = 12) => (A[name] = { v, t: v, k });
  const set = (name: string, t: number, k?: number) => {
    A[name].t = t;
    if (k) A[name].k = k;
  };
  const jump = (name: string, v: number) => {
    A[name].v = A[name].t = v;
  };
  (
    [
      ["owlX", 0, 8],
      ["owlY", 0, 10],
      ["sx", 1, 14],
      ["sy", 1, 14],
      ["headRot", 0, 10],
      ["headY", 0, 14],
      ["turn", 1, 16],
      ["widen", 0, 14],
      ["lidL", 0, 22],
      ["lidR", 0, 22],
      ["px", 0, 9],
      ["py", 0, 9],
      ["tuft", 0, 14],
      ["wing", 0, 12],
      ["bend", 0, 10],
      ["brow", 0, 10],
      ["mini", 0, 6],
      ["bubble", 0, 10],
      ["zz", 0, 4],
    ] as [string, number, number][]
  ).forEach(([n, v, k]) => value(n, v, k));

  let flapping = false;
  let showBack = $state(false);
  let sleeping = $state(false);
  let away = $state(false);
  let busy = false;
  let bubbleText = $state("hoo");
  let searchStart = 0;
  let lastTouch = 0;
  let pointer: { x: number; y: number } | null = null;

  let selected = $state<number | null>(null);
  let note = $state<string | null>(null);
  let shoot = $state<{ x: number; y: number; key: number } | null>(null);
  let noteTimer: ReturnType<typeof setTimeout> | undefined;

  function say(text: string) {
    clearTimeout(noteTimer);
    note = text;
    noteTimer = setTimeout(() => (note = null), 2600);
  }

  const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

  // ── Elements the frame loop moves ────────────────────────────────────────
  let svg: SVGSVGElement | undefined = $state();
  let owlEl: SVGGElement | undefined = $state();
  let headEl: SVGGElement | undefined = $state();
  let tuftsEl: SVGGElement | undefined = $state();
  let tuftLEl: SVGPathElement | undefined = $state();
  let tuftREl: SVGPathElement | undefined = $state();
  let wingLEl: SVGGElement | undefined = $state();
  let wingREl: SVGGElement | undefined = $state();
  let irisLEl: SVGGElement | undefined = $state();
  let irisREl: SVGGElement | undefined = $state();
  let pupLEl: SVGGElement | undefined = $state();
  let pupREl: SVGGElement | undefined = $state();
  let lidLEl: SVGRectElement | undefined = $state();
  let lidREl: SVGRectElement | undefined = $state();
  let closedLEl: SVGPathElement | undefined = $state();
  let closedREl: SVGPathElement | undefined = $state();
  let browsEl: SVGPathElement | undefined = $state();
  let branchEl: SVGPathElement | undefined = $state();
  let twigEl: SVGPathElement | undefined = $state();
  let leafEls: SVGGElement[] = $state([]);
  let miniEl: SVGGElement | undefined = $state();
  let miniWingEl: SVGPathElement | undefined = $state();
  let bubbleEl: SVGGElement | undefined = $state();
  let zzEl: SVGGElement | undefined = $state();
  let glowEl: SVGCircleElement | undefined = $state();

  function render(now: number) {
    if (!owlEl) return;
    const p = Math.min(pull, 1.5);
    const g = Math.min(p, 1);
    const ready = p >= 1;
    const still = reduced || busy || away || p > 0;
    const br = still ? 0 : Math.sin(now / 950) * 0.012;

    const bend = A.bend.v + 7 * g;
    branchEl?.setAttribute("d", `M -220 436 Q 180 ${418 + 2 * bend} 580 402`);
    const ty = branchY(52, bend);
    twigEl?.setAttribute("d", `M 52 ${ty + 2} Q 40 ${ty + 16} 22 ${ty + 22}`);
    leafEls.forEach((el, i) => {
      const x = LEAVES[i][0];
      el?.setAttribute("transform", `translate(${x} ${branchY(x, bend) - 2})`);
    });

    const feetY = 413.5 + bend;
    const sx = A.sx.v * (1 + 0.035 * g) * (1 + br * 0.4);
    const sy = A.sy.v * (1 - 0.07 * g) * (1 + br);
    owlEl.setAttribute(
      "transform",
      `translate(${180 + A.owlX.v} ${feetY + A.owlY.v}) scale(${sx} ${sy})`,
    );
    headEl?.setAttribute(
      "transform",
      `translate(0 ${A.headY.v}) rotate(${A.headRot.v} 0 -112) scale(${A.turn.v} 1)`,
    );

    const tu = Math.max(A.tuft.v, ready ? 1 : 0);
    tuftsEl?.setAttribute("transform", `translate(0 ${-tu * 6})`);
    tuftLEl?.setAttribute("transform", `rotate(${-tu * 10} -34 -166)`);
    tuftREl?.setAttribute("transform", `rotate(${tu * 10} 34 -166)`);

    const w =
      flapping && !reduced
        ? 38 + Math.sin(now / 48) * 42
        : A.wing.v + (ready ? 10 : 0);
    wingLEl?.setAttribute("transform", `rotate(${w} -40 -96)`);
    wingREl?.setAttribute("transform", `rotate(${-w} 40 -96)`);

    const sc = 1 + 0.2 * Math.max(A.widen.v, g * 0.9);
    irisLEl?.setAttribute("transform", `translate(-19 -138) scale(${sc})`);
    irisREl?.setAttribute("transform", `translate(19 -138) scale(${sc})`);
    const look = `translate(${A.px.v} ${A.py.v - 3.5 * g})`;
    pupLEl?.setAttribute("transform", look);
    pupREl?.setAttribute("transform", look);
    // A sleeping owl wakes as it is pulled: one eye, then the other.
    const clamp = (v: number) => Math.max(0, Math.min(1, v));
    const lL = clamp(Math.min(A.lidL.v, 1 - clamp((p - 0.45) / 0.45)));
    const lR = clamp(Math.min(A.lidR.v, 1 - clamp((p - 0.15) / 0.45)));
    lidLEl?.setAttribute("height", (32 * lL).toFixed(2));
    lidREl?.setAttribute("height", (32 * lR).toFixed(2));
    closedLEl?.setAttribute("opacity", lL > 0.88 ? "1" : "0");
    closedREl?.setAttribute("opacity", lR > 0.88 ? "1" : "0");
    browsEl?.setAttribute("opacity", A.brow.v.toFixed(2));
    bubbleEl?.setAttribute("opacity", A.bubble.v.toFixed(2));
    zzEl?.setAttribute("opacity", (p > 0.2 ? 0 : A.zz.v).toFixed(2));

    const m = A.mini.v;
    miniEl?.setAttribute("opacity", m.toFixed(2));
    if (m > 0.01 && miniEl) {
      const t = ((now - searchStart) / 1700) % 1;
      const x = MOON.x - 95 + t * 190;
      const y = MOON.y + 6 - Math.sin(t * Math.PI) * 18 + Math.sin(now / 160) * 2;
      miniEl.setAttribute("transform", `translate(${x} ${y})`);
      miniWingEl?.setAttribute(
        "transform",
        `scale(1 ${reduced ? 1 : Math.sin(now / 55)})`,
      );
    }
    glowEl?.setAttribute("opacity", (0.7 + 0.3 * g).toFixed(2));
  }

  // ── Idle life ────────────────────────────────────────────────────────────
  let nextBlink = 0;
  let nextDrift = 0;
  let nextShoot = 0;
  function idle(now: number) {
    if (busy || away || pull > 0) {
      if (pull > 0) lastTouch = now;
      return;
    }
    if (!sleeping && now > nextBlink) {
      nextBlink = now + 2600 + Math.random() * 3800;
      set("lidL", 1, 45);
      set("lidR", 1, 45);
      setTimeout(() => {
        if (!sleeping && !away) {
          set("lidL", 0, 22);
          set("lidR", 0, 22);
        }
      }, 120);
    }
    if (!pointer && now > nextDrift) {
      nextDrift = now + 2500 + Math.random() * 3500;
      set("px", (Math.random() - 0.5) * 6, 4);
      set("py", (Math.random() - 0.5) * 3, 4);
    }
    if (!sleeping && !reduced && now - lastTouch > 20000) {
      lastTouch = now;
      void play(turnAround);
    }
    if (night && !reduced && now > nextShoot) {
      nextShoot = now + 18000 + Math.random() * 20000;
      const x = 150 + Math.random() * 180;
      const y = 20 + Math.random() * 70;
      shoot = { x, y, key: now };
      if (!sleeping) lookAt(x - 60, y + 30);
      setTimeout(() => {
        if (shoot?.key === now) shoot = null;
      }, 1300);
    }
  }

  async function play(scene: () => Promise<void>) {
    if (busy || away) return;
    busy = true;
    try {
      await scene();
    } finally {
      busy = false;
    }
  }

  function lookAt(x: number, y: number) {
    const ex = 180 + A.owlX.v;
    const ey = 413.5 + A.bend.v + A.owlY.v - 138;
    const dx = x - ex;
    const dy = y - ey;
    const d = Math.hypot(dx, dy) || 1;
    const k = Math.min(1, d / 120);
    set("px", (dx / d) * 4.2 * k, 12);
    set("py", (dy / d) * 3.6 * k, 12);
  }

  async function turnAround() {
    set("turn", 0, 14);
    await wait(230);
    showBack = true;
    set("turn", 1);
    await wait(1600);
    set("turn", 0);
    await wait(230);
    showBack = false;
    set("turn", 1);
    await wait(250);
  }

  function sleep(on: boolean, instant = false) {
    sleeping = on;
    const v = on ? 1 : 0;
    if (instant) {
      jump("lidL", v);
      jump("lidR", v);
    } else {
      set("lidL", v, 5);
      set("lidR", v, 4);
    }
    set("zz", on ? 1 : 0);
  }

  async function land() {
    set("bend", 12, 26);
    set("sy", 0.91, 26);
    await wait(130);
    set("bend", 0, 6);
    set("sy", 1, 9);
  }

  async function enter() {
    busy = true;
    sleeping = false;
    jump("lidL", night ? 0 : 1);
    jump("lidR", night ? 0 : 1);
    if (!reduced) {
      jump("owlX", 230);
      jump("owlY", -150);
      flapping = true;
      set("owlX", 0, 5);
      set("owlY", 0, 5.5);
      await wait(800);
      flapping = false;
      set("wing", 0, 10);
      await land();
      await wait(350);
    } else {
      jump("owlX", 0);
      jump("owlY", 0);
    }
    sleep(!night, night);
    busy = false;
    lastTouch = performance.now();
  }

  // ── The refresh ──────────────────────────────────────────────────────────
  let flight: Promise<void> | null = null;

  async function takeOff() {
    away = true;
    busy = false;
    selected = null;
    showBack = false;
    set("turn", 1, 20);
    set("headRot", 0, 14);
    set("brow", 0, 14);
    set("zz", 0, 12);
    set("lidL", 0, 20);
    set("lidR", 0, 20);
    set("widen", 0.7, 14);
    set("tuft", 1, 14);
    if (reduced) {
      searchStart = performance.now();
      set("mini", 1, 6);
      return;
    }
    set("sy", 0.86, 22);
    set("sx", 1.05, 22);
    set("bend", 9, 20);
    await wait(170);
    flapping = true;
    set("sy", 1.04, 12);
    set("sx", 0.98, 12);
    set("bend", -5, 18);
    set("owlY", -900, 4.2);
    set("owlX", -30, 3);
    await wait(180);
    set("bend", 0, 7);
    await wait(420);
    searchStart = performance.now();
    set("mini", 1, 6);
  }

  async function comeBack() {
    await flight;
    set("mini", 0, 8);
    if (!reduced) {
      await wait(150);
      jump("owlX", 150);
      jump("owlY", -800);
      set("owlX", 0, 4.4);
      set("owlY", 0, 4.6);
      set("sy", 1, 10);
      set("sx", 1, 10);
      set("widen", 0.3, 8);
      await wait(950);
      flapping = false;
      set("wing", 0, 10);
      await land();
    }
    set("tuft", 0, 8);
    sleeping = false;
    set("widen", 0, 8);
    away = false;
    if (!night) {
      await wait(2600);
      if (!busy && !away) sleep(true);
    }
  }

  let wasRefreshing = false;
  $effect(() => {
    if (refreshing && !wasRefreshing) {
      wasRefreshing = true;
      flight = takeOff();
    } else if (!refreshing && wasRefreshing) {
      wasRefreshing = false;
      void comeBack();
    }
  });

  let wasReady = false;
  $effect(() => {
    const ready = pull >= 1;
    if (ready && !wasReady) {
      set("headY", -4, 30);
      setTimeout(() => set("headY", 0, 12), 90);
      try {
        navigator.vibrate?.(8);
      } catch {
        /* not offered here */
      }
    }
    wasReady = ready;
  });

  // ── Taps ─────────────────────────────────────────────────────────────────
  let taps: number[] = [];
  let pressTimer: ReturnType<typeof setTimeout> | undefined;
  let pressed = false;

  function toScene(event: PointerEvent): { x: number; y: number } | null {
    const matrix = svg?.getScreenCTM();
    if (!svg || !matrix) return null;
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const p = point.matrixTransform(matrix.inverse());
    return { x: p.x, y: p.y };
  }

  function onOwl(p: { x: number; y: number }): boolean {
    const x = 180 + A.owlX.v;
    const feet = 413.5 + A.bend.v + A.owlY.v;
    return p.x > x - 60 && p.x < x + 60 && p.y > feet - 200 && p.y < feet + 10;
  }

  function onPointerMove(event: PointerEvent) {
    const p = toScene(event);
    pointer = p;
    if (p && !sleeping && !busy && !away && event.pointerType === "mouse") lookAt(p.x, p.y);
  }

  function onPointerDown(event: PointerEvent) {
    lastTouch = performance.now();
    const p = toScene(event);
    pressed = false;
    if (p && onOwl(p) && !sleeping && !away) {
      pressTimer = setTimeout(() => {
        pressed = true;
        void play(async () => {
          bubbleText = "hoo… hoo";
          set("bubble", 1, 14);
          for (let i = 0; i < 2; i++) {
            set("tuft", 1, 20);
            set("sy", 1.04, 18);
            await wait(200);
            set("tuft", 0, 12);
            set("sy", 1, 12);
            await wait(220);
          }
          await wait(600);
          set("bubble", 0, 8);
        });
      }, 550);
    }
  }

  function cancelPress() {
    clearTimeout(pressTimer);
  }

  function onClick(event: MouseEvent) {
    cancelPress();
    if (pressed) {
      pressed = false;
      return;
    }
    lastTouch = performance.now();
    const p = toScene(event as PointerEvent);
    if (!p) return;
    if (shoot) {
      const age = (performance.now() - shoot.key) / 1250;
      const hx = shoot.x - 190 * age;
      const hy = shoot.y + 105 * age;
      if (Math.hypot(hx - p.x, hy - p.y) < 40) {
        shoot = null;
        say("You caught a shooting star. Make a wish.");
        set("widen", 0.8, 20);
        setTimeout(() => set("widen", 0, 6), 700);
        return;
      }
    }
    if (selected !== null) {
      selected = null;
      return;
    }
    if (Math.hypot(p.x - MOON.x, p.y - MOON.y) < 36) {
      say(`${moonPhaseName(phase)} · ${Math.round(moonLit(phase) * 100)}% lit`);
      void play(async () => {
        if (!sleeping) {
          set("px", 4, 10);
          set("py", -3.5, 10);
          set("headRot", 9, 8);
        }
        await wait(1600);
        set("headRot", 0, 6);
      });
      return;
    }
    if (!onOwl(p) || away) return;
    if (sleeping) {
      void play(async () => {
        set("lidR", 0.25, 20);
        await wait(900);
        set("lidR", 1, 6);
      });
      return;
    }
    const now = performance.now();
    taps = taps.filter((t) => now - t < 2500);
    taps.push(now);
    if (taps.length >= 6) {
      taps = [];
      void play(async () => {
        set("sx", 1.13, 10);
        set("sy", 1.06, 10);
        set("brow", 1, 14);
        set("lidL", 0.42, 14);
        set("lidR", 0.42, 14);
        set("tuft", 1, 10);
        bubbleText = "…";
        set("bubble", 1, 14);
        await wait(2400);
        set("bubble", 0, 8);
        set("sx", 1, 4);
        set("sy", 1, 4);
        set("brow", 0, 4);
        set("tuft", 0, 4);
        set("lidL", 0, 6);
        set("lidR", 0, 6);
        await wait(500);
      });
      return;
    }
    void play(async () => {
      set("headY", -6, 22);
      set("headRot", 13, 14);
      await wait(240);
      set("headY", 0, 14);
      set("headRot", -7, 12);
      await wait(220);
      set("headRot", 0, 10);
      await wait(120);
    });
  }

  function onStar(event: MouseEvent, index: number) {
    event.stopPropagation();
    lastTouch = performance.now();
    selected = selected === index ? null : index;
  }

  // ── Frame loop ───────────────────────────────────────────────────────────
  onMount(() => {
    let last = performance.now();
    let frame = 0;
    const tick = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const speed = reduced ? 6 : 1;
      for (const key in A) {
        const a = A[key];
        a.v += (a.t - a.v) * (1 - Math.exp(-dt * a.k * speed));
      }
      render(now);
      idle(now);
      frame = requestAnimationFrame(tick);
    };
    const start = performance.now();
    nextBlink = start + 2500;
    nextShoot = start + 9000;
    void enter();
    frame = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame);
      clearTimeout(noteTimer);
    };
  });

  const timeFormat = new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
  });
  const description = $derived(
    `An owl on a branch under the ${night ? "night " : ""}sky, with a star for each of the ${reads.length} articles read today`,
  );
  const selectedRead = $derived(selected === null ? null : reads[selected]);
  const selectedStar = $derived(selected === null ? null : stars[selected]);
</script>

<div
  class="night-owl relative overflow-hidden rounded-3xl"
  class:day={!night}
  class:reduced
>
  <!-- The owl's taps are play: the panel's Refresh button and the r key do the work. -->
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions -->
  <svg
    bind:this={svg}
    class="block h-full w-full select-none"
    viewBox="0 0 360 470"
    preserveAspectRatio="xMidYMax meet"
    role="img"
    aria-label={description}
    onpointermove={onPointerMove}
    onpointerleave={() => (pointer = null)}
    onpointerdown={onPointerDown}
    onpointerup={cancelPress}
    onpointercancel={cancelPress}
    onclick={onClick}
  >
    <defs>
      <linearGradient id="{uid}-sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="var(--owl-sky-top)" />
        <stop offset="1" stop-color="var(--owl-sky-bottom)" />
      </linearGradient>
      <radialGradient id="{uid}-glow">
        <stop offset="0" stop-color="#fff6d6" stop-opacity=".55" />
        <stop offset="1" stop-color="#fff6d6" stop-opacity="0" />
      </radialGradient>
      <linearGradient id="{uid}-streak" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0" stop-color="#fff" stop-opacity="1" />
        <stop offset="1" stop-color="#fff" stop-opacity="0" />
      </linearGradient>
      <clipPath id="{uid}-eyeL"><circle cx="-19" cy="-138" r="16" /></clipPath>
      <clipPath id="{uid}-eyeR"><circle cx="19" cy="-138" r="16" /></clipPath>
    </defs>

    <rect x="-1000" y="-1000" width="2360" height="1470" fill="url(#{uid}-sky)" />
    {#if night}
      <g>
        {#each backgroundStars as s, i (i)}
          <circle
            class="twinkle"
            cx={s.x}
            cy={s.y}
            r={s.r}
            fill="#fff"
            opacity={s.o}
            style={`--d:${s.d}s; --dur:${s.dur}s`}
          />
        {/each}
      </g>
    {/if}

    <g class="cursor-pointer">
      {#if night}
        <circle bind:this={glowEl} cx={MOON.x} cy={MOON.y} r="70" fill="url(#{uid}-glow)" />
      {/if}
      <circle cx={MOON.x} cy={MOON.y} r={MOON.r} fill="#fff" opacity=".07" />
      <path d={moonLitPath(phase)} fill={night ? "#f7f0d6" : "#ffffff"} />
    </g>

    <g bind:this={miniEl} opacity="0">
      <ellipse rx="6.5" ry="5.5" fill="var(--owl-mini)" />
      <path
        bind:this={miniWingEl}
        d="M -2 -1 L -14 -7 L -6 2 Z M 2 -1 L 14 -7 L 6 2 Z"
        fill="var(--owl-mini)"
      />
    </g>

    <g class="constellation">
      {#each stars as star, i (reads[i]?.id ?? i)}
        {#if i > 0}
          <line
            class="cline"
            x1={stars[i - 1].x}
            y1={stars[i - 1].y}
            x2={star.x}
            y2={star.y}
            stroke="var(--owl-star)"
            stroke-opacity={night ? 0.32 : 0.28}
            stroke-width="1"
            style={`--len:${Math.hypot(star.x - stars[i - 1].x, star.y - stars[i - 1].y).toFixed(1)}; --fd:${(0.5 + i * Math.min(0.12, 1.5 / stars.length)).toFixed(2)}s`}
          />
        {/if}
      {/each}
      {#each stars as star, i (reads[i]?.id ?? i)}
        {@const r = 2.2 + (Number(reads[i]?.id ?? 0) % 3) * 0.9}
        <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
        <g
          class="cstar cursor-pointer"
          class:newest={i === stars.length - 1}
          class:on={i === selected}
          transform={`translate(${star.x} ${star.y})`}
          style={`--fd:${(0.3 + i * Math.min(0.12, 1.5 / stars.length)).toFixed(2)}s`}
          onclick={(e) => onStar(e, i)}
        >
          <circle class="halo" r={r * 3.2} fill="var(--owl-star)" opacity=".14" />
          <circle class="core" {r} fill="var(--owl-star)" />
          <circle r="15" fill="transparent" />
        </g>
      {/each}
    </g>

    {#if shoot}
      {#key shoot.key}
        <g class="shoot">
          <line
            x1={shoot.x}
            y1={shoot.y}
            x2={shoot.x + 46}
            y2={shoot.y - 25}
            stroke="url(#{uid}-streak)"
            stroke-width="2"
            stroke-linecap="round"
          />
          <circle cx={shoot.x} cy={shoot.y} r="1.8" fill="#fff" />
        </g>
      {/key}
    {/if}

    <path
      d="M -220 470 L -220 442 Q -120 432 -20 442 Q 60 450 110 440 Q 180 430 250 440 Q 320 450 380 438 Q 480 430 580 440 L 580 470 Z"
      fill="var(--owl-ground)"
    />
    <path bind:this={twigEl} fill="none" stroke="#6b4a2f" stroke-width="4" stroke-linecap="round" />
    <path bind:this={branchEl} fill="none" stroke="#6b4a2f" stroke-width="10" stroke-linecap="round" />
    {#each LEAVES as [, angle, side], i (i)}
      <g bind:this={leafEls[i]}>
        <ellipse cx={side * 9} cy="-4" rx="10" ry="4.5" fill="#7aa35a" transform={`rotate(${angle})`} />
      </g>
    {/each}

    <g bind:this={owlEl} class="cursor-pointer">
      <g bind:this={wingLEl}>
        <path d="M -40 -98 C -66 -84 -68 -38 -48 -4 C -40 -30 -34 -66 -40 -98 Z" fill="#262a74" />
      </g>
      <g bind:this={wingREl}>
        <path d="M 40 -98 C 66 -84 68 -38 48 -4 C 40 -30 34 -66 40 -98 Z" fill="#262a74" />
      </g>
      <path
        class="rim"
        d="M -50 -24 C -58 -84 -38 -118 0 -118 C 38 -118 58 -84 50 -24 C 46 -2 26 4 0 4 C -26 4 -46 -2 -50 -24 Z"
        fill="#2f3389"
      />
      <ellipse cx="0" cy="-48" rx="33" ry="42" fill="#f3e7cd" />
      <path
        d="M -17 -74 q 5 5 10 0 M 7 -74 q 5 5 10 0 M -5 -62 q 5 5 10 0 M -21 -50 q 5 5 10 0 M 11 -50 q 5 5 10 0 M -5 -38 q 5 5 10 0 M -15 -26 q 5 5 10 0 M 5 -26 q 5 5 10 0"
        stroke="#d6c29a"
        stroke-width="2"
        fill="none"
        stroke-linecap="round"
      />
      <path
        d="M -20 -2 l -3 7 M -15 -2 l 0 8 M -10 -2 l 3 7 M 10 -2 l -3 7 M 15 -2 l 0 8 M 20 -2 l 3 7"
        stroke="#e59c34"
        stroke-width="3"
        stroke-linecap="round"
        fill="none"
      />
      <g bind:this={headEl}>
        <g bind:this={tuftsEl}>
          <path bind:this={tuftLEl} d="M -46 -156 L -38 -190 L -22 -168 Z" fill="#2f3389" />
          <path bind:this={tuftREl} d="M 46 -156 L 38 -190 L 22 -168 Z" fill="#2f3389" />
        </g>
        <ellipse class="rim" cx="0" cy="-138" rx="50" ry="40" fill="#2f3389" />
        <g opacity={showBack ? 0 : 1}>
          <circle cx="-19" cy="-138" r="15.5" fill="#fff" />
          <circle cx="19" cy="-138" r="15.5" fill="#fff" />
          <g bind:this={irisLEl}>
            <circle r="11" fill="#f4bd2a" />
            <g bind:this={pupLEl}>
              <circle r="6.5" fill="#1a1c45" />
              <circle cx="-2" cy="-2.5" r="2.2" fill="#fff" />
            </g>
          </g>
          <g bind:this={irisREl}>
            <circle r="11" fill="#f4bd2a" />
            <g bind:this={pupREl}>
              <circle r="6.5" fill="#1a1c45" />
              <circle cx="-2" cy="-2.5" r="2.2" fill="#fff" />
            </g>
          </g>
          <g clip-path="url(#{uid}-eyeL)">
            <rect bind:this={lidLEl} x="-36" y="-155" width="34" height="0" fill="#2f3389" />
          </g>
          <g clip-path="url(#{uid}-eyeR)">
            <rect bind:this={lidREl} x="2" y="-155" width="34" height="0" fill="#2f3389" />
          </g>
          <path bind:this={closedLEl} d="M -29 -137 q 10 7 20 0" stroke="#f3e7cd" stroke-width="2.2" fill="none" stroke-linecap="round" opacity="0" />
          <path bind:this={closedREl} d="M 9 -137 q 10 7 20 0" stroke="#f3e7cd" stroke-width="2.2" fill="none" stroke-linecap="round" opacity="0" />
          <path bind:this={browsEl} d="M -35 -161 L -9 -151 M 35 -161 L 9 -151" stroke="#15174a" stroke-width="5" stroke-linecap="round" opacity="0" />
          <path d="M -6 -125 L 6 -125 L 0 -112 Z" fill="#e59c34" />
        </g>
        <g opacity={showBack ? 1 : 0}>
          <path
            d="M -24 -150 q 6 6 12 0 M 12 -150 q 6 6 12 0 M -6 -134 q 6 6 12 0 M -24 -120 q 6 6 12 0 M 12 -120 q 6 6 12 0"
            stroke="#4a50b0"
            stroke-width="2"
            fill="none"
            stroke-linecap="round"
          />
        </g>
      </g>
      <g bind:this={bubbleEl} opacity="0">
        <rect x="44" y="-226" width={Math.max(30, bubbleText.length * 7.5 + 18)} height="26" rx="13" fill="#faf6ea" />
        <path d="M 52 -201 l -6 9 l 14 -9 Z" fill="#faf6ea" />
        <text
          x={44 + Math.max(30, bubbleText.length * 7.5 + 18) / 2}
          y="-208"
          text-anchor="middle"
          font-style="italic"
          font-weight="800"
          font-size="13"
          fill="#2f3389">{bubbleText}</text>
      </g>
      <g bind:this={zzEl} class="zz" opacity="0" font-weight="800" fill="var(--owl-text)">
        <text x="44" y="-180" font-size="12">z</text>
        <text x="50" y="-186" font-size="15">z</text>
        <text x="56" y="-192" font-size="18">z</text>
      </g>
    </g>
  </svg>

  <div class="pointer-events-none absolute left-4 top-4 text-left text-[var(--owl-text)]">
    <div class="text-[11px] font-bold tracking-wider">TODAY'S READING</div>
    <div class="text-xs">
      {reads.length === 0
        ? "Nothing read yet"
        : reads.length === 1
          ? "1 article"
          : `${reads.length} articles`}
    </div>
  </div>

  {#if note}
    <div
      class="absolute left-1/2 top-14 max-w-[86%] -translate-x-1/2 truncate rounded-full bg-[#faf6ea] px-4 py-1.5 text-sm font-bold text-[#1f2250] shadow"
    >
      {note}
    </div>
  {/if}

  {#if selectedRead && selectedStar && svg}
    {@const box = svg.getBoundingClientRect()}
    {@const scale = Math.min(box.width / 360, box.height / 470)}
    {@const left = (box.width - 360 * scale) / 2 + selectedStar.x * scale}
    {@const top = box.height - 470 * scale + selectedStar.y * scale}
    <div
      class="absolute z-10 w-60 rounded-2xl bg-[#faf6ea] p-3 text-left text-[#1f2250] shadow-lg"
      style={`left: ${Math.max(12, Math.min(box.width - 252, left - 120))}px; top: ${top + 14}px`}
    >
      <div class="truncate text-[11px] font-bold uppercase tracking-wide text-[#6e5f33]">
        {selectedRead.feed_name}
      </div>
      <div class="mb-1 mt-0.5 line-clamp-3 font-bold leading-snug">{selectedRead.title}</div>
      <div class="flex items-center justify-between gap-2 text-xs text-[#555880]">
        <span>Read at {timeFormat.format(new Date(selectedRead.read_at))}</span>
        <a
          class="rounded-full bg-[#2f3389] px-3 py-1 font-bold text-[#f6f0dc]"
          href={selectedRead.url}
          target="_blank"
          rel="noopener noreferrer">Open</a
        >
      </div>
    </div>
  {/if}
</div>

<style>
  .night-owl {
    --owl-sky-top: #080a22;
    --owl-sky-bottom: #1b2056;
    --owl-ground: #0f1236;
    --owl-star: #ffe7a6;
    --owl-text: #a9acd0;
    --owl-mini: #0a0c22;
    touch-action: pan-y;
  }
  .night-owl.day {
    --owl-sky-top: #bcd3ea;
    --owl-sky-bottom: #efe7d4;
    --owl-ground: #e2dbc6;
    --owl-star: #8b7845;
    --owl-text: #5d6085;
    --owl-mini: #2f3389;
  }
  .night-owl:not(.day) .rim {
    stroke: rgba(210, 215, 255, 0.16);
    stroke-width: 2;
  }
  .twinkle {
    animation: twinkle var(--dur, 4s) ease-in-out var(--d, 0s) infinite;
  }
  @keyframes twinkle {
    50% {
      opacity: 0.25;
    }
  }
  .cline {
    stroke-dasharray: var(--len);
    animation: draw 0.7s ease-out both;
    animation-delay: var(--fd, 0s);
  }
  @keyframes draw {
    from {
      stroke-dashoffset: var(--len);
    }
  }
  .cstar {
    animation: fadein 0.6s ease-out both;
    animation-delay: var(--fd, 0s);
  }
  @keyframes fadein {
    from {
      opacity: 0;
    }
  }
  .cstar .halo {
    transition: opacity 0.2s;
  }
  .cstar:hover .halo,
  .cstar.on .halo {
    opacity: 0.45;
  }
  .cstar.newest .core {
    animation: twinkle 2.6s ease-in-out infinite;
  }
  .zz text {
    animation: zz 3.2s ease-in infinite;
    opacity: 0;
  }
  .zz text:nth-child(2) {
    animation-delay: 1.05s;
  }
  .zz text:nth-child(3) {
    animation-delay: 2.1s;
  }
  @keyframes zz {
    0% {
      opacity: 0;
      transform: translate(0, 0);
    }
    20% {
      opacity: 0.9;
    }
    100% {
      opacity: 0;
      transform: translate(14px, -26px);
    }
  }
  .shoot {
    animation: shoot 1.25s ease-in forwards;
  }
  @keyframes shoot {
    from {
      transform: translate(0, 0);
      opacity: 0;
    }
    12% {
      opacity: 1;
    }
    to {
      transform: translate(-190px, 105px);
      opacity: 0;
    }
  }
  .reduced .twinkle,
  .reduced .cstar.newest .core,
  .reduced .zz text,
  .reduced .cline,
  .reduced .cstar {
    animation: none;
  }
  .reduced .zz text {
    opacity: 0.8;
  }
</style>
