/**
 * Keeps the screen on through the Screen Wake Lock, for the ticket at the
 * gate and for a live trip at the station or changing trains, where the
 * traveller glances at the platform and the countdown without touching the
 * phone. Android keeps the screen on the same way (`view.keepScreenOn`).
 *
 * The browser drops the lock whenever the page is hidden, so it is taken
 * again each time the page comes back into view. Without the API (an old
 * browser, an insecure origin) it does nothing. Returns the release.
 */
interface WakeLockSentinelLike {
  readonly released: boolean;
  release(): Promise<void>;
}

type WakeLockNavigator = Navigator & {
  wakeLock?: { request(type: "screen"): Promise<WakeLockSentinelLike> };
};

export function keepScreenOn(): () => void {
  const api = typeof navigator === "undefined" ? undefined : (navigator as WakeLockNavigator).wakeLock;
  if (!api || typeof document === "undefined") return () => {};

  let lock: WakeLockSentinelLike | null = null;
  let asking = false;
  let stopped = false;

  const request = () => {
    if (stopped || asking || document.visibilityState !== "visible" || (lock && !lock.released)) return;
    asking = true;
    api
      .request("screen")
      .then((sentinel) => {
        if (stopped) void sentinel.release().catch(() => {});
        else lock = sentinel;
      })
      // Refused (battery saver, no user activation yet): the next time the page shows, it is asked again.
      .catch(() => {})
      .finally(() => (asking = false));
  };

  document.addEventListener("visibilitychange", request);
  request();

  return () => {
    stopped = true;
    document.removeEventListener("visibilitychange", request);
    void lock?.release().catch(() => {});
    lock = null;
  };
}
