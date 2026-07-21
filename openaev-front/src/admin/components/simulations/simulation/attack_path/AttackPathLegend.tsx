import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useRef, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { attackPathChokepointColor } from './attack-path-colors';

interface Props {
  // Bumped by the parent whenever a side panel/drawer opens, so the legend folds away to avoid
  // overlapping it. The user can always reopen it manually afterwards.
  collapseSignal?: number;
}

// Interactive, collapsible legend (bottom-left) explaining the attack-path graph's shapes and colours
// — mirrors the design's Legend section.
const AttackPathLegend = ({ collapseSignal }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [open, setOpen] = useState(true);

  // Fold the legend when a panel opens (ignoring the initial mount), without locking it: reopening is
  // still up to the user.
  const lastSignal = useRef(collapseSignal);
  useEffect(() => {
    if (collapseSignal !== lastSignal.current) {
      lastSignal.current = collapseSignal;
      setOpen(false);
    }
  }, [collapseSignal]);

  const shapes: {
    shape: 'diamond' | 'dashedCircle' | 'pill' | 'circle';
    label: string;
  }[] = [
    {
      shape: 'diamond',
      label: t('Injector — click for the executed action'),
    },
    {
      shape: 'dashedCircle',
      label: t('Endpoint cluster (+N) — click to expand'),
    },
    {
      shape: 'pill',
      label: t('Finding cluster — click to expand'),
    },
    {
      shape: 'circle',
      label: t('Finding — click for details'),
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
      color: theme.palette.primary.main,
      label: t('Selected attack path'),
    },
  ];

  const renderShape = (shape: 'diamond' | 'dashedCircle' | 'pill' | 'circle') => {
    const base = {
      width: 16,
      height: 16,
      flex: '0 0 auto',
      border: `1.5px solid ${theme.palette.text.secondary}`,
      background: theme.palette.background.default,
    };
    if (shape === 'diamond') {
      return (
        <span style={{
          ...base,
          clipPath: 'polygon(50% 0, 100% 50%, 50% 100%, 0 50%)',
        }}
        />
      );
    }
    if (shape === 'dashedCircle') {
      return (
        <span style={{
          ...base,
          borderRadius: '50%',
          borderStyle: 'dashed',
        }}
        />
      );
    }
    if (shape === 'pill') {
      return (
        <span style={{
          ...base,
          width: 24,
          borderRadius: 8,
        }}
        />
      );
    }
    return (
      <span style={{
        ...base,
        borderRadius: '50%',
      }}
      />
    );
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        position: 'absolute',
        bottom: 12,
        left: 12,
        zIndex: 5,
        width: open ? 260 : 'auto',
        p: 1,
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
      }}
      >
        <Typography variant="subtitle2">{t('Legend')}</Typography>
        <IconButton size="small" onClick={() => setOpen(o => !o)} aria-label={t('Toggle legend')}>
          {open ? <ExpandMore fontSize="small" /> : <ExpandLess fontSize="small" />}
        </IconButton>
      </div>
      {!open && (
        // Minimal verdict-colour key stays visible when the legend is folded (e.g. a side panel open),
        // so the analyst can still read what a coloured verdict means.
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          marginTop: 4,
        }}
        >
          {verdictColors.map(c => (
            <Tooltip key={c.label} title={c.label}>
              <span style={{
                width: 12,
                height: 12,
                flex: '0 0 auto',
                borderRadius: '50%',
                background: c.color,
              }}
              />
            </Tooltip>
          ))}
        </div>
      )}
      {open && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
          marginTop: 4,
        }}
        >
          {shapes.map(s => (
            <div
              key={s.label}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              {renderShape(s.shape)}
              <Typography variant="caption" color="text.secondary">{s.label}</Typography>
            </div>
          ))}
          <div style={{
            height: 1,
            background: theme.palette.divider,
            margin: '2px 0',
          }}
          />
          {colors.map(c => (
            <div
              key={c.label}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <span style={{
                width: 16,
                height: 3,
                flex: '0 0 auto',
                borderRadius: 2,
                background: c.color,
              }}
              />
              <Typography variant="caption" color="text.secondary">{c.label}</Typography>
            </div>
          ))}
        </div>
      )}
    </Paper>
  );
};

export default AttackPathLegend;
