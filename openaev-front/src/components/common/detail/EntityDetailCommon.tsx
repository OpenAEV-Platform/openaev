import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type ReactNode } from 'react';
import { Link } from 'react-router';

const SECTION_LABEL_SX = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 11,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
  marginBottom: 1.5,
};

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
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
  }}
  >
    <Typography sx={SECTION_LABEL_SX}>{title}</Typography>
    <Paper
      variant="outlined"
      sx={{
        padding: 2,
        borderRadius: 1,
        flex: 1,
      }}
    >
      {children}
    </Paper>
  </div>
);

// An information grid section (auto-fitting labelled fields), packed densely
// into as many columns as fit - the compact, OpenCTI-style overview card.
export const InformationGrid = ({ title, children }: {
  title: string;
  children: ReactNode;
}) => (
  // Flex column + Paper flex:1 so that, when several InformationGrids sit side by
  // side in a stretched DetailSections row, every Paper fills the row height and
  // shares the same bottom edge (matching SectionBlock everywhere in the app).
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
  }}
  >
    <Typography sx={SECTION_LABEL_SX}>{title}</Typography>
    <Paper
      variant="outlined"
      sx={{
        padding: 2,
        borderRadius: 1,
        flex: 1,
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 1.5,
        rowGap: 2,
        alignContent: 'start',
      }}
    >
      {children}
    </Paper>
  </div>
);

// Lays the related-entity sections in an adaptive multi-column grid: two (or
// more) sections sit side by side on wide screens, while a lone section spans
// the full width (auto-fit collapses the empty track).
export const DetailSections = ({ children }: { children: ReactNode }) => (
  <Box sx={{
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
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
export const SectionBlock = ({ title, children, disablePadding }: {
  title: string;
  children: ReactNode;
  disablePadding?: boolean;
}) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
  }}
  >
    <Typography sx={SECTION_LABEL_SX}>{title}</Typography>
    <Paper
      variant="outlined"
      sx={{
        padding: disablePadding ? 0 : 2,
        borderRadius: 1,
        flex: 1,
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
          {value}
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

// A horizontal cluster of hero stats separated by hairline dividers, spread
// across the available width and wrapping on narrow viewports.
export const HeroStats = ({ children }: { children: ReactNode }) => {
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
    }}
    >
      {children}
    </Box>
  );
};

// The hero header shared by all Security detail pages. When `stats` is set, the
// headline metrics render as a second row of tiny HeroStats inside the hero
// (mirroring the scenario / simulation overviews) instead of a separate tile
// grid below it.
export const DetailHero = ({ icon: Icon, iconNode, title, chips, action, stats }: {
  icon?: ComponentType<{
    color?: 'primary';
    sx?: object;
  }>;
  /** Custom node rendered inside the icon box (e.g. a brand logo), overrides `icon`. */
  iconNode?: ReactNode;
  title: string;
  chips?: ReactNode;
  action?: ReactNode;
  /** Tiny headline stats rendered as a second hero row (wrap in HeroStat). */
  stats?: ReactNode;
}) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  return (
    <Paper
      variant="outlined"
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
          <Tooltip title={title}>
            <Typography
              variant="h1"
              sx={{
                margin: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
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
        {action}
      </Box>
      {stats && <HeroStats>{stats}</HeroStats>}
    </Paper>
  );
};
