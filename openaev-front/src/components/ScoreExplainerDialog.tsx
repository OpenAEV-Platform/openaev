import { InfoOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from './i18n';

// Shared score-explainer dialog (posture score + attack-path chokepoints).
// One coloured segment of a breakdown bar (e.g. met=green / missed=red, or a single exposure fill).
export interface ScoreBarSegment {
  widthPct: number;
  color: string;
}

// One row of the "breakdown" section: a label, a right-aligned value, and a segmented bar. Optionally
// clickable (the chokepoints use this to focus an endpoint).
export interface ScoreBreakdownRow {
  key: string;
  label: string;
  valueLabel: string;
  segments: ScoreBarSegment[];
  sublabel?: string;
  tooltip?: string;
  onClick?: () => void;
}

export interface ScoreBand {
  range: string;
  label: string;
  color: string;
  desc: string;
}

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Hero number (or '-' when not applicable). */
  score: number | string | null;
  scoreColor: string;
  bandLabel: string;
  verdict: string;
  measures: string;
  /** Formula box content (already composed with any coloured spans). */
  formula: ReactNode;
  footnote?: string;
  breakdownTitle?: string;
  breakdown?: ScoreBreakdownRow[];
  bandsTitle?: string;
  bands?: ScoreBand[];
  /** Legend chips under the breakdown bars (e.g. Met / Missed). */
  legend?: {
    color: string;
    label: string;
  }[];
}

/**
 * Shared "explain this score" dialog: a hero (score + band + verdict), a plain-language "what it
 * measures", the exact formula, an optional per-item breakdown of segmented bars, and an optional
 * severity-band scale. Used by the asset posture score and the attack-path chokepoints so every score in
 * the app is explained with the same layout — only the numbers, formula and wording differ.
 */
const ScoreExplainerDialog: FunctionComponent<Props> = ({
  open,
  onClose,
  title,
  score,
  scoreColor,
  bandLabel,
  verdict,
  measures,
  formula,
  footnote,
  breakdownTitle,
  breakdown = [],
  bandsTitle,
  bands = [],
  legend = [],
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{ sx: { borderRadius: 1 } }}
    >
      <DialogTitle sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        <InfoOutlined color="primary" />
        {title}
      </DialogTitle>
      <DialogContent>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          padding: 2,
          borderRadius: 1,
          marginBottom: 2,
          border: `1px solid ${alpha(scoreColor, 0.3)}`,
          background: alpha(scoreColor, 0.08),
        }}
        >
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 44,
            fontWeight: 500,
            lineHeight: 1,
            color: scoreColor,
          }}
          >
            {score === null ? '-' : score}
          </Typography>
          <Box>
            <Typography sx={{
              fontWeight: 600,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              color: scoreColor,
            }}
            >
              {bandLabel}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {verdict}
            </Typography>
          </Box>
        </Box>

        <Typography variant="h4" gutterBottom>{t('What it measures')}</Typography>
        <Typography variant="body2" color="text.secondary" paragraph>
          {measures}
        </Typography>
        <Box sx={{
          padding: 1.5,
          borderRadius: 1,
          marginBottom: 2,
          fontFamily: 'monospace',
          fontSize: 13,
          textAlign: 'center',
          color: 'text.primary',
          background: theme.palette.action.hover,
          border: `1px solid ${theme.palette.divider}`,
        }}
        >
          {formula}
        </Box>
        {footnote && (
          <Typography variant="body2" color="text.secondary" paragraph>
            {footnote}
          </Typography>
        )}

        {breakdown.length > 0 && (
          <>
            <Typography variant="h4" gutterBottom sx={{ marginTop: 2 }}>{breakdownTitle}</Typography>
            {breakdown.map((row) => {
              const bar = (
                <Box sx={{
                  display: 'flex',
                  height: 8,
                  borderRadius: 999,
                  overflow: 'hidden',
                  background: theme.palette.action.hover,
                }}
                >
                  {row.segments.map((seg, i) => (
                    <Box
                      key={i}
                      sx={{
                        width: `${seg.widthPct}%`,
                        background: seg.color,
                      }}
                    />
                  ))}
                </Box>
              );
              return (
                <Box
                  key={row.key}
                  {...(row.onClick
                    ? {
                        role: 'button',
                        tabIndex: 0,
                        onClick: row.onClick,
                        onKeyDown: (e: React.KeyboardEvent) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            row.onClick?.();
                          }
                        },
                      }
                    : {})}
                  sx={{
                    'marginBottom': 1.5,
                    'borderRadius': 1,
                    'p': row.onClick ? 0.5 : 0,
                    'cursor': row.onClick ? 'pointer' : 'default',
                    '&:hover': row.onClick ? { backgroundColor: 'action.hover' } : undefined,
                    '&:focus-visible': row.onClick
                      ? {
                          outline: `2px solid ${theme.palette.primary.main}`,
                          outlineOffset: -2,
                        }
                      : undefined,
                  }}
                >
                  <Box sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    marginBottom: 0.5,
                  }}
                  >
                    <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap title={row.label}>
                      {row.label}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {row.valueLabel}
                    </Typography>
                  </Box>
                  {row.tooltip ? <Tooltip title={row.tooltip}>{bar}</Tooltip> : bar}
                  {row.sublabel && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      noWrap
                      sx={{
                        display: 'block',
                        mt: 0.25,
                      }}
                    >
                      {row.sublabel}
                    </Typography>
                  )}
                </Box>
              );
            })}
            {legend.length > 0 && (
              <Box sx={{
                display: 'flex',
                gap: 2,
                marginTop: 1,
              }}
              >
                {legend.map(l => (
                  <Box
                    key={l.label}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 0.5,
                    }}
                  >
                    <Box sx={{
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      background: l.color,
                    }}
                    />
                    <Typography variant="body2" color="text.secondary">{l.label}</Typography>
                  </Box>
                ))}
              </Box>
            )}
          </>
        )}

        {bands.length > 0 && (
          <>
            <Typography variant="h4" gutterBottom sx={{ marginTop: 2 }}>{bandsTitle}</Typography>
            {bands.map((entry) => {
              const isCurrent = entry.label === bandLabel;
              return (
                <Box
                  key={entry.range}
                  sx={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 1.5,
                    paddingBlock: 0.75,
                    paddingInline: 1,
                    borderRadius: 1,
                    background: isCurrent ? alpha(entry.color, 0.1) : 'transparent',
                    border: `1px solid ${isCurrent ? alpha(entry.color, 0.4) : 'transparent'}`,
                  }}
                >
                  <Box sx={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    marginTop: 0.5,
                    flexShrink: 0,
                    background: entry.color,
                    boxShadow: `0 0 6px ${alpha(entry.color, 0.7)}`,
                  }}
                  />
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {`${entry.range} - ${entry.label}`}
                      {isCurrent ? ` - ${t('current')}` : ''}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">{entry.desc}</Typography>
                  </Box>
                </Box>
              );
            })}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button variant="outlined" color="primary" onClick={onClose}>{t('Close')}</Button>
      </DialogActions>
    </Dialog>
  );
};

export default ScoreExplainerDialog;
