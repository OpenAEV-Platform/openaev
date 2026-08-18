import { Paper } from '@filigran/design-system';
import { Box, Paper as MuiPaper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type ReactNode } from 'react';
import { Link } from 'react-router';

import { compactNumber } from '../../../utils/number';
// The shared section-subtitle style lives in a component-free module so it does
// not trip react-refresh/only-export-components on this component file.
import { SECTION_LABEL_SX } from './detailStyles';

// A single labelled field inside an information section.
export const Field = ({ label, children }: {
  label: string;
  children: ReactNode;
}) => (
  <div>
    <Typography variant="h3" gutterBottom sx={{ fontSize: 12 }}>{label}</Typography>
    <div>{children}</div>
  </div>
);

// A titled section with an outlined paper body (mirrors AssetGroupDetail). The
// wrapper fills the grid cell height so side-by-side sections align at the
// bottom (the Paper stretches to match the taller sibling).
export const Section = ({ title, children }: {
  title: string;
  children: ReactNode;
}) => (
  // GRID, not flex-column: with `title` set the library renders its own wrapper
  // around header + surface, and `style` reaches the SURFACE, never that
  // wrapper. A flex column therefore leaves the wrapper at content height and
  // side-by-side panels stop aligning (measured: 58px vs 130px). One grid row
  // at `1fr` stretches the wrapper without needing to style it, and `flex: 1`
  // below makes the surface fill it — PAPER-GAP-INVENTORY §13.2.
  <div style={{
    display: 'grid',
    // `minmax(0, 1fr)` and not just `1fr`: an implicit grid column is `auto`,
    // i.e. sized to max-content, so the library's wrapper grew past the panel
    // (measured 354px inside a 340px track) and the title never truncated —
    // it overflowed. The explicit 0 minimum is what lets `min-w-0 truncate`
    // do its job inside.
    gridTemplateColumns: 'minmax(0, 1fr)',
    gridTemplateRows: '1fr',
    height: '100%',
    minHeight: 0,
  }}
  >
    <Paper
      padding={16}
      title={title}
      data-testid="section-paper"
      style={{
        flex: 1,
        minHeight: 0,
      }}
    >
      {children}
    </Paper>
  </div>
);

// An information grid section (auto-fitting labelled fields), packed densely
// into as many columns as fit - the compact, OpenCTI-style overview card.
// The optional `action` slot renders right-aligned in the library header row.
// That row is a CONSTANT 24px whether or not an action is present, so a Paper
// top-aligns with an action-bearing sibling column for free. `action={null}`
// call sites predate the library header — back then the product drew a short
// header without an action and a 32px one with, and the null forced the tall
// variant. It no longer does anything; keep or drop it, but do not re-derive
// an alignment need from it.
export const InformationGrid = ({ title, action, children }: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) => (
  // One grid row at `1fr` — see Section above for why this cannot be a flex
  // column once `title` is set.
  <div style={{
    display: 'grid',
    // `minmax(0, 1fr)` and not just `1fr`: an implicit grid column is `auto`,
    // i.e. sized to max-content, so the library's wrapper grew past the panel
    // (measured 354px inside a 340px track) and the title never truncated —
    // it overflowed. The explicit 0 minimum is what lets `min-w-0 truncate`
    // do its job inside.
    gridTemplateColumns: 'minmax(0, 1fr)',
    gridTemplateRows: '1fr',
    height: '100%',
    minHeight: 0,
  }}
  >
    {/* padding=16 (iso): the surface IS the grid, +8px would drop a column
        (tracks are minmax(180px, 1fr)). */}
    <Paper
      padding={16}
      title={title}
      action={action ?? undefined}
      data-testid="information-grid-paper"
      style={{
        flex: 1,
        minHeight: 0,
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        rowGap: 16,
        alignContent: 'start',
      }}
    >
      {children}
    </Paper>
  </div>
);

// Lays the related-entity sections in an adaptive multi-column grid: two (or
// more) sections sit side by side on wide screens, while a lone section spans
// the full width (auto-fit collapses the empty track). An explicit `columns`
// template (e.g. '2fr 2fr 1fr') overrides the equal split on large screens
// when the sections have known, unequal content densities; smaller screens
// keep the adaptive wrap.
export const DetailSections = ({ children, columns }: {
  children: ReactNode;
  columns?: string;
}) => (
  <Box sx={{
    display: 'grid',
    gridTemplateColumns: columns
      ? {
          xs: 'repeat(auto-fit, minmax(340px, 1fr))',
          lg: columns,
        }
      : 'repeat(auto-fit, minmax(340px, 1fr))',
    gap: 2,
    alignItems: 'stretch',
  }}
  >
    {children}
  </Box>
);

