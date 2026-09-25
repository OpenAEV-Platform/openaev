import { Paper } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import LibHeaderRow from '../../../components/common/LibHeaderRow';

interface Props {
  title: string;
  // Optional item count rendered as a subtle badge next to the title.
  count?: number;
  // Right-aligned action slot (add / preview button). It sits in a 24px row, so
  // it must be 24px tall: a header action passes the library button's `sm`.
  action?: ReactNode;
  /**
   * Whether this section OWNS the surface.
   *
   * Two treatments, each justified by what the site already has:
   *
   * - `true` — the child used to draw its own `Paper`, so the surface simply
   *   changes owner and the section gets the library's real header. Iso.
   * - `false` (default) — the child has no surface and must not gain one: the
   *   content sits on the page background, and giving it a Paper would add a
   *   border and a fill it never had. The header is ALIGNED on the library's
   *   instead, through `LibHeaderRow`.
   */
  withSurface?: boolean;
  // Surface padding, on the library scale. Only read when `withSurface`.
  padding?: 0 | 8 | 16 | 24 | 32;
  children: ReactNode;
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
const ConfigurationSection: FunctionComponent<Props> = ({ title, count, action, withSurface = false, padding = 0, children }) => {
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
      <Paper
        padding={padding}
        title={heading}
        action={action ?? undefined}
        style={{
          flex: 1,
          overflow: 'hidden',
        }}
      >
        {children}
      </Paper>
    );
  }

  return (
    <LibHeaderRow title={heading} action={action}>
      {children}
    </LibHeaderRow>
  );
};

export default ConfigurationSection;
