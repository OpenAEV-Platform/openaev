import { type Theme } from '@mui/material';

/**
 * Edge of a neutral chip in standard colours ("Pastel colours" off): the design-system
 * neutral chip token, which holds 3:1 against the surface in dark and light
 * (`theme.palette.divider` drew 1.2:1 to 1.4:1).
 */
export const STANDARD_CHIP_EDGE = '1px solid var(--color-feedback-neutral-tertiary)';

/**
 * Style of a hand-drawn or MUI neutral chip: outlined in standard colours, as the
 * design-system Chip; under the `pastel` root class (useFdsThemeScope) the filled tile
 * of today. Use it with `variant="outlined"` on an MUI Chip.
 */
export const neutralChipSx = (theme: Theme) => ({
  'border': STANDARD_CHIP_EDGE,
  'backgroundColor': 'transparent',
  '.pastel &': {
    borderColor: 'transparent',
    backgroundColor: theme.palette.action.selected,
  },
});