// A full-width titled block: uppercase overline label above an outlined Paper.
// Use for embedded lists (injects played, findings) on overview pages so the
// section-title styling stays consistent and is defined once.
// Standalone section label (same look as the SectionBlock title) for sections
// that must render flat on the page background, without the Paper wrapper
// (e.g. standard entity lists on detail pages).
export const SectionLabel = ({ children }: { children: ReactNode }) => (
  <Typography sx={SECTION_LABEL_SX}>{children}</Typography>
);

export const SectionBlock = ({ title, action, children, disablePadding, centerContent }: {
  title: string;
  // Right-aligned node in the library header row (same geometry as the
  // InformationGrid action slot). That row is a constant 24px with or without
  // an action, so `action={null}` — an idiom from the product's own two-height
  // header — is now a no-op rather than an alignment lever.
  action?: ReactNode;
  children: ReactNode;
  disablePadding?: boolean;
  // Vertically centers the content when a side-by-side sibling stretches the
  // Paper taller than the content (grid alignItems: stretch). A plain
  // `height: 100%` on the child does not resolve inside the flex-grown Paper,
  // so the Paper itself must become the centering flex container.
  centerContent?: boolean;
}) => (
  // One grid row at `1fr` — see Section above for why this cannot be a flex
  // column once `title` is set.
  <div style={{
    display: 'grid',
    // `minmax(0, 1fr)` and not just `1fr`: an implicit grid column is `auto`,
    // i.e. sized to max-content, so the library's wrapper grew past the panel
    // (measured 354px inside a 340px track) and the title never truncated —
    // it overflowed. The explicit 0 minimum is what lets `min-w-0 truncate`
    // do its job inside.
    gridTemplateColumns: 'minmax(0, 1fr)',
    gridTemplateRows: '1fr',
    height: '100%',
    minHeight: 0,
  }}
  >
    {/* padding=16 (iso), 0 under `disablePadding`. The 16+16 cumulation with
        the row gutters is REPRODUCED as-is: correcting it is a density decision
        outside this wave — PAPER-GAP-INVENTORY §5.7. */}
    <Paper
      padding={disablePadding ? 0 : 16}
      title={title}
      action={action ?? undefined}
      data-testid="section-block-paper"
      style={{
        flex: 1,
        minHeight: 0,
        ...(centerContent && {
          display: 'flex',
          alignItems: 'center',
        }),
      }}
    >
      {children}
    </Paper>
  </div>
);

// A single headline stat rendered in the entity hero, mirroring the custom
// dashboard NumberWidget look & feel: a tinted rounded icon box next to a big
// Geologica number with an uppercase caption beneath it. When `to` is set the
// whole stat becomes a pivot link.
export const HeroStat = ({ icon: Icon, label, value, color, to }: {
  icon: ComponentType<{ sx?: object }>;
  label: string;
  value: ReactNode;
  color?: string;
  to?: string;
}) => {
  const theme = useTheme();
  const accent = color ?? theme.palette.primary.main;
  // Numeric values are shortened ("70.9K") with the exact count in a tooltip;
  // non-numeric values (percentages, custom nodes) render untouched.
  const isCompacted = typeof value === 'number' && Math.abs(value) >= 1000;
  const displayValue = typeof value === 'number' ? compactNumber(value) : value;
  const content = (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        minWidth: 0,
        padding: 0.5,
        ...(to
          ? {
              'borderRadius': 1,
              'transition': 'background-color 120ms',
              '&:hover': { backgroundColor: alpha(accent, 0.06) },
            }
          : {}),
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 30,
        height: 30,
        borderRadius: 1,
        flexShrink: 0,
        color: accent,
        background: alpha(accent, 0.1),
        boxShadow: `inset 0 0 12px ${alpha(accent, 0.13)}`,
      }}
      >
        <Icon sx={{ fontSize: 16 }} />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 18,
          fontWeight: 500,
          lineHeight: 1.05,
          color: 'text.primary',
        }}
        >
          {isCompacted
            ? (
                <Tooltip title={(value as number).toLocaleString()}>
                  <span>{displayValue}</span>
                </Tooltip>
              )
            : displayValue}
        </Typography>
        <Typography sx={{
          fontSize: 9.5,
          fontWeight: 600,
          letterSpacing: '0.07em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
        >
          {label}
        </Typography>
      </Box>
    </Box>
  );
  return to
    ? (
        <Link to={to} style={{ textDecoration: 'none' }}>
          {content}
        </Link>
      )
    : content;
};

