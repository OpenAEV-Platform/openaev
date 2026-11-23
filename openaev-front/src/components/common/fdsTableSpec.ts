import { type SxProps, type Theme } from '@mui/material/styles';
import { type CSSProperties } from 'react';

import LIB_SURFACE_BORDER from './libSurfaceBorder';

// FDS-WORKAROUND: MUI Table dressed as the Figma DS Table — remove when the library ships Table
// The frame carries LIB_SURFACE_LAYER: the border needs its scope.

/** Row heights of the library Table spec, in pixels. */
const ROW_HEIGHT = 40;
const GROUP_ROW_HEIGHT = 44;

/** `sx` of a MUI Table: compact rows, header cells content-compact-bold. */
export const FDS_TABLE_SX: SxProps<Theme> = {
  '& .MuiTableCell-root': {
    p: 1,
    borderBottom: LIB_SURFACE_BORDER,
    color: 'var(--text-default-primary)',
    fontFamily: 'var(--font-content-compact)',
    fontSize: 'var(--text-content-compact)',
    lineHeight: 'var(--leading-content-compact)',
  },
  '& .MuiTableHead-root .MuiTableCell-root': {
    height: ROW_HEIGHT,
    fontWeight: 'var(--font-weight-content-compact-bold)',
  },
  '& .MuiTableBody-root .MuiTableRow-root': { height: ROW_HEIGHT },
};

/** The frame around a table and its group rows. */
export const fdsTableFrameStyle = (theme: Theme): CSSProperties => ({
  border: LIB_SURFACE_BORDER,
  borderRadius: theme.shape.borderRadius,
  overflow: 'hidden',
});

/** Table group row: bold label, count in the secondary ink, chevron on the right. */
export const fdsGroupRowStyle = (theme: Theme): CSSProperties => ({
  display: 'flex',
  alignItems: 'center',
  gap: theme.spacing(1),
  width: '100%',
  minHeight: GROUP_ROW_HEIGHT,
  padding: theme.spacing(1.25, 2),
  boxSizing: 'border-box',
  background: 'none',
  border: 'none',
  borderBottom: LIB_SURFACE_BORDER,
  color: 'inherit',
  textAlign: 'left',
  cursor: 'pointer',
});

/** A line of text inside the table, aligned with its cells. */
export const fdsTableSectionStyle = (theme: Theme): CSSProperties => ({
  padding: theme.spacing(1.5, 2),
  borderBottom: LIB_SURFACE_BORDER,
});
