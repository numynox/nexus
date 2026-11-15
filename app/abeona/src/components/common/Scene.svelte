<script lang="ts">
  /**
   * The scene (abeona-design.md §7): a landscape with a railway through it,
   * under the real sky of the moment. Large when nothing is urgent; on the
   * travel day a strip whose track is the ride's rail. The web's copy of
   * Android's ui/scene/Scene.kt, in the same units, from lib/scene.ts.
   */
  import { onMount } from "svelte";
  import {
    FRANKFURT,
    hillHeight,
    hillPath,
    LAND_SCALE,
    lightPosition,
    MAST_EVERY,
    moonLitPath,
    moonPhase,
    moonPhaseName,
    PARALLAX,
    RAIL,
    railX,
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
    TRAINS,
    TRAIN_SCALE,
    travelSpeed,
    TREES,
  } from "../../lib/scene";

  interface Props {
    state: SceneState;
    size?: SceneSize;
    /** The strip as the ride's rail: every stop as a fraction of the ride, and where the train is. */
    /** Which train the ride is (lib/scene.ts trainKind). */
    train?: TrainKind;
    stops?: { fraction: number; name?: string }[] | null;
    fraction?: number;
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
    stops = null,
    fraction = 0,
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
  const rail = $derived(stops != null && stops.length > 1 && size === "strip");
  const scale = $derived(TRAIN_SCALE[size]);
  const railY = $derived(height - RAIL);
  const hasTrain = $derived(sceneState !== "idle" && !(sceneState === "stopped" && !rail));
  const atPlatform = $derived(!rail && (sceneState === "parked" || sceneState === "waiting" || sceneState === "arrived"));
  /** Where the train's nose is: on the rail where the ride is, else a little past the middle, its cars running off to the left. */
  const trainX = $derived(rail ? railX(fraction, width) : Math.min(width - 60, Math.max(width * 0.56, 90)));
  const spec = $derived(TRAINS[kind]);
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

    const still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
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
    if (!still) frame = requestAnimationFrame(step);
    return () => {
      themes.disconnect();
      clearInterval(tick);
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

{#snippet train(x: number, s: number)}
  <!-- Drawn at scale 1 with its wheels on y = 0, its nose at x = 0, the cars trailing to the left. -->
  <g transform="translate({x} {railY}) scale({s})">
    {#each { length: spec.cars - 1 } as _, c (c)}
      {@render car(kind, -spec.head - spec.gap - c * (spec.car + spec.gap), spec.car)}
    {/each}
    {#if kind === "ice"}
      {@render bogies(-44, 40)}
      <path d="M-44 -13 H-15 C-8 -13 -3 -8.5 0 -4.5 C0.6 -3.2 -0.4 -2 -2 -2 H-44 Z" fill={colours.trainBody} stroke={colours.trainLine} stroke-width="0.5" />
      <rect x="-41.5" y="-11.2" width="26" height="3.4" rx="1.2" fill={colours.trainWindow} opacity={lit ? 1 : 0.55} />
      <path d="M-13.5 -11.4 H-11.5 C-8.2 -10.9 -5.6 -9.2 -4.1 -7.6 H-12.5 C-13.1 -7.6 -13.5 -8 -13.5 -8.6 Z" fill={colours.trainWindow} opacity={lit ? 1 : 0.55} />
      <path d="M-44 -6.6 H-2.4 L-1.6 -5.5 H-44 Z" fill={colours.trainRed} />
    {:else if kind === "regional"}
      {@render bogies(-38, 36)}
      <path d="M-38 -17 H-5.5 C-3.4 -17 -1.9 -15.6 -1.5 -13.4 L0 -4.4 C0.2 -3 -0.8 -2 -2.2 -2 H-38 Z" fill={colours.trainRed} />
      <rect x="-35" y="-15.2" width="25" height="3" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
      <rect x="-31" y="-8.6" width="17" height="3" rx="1" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
      <rect x="-35.8" y="-10.5" width="3" height="8" rx="0.8" fill={colours.trainBody} />
      <path d="M-7.5 -14.6 H-4.6 C-3.9 -14.6 -3.4 -14.1 -3.3 -13.4 L-2.6 -9 H-7.5 Z" fill={colours.trainWindow} opacity={lit ? 1 : 0.6} />
      <rect x="-12" y="-10.5" width="3" height="8" rx="0.8" fill={colours.trainBody} />
    {:else if kind === "suburban"}
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
    {#if lit}
      <circle cx="-1.8" cy="-4.2" r={flash ? 2.4 : 0.9} fill="#ffe3a6" class="transition-all duration-200" />
    {/if}
    <!-- The whole train is the thing to tap. -->
    <rect x={-trainLength(kind)} y={-spec.height} width={trainLength(kind)} height={spec.height} fill="transparent" onpointerdown={tapTrain} role="presentation" />
  </g>
{/snippet}

<div class="relative w-full overflow-hidden {className}" style="height: {height}px" bind:clientWidth={width} aria-hidden={rail ? undefined : "true"}>
  <svg viewBox="0 0 {width} {height}" {width} {height} class="block" role={rail ? "img" : undefined} aria-label={rail ? `${Math.round(fraction * 100)} % of the ride done` : undefined}>
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
    {#if sky.glow > 0}<rect width={width} height={height - RAIL} fill="url(#{uid}-glow)" />{/if}

    {#if sky.light < 0.6}
      <g fill={colours.star} opacity={1 - sky.light / 0.6}>
        {#each STARS as star, i (i)}
          <circle cx={star.x * width} cy={star.y * (height - 30)} r={star.r} />
        {/each}
      </g>
    {/if}

    <!-- The sun by day, the moon in its phase by night. -->
    <g class="origin-center transition-transform duration-300 {pulse ? 'scale-125' : ''}" style="transform-box: fill-box" onpointerdown={tapLight} role="img" aria-label={lightLabel}>
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
    <rect x="0" y={railY} width={width} height={RAIL} fill={colours.ground} />
    <g transform="translate({-mod(travel * PARALLAX.track, MAST_EVERY)} 0)" stroke={colours.mast} stroke-width="1" fill="none" opacity="0.7">
      {#each { length: Math.ceil(width / MAST_EVERY) + 2 } as _, m (m)}
        <path d="M{m * MAST_EVERY + 20} {railY} v-{26 * Math.min(scale, 1.6)} h7" />
      {/each}
    </g>
    <line x1="0" x2={width} y1={railY - 24 * Math.min(scale, 1.6)} y2={railY - 24 * Math.min(scale, 1.6)} stroke={colours.mast} stroke-width="0.6" opacity="0.6" />
    <g transform="translate({-mod(travel * PARALLAX.track, SLEEPER_EVERY)} 0)" fill={colours.rail} opacity="0.3">
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

    <!-- The rail: on the travel day it carries the ride's stops. -->
    <line x1="0" x2={width} y1={railY} y2={railY} stroke={colours.rail} stroke-width="2" opacity={rail ? 0.35 : 0.85} />
    {#if rail && stops}
      <line x1={railX(0, width)} x2={railX(1, width)} y1={railY} y2={railY} stroke={colours.rail} stroke-width="3" stroke-linecap="round" opacity="0.55" />
      {#if fraction > 0}
        <line x1={railX(0, width)} x2={railX(fraction, width)} y1={railY} y2={railY} stroke={colours.accent} stroke-width="3" stroke-linecap="round" />
      {/if}
      {#each stops as stop, i (i)}
        {@const end = i === 0 || i === stops.length - 1}
        {@const passed = sceneState !== "waiting" && stop.fraction <= fraction}
        <circle
          cx={railX(stop.fraction, width)}
          cy={railY}
          r={end ? 4.5 : 3.2}
          fill={passed ? colours.accent : colours.dot}
          stroke={passed ? colours.accent : end ? colours.accent : colours.rail}
          stroke-width="1.6"
        >
          {#if stop.name}<title>{stop.name}</title>{/if}
        </circle>
      {/each}
    {/if}

    {#if hasTrain && !(rail && sceneState === "stopped")}
      {@render train(trainX, scale)}
    {/if}
  </svg>
</div>
