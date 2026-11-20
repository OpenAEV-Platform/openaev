import { useSyncExternalStore } from 'react';

// The user's own choices about older versions, kept in this browser: no API stores them yet.
// Unreadable storage (private window, blocked site data) falls back to the defaults: the banner
// shows.

const KEPT_KEY = 'threat-arsenal:kept-older-versions';
// Enough for every action of a large arsenal; the oldest choices drop first.
const MAX_KEPT = 500;

const read = <T>(key: string, fallback: T): T => {
  try {
    const value = localStorage.getItem(key);
    return value ? (JSON.parse(value) as T) : fallback;
  } catch {
    return fallback;
  }
};

// Storage unavailable: the choice lasts until the page reloads.
const memory: Record<string, unknown> = {};

const write = (key: string, value: unknown) => {
  memory[key] = value;
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Kept in memory above.
  }
};

const readKept = () => read<string[]>(KEPT_KEY, (memory[KEPT_KEY] as string[] | undefined) ?? []);

// The cards and the Versions tab follow the same choice: changing it in the drawer updates the card
// behind it at once.
const listeners = new Set<() => void>();
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
const notify = () => listeners.forEach(listener => listener());

// "Keep them on their version" holds for the version shown now (its action id): the next version
// shows the card banner again.
export const isKeptOnOlderVersions = (latestActionId: string) => readKept().includes(latestActionId);

export const setKeptOnOlderVersions = (latestActionId: string, kept: boolean) => {
  const others = readKept().filter(entry => entry !== latestActionId);
  write(KEPT_KEY, (kept ? [...others, latestActionId] : others).slice(-MAX_KEPT));
  notify();
};

export const useKeptOnOlderVersions = (latestActionId: string | undefined) => useSyncExternalStore(
  subscribe,
  () => !!latestActionId && isKeptOnOlderVersions(latestActionId),
);
