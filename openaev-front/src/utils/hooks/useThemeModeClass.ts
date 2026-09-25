import type { Theme } from '@mui/material/styles';
import { useTheme } from '@mui/material/styles';
import { useEffect } from 'react';

export type ThemeModeClassName = 'dark' | 'light';

/**
 * Reactively applies the current MUI theme mode as a class — exactly one of
 * 'dark' or 'light' at all times — on a given DOM container, so that its
 * subtree can rely on either selector to style itself (e.g. FDS-generated
 * or third-party CSS bundled in mode-scoped selectors, such as the Tailwind
 * `.dark` variant shipped by `@filigran/chatbot`).
 *
 * Generalizes the ad-hoc `container.className = isDarkMode ? 'dark' : ''`
 * pattern from AskArianePanel.tsx into reusable infrastructure, with two
 * deliberate improvements over that original pattern:
 *  - always applies exactly one of 'dark' | 'light' (never both, never
 *    neither), instead of 'dark' or nothing — so consumers can rely on a
 *    '.light' selector too, not just the absence of '.dark';
 *  - uses `classList.add`/`remove` instead of overwriting `className`, so it
 *    never clobbers other class names already present on the container.
 *
 * Strictly scoped to the given `container` — never touches
 * `document.documentElement` or `document.body` — safe to use on any
 * FDS-consuming subtree (a portal container, a widget root, …) without
 * affecting the rest of the app.
 *
 * @param container the DOM node to apply the mode class to, or `null` if not
 * yet mounted (e.g. a container held in `useState` created by an effect).
 * @returns the resolved mode ('dark' | 'light') matching the current theme.
 */
const useThemeModeClass = (container: HTMLElement | null): ThemeModeClassName => {
  const theme = useTheme<Theme>();
  const mode: ThemeModeClassName = theme.palette.mode === 'dark' ? 'dark' : 'light';

  useEffect(() => {
    if (!container) {
      return undefined;
    }
    const opposite: ThemeModeClassName = mode === 'dark' ? 'light' : 'dark';
    container.classList.remove(opposite);
    container.classList.add(mode);
    return () => {
      container.classList.remove(mode);
    };
  }, [container, mode]);

  return mode;
};

export default useThemeModeClass;
