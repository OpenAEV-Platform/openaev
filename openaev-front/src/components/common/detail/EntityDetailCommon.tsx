import { Hero, HeroBody, HeroHeader, Paper, Text, Thumbnail, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type CSSProperties, type ReactNode } from 'react';
import { Link } from 'react-router';

import { compactNumber } from '../../../utils/number';
// The shared section-subtitle style lives in a component-free module so it does
// not trip react-refresh/only-export-components on this component file.
import { SECTION_LABEL_SX } from './detailStyles';

export const Field = ({ label, children }: {
  label: string;
  children: ReactNode;
}) => (
  <div>
    <Typography
      variant="h3"
      sx={{
        fontSize: 12,
        color: 'text.secondary',
        marginBottom: 1,
      }}
    >
      {label}
    </Typography>
    <Typography component="div" sx={{ fontSize: 14 }}>{children}</Typography>
  </div>
);

// A titled section whose body fills its grid cell, so siblings align at the bottom.
export const Section = ({ title, children }: {
  title: string;
  children: ReactNode;
}) => (
  // GRID, not flex-column: with `title` set, `style` reaches the library SURFACE,
  // never the wrapper it draws around header + surface, so a flex column leaves
  // that wrapper at content height (measured 58px against 130px).
  <div style={{
    display: 'grid',
    // `minmax(0, 1fr)`, not `1fr`: an implicit column is max-content sized, so the
    // wrapper grew past its track (354px in 340px) and the title overflowed.
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

// The library header row is a CONSTANT 24px with or without an `action`, so
// siblings top-align for free; `action={null}` at call sites does nothing.
export const InformationGrid = ({ title, action, children }: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) => (
  // See Section: grid, not flex, once `title` is set.
  <div style={{
    display: 'grid',
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

// `columns` overrides the equal split on wide screens; narrow screens keep the wrap.
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

export const SectionLabel = ({ children }: { children: ReactNode }) => (
  <Typography sx={SECTION_LABEL_SX}>{children}</Typography>
);

export const SectionBlock = ({ title, action, children, disablePadding, centerContent }: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  disablePadding?: boolean;
  // The Paper itself centers: `height: 100%` on the child does not resolve
  // inside a flex-grown Paper.
  centerContent?: boolean;
}) => (
  // See Section: grid, not flex, once `title` is set.
  <div style={{
    display: 'grid',
    gridTemplateColumns: 'minmax(0, 1fr)',
    gridTemplateRows: '1fr',
    // No `height: 100%`: inside a flex column every block claimed the whole
    // height and they drew on top of each other.
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

export const HeroStat = ({ icon: Icon, label, value, color, to }: {
  icon: ComponentType<{ sx?: object }>;
  label: string;
  value: ReactNode;
  color?: string;
  to?: string;
}) => {
  const theme = useTheme();
  const accent = color ?? theme.palette.primary.main;
  const isCompacted = typeof value === 'number' && Math.abs(value) >= 1000;
  const displayValue = typeof value === 'number' ? compactNumber(value) : value;
  const content = (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        minWidth: 0,
        padding: 1,
        borderRadius: 1,
        border: '1px solid var(--border-elevation-subtle-soft)',
        ...(to
          ? {
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
        flexShrink: 0,
        color: accent,
      }}
      >
        <Icon sx={{ fontSize: 24 }} />
      </Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        minWidth: 0,
      }}
      >
        <Text variant="content-base-bold" className="text-default-primary">
          {isCompacted
            ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span>{displayValue}</span>
                  </TooltipTrigger>
                  {(value as number).toLocaleString() && <TooltipContent>{(value as number).toLocaleString()}</TooltipContent>}
                </Tooltip>
              )
            : displayValue}
        </Text>
        <Text variant="content-compact" className="text-default-secondary">
          {label}
        </Text>
      </Box>
    </Box>
  );
  return to
    ? (
        <Box
          component={Link}
          to={to}
          onKeyDown={(event) => {
            if (event.key === ' ') {
              event.preventDefault();
              event.currentTarget.click();
            }
          }}
          sx={{
            'textDecoration': 'none',
            'display': 'block',
            'borderRadius': 1,
            'outline': 'none',
            '&:focus-visible': { boxShadow: `0 0 0 2px ${alpha(accent, 0.3)}` },
            '&:focus-visible > div': {
              borderColor: accent,
              backgroundColor: alpha(accent, 0.08),
            },
          }}
        >
          {content}
        </Box>
      )
    : content;
};

// With `spread`, every tile takes an equal share so the cluster fills the width.
export const HeroStats = ({ children, spread }: {
  children: ReactNode;
  spread?: boolean;
}) => (
  <Box sx={{
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 1,
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

/** The hero's icon square (Figma 7910:12881). */
const HERO_ICON_SIZE = 54;

export const DetailHero = ({ icon: Icon, iconNode, iconFills, overline, title, subtitle, chips, chipsInline, action, stats, bodyAction, footer }: {
  icon?: ComponentType<{
    color?: 'primary';
    sx?: object;
  }>;
  /** Custom node rendered inside the icon box (e.g. a brand logo), overrides `icon`. */
  iconNode?: ReactNode;
  /** The node is an image that fills the square itself: no thumbnail chrome around it. */
  iconFills?: boolean;
  /** Small uppercase label rendered above the title (e.g. entity type). */
  overline?: ReactNode;
  title: string;
  /** One line under the title, in the secondary ink (e.g. a channel's subtitle). */
  subtitle?: ReactNode;
  chips?: ReactNode;
  /** Chips share the title's line instead of taking a row of their own. */
  chipsInline?: boolean;
  action?: ReactNode;
  /** Tiny headline stats rendered as a second hero row (wrap in HeroStat). */
  stats?: ReactNode;
  /** Controls belonging to the body row, right-aligned beside the stats. */
  bodyAction?: ReactNode;
  /** Free-form extra hero row rendered after the stats (e.g. meta items). */
  footer?: ReactNode;
}) => {
  // Artwork fills the square: the thumbnail's frame exists to hold a glyph.
  const filledIcon = (
    <Box sx={{
      'width': HERO_ICON_SIZE,
      'height': HERO_ICON_SIZE,
      'borderRadius': 1,
      'overflow': 'hidden',
      'flexShrink': 0,
      '& > *': {
        width: '100%',
        height: '100%',
        objectFit: 'cover',
        display: 'block',
      },
    }}
    >
      {iconNode}
    </Box>
  );
  // Thumbnail renders a library Paper, which re-declares `--bg-elevation-default`
  // on ITSELF, so a `.layer-2` wrapper loses: repoint the variable instead.
  const framedIcon = (
    <span style={{ '--bg-elevation-default-layer-1': 'var(--bg-elevation-default-layer-2)' } as CSSProperties}>
      {/* The library's Thumbnail is fixed at 48 and paints Paper's
          `subtle-soft` border; the hero asks for 54 and the plain
          `subtle` one (LIBRARY-FEEDBACK.md 68). */}
      <Thumbnail style={{
        width: HERO_ICON_SIZE,
        height: HERO_ICON_SIZE,
        borderColor: 'var(--border-elevation-subtle)',
      }}
      >
        {iconNode ?? (Icon ? <Icon color="primary" /> : null)}
      </Thumbnail>
    </span>
  );

  let heroIcon;
  if (iconNode || Icon) {
    heroIcon = iconFills ? filledIcon : framedIcon;
  }

  return (
    <Hero data-testid="detail-hero">
      <HeroHeader
        icon={heroIcon}
        action={action && (
          <Box sx={{
            'display': 'flex',
            'alignItems': 'center',
            'gap': 1,
            'flexShrink': 0,
            'flexWrap': 'wrap',
            'justifyContent': 'flex-end',
            // One control geometry for every hero, so no CTA looks smaller.
            '& .MuiButton-root': {
              height: 36,
              fontSize: 13,
              fontWeight: 500,
              lineHeight: '21px',
              paddingTop: 0,
              paddingBottom: 0,
              paddingInline: 1.5,
            },
            '& .MuiButton-root .MuiButton-startIcon .MuiSvgIcon-root': { fontSize: 18 },
            '& .MuiToggleButton-root': {
              width: 36,
              height: 36,
            },
            // Direct children only: nested toolbars keep their own sizing.
            '& > .MuiIconButton-root': {
              width: 36,
              height: 36,
              borderRadius: 1,
            },
            '& > .MuiIconButton-root .MuiSvgIcon-root': { fontSize: 20 },
          }}
          >
            {action}
          </Box>
        )}
      >
        <Box sx={{
          minWidth: 0,
          flex: 1,
        }}
        >
          {overline && (
            <Text variant="content-base-medium" className="block text-filigran-brand-primary">
              {overline}
            </Text>
          )}
          {/* Inline: title and chips are one line, 4px apart. Otherwise the
              chips take their own row under the title. */}
          <Box sx={{
            display: 'flex',
            alignItems: chipsInline ? 'center' : 'stretch',
            flexDirection: chipsInline ? 'row' : 'column',
            gap: chipsInline ? 0.5 : 0,
            minWidth: 0,
            flexWrap: chipsInline ? 'wrap' : 'nowrap',
          }}
          >
            <Tooltip>
              <TooltipTrigger asChild>
                <Text
                  variant="title-md"
                  as="h1"
                  style={{
                    margin: 0,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    // Anchor on the title width, so the tooltip sits under the text.
                    width: 'fit-content',
                    maxWidth: '100%',
                  }}
                >
                  {title}
                </Text>
              </TooltipTrigger>
              {title && <TooltipContent side="bottom" align="start">{title}</TooltipContent>}
            </Tooltip>
            {chips && (
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: chipsInline ? 0.5 : 1,
                marginTop: chipsInline ? 0 : 0.5,
                minWidth: 0,
                flexWrap: 'wrap',
              }}
              >
                {chips}
              </Box>
            )}
          </Box>
          {subtitle && (
            <Text variant="content-base" className="block text-default-secondary">
              {subtitle}
            </Text>
          )}
        </Box>
      </HeroHeader>
      {(stats || bodyAction || footer) && (
        <HeroBody>
          {/* HeroBody lays its children in a row, so a footer put beside the
              stats gets pushed to the far right and reads as a stray caption.
              The two rows are stacked here instead. */}
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            width: '100%',
            minWidth: 0,
            gap: 2,
          }}
          >
            <Box sx={{
              display: 'flex',
              width: '100%',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              gap: 2,
              flexWrap: 'wrap',
            }}
            >
              {stats ? <HeroStats>{stats}</HeroStats> : <span />}
              {bodyAction && (
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  flexShrink: 0,
                }}
                >
                  {bodyAction}
                </Box>
              )}
            </Box>
            {footer}
          </Box>
        </HeroBody>
      )}
    </Hero>
  );
};
