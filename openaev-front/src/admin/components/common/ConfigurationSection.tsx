import { Paper } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import LibHeaderRow from '../../../components/common/LibHeaderRow';

/** Header floor for side-by-side sections whose headers may hold the default 36px button. */
export const SECTION_HEADER_WITH_ACTION_HEIGHT = 36;

interface Props {
  title: string;
  // Optional item count rendered as a subtle badge next to the title.
  count?: number;
  // Right-aligned action slot (add / preview button). The configuration and lessons create
  // actions use the library button's default 36px size, validated with product: LibHeaderRow
  // grows to fit it, with or without `withSurface`.
  action?: ReactNode;
  /**
   * Whether this section OWNS the surface.
   *
   * Two treatments, each justified by what the site already has:
   *
   * - `true` — the child used to draw its own `Paper`, so the surface simply
   *   changes owner. Its header is `LibHeaderRow` too (the library header's
   *   twin), so it can grow to fit a 36px action.
   * - `false` (default) — the child has no surface and must not gain one: the
   *   content sits on the page background, and giving it a Paper would add a
   *   border and a fill it never had. The header is ALIGNED on the library's
   *   instead, through `LibHeaderRow`.
   */
  withSurface?: boolean;
  // Surface padding, on the library scale. Only read when `withSurface`.
  padding?: 0 | 8 | 16 | 24 | 32;
  children: ReactNode;
  headerMinHeight?: number;
}

const CountBadge: FunctionComponent<{ count: number }> = ({ count }) => (
  <Box
    component="span"
    sx={theme => ({
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      minWidth: 20,
      height: 18,
      paddingInline: 0.75,
      borderRadius: 0.5,
      fontSize: 11,
      fontWeight: 600,
      color: theme.palette.text.secondary,
      backgroundColor: theme.palette.action.hover,
    })}
  >
    {count}
  </Box>
);

/**
 * Shared header shell for the simulation / scenario configuration tabs (Teams,
 * Variables, Media pressure, Objectives, Crisis intensity).
 *
 * The header is the library's, not the product's: a section title must match
 * the one a converted neighbour renders, on the same row and on the same
 * screen.
 */
const ConfigurationSection: FunctionComponent<Props> = ({ title, count, action, withSurface = false, padding = 0, headerMinHeight, children }) => {
  const heading = (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
      minWidth: 0,
    }}
    >
      {title}
      {count != null && <CountBadge count={count} />}
    </Box>
  );

  if (withSurface) {
    return (
      <LibHeaderRow title={heading} action={action} minRowHeight={headerMinHeight}>
        <Paper
          padding={padding}
          style={{
            flex: 1,
            minHeight: 0,
            overflow: 'hidden',
          }}
        >
          {children}
        </Paper>
      </LibHeaderRow>
    );
  }

  return (
    <LibHeaderRow title={heading} action={action} minRowHeight={headerMinHeight}>
      {children}
    </LibHeaderRow>
  );
};

export default ConfigurationSection;
