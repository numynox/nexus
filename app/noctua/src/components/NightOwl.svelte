<!--
  The owl at the end of the feed, on a branch drawn straight onto the page's
  own background: by night under a few stars and the moon in its real phase,
  by day asleep in the sun. The pull past the end is the owl's — its eyes
  widen and its ear tufts pop up at the threshold; let go and it flies off to
  look for news.

  Unlike Android's, this one rarely gets to see its flight through: the feed
  page swaps the list for a spinner while it refreshes, and the owl hops back
  in with the new list instead.

  The Android app's EndOfFeed is the fuller version of this and its source of
  truth: the shapes are the same SVG paths, in the same 360 × 470 units
  (lib/nightSky.ts). Positions and motion are eased towards targets each
  frame, so a new target taken mid-way never jerks. Colours mix with the
  theme's own, so the scene works in light and dark themes alike.
-->
<script lang="ts">
  import { onMount } from "svelte";
  import {
    BRANCH_TIP_X,
    branchHighlight,
    branchPath,
    branchWidth,
    branchY,
    isNight,
    MOON,
    moonLit,
    moonLitPath,
    moonPhase,
    moonPhaseName,
    SKY_STARS,
  } from "../lib/nightSky";

  interface Props {
    /** How far the end is pulled: 1 at the refresh threshold, up to 1.5. */
    pull?: number;
    refreshing?: boolean;
    now?: Date;
  }

  let {
    pull = 0,
    refreshing = false,
    now = new Date(),
  }: Props = $props();

  const uid = $props.id();
  const night = $derived(isNight(now));
  const phase = $derived(moonPhase(now));

  const reducedQuery =
    typeof window !== "undefined"
      ? window.matchMedia("(prefers-reduced-motion: reduce)")
      : null;
  const reduced = reducedQuery?.matches ?? false;

  // ── The branch's leaves ──────────────────────────────────────────────────
  /** At `x` along the branch, on its lower side or not, pointing at `angle` degrees (0 right, 90 down). */
  interface Leaf {
    x: number;
    below: boolean;
    angle: number;
    size: number;
    dark: boolean;
  }
  const LEAVES: Leaf[] = [
    { x: 92, below: false, angle: -118, size: 0.9, dark: false },
    { x: 100, below: false, angle: -72, size: 0.8, dark: true },
    { x: 262, below: true, angle: 112, size: 0.8, dark: true },
    { x: 292, below: false, angle: -58, size: 0.9, dark: false },
    { x: 312, below: true, angle: 64, size: 0.85, dark: true },
    { x: BRANCH_TIP_X - 2, below: false, angle: -22, size: 0.9, dark: false },
  ];
  /** The twig hanging off to the left of the owl, and its three leaves. */
  const TWIG_X = 58;
  const TWIG_LEAVES = [
    { dx: -32, dy: 22, angle: 150, size: 1, dark: false },
    { dx: -32, dy: 22, angle: 196, size: 0.85, dark: true },
    { dx: -11, dy: 12.5, angle: 58, size: 0.75, dark: false },
  ];

  function leafAt(x: number, y: number, angle: number, size: number): string {
    return `translate(${x} ${y}) rotate(${angle}) scale(${size * 1.35})`;
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
  let highlightEl: SVGPathElement | undefined = $state();
  let twigEl: SVGPathElement | undefined = $state();
  let leafEls: SVGGElement[] = $state([]);
  let twigLeafEls: SVGGElement[] = $state([]);
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
    branchEl?.setAttribute("d", branchPath(bend));
    highlightEl?.setAttribute("d", branchHighlight(bend));
    const joinY = branchY(TWIG_X, bend);
    twigEl?.setAttribute(
      "d",
      `M ${TWIG_X + 4} ${joinY} Q ${TWIG_X - 8} ${joinY + 14} ${TWIG_X - 32} ${joinY + 22}`,
    );
    twigLeafEls.forEach((el, i) => {
      const l = TWIG_LEAVES[i];
      el?.setAttribute("transform", leafAt(TWIG_X + l.dx, joinY + l.dy, l.angle, l.size));
    });
    leafEls.forEach((el, i) => {
      const l = LEAVES[i];
      const half = branchWidth(l.x) / 2 - 1.5;
      const y = branchY(l.x, bend) + (l.below ? half : -half);
      el?.setAttribute("transform", leafAt(l.x, y, l.angle, l.size));
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
    if (Math.hypot(p.x - MOON.x, p.y - MOON.y) < 40) {
      if (!night) {
        say("The owl sleeps until dark");
        return;
      }
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

  const description = $derived(
    night
      ? "An owl on a branch under the moon and stars"
      : "An owl asleep on a branch in the sun",
  );
</script>

<div class="night-owl relative h-full" class:reduced>
  <!-- The owl's taps are play: the panel's Refresh button and the r key do the work. -->
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions -->
  <svg
    bind:this={svg}
    class="block h-full w-full select-none overflow-visible"
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
      <radialGradient id="{uid}-moonglow">
        <stop offset="0" stop-color="var(--owl-moon)" stop-opacity=".35" />
        <stop offset="1" stop-color="var(--owl-moon)" stop-opacity="0" />
      </radialGradient>
      <radialGradient id="{uid}-sunglow">
        <stop offset="0" stop-color="#f6c343" stop-opacity=".32" />
        <stop offset="1" stop-color="#f6c343" stop-opacity="0" />
      </radialGradient>
      <linearGradient id="{uid}-streak" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0" stop-color="var(--owl-star)" stop-opacity="1" />
        <stop offset="1" stop-color="var(--owl-star)" stop-opacity="0" />
      </linearGradient>
      <clipPath id="{uid}-eyeL"><circle cx="-19" cy="-138" r="16" /></clipPath>
      <clipPath id="{uid}-eyeR"><circle cx="19" cy="-138" r="16" /></clipPath>
      <path id="{uid}-leaf" d="M 0 0 C 4 -6 12 -7 18 0 C 12 7 4 6 0 0 Z" />
    </defs>

    {#if night}
      <g class="sky-in">
        {#each SKY_STARS as star, i (i)}
          <circle
            class="twinkle"
            cx={star.x}
            cy={star.y}
            r={star.r}
            fill="var(--owl-star)"
            style={`--d:${star.delay}s`}
          />
        {/each}
      </g>
    {/if}

    <g class="sky-in cursor-pointer">
      {#if night}
        <circle bind:this={glowEl} cx={MOON.x} cy={MOON.y} r="64" fill="url(#{uid}-moonglow)" />
        <circle cx={MOON.x} cy={MOON.y} r={MOON.r} fill="var(--owl-moon-shadow)" />
        <path d={moonLitPath(phase)} fill="var(--owl-moon)" />
      {:else}
        <circle cx={MOON.x} cy={MOON.y} r="66" fill="url(#{uid}-sunglow)" />
        <g class="rays" style={`transform-origin: ${MOON.x}px ${MOON.y}px`}>
          {#each Array.from({ length: 10 }, (_, i) => (i * Math.PI) / 5) as a (a)}
            <line
              x1={MOON.x + Math.cos(a) * 28}
              y1={MOON.y + Math.sin(a) * 28}
              x2={MOON.x + Math.cos(a) * 36}
              y2={MOON.y + Math.sin(a) * 36}
              stroke="#f6c343"
              stroke-opacity=".7"
              stroke-width="2.5"
              stroke-linecap="round"
            />
          {/each}
        </g>
        <circle cx={MOON.x} cy={MOON.y} r="21" fill="#f6c343" />
      {/if}
    </g>

    <g bind:this={miniEl} opacity="0">
      <ellipse rx="6.5" ry="5.5" fill="var(--owl-mini)" />
      <path
        bind:this={miniWingEl}
        d="M -2 -1 L -14 -7 L -6 2 Z M 2 -1 L 14 -7 L 6 2 Z"
        fill="var(--owl-mini)"
      />
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
          <circle cx={shoot.x} cy={shoot.y} r="1.8" fill="var(--owl-star)" />
        </g>
      {/key}
    {/if}

    <!-- The twig first, so the branch covers where it joins. -->
    <path bind:this={twigEl} fill="none" stroke="#6b4a2f" stroke-width="4.5" stroke-linecap="round" />
    <path bind:this={branchEl} fill="#6b4a2f" />
    <path bind:this={highlightEl} fill="none" stroke="#8a6446" stroke-opacity=".7" stroke-width="1.4" stroke-linecap="round" />
    {#each TWIG_LEAVES as leaf, i (i)}
      <g bind:this={twigLeafEls[i]}>{@render leafShape(leaf.dark)}</g>
    {/each}
    {#each LEAVES as leaf, i (i)}
      <g bind:this={leafEls[i]}>{@render leafShape(leaf.dark)}</g>
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

  {#if note}
    <div
      class="absolute left-1/2 top-4 max-w-[86%] -translate-x-1/2 truncate rounded-full bg-neutral px-4 py-1.5 text-sm font-bold text-neutral-content shadow"
    >
      {note}
    </div>
  {/if}
</div>

<!-- A leaf on a short stalk that starts inside the bark, pointing along +x. -->
{#snippet leafShape(dark: boolean)}
  <line x1="-3" y1="0" x2="2.5" y2="0" stroke="#5e7a3a" stroke-width="1.6" stroke-linecap="round" />
  <g transform="translate(2 0)">
    <use href="#{uid}-leaf" fill={dark ? "#557f3b" : "#6f9c4e"} />
    <path d="M 2 0 L 14 0" stroke="#e6f2d8" stroke-opacity=".4" stroke-width=".9" stroke-linecap="round" />
  </g>
{/snippet}

<style>
  /*
   * The scene sits on the page's own background, so its colours mix with the
   * theme's text colour: pale on a dark theme, deeper on a light one, where a
   * white moon or star would vanish.
   */
  .night-owl {
    --owl-star: color-mix(in oklab, #f2c94c 62%, var(--color-base-content));
    --owl-moon: color-mix(in oklab, #f2d27a 88%, var(--color-base-content));
    --owl-moon-shadow: color-mix(in oklab, var(--color-base-content) 7%, transparent);
    --owl-rim: color-mix(in oklab, var(--color-base-content) 16%, transparent);
    --owl-text: color-mix(in oklab, var(--color-base-content) 55%, transparent);
    --owl-mini: #1a1c45;
    touch-action: pan-y;
  }
  .rim {
    stroke: var(--owl-rim);
    stroke-width: 2;
  }
  .sky-in {
    animation: fadein 1.2s ease-out both;
  }
  @keyframes fadein {
    from {
      opacity: 0;
    }
  }
  .twinkle {
    opacity: 0.8;
    animation: twinkle 4.5s ease-in-out var(--d, 0s) infinite;
  }
  @keyframes twinkle {
    50% {
      opacity: 0.3;
    }
  }
  .rays {
    animation: turn 52s linear infinite;
  }
  @keyframes turn {
    to {
      transform: rotate(360deg);
    }
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
  .reduced .rays,
  .reduced .zz text,
  .reduced .sky-in {
    animation: none;
  }
  .reduced .zz text {
    opacity: 0.8;
  }
</style>
