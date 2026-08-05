import { useCallback, useState } from 'react';

// Default / minimum width (px) of the always-open autonomous reasoning panel. The panel is
// user-resizable (drag its left edge); the live width is owned here so the same value drives BOTH
// the panel width and the scenario/simulation content's right padding, keeping them in lockstep.
export const AUTONOMOUS_PANEL_WIDTH = 460;
const AUTONOMOUS_PANEL_MIN_WIDTH = 360;
const AUTONOMOUS_PANEL_STORAGE_KEY = 'autonomousReasoningPanelWidth';

// The panel may grow by dragging its left edge, up to a third of the viewport - wide enough to read
// long tool payloads, never so wide it swallows the cockpit it sits beside.
export const clampAutonomousPanelWidth = (
  width: number,
  viewportWidth: number = window.innerWidth,
): number => {
  const max = Math.max(AUTONOMOUS_PANEL_MIN_WIDTH, Math.floor(viewportWidth / 3));
  return Math.min(Math.max(width, AUTONOMOUS_PANEL_MIN_WIDTH), max);
};

/**
 * Owns the resizable panel width (persisted in localStorage, clamped to [min, 1/3 viewport]). Lives
 * in the parent (scenario / simulation Index) so the same value drives both the panel width and the
 * content's right padding, and the two stay in lockstep while dragging.
 */
const useAutonomousPanelWidth = (): [number, (width: number) => void] => {
  const [width, setWidthState] = useState<number>(() => {
    const stored = parseInt(localStorage.getItem(AUTONOMOUS_PANEL_STORAGE_KEY) ?? '', 10);
    return clampAutonomousPanelWidth(Number.isNaN(stored) ? AUTONOMOUS_PANEL_WIDTH : stored);
  });
  const setWidth = useCallback((next: number) => {
    const clamped = clampAutonomousPanelWidth(next);
    setWidthState(clamped);
    localStorage.setItem(AUTONOMOUS_PANEL_STORAGE_KEY, String(clamped));
  }, []);
  return [width, setWidth];
};

export default useAutonomousPanelWidth;
