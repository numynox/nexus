<script lang="ts">
  /**
   * The scene (abeona-design.md §7): a landscape with a railway through it,
   * under the real sky of the moment. Large when nothing is urgent; on the
   * travel day a strip whose bar under the track is the whole journey, in a
   * frame that never moves: every ride and change, the one in focus taking
   * the room, the others stubs. The web's copy of Android's
   * ui/scene/Scene.kt, in the same units, from lib/scene.ts.
   *
   * The bar and the train move by the frame clock (lib/frameClock.ts), from
   * the expected times: continuously, not minute by minute. A time moved by
   * live data glides to its new place; when the focus moves, each part grows
   * or shrinks to its new width (the ends stay put) while the old train
   * drives out and the next one in. With less motion asked for, all of it is
   * simply set. A figure on the bar is a badge, so it never vanishes into it.
   */
  import { onMount, untrack } from "svelte";
  import { frameClock } from "../../lib/frameClock.svelte";
  import {
    BADGE,
    barLayout,
    type BarJourney,
    BAR_NODE,
    type BarTone,
    FRANKFURT,
    GLIDE_MS,
    journeyBar,
    type JourneyBar,
    MORPH_MS,
    RIDE_STUB,
    hillHeight,
    hillPath,
    LAND_SCALE,
    lightPosition,
    MAST_EVERY,
    moonLitPath,
    moonPhase,
    moonPhaseName,
    PARALLAX,
    BAR,
    BAR_DOT_END,
    BAR_UP,
    RAIL,
    RAIL_WITH_BAR,
    SCENE_HEIGHT,
    scenePalette,
    type SceneSize,
    type SceneState,
    skyLight,
    SLEEPER_EVERY,
    STARS,
    TILE,
    type TrainKind,
    trainLength,
    trainNoseX,
    TRAINS,
    TRAIN_SCALE,
    trainOnBar,
    travelSpeed,
    TREES,
  } from "../../lib/scene";

  interface Props {
    state: SceneState;
    size?: SceneSize;
    /** Which train the ride is (lib/scene.ts trainKind). */
    train?: TrainKind;
    /** The strip's bar: the journey's rides with their expected times, and where the traveller is in it. */
    journey?: BarJourney | null;
    /** The train's speed, when its Wi-Fi tells: the land passes faster. */
    speedKmh?: number | null;
    /** The moment the sky shows; the clock when left out. */
    at?: number;
    /** Where the sky is reckoned: the trip's current stop. */
    lat?: number | null;
    lon?: number | null;
    class?: string;
  }

  let {
    state: sceneState,
    size = "header",
    train: kind = "ice",
    journey = null,
    speedKmh = null,
    at,
    lat = null,
    lon = null,
    class: className = "",
  }: Props = $props();

  const uid = $props.id();
  const height = $derived(SCENE_HEIGHT[size]);
  let width = $state(360);
  let dark = $state(false);
  let clock = $state(Date.now());
  /** How far the land has passed, in units; and the clouds. */
  let travel = $state(0);
  let drift = $state(0);
  /** Seconds since the far train set off, or -1. */
  let farTrain = $state(-1);
  let flash = $state(false);
  let pulse = $state(false);

  const moment = $derived(at ?? clock);
  const sky = $derived(skyLight(moment, lat ?? FRANKFURT.lat, lon ?? FRANKFURT.lon));
  const colours = $derived(scenePalette(dark, sky.light));
  const rail = $derived(!!journey && journey.rides.length > 0 && size === "strip");
  const scale = $derived(TRAIN_SCALE[size]);
  const ground = $derived(rail ? RAIL_WITH_BAR : RAIL);
  const railY = $derived(height - ground);
  const barY = $derived(height - BAR_UP);
  const hasTrain = $derived(sceneState !== "idle" && !(sceneState === "stopped" && !rail));
  const atPlatform = $derived(!rail && (sceneState === "parked" || sceneState === "waiting" || sceneState === "arrived"));

  // ------------------------------------------------------------ the bar, frame by frame

  /** The bar as drawn: where each part lies, and the state it shows. */
  interface Drawn {
    at: { from: number; to: number }[];
    bar: JourneyBar;
  }

  const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
  const clamp01 = (v: number) => Math.max(0, Math.min(1, v));
  const easeOut = (t: number) => 1 - (1 - t) ** 3;
  /** The standard easing (Material's 0.2, 0, 0, 1), near enough. */
  const standard = (t: number) => (t < 0.5 ? 4 * t ** 3 : 1 - (-2 * t + 2) ** 3 / 2);
  const progressOf = (since: number, now: number, ms: number) => clamp01((now - since) / ms);

  /** Between two bars of the same journey: each part from its old stretch to its new one. Another journey is a cut. */
  function between(a: Drawn, b: Drawn, t: number): Drawn {
    if (a.at.length !== b.at.length) return b;
    return { at: b.at.map((r, i) => ({ from: lerp(a.at[i].from, r.from, t), to: lerp(a.at[i].to, r.to, t) })), bar: b.bar };
  }

  $effect(() => {
    if (rail) return frameClock.use();
  });
  const still = $derived(rail && frameClock.still);
  /** The moment of this frame: the shared frame clock while there is a bar. */
  const tick = $derived(rail ? frameClock.now : 0);
  const live = $derived(rail && journey ? journeyBar(journey, tick) : null);

  /** The focus moving: the bar as it was, and when the change came. */
  let morph = $state<{ from: Drawn; at: number } | null>(null);
  /** Live data moved a time: how far the shown position was from the new one, fading out. */
  let glide = $state<{ fraction: number; at: number } | null>(null);
  /** The next ride's train instead of this one: the old drives out, the new one in. */
  let swap = $state<{ kind: TrainKind; x: number; at: number } | null>(null);
  const SWAP_MS = 1200;

  const target = $derived.by((): Drawn | null => {
    if (!live) return null;
    let bar = live;
    if (glide && !still) bar = { ...bar, fraction: clamp01(bar.fraction + glide.fraction * (1 - easeOut(progressOf(glide.at, tick, GLIDE_MS)))) };
    return { at: barLayout(bar, width), bar };
  });
  const shown = $derived(target && morph && !still ? between(morph.from, target, standard(progressOf(morph.at, tick, MORPH_MS))) : target);

  /** How far the train has gone on the focused ride: nothing yet while waiting for it. */
  const fill = $derived(!shown || shown.bar.parts[shown.bar.focus ?? -1] !== "ride" || sceneState === "waiting" ? 0 : shown.bar.fraction);

  /**
   * Where the train's nose is: over the point of the focused ride it has
   * reached; while changing, at the start of the next ride, waiting; off
   * the bar a little past the middle, its cars running off to the left.
   */
  const restX = $derived.by(() => {
    if (!rail || !shown || shown.bar.focus === null) return trainNoseX(kind, scale, width);
    const { focus, parts } = shown.bar;
    const part = shown.at[focus];
    const x = parts[focus] === "ride" ? lerp(part.from, part.to, fill) : (shown.at[focus + 1]?.from ?? part.to);
    return trainOnBar(x, kind, scale);
  });
  /** The train as drawn: during a swap the old one on its way out, then the new one on its way in. */
  const drawnTrain = $derived.by((): { kind: TrainKind; x: number } => {
    if (!swap || still) return { kind, x: restX };
    const p = progressOf(swap.at, tick, SWAP_MS);
    const out = 0.45;
    if (p < out) {
      const gone = width + trainLength(swap.kind) * scale + 8;
      return { kind: swap.kind, x: lerp(swap.x, gone, (p / out) ** 2) };
    }
    return { kind, x: lerp(0, restX, easeOut((p - out) / (1 - out))) };
  });
  const trainX = $derived(drawnTrain.x);

  /** What the last frame drew, for the next change to start from. */
  let painted: { bar: Drawn | null; train: { kind: TrainKind; x: number } } = { bar: null, train: { kind: "ice", x: 0 } };
  $effect(() => {
    painted = { bar: shown, train: drawnTrain };
    if (morph && tick - morph.at > MORPH_MS) morph = null;
    if (glide && tick - glide.at > GLIDE_MS) glide = null;
    if (swap && tick - swap.at > SWAP_MS) swap = null;
  });

  /** A new journey: the same state with moved times glides; another state, or another ride, morphs. */
  let lastKey = "";
  let lastTimes = "";
  $effect.pre(() => {
    const j = journey;
    const k = kind;
    const key = j && size === "strip" ? `${j.state}|${j.current}|${j.rides.length}` : "";
    const times = j ? JSON.stringify(j.rides) : "";
    untrack(() => {
      const before = painted;
      const at = Date.now();
      if (key && lastKey && before.bar && !frameClock.still) {
        if (key !== lastKey) {
          morph = { from: before.bar, at };
          glide = null;
          const [, oldCurrent] = lastKey.split("|");
          if (oldCurrent !== String(j!.current) || before.train.kind !== k) swap = { kind: before.train.kind, x: before.train.x, at };
        } else if (times !== lastTimes) {
          glide = { fraction: before.bar.bar.fraction - journeyBar(j!, at).fraction, at };
        }
      }
      lastKey = key;
      lastTimes = times;
    });
  });

  function toneColour(tone: BarTone): string {
    return tone === "neutral" ? colours.rail : colours[tone];
  }
  const lightAt = $derived(lightPosition(sky.across, width, height));
  const lightR = $derived(size === "strip" ? 8 : size === "header" ? 11 : 16);
  const lit = $derived(sceneState !== "parked" && sceneState !== "idle");
  const land = $derived(LAND_SCALE[size]);
  /** The land grows from the rail, so the track stays where it is. */
  const landTransform = $derived(`translate(0 ${railY}) scale(${land}) translate(0 ${-railY})`);
  const tiles = $derived(Math.ceil(width / land / TILE) + 1);
  const mod = (v: number, m: number) => ((v % m) + m) % m;

  const lightLabel = $derived(sky.night ? moonPhaseName(moonPhase(moment)) : sky.glow > 0.5 ? (sky.across < 0.5 ? "Sunrise" : "Sunset") : "Daylight");

  function tapTrain() {
    flash = true;
    setTimeout(() => (flash = false), 700);
  }
  function tapLight() {
    pulse = true;
    setTimeout(() => (pulse = false), 700);
  }

  onMount(() => {
    const root = document.documentElement;
    const readTheme = () => (dark = getComputedStyle(root).colorScheme.includes("dark"));
    readTheme();
    const themes = new MutationObserver(readTheme);
    themes.observe(root, { attributes: true, attributeFilter: ["data-theme", "class"] });
    const tick = setInterval(() => (clock = Date.now()), 60_000);

    // Animations off in the system (prefers-reduced-motion): a still
    // picture, the clouds and the land where they are. Turned back on, it
    // moves again.
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");
    let frame = 0;
    let last = 0;
    let velocity = 0;
    let idleFor = 0;
    const step = (t: number) => {
      const dt = last ? Math.min(0.1, (t - last) / 1000) : 0;
      last = t;
      // The land eases into and out of motion with the train.
      const target = sceneState === "riding" ? travelSpeed(speedKmh) : 0;
      velocity += (target - velocity) * Math.min(1, dt * 1.5);
      if (velocity > 0.05) travel += velocity * dt;
      drift += dt * 2;
      if (sceneState === "idle") {
        if (farTrain >= 0) {
          farTrain += dt;
          if (farTrain > 10) farTrain = -1;
        } else {
          idleFor += dt;
          if (idleFor > 18) {
            idleFor = 0;
            farTrain = 0;
          }
        }
      } else farTrain = -1;
      frame = requestAnimationFrame(step);
    };
    const motion = () => {
      cancelAnimationFrame(frame);
      frame = 0;
      last = 0;
      if (!reduce.matches) frame = requestAnimationFrame(step);
    };
    motion();
    reduce.addEventListener("change", motion);
    return () => {
      themes.disconnect();
      clearInterval(tick);
      reduce.removeEventListener("change", motion);
      cancelAnimationFrame(frame);
    };
  });
