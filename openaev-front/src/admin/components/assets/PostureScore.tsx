import { InfoOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type KeyboardEvent, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type PostureBreakdownEntry } from './useExpectationPosture';

// Human label per expectation-type pillar (same taxonomy as the home
// command-center exposure breakdown).
const PILLAR_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Manual',
  CHALLENGE: 'Challenge',
  ARTICLE: 'Article',
};

interface Props {
  /** Expectations met (SUCCESS) in this scope. */
  success: number;
  /** Expectations missed (FAILED) in this scope. */
  failed: number;
  /** Per-expectation-type contribution (only types that actually ran). */
  breakdown: PostureBreakdownEntry[];
  loading?: boolean;
  /** Adjusts the dialog wording to the scored entity. */
  scope?: 'asset' | 'security-platform' | 'asset-group';
}

/**
 * The posture score hero element: a mini ring gauge + score, rendered in the
 * hero stats row but visually distinct from the plain HeroStat counters.
 * Like the home exposure orb, clicking it opens a dialog explaining the
 * rationale behind the number (formula, per-pillar breakdown, severity bands).
 * Posture = share of validated expectations the defenses met - HIGHER is
 * BETTER (the inverse reading of the home exposure score).
 */
const PostureScore: FunctionComponent<Props> = ({ success, failed, breakdown, loading = false, scope = 'asset' }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [explainOpen, setExplainOpen] = useState(false);

  const total = success + failed;
  const score = total > 0 ? Math.round((success / total) * 100) : null;

  // Scope-specific wording (the score semantics are identical, only the
  // scored entity changes).
  let strings;
  if (scope === 'security-platform') {
    strings = {
      title: t('Security platform posture score'),
      empty: t('No expectations have been validated by this security platform yet.'),
      strongDesc: t('This security platform stopped or detected almost every validated expectation.'),
      criticalDesc: t('Most validated expectations were missed by this security platform.'),
      verdict: t('{met} of {total} validated expectations were met by this security platform.', {
        met: success,
        total,
      }),
      measures: t('The posture score is the share of security validations (prevention, detection, vulnerability and manual expectations) this security platform met. It runs from 0 to 100 and, unlike the home exposure score, a HIGHER number is BETTER - it means this platform stops more of what it is tested against.'),
    };
  } else if (scope === 'asset-group') {
    strings = {
      title: t('Asset group posture score'),
      empty: t('No expectations have been validated on the assets of this group yet.'),
      strongDesc: t('Your defenses met almost every validated expectation on the assets of this group.'),
      criticalDesc: t('Most validated expectations were breached on the assets of this group.'),
      verdict: t('{met} of {total} validated expectations were met on the assets of this group.', {
        met: success,
        total,
      }),
      measures: t('The posture score is the share of security validations (prevention, detection, vulnerability and manual expectations) the defenses of the assets in this group met. It runs from 0 to 100 and, unlike the home exposure score, a HIGHER number is BETTER - it means this group is better defended.'),
    };
  } else {
    strings = {
      title: t('Asset posture score'),
      empty: t('No expectations have been validated on this asset yet.'),
      strongDesc: t('Your defenses met almost every validated expectation on this asset.'),
      criticalDesc: t('Most validated expectations were breached on this asset.'),
      verdict: t('{met} of {total} validated expectations were met on this asset.', {
        met: success,
        total,
      }),
      measures: t('The posture score is the share of security validations (prevention, detection, vulnerability and manual expectations) the defenses of this asset met. It runs from 0 to 100 and, unlike the home exposure score, a HIGHER number is BETTER - it means this asset is better defended.'),
    };
  }

  const band = (() => {
    if (score === null) return {
      label: t('No validations yet'),
      color: theme.palette.text.secondary,
      desc: strings.empty,
    };
    if (score >= 75) return {
      label: t('Strong posture'),
      color: theme.palette.success.main,
      desc: strings.strongDesc,
    };
    if (score >= 50) return {
      label: t('Moderate posture'),
      color: theme.palette.warning.main,
      desc: t('A meaningful share of validations got through - worth reviewing.'),
    };
    if (score >= 25) return {
      label: t('Weak posture'),
      color: '#ff7043',
      desc: t('More than half of the validated expectations were missed.'),
    };
    return {
      label: t('Critical posture'),
      color: theme.palette.error.main,
      desc: strings.criticalDesc,
    };
  })();
  const { color } = band;

  // Severity scale (kept in sync with `band` above) surfaced in the dialog.
  const bands = [
    {
      range: '75 - 100',
      label: t('Strong posture'),
      color: theme.palette.success.main,
      desc: strings.strongDesc,
    },
    {
      range: '50 - 74',
      label: t('Moderate posture'),
      color: theme.palette.warning.main,
      desc: t('A meaningful share of validations got through - worth reviewing.'),
    },
    {
      range: '25 - 49',
      label: t('Weak posture'),
      color: '#ff7043',
      desc: t('More than half of the validated expectations were missed.'),
    },
    {
      range: '0 - 24',
      label: t('Critical posture'),
      color: theme.palette.error.main,
      desc: strings.criticalDesc,
    },
  ];

  const pillars = breakdown.filter(entry => entry.success + entry.failed > 0);

  // Mini ring gauge geometry (sized to sit next to the 30px HeroStat icon boxes).
  const ringSize = 30;
  const ringRadius = 12;
  const circumference = 2 * Math.PI * ringRadius;
  const fill = score === null ? 0 : score / 100;

  const explainA11yProps = {
    'role': 'button',
    'tabIndex': 0,
    'aria-label': t('How is this score computed?'),
    'onKeyDown': (event: KeyboardEvent<Element>) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        setExplainOpen(true);
      }
    },
  };

  const displayValue = (() => {
    if (loading) return '-';
    return score === null ? '-' : score;
  })();

  return (
    <>
      <Tooltip title={t('Posture score - click to understand how it is computed')}>
        <Box
          onClick={() => setExplainOpen(true)}
          {...explainA11yProps}
          sx={{
            'display': 'flex',
            'alignItems': 'center',
            'gap': 1,
            'minWidth': 0,
            'padding': 0.5,
            'borderRadius': 1,
            'cursor': 'pointer',
            'transition': 'background-color 120ms',
            '&:hover': { backgroundColor: alpha(color, 0.08) },
          }}
        >
          {/* Ring gauge instead of the standard tinted icon box: the posture
              score reads as a gauge, not a counter. */}
          <Box sx={{
            position: 'relative',
            width: ringSize,
            height: ringSize,
            flexShrink: 0,
          }}
          >
            <svg width={ringSize} height={ringSize} viewBox={`0 0 ${ringSize} ${ringSize}`} style={{ transform: 'rotate(-90deg)' }}>
              <circle
                cx={ringSize / 2}
                cy={ringSize / 2}
                r={ringRadius}
                fill={alpha(color, 0.1)}
                stroke={alpha(theme.palette.text.primary, 0.12)}
                strokeWidth={2.5}
              />
              <circle
                cx={ringSize / 2}
                cy={ringSize / 2}
                r={ringRadius}
                fill="none"
                stroke={color}
                strokeWidth={2.5}
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={circumference * (1 - fill)}
                style={{ transition: 'stroke-dashoffset 800ms cubic-bezier(0.22, 1, 0.36, 1)' }}
              />
            </svg>
            <Box sx={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            >
              <Box sx={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: color,
                boxShadow: `0 0 6px ${alpha(color, 0.8)}`,
              }}
              />
            </Box>
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 18,
              fontWeight: 500,
              lineHeight: 1.05,
              color: score === null ? 'text.primary' : color,
            }}
            >
              {displayValue}
            </Typography>
            <Typography sx={{
              fontSize: 9.5,
              fontWeight: 600,
              letterSpacing: '0.07em',
              textTransform: 'uppercase',
              color: 'text.secondary',
            }}
            >
              {t('Posture score')}
            </Typography>
          </Box>
        </Box>
      </Tooltip>

      <Dialog
        open={explainOpen}
        onClose={() => setExplainOpen(false)}
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
          {strings.title}
        </DialogTitle>
        <DialogContent>
          {/* Hero: the current score, its band and the plain-language verdict */}
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            padding: 2,
            borderRadius: 1,
            marginBottom: 2,
            border: `1px solid ${alpha(color, 0.3)}`,
            background: alpha(color, 0.08),
          }}
          >
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 44,
              fontWeight: 500,
              lineHeight: 1,
              color,
            }}
            >
              {score === null ? '-' : score}
            </Typography>
            <Box>
              <Typography sx={{
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                color,
              }}
              >
                {band.label}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {total === 0 ? strings.empty : strings.verdict}
              </Typography>
            </Box>
          </Box>

          <Typography variant="h4" gutterBottom>{t('What it measures')}</Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            {strings.measures}
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
            {t('posture')}
            {' = '}
            <Box component="span" sx={{ color: theme.palette.success.main }}>{`${success} ${t('met')}`}</Box>
            {' / '}
            <Box component="span">{`${total} ${t('total')}`}</Box>
            {` x 100 = `}
            <Box
              component="span"
              sx={{
                color,
                fontWeight: 700,
              }}
            >
              {score === null ? '-' : score}
            </Box>
          </Box>
          <Typography variant="body2" color="text.secondary" paragraph>
            {t('Every validated expectation counts equally - there is no per-pillar weighting. Pending or unscored expectations are excluded until they resolve.')}
          </Typography>

          {/* Visual per-pillar breakdown: stacked met/missed bars */}
          {pillars.length > 0 && (
            <>
              <Typography variant="h4" gutterBottom sx={{ marginTop: 2 }}>{t('Breakdown by pillar')}</Typography>
              {pillars.map((pillar) => {
                const pillarTotal = pillar.success + pillar.failed;
                const metPct = Math.round((pillar.success / pillarTotal) * 100);
                return (
                  <Box key={pillar.key} sx={{ marginBottom: 1.5 }}>
                    <Box sx={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      marginBottom: 0.5,
                    }}
                    >
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {t(PILLAR_LABELS[pillar.key.toUpperCase()] ?? pillar.key)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t('{met} / {total} met ({pct}%)', {
                          met: pillar.success,
                          total: pillarTotal,
                          pct: metPct,
                        })}
                      </Typography>
                    </Box>
                    <Tooltip title={t('{met} met - {missed} missed', {
                      met: pillar.success,
                      missed: pillar.failed,
                    })}
                    >
                      <Box sx={{
                        display: 'flex',
                        height: 8,
                        borderRadius: 999,
                        overflow: 'hidden',
                        background: theme.palette.action.hover,
                      }}
                      >
                        <Box sx={{
                          width: `${metPct}%`,
                          background: theme.palette.success.main,
                        }}
                        />
                        <Box sx={{
                          width: `${100 - metPct}%`,
                          background: theme.palette.error.main,
                        }}
                        />
                      </Box>
                    </Tooltip>
                  </Box>
                );
              })}
              <Box sx={{
                display: 'flex',
                gap: 2,
                marginTop: 1,
              }}
              >
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                }}
                >
                  <Box sx={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    background: theme.palette.success.main,
                  }}
                  />
                  <Typography variant="body2" color="text.secondary">{t('Met')}</Typography>
                </Box>
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                }}
                >
                  <Box sx={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    background: theme.palette.error.main,
                  }}
                  />
                  <Typography variant="body2" color="text.secondary">{t('Missed')}</Typography>
                </Box>
              </Box>
            </>
          )}

          {/* Severity scale: how the number maps to a verdict + gauge color */}
          <Typography variant="h4" gutterBottom sx={{ marginTop: 2 }}>{t('Severity bands')}</Typography>
          {bands.map((entry) => {
            const isCurrent = entry.label === band.label;
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
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setExplainOpen(false)}>{t('Close')}</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default PostureScore;
