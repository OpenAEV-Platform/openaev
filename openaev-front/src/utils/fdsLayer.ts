/**
 * Elevation layers, and the three input aliases the `.layer-N` class cannot reach on
 * its own: a `var()` inside a custom-property declaration is substituted where it is
 * DECLARED (the root), so `--bg-input-default` keeps its layer-0 value however deep the
 * class is applied. Same mechanism as the sibling product. Drop `layerInputVars` and
 * keep the class once `.layer-N` re-declares them in the library.
 */

export type FdsLayer = 0 | 1 | 2 | 3;

export const layerInputVars = {
  '--bg-input-default': 'var(--bg-elevation-highlight)',
  '--bg-input-disabled': 'var(--bg-elevation-disabled)',
  '--bg-input-hover': 'var(--bg-elevation-hover)',
} as const;

export const fdsLayerClass = (layer: FdsLayer) => `layer-${layer}`;

/** Drawers and dialogs: the floating surface the product hosts its forms in. */
export const SURFACE_LAYER: FdsLayer = 2;
