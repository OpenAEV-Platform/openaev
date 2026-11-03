import { type CSSProperties } from 'react';

import { fdsLayerClass } from '../../utils/fdsLayer';
import { tint } from '../../utils/tint';

// FDS-WORKAROUND: MUI Table dressed as the Figma DS Table (8582:34772) — remove when the library ships Table — see sf-approval-card-ooux.md §7

const BORDER = '1px solid var(--border-elevation-subtle-soft)';

/**
 * The Figma Table sits on the base surface (layer 0): put this class on the table (or its frame)
 * so its tokens resolve to layer 0 inside a drawer or a dialog too, as in the Figma dark theme.
 */
export const FDS_TABLE_LAYER_CLASS = fdsLayerClass(0);

/** `sx` of a MUI Table: compact rows (40px), header cells content-compact-bold, DS hover and selected. */
export const FDS_TABLE_SX = {
  'backgroundColor': 'var(--bg-elevation-default)',
  '& .MuiTableCell-root': {
    padding: '8px',
    borderBottom: BORDER,
    color: 'var(--text-default-primary)',
    fontFamily: 'var(--font-content-compact)',
    fontSize: 'var(--text-content-compact)',
    lineHeight: 'var(--leading-content-compact)',
  },
  '& .MuiTableHead-root .MuiTableCell-root': {
    height: 40,
    fontWeight: 'var(--font-weight-content-compact-bold)',
    backgroundColor: 'var(--bg-elevation-default)',
  },
  '& .MuiTableBody-root .MuiTableRow-root': { height: 40 },
  '& .MuiTableBody-root .MuiTableRow-hover:hover': { backgroundColor: 'var(--bg-elevation-highlight)' },
  '& .MuiTableBody-root .MuiTableRow-root.Mui-selected, & .MuiTableBody-root .MuiTableRow-root.Mui-selected:hover': { backgroundColor: tint('var(--color-filigran-brand-primary)', 10) },
};

/** The frame around a table (and its group rows): one soft border, rounded. */
export const FDS_TABLE_FRAME_STYLE: CSSProperties = {
  border: BORDER,
  backgroundColor: 'var(--bg-elevation-default)',
  borderRadius: 4,
  overflow: 'hidden',
};

/** `sx` of the cell holding an opened row's detail (Table row detail): on the highlight surface. */
export const FDS_ROW_DETAIL_CELL_SX = {
  backgroundColor: 'var(--bg-elevation-highlight)',
  padding: '16px !important',
};

/** Table group row: chevron, bold label, count in the secondary ink, on the highlight surface (44px). Use as `style`, not `sx`. */
export const FDS_GROUP_ROW_STYLE: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  minHeight: 44,
  padding: '10px 16px',
  boxSizing: 'border-box',
  backgroundColor: 'var(--bg-elevation-highlight)',
  borderBottom: BORDER,
};

/** Select all banner: one line on the highlight surface, above the rows (36px). */
export const FDS_SELECT_ALL_BANNER_STYLE: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  minHeight: 36,
  padding: '0 24px',
  boxSizing: 'border-box',
  backgroundColor: 'var(--bg-elevation-highlight)',
  borderBottom: BORDER,
};
