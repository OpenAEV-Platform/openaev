// The user's own choices about older versions, kept in this browser: no API stores them yet.
// Unreadable storage (private window, blocked site data) falls back to the defaults: the banner
// shows and the dialog asks.

const KEPT_KEY = 'threat-arsenal:kept-older-versions';
const SKIP_DIALOG_KEY = 'threat-arsenal:skip-new-version-dialog';
// Enough for every card of a large arsenal; the oldest choices drop first.
const MAX_KEPT = 500;

const read = <T>(key: string, fallback: T): T => {
  try {
    const value = localStorage.getItem(key);
    return value ? (JSON.parse(value) as T) : fallback;
  } catch {
    return fallback;
  }
};

const write = (key: string, value: unknown) => {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage unavailable: the choice lasts until the page reloads.
  }
};

// A card's "Keep them on their version" holds for this exact situation: the version shown and how
// many injects run an older one. A new version, or more injects left behind, shows the banner again.
export const keptSignature = (actionId: string, olderInUse: number) => `${actionId}|${olderInUse}`;

export const isKeptOnOlderVersions = (signature: string) => read<string[]>(KEPT_KEY, []).includes(signature);

export const keepOnOlderVersions = (signature: string) => {
  const kept = read<string[]>(KEPT_KEY, []).filter(entry => entry !== signature);
  write(KEPT_KEY, [...kept, signature].slice(-MAX_KEPT));
};

// "Don't ask me after saving": the New version dialog stays closed; the Versions tab turns it back on.
export const skipsNewVersionDialog = () => read<boolean>(SKIP_DIALOG_KEY, false);

export const setSkipsNewVersionDialog = (skip: boolean) => write(SKIP_DIALOG_KEY, skip);
