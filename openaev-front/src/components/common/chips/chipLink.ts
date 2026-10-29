// A chip that navigates is a real link around a non-clickable library Chip: the anchor keeps
// ⌘-click and "open in a new tab", the chip stays a plain span (no button inside a link).
// The classes reproduce the library focus ring on the anchor.
const chipLinkClassName = 'inline-flex rounded-sm no-underline focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-focus';

export default chipLinkClassName;