</script>

{#snippet bogies(from: number, length: number)}
  <rect x={from + 4} y="-2.6" width="8" height="2.6" rx="1.3" fill={colours.rail} />
  <rect x={from + length - 12} y="-2.6" width="8" height="2.6" rx="1.3" fill={colours.rail} />
{/snippet}

{#snippet car(k: TrainKind, x: number, length: number)}
  <!-- A car behind the leading one: its right end at x. -->
  {@const from = x - length}
  {@render bogies(from, length)}
  {#if k === "ice"}
    <rect x={from} y="-13" width={length} height="11" rx="1.6" fill={colours.trainBody} stroke={colours.trainLine} stroke-width="0.5" />
    <rect x={from + 2.5} y="-11.2" width={length - 5} height="3.4" rx="1.2" fill={colours.trainWindow} opacity={lit ? 1 : 0.55} />
    <rect x={from} y="-6.6" width={length} height="1.1" fill={colours.trainRed} />
  {:else if k === "regional"}
    <rect x={from} y="-17" width={length} height="15" rx="1.8" fill={colours.trainRed} />
    <rect x={from + 3} y="-15.2" width={length - 6} height="3" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <rect x={from + 7} y="-8.6" width={length - 14} height="3" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <rect x={from + 2.2} y="-10.5" width="3" height="8" rx="0.8" fill={colours.trainBody} />
    <rect x={from + length - 5.2} y="-10.5" width="3" height="8" rx="0.8" fill={colours.trainBody} />
  {:else}
    <rect x={from} y="-13" width={length} height="11" rx="1.6" fill={colours.trainRed} />
    <rect x={from + 2} y="-11" width={length - 4} height="4" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    {#each [0.14, 0.5, 0.86] as door, d (d)}
      <rect x={from + length * door - 1.8} y="-11.6" width="3.6" height="9.2" rx="0.8" fill={colours.trainBody} />
    {/each}
  {/if}
{/snippet}

{#snippet endCar(k: TrainKind)}
  <!-- An end car with its nose at x = 0; the last car is the same, mirrored. -->
  {#if k === "ice"}
    {@render bogies(-44, 40)}
    <path d="M-44 -13 H-15 C-8 -13 -3 -8.5 0 -4.5 C0.6 -3.2 -0.4 -2 -2 -2 H-44 Z" fill={colours.trainBody} stroke={colours.trainLine} stroke-width="0.5" />
    <rect x="-41.5" y="-11.2" width="26" height="3.4" rx="1.2" fill={colours.trainWindow} opacity={lit ? 1 : 0.55} />
    <path d="M-13.5 -11.4 H-11.5 C-8.2 -10.9 -5.6 -9.2 -4.1 -7.6 H-12.5 C-13.1 -7.6 -13.5 -8 -13.5 -8.6 Z" fill={colours.trainWindow} opacity={lit ? 1 : 0.55} />
    <path d="M-44 -6.6 H-2.4 L-1.6 -5.5 H-44 Z" fill={colours.trainRed} />
  {:else if k === "regional"}
    {@render bogies(-38, 36)}
    <path d="M-38 -17 H-5.5 C-3.4 -17 -1.9 -15.6 -1.5 -13.4 L0 -4.4 C0.2 -3 -0.8 -2 -2.2 -2 H-38 Z" fill={colours.trainRed} />
    <rect x="-35" y="-15.2" width="25" height="3" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <rect x="-31" y="-8.6" width="17" height="3" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <rect x="-35.8" y="-10.5" width="3" height="8" rx="0.8" fill={colours.trainBody} />
    <path d="M-7.5 -14.6 H-4.6 C-3.9 -14.6 -3.4 -14.1 -3.3 -13.4 L-2.6 -9 H-7.5 Z" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <rect x="-12" y="-10.5" width="3" height="8" rx="0.8" fill={colours.trainBody} />
  {:else if k === "suburban"}
    {@render bogies(-31, 29)}
    <path d="M-31 -13 H-4.5 C-2.8 -13 -1.6 -11.9 -1.3 -10.2 L0 -4.2 C0.2 -3 -0.8 -2 -2.2 -2 H-31 Z" fill={colours.trainRed} />
    <rect x="-29" y="-11" width="21" height="4" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    {#each [-25.5, -15] as door, d (d)}
      <rect x={door} y="-11.6" width="3.6" height="9.2" rx="0.8" fill={colours.trainBody} />
    {/each}
    <path d="M-6.4 -11 H-4 C-3.4 -11 -2.9 -10.6 -2.8 -10 L-2.2 -7 H-6.4 Z" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
  {:else}
    <!-- A bus: one body, two wheels. -->
    <rect x="-30" y="-12" width="30" height="10.4" rx="2.2" fill={colours.trainBody} stroke={colours.trainLine} stroke-width="0.5" />
    <rect x="-30" y="-5.4" width="30" height="3.8" rx="1.6" fill={colours.trainRed} />
    <rect x="-27.5" y="-10.4" width="20" height="3.8" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <rect x="-6.2" y="-10.4" width="4.6" height="5.4" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
    <circle cx="-23" cy="-1.6" r="2.1" fill={colours.rail} />
    <circle cx="-7" cy="-1.6" r="2.1" fill={colours.rail} />
  {/if}
{/snippet}

{#snippet train(k: TrainKind, x: number, s: number)}
  <!-- Drawn at scale 1 with its wheels on y = 0, its nose at x = 0, the cars trailing to the left. -->
  {@const spec = TRAINS[k]}
  <g transform="translate({x} {railY}) scale({s})">
    {#each { length: Math.max(0, spec.cars - 2) } as _, c (c)}
      {@render car(k, -spec.head - spec.gap - c * (spec.car + spec.gap), spec.car)}
    {/each}
    {#if spec.cars > 1}
      <g transform="translate({-trainLength(k)} 0) scale(-1 1)">
        {@render endCar(k)}
        {#if lit}<circle cx="-1.8" cy="-4.2" r="0.9" fill="#ff6a5a" />{/if}
      </g>
    {/if}
    {@render endCar(k)}
    {#if lit}
      <circle cx="-1.8" cy="-4.2" r={flash ? 2.4 : 0.9} fill="#ffe3a6" class="transition-all duration-200 motion-reduce:transition-none" />
    {/if}
    <!-- The whole train is the thing to tap. -->
    <rect x={-trainLength(k)} y={-spec.height} width={trainLength(k)} height={spec.height} fill="transparent" onpointerdown={tapTrain} role="presentation" />
  </g>
{/snippet}

{#snippet badge(x: number, colour: string)}
  <!--
    The walker on the bar, as a badge so it never vanishes into the line under it: a disc of the card's
    surface, a ring and the figure in the colour of the part it is on, lifted by a soft shadow.
    Material's directions_walk, as on Android.
  -->
  <circle cx={x} cy={barY + 0.8} r={BADGE / 2 + 0.6} fill="#000000" opacity={dark ? 0.45 : 0.16} />
  <circle cx={x} cy={barY} r={BADGE / 2 - 1} fill={colours.dot} stroke={colour} stroke-width="2" />
  <path
    transform="translate({x - 5.4} {barY - 5.5}) scale(0.45)"
    d="M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9L7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7"
    fill={colour}
  />
{/snippet}

{#snippet node(x: number, passed: boolean)}
  <circle cx={x} cy={barY} r={BAR_NODE} fill={passed ? colours.accent : colours.dot} stroke={passed ? colours.accent : colours.rail} stroke-width="1.5" />
{/snippet}

<div class="relative w-full overflow-hidden {className}" style="height: {height}px" bind:clientWidth={width} aria-hidden={rail ? undefined : "true"}>
  <svg viewBox="0 0 {width} {height}" {width} {height} class="block" role={rail ? "img" : undefined} aria-label={!rail || !live || live.focus === null ? undefined : live.parts[live.focus] === "ride" ? `${Math.round(live.fraction * 100)} % of the ride done` : "Changing trains"}>
    <defs>
      <linearGradient id="{uid}-sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color={colours.sky} stop-opacity="0" />
        <stop offset="0.55" stop-color={colours.sky} stop-opacity="0.85" />
        <stop offset="1" stop-color={colours.sky} />
      </linearGradient>
      <linearGradient id="{uid}-glow" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0.3" stop-color={colours.glow} stop-opacity="0" />
        <stop offset="1" stop-color={colours.glow} stop-opacity={0.85 * sky.glow} />
      </linearGradient>
    </defs>

    <rect width={width} height={height} fill="url(#{uid}-sky)" />
    {#if sky.glow > 0}<rect width={width} height={railY} fill="url(#{uid}-glow)" />{/if}

    {#if sky.light < 0.6}
      <g fill={colours.star} opacity={1 - sky.light / 0.6}>
        {#each STARS as star, i (i)}
          <circle cx={star.x * width} cy={star.y * (height - 30)} r={star.r} />
        {/each}
      </g>
    {/if}

    <!-- The sun by day, the moon in its phase by night. -->
    <g class="origin-center transition-transform duration-300 motion-reduce:transition-none {pulse ? 'scale-125' : ''}" style="transform-box: fill-box" onpointerdown={tapLight} role="img" aria-label={lightLabel}>
      {#if sky.night}
        <circle cx={lightAt.x} cy={height - lightAt.up} r={lightR} fill={colours.moon} opacity="0.22" />
        <path d={moonLitPath(moonPhase(moment), lightAt.x, height - lightAt.up, lightR)} fill={colours.moon} />
      {:else}
        <circle cx={lightAt.x} cy={height - lightAt.up} r={lightR * 1.5} fill={colours.sun} opacity="0.2" />
        <circle cx={lightAt.x} cy={height - lightAt.up} r={lightR} fill={colours.sun} />
      {/if}
    </g>

    <!-- Clouds always drift. -->
    <g fill={colours.cloud} opacity="0.9">
      {#each [{ x: 60, up: 16, s: 1 }, { x: 250, up: 9, s: 0.75 }, { x: 430, up: 20, s: 0.9 }] as cloud, i (i)}
        {@const span = width + 120}
        <g transform="translate({mod(cloud.x + drift * (0.6 + i * 0.2), span) - 60} {Math.max(6, height - 30 - cloud.up * (height / 56))}) scale({cloud.s * (size === 'full' ? 1.6 : 1)})">
          <rect x="0" y="4" width="34" height="8" rx="4" />
          <circle cx="11" cy="5" r="6" />
          <circle cx="21" cy="3" r="7.5" />
        </g>
      {/each}
    </g>

    <!-- Far hills, and now and then a small train on them when nothing is planned. -->
    <g transform="{landTransform} translate({-mod(travel * PARALLAX.far, TILE)} 0)">
      {#each { length: tiles } as _, t (t)}
        <path d={hillPath("far", TILE, height)} transform="translate({t * TILE} 0)" fill={colours.far} />
      {/each}
    </g>
    {#if farTrain >= 0}
      {@const x = -40 + (width / land + 80) * (farTrain / 10)}
      <g transform="{landTransform} translate({x} {height - hillHeight('far', x + mod(travel * PARALLAX.far, TILE)) + 1})" opacity="0.8">
        <rect x="0" y="-4" width="24" height="4" rx="2" fill={colours.trainBody} />
        <rect x="0" y="-2.2" width="24" height="0.7" fill={colours.trainRed} />
      </g>
    {/if}

    <!-- Near hills with their trees. -->
    <g transform="{landTransform} translate({-mod(travel * PARALLAX.near, TILE)} 0)">
      {#each { length: tiles } as _, t (t)}
        <g transform="translate({t * TILE} 0)">
          <path d={hillPath("near", TILE, height)} fill={colours.near} />
          {#each TREES as tree, i (i)}
            {@const base = height - hillHeight("near", tree.x) + 1}
            <rect x={tree.x - 0.6} y={base - tree.r * 1.6} width="1.2" height={tree.r * 1.6} fill={colours.tree} />
            <circle cx={tree.x} cy={base - tree.r * 2} r={tree.r} fill={colours.tree} />
          {/each}
        </g>
      {/each}
    </g>

    <!-- The ground, the masts and their wire, the sleepers. -->
    <rect x="0" y={railY} width={width} height={ground} fill={colours.ground} />
    <g transform="translate({-mod(travel * PARALLAX.track, MAST_EVERY)} 0)" stroke={colours.mast} stroke-width="1" fill="none" opacity="0.7">
      {#each { length: Math.ceil(width / MAST_EVERY) + 2 } as _, m (m)}
        <path d="M{m * MAST_EVERY + 20} {railY} v-{26 * Math.min(scale, 1.6)} h7" />
      {/each}
    </g>
    <line x1="0" x2={width} y1={railY - 24 * Math.min(scale, 1.6)} y2={railY - 24 * Math.min(scale, 1.6)} stroke={colours.mast} stroke-width="0.6" opacity="0.6" />
    <g transform="translate({-mod(travel * PARALLAX.track, SLEEPER_EVERY)} 0)" fill={colours.rail} opacity={rail ? 0 : 0.3}>
      {#each { length: Math.ceil(width / SLEEPER_EVERY) + 2 } as _, s (s)}
        <rect x={s * SLEEPER_EVERY} y={railY + 3} width="6" height="2" rx="1" />
      {/each}
    </g>

    {#if atPlatform}
      <!-- The platform under the train, its shelter and lamp. -->
      {@const from = -4}
      {@const to = trainX + 52}
      <rect x={from} y={railY - 3} width={to - from} height="5" rx="1.5" fill={colours.platform} />
      <g stroke={colours.mast} stroke-width="1.4" stroke-linecap="round" fill="none">
        <path d="M{to - 30} {railY - 3} v-{13 * scale} M{to - 12} {railY - 3} v-{13 * scale}" />
        <path d="M{to - 36} {railY - 3 - 13 * scale} h30" stroke-width="2.4" />
      </g>
      {#if sceneState === "arrived"}
        <path d="M{to - 44} {railY - 3} v-{20 * scale}" stroke={colours.mast} stroke-width="1.2" />
        <path d="M{to - 44} {railY - 3 - 20 * scale} l{9 * scale} {3 * scale} l-{9 * scale} {3 * scale} Z" fill={colours.accent} />
      {/if}
    {/if}

    {#if sceneState === "idle" || sceneState === "stopped"}
      <!-- The signal: green when the line is clear, red when nothing runs. -->
      {@const x = rail ? width - 14 : width * 0.72}
      <path d="M{x} {railY} v-{17 * scale}" stroke={colours.mast} stroke-width="1.4" />
      <rect x={x - 3 * scale} y={railY - 26 * scale} width={6 * scale} height={11 * scale} rx={3 * scale} fill={colours.rail} />
      <circle cx={x} cy={railY - (sceneState === "stopped" ? 23 : 18) * scale} r={1.9 * scale} fill={sceneState === "stopped" ? "#ff5a4a" : "#57d39a"} />
    {/if}

    <line x1="0" x2={width} y1={railY} y2={railY} stroke={colours.rail} stroke-width="2" opacity="0.85" />
    {#if rail && shown}
      {@const bar = shown.bar}
      {@const last = shown.at.length - 1}
      <!-- The journey under the track, its ends where they always are: rides solid, changes dashed; done in burgundy, ahead in the track's colour. -->
      {#each shown.at as part, i (i)}
        {@const focused = i === bar.focus}
        {@const done = i < bar.done}
        {#if bar.parts[i] === "ride"}
          <!-- A ride: thick while in focus (its stops on it, filled up to the train), a thinner stub otherwise. -->
          {@const grow = clamp01((part.to - part.from - RIDE_STUB) / 40)}
          {@const thick = lerp(4, BAR, grow)}
          {#if done}
            <line x1={part.from} x2={part.to} y1={barY} y2={barY} stroke={colours.accent} stroke-width={thick} />
          {:else}
            <line x1={part.from} x2={part.to} y1={barY} y2={barY} stroke={colours.rail} stroke-width={thick} opacity="0.3" />
            {#if focused && fill > 0}
              <line x1={part.from} x2={lerp(part.from, part.to, fill)} y1={barY} y2={barY} stroke={colours.accent} stroke-width={thick} />
            {/if}
          {/if}
          {#if focused}
            {#each bar.stops.slice(1, -1) as stop, k (k)}
              <g opacity={grow}>{@render node(lerp(part.from, part.to, stop), fill > 0 && stop <= fill)}</g>
            {/each}
          {/if}
        {:else if focused && bar.tone}
          <!-- The change in focus: its line in the change's confidence colour. -->
          <line x1={part.from} x2={part.to} y1={barY} y2={barY} stroke={toneColour(bar.tone)} stroke-width="3" stroke-dasharray="5 4" />
        {:else}
          <line x1={part.from} x2={part.to} y1={barY} y2={barY} stroke={done ? colours.accent : colours.rail} stroke-width="2.5" stroke-dasharray="3 3" opacity={done ? 1 : 0.6} />
        {/if}
      {/each}
      <!-- Every ride's ends, filled once passed; at the far right the destination's ring, and only there. -->
      {#each shown.at as part, i (i)}
        {#if bar.parts[i] === "ride"}
          {@render node(part.from, i < bar.done || (i === bar.focus && fill > 0))}
          {#if i === last}
            <circle cx={part.to} cy={barY} r={BAR_DOT_END} fill={colours.dot} stroke={colours.accent} stroke-width="2" />
          {:else}
            {@render node(part.to, i < bar.done || (i === bar.focus && fill >= 1))}
          {/if}
        {/if}
      {/each}
      {#if bar.focus !== null && bar.parts[bar.focus] === "change"}
        <!-- On the change: the walker, from the arrival to the next departure as the change time passes. -->
        {@render badge(lerp(shown.at[bar.focus].from, shown.at[bar.focus].to, bar.fraction), toneColour(bar.tone ?? "neutral"))}
      {:else if bar.walkerAtStart}
        <!-- Before leaving: the walker waits on the origin. -->
        {@render badge(shown.at[0].from, toneColour("neutral"))}
      {/if}
    {/if}

    {#if hasTrain && !(rail && sceneState === "stopped")}
      {@render train(drawnTrain.kind, trainX, scale)}
    {/if}
  </svg>
</div>