// A horizontal cluster of hero stats separated by hairline dividers and
// wrapping on narrow viewports. With `spread`, every stat takes an equal
// share of the row so the cluster fills the full available width (used by
// standalone stat bars, e.g. the simulation Execution tab).
export const HeroStats = ({ children, spread }: {
  children: ReactNode;
  spread?: boolean;
}) => {
  const theme = useTheme();
  return (
    <Box sx={{
      'display': 'flex',
      'alignItems': 'center',
      'flexWrap': 'wrap',
      'columnGap': 4,
      'rowGap': 1,
      '& > *:not(:last-child)': {
        paddingRight: 4,
        borderRight: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      },
      ...(spread
        ? {
            '& > *': {
              flex: 1,
              minWidth: 150,
            },
          }
        : {}),
    }}
    >
      {children}
    </Box>
  );
};

// The hero header shared by ALL entity detail pages (scenario, simulation,
// atomic testing, assets, teams, persons, findings, connectors...). When
// `stats` is set, the headline metrics render as a second row of tiny
// HeroStats inside the hero.
//
// The `action` node is wrapped in a normalized cluster: every top-level
// Button / ToggleButton / IconButton is forced to the same 32px control
// height so the top-right of every hero in the app looks identical.
export const DetailHero = ({ icon: Icon, iconNode, overline, title, chips, action, stats, footer }: {
  icon?: ComponentType<{
    color?: 'primary';
    sx?: object;
  }>;
  /** Custom node rendered inside the icon box (e.g. a brand logo), overrides `icon`. */
  iconNode?: ReactNode;
  /** Small uppercase label rendered above the title (e.g. entity type). */
  overline?: ReactNode;
  title: string;
  chips?: ReactNode;
  action?: ReactNode;
  /** Tiny headline stats rendered as a second hero row (wrap in HeroStat). */
  stats?: ReactNode;
  /** Free-form extra hero row rendered after the stats (e.g. meta items). */
  footer?: ReactNode;
}) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  return (
    // DetailHero stays on MUI: accent gradient + transparent fill, and the
    // transparency falls under the "semi-transparent = phase 2" exclusion.
    // It also leaves the Paper waves permanently — it becomes its own
    // component (PAPER-GAP-INVENTORY §5.8).
    <MuiPaper
      variant="outlined"
      data-testid="detail-hero"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        padding: 2,
        borderRadius: 1,
        background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
      }}
      >
        <Box
          sx={{
            width: 52,
            height: 52,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            backgroundColor: alpha(accent, 0.12),
            border: `1px solid ${alpha(accent, 0.3)}`,
          }}
        >
          {iconNode ?? (Icon ? <Icon color="primary" /> : null)}
        </Box>
        <Box sx={{
          minWidth: 0,
          flex: 1,
        }}
        >
          {overline && (
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontWeight: 600,
              fontSize: 11,
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
              color: 'primary.main',
            }}
            >
              {overline}
            </Typography>
          )}
          <Tooltip title={title} placement="bottom-start">
            <Typography
              variant="h1"
              sx={{
                margin: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                // Shrink the anchor to the actual title width (capped at the
                // column) so the tooltip sits under the text instead of the
                // center of a full-width block.
                width: 'fit-content',
                maxWidth: '100%',
              }}
            >
              {title}
            </Typography>
          </Tooltip>
          {chips && (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              marginTop: 0.5,
              flexWrap: 'wrap',
            }}
            >
              {chips}
            </Box>
          )}
        </Box>
        {action && (
          <Box sx={{
            'display': 'flex',
            'alignItems': 'center',
            'gap': 1,
            'flexShrink': 0,
            'flexWrap': 'wrap',
            'justifyContent': 'flex-end',
            // One control geometry for every hero across the app: identical
            // height, font, line-height and padding for all text buttons
            // (outlined or contained), so no CTA ever looks smaller than its
            // neighbors.
            '& .MuiButton-root': {
              height: 32,
              fontSize: 13,
              fontWeight: 500,
              lineHeight: '21px',
              paddingTop: 0,
              paddingBottom: 0,
              paddingInline: 1.5,
            },
            '& .MuiButton-root .MuiButton-startIcon .MuiSvgIcon-root': { fontSize: 18 },
            '& .MuiToggleButton-root': {
              width: 32,
              height: 32,
            },
            // Only normalize IconButtons sitting directly in the cluster
            // (custom nested toolbars keep their own internal sizing).
            '& > .MuiIconButton-root': {
              width: 32,
              height: 32,
              borderRadius: 1,
            },
            '& > .MuiIconButton-root .MuiSvgIcon-root': { fontSize: 20 },
          }}
          >
            {action}
          </Box>
        )}
      </Box>
      {stats && <HeroStats>{stats}</HeroStats>}
      {footer}
    </MuiPaper>
  );
};
