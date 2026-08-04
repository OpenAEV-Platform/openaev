import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { Box, Divider, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useRef, useState } from 'react';

import { SECTION_LABEL_SX } from '../../../../../components/common/detail/detailStyles';
import { useFormatter } from '../../../../../components/i18n';
import { attackPathCausalColor, attackPathChokepointColor } from './attack-path-colors';

interface Props {
  // Bumped by the parent whenever a side panel opens, so the legend folds away to avoid competing
  // with it. The user can always reopen it manually afterwards.
  collapseSignal?: number;
}

type LegendShape = 'action' | 'target' | 'finding' | 'cluster';

// Uppercase overline for the legend's sections: the app's shared section-label recipe, without the
// recipe's block margin (the legend manages its own compact rhythm).
const legendSectionSx = {
  ...SECTION_LABEL_SX,
  marginBottom: 0,
} as const;

// Full legend of the attack-path canvas (bottom-right): the card kinds, the verdict colours and the
// special edge/badge colours. Open by default — it is part of reading the map — and folds to a
// compact verdict key when a side panel needs the room.
const AttackPathLegend = ({ collapseSignal }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [open, setOpen] = useState(true);

  // Fold the legend when a panel opens (ignoring the initial mount), without locking it: reopening
  // is still up to the user.
  const lastSignal = useRef(collapseSignal);
  useEffect(() => {
    if (collapseSignal !== lastSignal.current) {
      lastSignal.current = collapseSignal;
      setOpen(false);
    }
  }, [collapseSignal]);

  const shapes: {
    shape: LegendShape;
    label: string;
  }[] = [
    {
      shape: 'action',
      label: t('Action — the tool/injector that ran'),
    },
    {
      shape: 'target',
      label: t('Target — endpoint, person or team reached'),
    },
    {
      shape: 'finding',
      label: t('Finding — what the action discovered'),
    },
    {
      shape: 'cluster',
      label: t('Cluster (+N, dashed) — click to expand'),
    },
  ];

  // The three verdict colours form the minimal key that stays visible even when the legend is folded.
  const verdictColors: {
    color: string;
    label: string;
  }[] = [
    {
      color: theme.palette.success.main,
      label: t('Prevented (all endpoints)'),
    },
    {
      color: theme.palette.warning.main,
      label: t('Detected / partial'),
    },
    {
      color: theme.palette.error.main,
      label: t('Neither prevented nor detected'),
    },
  ];
  const colors: {
    color: string;
    label: string;
  }[] = [
    ...verdictColors,
    {
      color: attackPathChokepointColor(theme),
      label: t('Chokepoint (most exposed endpoint)'),
    },
    {
      color: attackPathCausalColor(theme),
      label: t('Event link — a finding that triggered the next action'),
    },
    {
      color: theme.palette.primary.main,
      label: t('Selected attack path'),
    },
  ];

  // Miniature card glyphs mirroring the real canvas cards (left accent bar + icon slot + text bar).
  const renderShape = (shape: LegendShape) => {
    const accent = shape === 'action' ? theme.palette.primary.main : theme.palette.text.secondary;
    return (
      <Box
        component="span"
        sx={{
          width: 30,
          height: 16,
          flex: '0 0 auto',
          display: 'inline-flex',
          alignItems: 'center',
          gap: '3px',
          paddingLeft: '4px',
          borderRadius: '3px',
          border: `1px ${shape === 'cluster' ? 'dashed' : 'solid'} ${theme.palette.divider}`,
          borderLeft: `2px solid ${accent}`,
          background: theme.palette.background.default,
        }}
      >
        <Box
          component="span"
          sx={{
            width: shape === 'finding' ? 5 : 7,
            height: shape === 'finding' ? 5 : 7,
            borderRadius: '2px',
            background: accent,
            opacity: 0.7,
          }}
        />
        <Box
          component="span"
          sx={{
            flex: 1,
            height: 2,
            marginRight: '4px',
            borderRadius: 1,
            background: theme.palette.text.disabled,
            opacity: 0.5,
          }}
        />
      </Box>
    );
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        width: 288,
        p: 1.25,
        borderRadius: 1,
        boxShadow: theme.shadows[2],
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 1,
      }}
      >
        <Typography sx={legendSectionSx}>
          {t('Legend')}
        </Typography>
        {/* Negative margins swallow the icon button's hit-target padding so the header row stays
            as compact as the title and the icon centers on the title's baseline. */}
        <IconButton
          size="small"
          onClick={() => setOpen(o => !o)}
          aria-label={t('Toggle legend')}
          sx={{
            my: -0.5,
            mr: -0.5,
          }}
        >
          {open ? <ExpandMore fontSize="small" /> : <ExpandLess fontSize="small" />}
        </IconButton>
      </Box>
      {!open && (
        // Minimal verdict-colour key stays visible when the legend is folded (e.g. a side panel
        // open), so the analyst can still read what a coloured verdict means.
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          mt: 0.75,
        }}
        >
          {verdictColors.map(c => (
            <Tooltip key={c.label} title={c.label}>
              <Box
                component="span"
                sx={{
                  width: 12,
                  height: 12,
                  flex: '0 0 auto',
                  borderRadius: '50%',
                  background: c.color,
                }}
              />
            </Tooltip>
          ))}
        </Box>
      )}
      {open && (
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.75,
          mt: 0.75,
        }}
        >
          <Typography sx={legendSectionSx}>{t('Shapes')}</Typography>
          {shapes.map(s => (
            <Box
              key={s.label}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
            >
              {renderShape(s.shape)}
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
            </Box>
          ))}
          <Divider sx={{ my: 0.5 }} />
          <Typography sx={legendSectionSx}>{t('Colors')}</Typography>
          {colors.map(c => (
            <Box
              key={c.label}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
            >
              {/* Swatch centered in the same 30px slot as the shape glyphs, so the label text of
                  the Shapes and Colors sections shares one left edge. */}
              <Box
                component="span"
                sx={{
                  width: 30,
                  flex: '0 0 auto',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Box
                  component="span"
                  sx={{
                    width: 16,
                    height: 3,
                    borderRadius: '2px',
                    background: c.color,
                  }}
                />
              </Box>
              <Typography variant="caption" color="text.secondary">{c.label}</Typography>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
};

export default AttackPathLegend;
