import { type RefObject, useEffect } from 'react';

/**
 * Pauses an SVG's SMIL animation timeline while the tab is hidden and resumes
 * it on refocus.
 *
 * Chromium keeps advancing SMIL clocks for hidden tabs and reconciles every
 * missed repeat interval when the tab is foregrounded again; with many
 * sub-second `repeatCount="indefinite"` loops (animateMotion particles, pulse
 * rings...) that catch-up can block the main thread long enough to trigger the
 * "Page Unresponsive" dialog. Pausing the timeline while hidden means there is
 * nothing to catch up: the animation simply resumes from where it stopped.
 */
const useSvgVisibilityPause = (svgRef: RefObject<SVGSVGElement | null>) => {
  useEffect(() => {
    if (typeof document === 'undefined') return undefined;
    const sync = () => {
      const svg = svgRef.current;
      // pauseAnimations is missing in some non-browser environments (jsdom).
      if (!svg || typeof svg.pauseAnimations !== 'function') return;
      if (document.visibilityState === 'hidden') {
        svg.pauseAnimations();
      } else {
        svg.unpauseAnimations();
      }
    };
    // Handle mounting while the tab is already hidden.
    sync();
    document.addEventListener('visibilitychange', sync);
    return () => document.removeEventListener('visibilitychange', sync);
  }, [svgRef]);
};

export default useSvgVisibilityPause;
