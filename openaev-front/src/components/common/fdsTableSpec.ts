import { type CSSProperties } from 'react';

import LIB_SURFACE_BORDER from './libSurfaceBorder';

// FDS-WORKAROUND: MUI Table dressed as the Figma DS Table — remove when the library ships Table
// The frame carries LIB_SURFACE_LAYER: the border needs its scope.

/** `sx` of a MUI Table: compact rows (40px), header cells content-compact-bold. */
export const FDS_TABLE_SX = {
  '& .MuiTableCell-root': {
    padding: '8px',
    borderBottom: LIB_SURFACE_BORDER,
    color: 'var(--text-default-primary)',
    fontFamily: 'var(--font-content-compact)',
    fontSize: 'var(--text-content-compact)',
    lineHeight: 'var(--leading-content-compact)',
  },
  '& .MuiTableHead-root .MuiTableCell-root': {
    height: 40,
    fontWeight: 'var(--font-weight-content-compact-bold)',
  },
  '& .MuiTableBody-root .MuiTableRow-root': { height: 40 },
};

/** The frame around a table and its group rows. */
export const FDS_TABLE_FRAME_STYLE: CSSProperties = {
  border: LIB_SURFACE_BORDER,
  borderRadius: 4,
  overflow: 'hidden',
};

/** Table group row: bold label, count in the secondary ink, chevron on the right (44px). */
export const FDS_GROUP_ROW_STYLE: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  width: '100%',
  minHeight: 44,
  padding: '10px 16px',
  boxSizing: 'border-box',
  background: 'none',
  border: 'none',
  borderBottom: LIB_SURFACE_BORDER,
  color: 'inherit',
  textAlign: 'left',
  cursor: 'pointer',
};

/** A line of text inside the table, aligned with its cells. */
export const FDS_TABLE_SECTION_STYLE: CSSProperties = {
  padding: '12px 16px',
  borderBottom: LIB_SURFACE_BORDER,
};
