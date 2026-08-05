// Shared attack-path presentation tokens: global keyframes, layout metrics and the visually-hidden
// recipe. Kept in a component-free module (like node-sizes.ts) so importing it never trips
// react-refresh/only-export-components on the components that consume it.

// Entrance affordance for nodes a live update just added: a short fade-in, only for users who have
// not asked for reduced motion (the keyframes live behind the media query, so with the preference
// set the class simply does nothing).
export const AP_NODE_ENTER_CLASS = 'ap-node-enter';

// Every attack-path keyframe in one object, mounted once by the container via <GlobalStyles/>:
// the node entrance fade and the "Live" beacon pulse (referenced by name from the top bar).
export const AP_GLOBAL_STYLES = {
  '@media (prefers-reduced-motion: no-preference)': {
    '@keyframes apNodeEnter': {
      from: {
        opacity: 0,
        transform: 'scale(0.94)',
      },
      to: {
        opacity: 1,
        transform: 'scale(1)',
      },
    },
    [`.${AP_NODE_ENTER_CLASS}`]: { animation: 'apNodeEnter 420ms ease-out' },
  },
  '@keyframes attackPathLivePulse': {
    '0%, 100%': { opacity: 1 },
    '50%': { opacity: 0.25 },
  },
};

// Off-screen but readable by assistive tech, for the live region that announces each update batch.
// Same recipe as MUI's `visuallyHidden`, inlined because @mui/utils is not a declared dependency.
export const AP_VISUALLY_HIDDEN = {
  border: 0,
  clip: 'rect(0 0 0 0)',
  height: '1px',
  margin: '-1px',
  overflow: 'hidden',
  padding: 0,
  position: 'absolute',
  whiteSpace: 'nowrap',
  width: '1px',
} as const;

// Side panel (drag-resizable) width bounds and default (px).
export const AP_PANEL_MIN_WIDTH = 320;
export const AP_PANEL_MAX_WIDTH = 1000;
export const AP_PANEL_DEFAULT_WIDTH = 560;

// Vertical room for the whole view: everything above it (header + tabs + picker/cards strip) is the
// page chrome the offset reserves.
export const AP_VIEW_HEIGHT = 'calc(100vh - 200px)';

// Summary stat cards: minimum width before the strip wraps.
export const AP_STAT_CARD_MIN_WIDTH = 150;
