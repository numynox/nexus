/**
 * The clock the live companion moves by: the time on every animation frame,
 * one clock for the scene's bar and the card's rail, so the train in the
 * header and the marker on the rail agree (abeona-design.md §7, §8). Only
 * what reads it updates on a frame, never the whole card.
 *
 * It runs while something on screen uses it (`use()` from an effect). When
 * the system asks for less motion it ticks once a minute instead, so the
 * picture stands still between the minutes.
 */
let now = $state(Date.now());
let still = $state(false);
let users = 0;
let stop: (() => void) | null = null;

function start(): () => void {
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");
  let frame = 0;
  let timer: ReturnType<typeof setInterval> | undefined;
  const step = () => {
    now = Date.now();
    frame = requestAnimationFrame(step);
  };
  const run = () => {
    cancelAnimationFrame(frame);
    clearInterval(timer);
    still = reduce.matches;
    now = Date.now();
    if (still) timer = setInterval(() => (now = Date.now()), 60_000);
    else frame = requestAnimationFrame(step);
  };
  run();
  reduce.addEventListener("change", run);
  return () => {
    cancelAnimationFrame(frame);
    clearInterval(timer);
    reduce.removeEventListener("change", run);
  };
}

export const frameClock = {
  /** The time (ms) of the current frame. */
  get now(): number {
    return now;
  },
  /** Less motion asked for: nothing glides or morphs, positions are set. */
  get still(): boolean {
    return still;
  },
  /** Keeps the clock running until the returned function is called: return it from an effect. */
  use(): () => void {
    if (users++ === 0) stop = start();
    let done = false;
    return () => {
      if (done) return;
      done = true;
      if (--users === 0) {
        stop?.();
        stop = null;
      }
    };
  },
};
