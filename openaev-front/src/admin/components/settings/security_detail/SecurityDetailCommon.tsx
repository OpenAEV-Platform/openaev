import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type ReactNode } from 'react';

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
  <div>
    <Typography sx={SECTION_LABEL_SX}>{title}</Typography>
    <Paper
      variant="outlined"
      sx={{
        padding: 2,
        borderRadius: 1,
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 1.5,
        rowGap: 2,
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

// The hero header shared by all Security detail pages.
export const DetailHero = ({ icon: Icon, title, chips, action }: {
  icon: ComponentType<{
    color?: 'primary';
    sx?: object;
  }>;
  title: string;
  chips?: ReactNode;
  action?: ReactNode;
}) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        padding: 2,
        borderRadius: 1,
        background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
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
        <Icon color="primary" />
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
    </Paper>
  );
};
