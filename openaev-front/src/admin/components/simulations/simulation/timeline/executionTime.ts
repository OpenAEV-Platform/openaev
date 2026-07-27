import { useEffect, useState } from 'react';

// Time primitives shared by the live Execution screen (hero clock, timeline
// "now" cursor, board countdowns). The whole screen ticks on a single
// 1-second clock so every live figure stays consistent.

type TranslateFn = (key: string) => string;

/** Returns the current epoch (ms), refreshed every second while `active`. */
export const useNowTick = (active = true): number => {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) {
      return undefined;
    }
    const intervalId = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(intervalId);
  }, [active]);
  return now;
};

/** "02:14:36" (or "3d 02:14:36") wall-clock style elapsed time. */
export const formatClock = (totalSeconds: number): string => {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const days = Math.floor(seconds / 86400);
  const pad = (value: number) => String(value).padStart(2, '0');
  const clock = `${pad(Math.floor((seconds % 86400) / 3600))}:${pad(Math.floor((seconds % 3600) / 60))}:${pad(seconds % 60)}`;
  return days > 0 ? `${days}d ${clock}` : clock;
};

/**
 * Compact remaining-time text with translated unit letters: "42s", "3m 05s",
 * "1h 04m", "2d 3h". Only the two most significant units are rendered.
 */
export const formatRemaining = (t: TranslateFn, totalSeconds: number): string => {
  const seconds = Math.max(0, Math.round(totalSeconds));
  if (seconds < 60) {
    return `${seconds}${t('s')}`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}${t('m')} ${String(seconds % 60).padStart(2, '0')}${t('s')}`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}${t('h')} ${String(minutes % 60).padStart(2, '0')}${t('m')}`;
  }
  const days = Math.floor(hours / 24);
  return `${days}${t('d')} ${hours % 24}${t('h')}`;
};

/**
 * Compact planned-offset label for the timeline axis ("0m", "45m", "1h 30m",
 * "2d 4h"): zero-valued leading units are dropped so labels stay short.
 */
export const formatOffset = (t: TranslateFn, totalSeconds: number): string => {
  const seconds = Math.max(0, Math.round(totalSeconds));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) {
    return hours > 0 ? `${days}${t('d')} ${hours}${t('h')}` : `${days}${t('d')}`;
  }
  if (hours > 0) {
    return minutes > 0 ? `${hours}${t('h')} ${String(minutes).padStart(2, '0')}${t('m')}` : `${hours}${t('h')}`;
  }
  return `${minutes}${t('m')}`;
};
