import { InfoOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type KeyboardEvent, memo, useEffect, useId, useMemo, useRef, useState } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import useCountUp from '../../../../../../../utils/hooks/useCountUp';
import useSvgVisibilityPause from '../../../../../../../utils/hooks/useSvgVisibilityPause';

interface OrbitPlatform {
  id: string;
  name: string;
  type: string;
  logo?: string;
}

interface DomainBreakdown {
  key: string; // expectation type: PREVENTION / DETECTION / VULNERABILITY / ...
  success: number; // validations stopped
  failed: number; // validations breached
}

interface Props {
  score: number; // 0..100, higher = more exposed
  gaps: number;
  validations: number;
  platforms: OrbitPlatform[];
  // Per-pillar validation counts, used to explain how the aggregate exposure is built.
  breakdown?: DomainBreakdown[];
  /** Drill into the gaps behind the score (opens the data drawer). */
  onInvestigate?: () => void;
}

// Human label per expectation-type pillar.
const PILLAR_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Manual',
  CHALLENGE: 'Challenge',
  ARTICLE: 'Article',
};

// Distinct accent per security-platform category.
const PLATFORM_COLORS: Record<string, string> = {
  EDR: '#0fbcff',
  XDR: '#00bcd4',
  SIEM: '#ffb300',
  SOAR: '#9575cd',
  NDR: '#26a96c',
  ISPM: '#ff7043',
  LLM_FIREWALL: '#00f1bd',
  AI_GATEWAY: '#7e57c2',
};

const abbreviate = (type: string): string => {
  if (!type) return '?';
  if (type.length <= 4) return type;
  return type.split('_').map(p => p[0]).join('').slice(0, 4);
};

const polar = (cx: number, cy: number, r: number, deg: number) => {
  const rad = (deg * Math.PI) / 180;
  return {
    x: cx + r * Math.cos(rad),
    y: cy + r * Math.sin(rad),
  };
};

const arc = (cx: number, cy: number, r: number, a0: number, a1: number) => {
  const s = polar(cx, cy, r, a0);
  const e = polar(cx, cy, r, a1);
  const large = a1 - a0 > 180 ? 1 : 0;
  return `M ${s.x} ${s.y} A ${r} ${r} 0 ${large} 1 ${e.x} ${e.y}`;
};

const START = -90;
const SWEEP = 360;

/**
 * The exposure orb - a translucent sphere wrapped by a gradient score ring and
 * a slow orbit of glowing nodes. Modern, glassy and alive. The raw "/ 100"
 * scale is intentionally hidden and surfaced on hover, keeping the face clean.
 */
const ExposureConsole: FunctionComponent<Props> = ({ score, gaps, validations, platforms, breakdown = [], onInvestigate }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const gradId = useId();
  const glassId = useId();
  const glossId = useId();
  // Freeze the SMIL timeline (orbit + node pulses) while the tab is hidden so
  // the browser never has to reconcile minutes of missed loops on refocus.
  const svgRef = useRef<SVGSVGElement>(null);
  useSvgVisibilityPause(svgRef);

  const band = useMemo(() => {
    if (score < 25) return {
      label: t('Low exposure'),
      color: theme.palette.success.main,
    };
    if (score < 50) return {
      label: t('Moderate exposure'),
      color: theme.palette.warning.main,
    };
    if (score < 75) return {
      label: t('High exposure'),
      color: '#ff7043',
    };
    return {
      label: t('Critical exposure'),
      color: theme.palette.error.main,
    };
  }, [score, t, theme]);
  const { color } = band;

  const [explainOpen, setExplainOpen] = useState(false);
  const stopped = Math.max(validations - gaps, 0);
  const resilience = validations > 0 ? Math.round((stopped / validations) * 100) : 0;

  // Per-pillar contribution to the aggregate exposure (only pillars that actually ran).
  const pillars = useMemo(() => breakdown
    .map(d => ({
      key: d.key,
      success: d.success,
      failed: d.failed,
      total: d.success + d.failed,
    }))
    .filter(d => d.total > 0), [breakdown]);

  // Severity bands (kept in sync with `band` above) surfaced in the explanation dialog.
  const bands = useMemo(() => [
    {
      range: '0 - 24',
      label: t('Low exposure'),
      color: theme.palette.success.main,
      desc: t('Your controls stopped almost every validated attack.'),
    },
    {
      range: '25 - 49',
      label: t('Moderate exposure'),
      color: theme.palette.warning.main,
      desc: t('A meaningful share of attacks got through - worth reviewing.'),
    },
    {
      range: '50 - 74',
      label: t('High exposure'),
      color: '#ff7043',
      desc: t('More than half of the validated attacks were not stopped.'),
    },
    {
      range: '75 - 100',
      label: t('Critical exposure'),
      color: theme.palette.error.main,
      desc: t('Most validated attacks breached your controls.'),
    },
  ], [t, theme]);

  const animated = useCountUp(score, 1400);
  const [progress, setProgress] = useState(0);
  useEffect(() => {
    const timeout = setTimeout(() => setProgress(score / 100), 60);
    return () => clearTimeout(timeout);
  }, [score]);

  // viewBox is large enough that orbiting nodes (r=100 + node radius + pulse)
  // never get clipped by the widget container.
  const size = 256;
  const cx = size / 2;
  const cy = size / 2;
  const rRing = 84;
  const rOrbit = 102;
  const dark = theme.palette.mode === 'dark';

  // The score ring fills smoothly by animating stroke-dashoffset on a FULL-circle path (a real,
  // animatable property), instead of morphing a partial-arc `d` (which browsers can't transition,
  // so the old ring "jumped" / slid instead of filling and never lined up with the track).
  const clampedProgress = Math.min(Math.max(progress, 0), 1);
  const circumference = 2 * Math.PI * rRing;
  const ring = arc(cx, cy, rRing, START, START + 359.99);
  const dashOffset = circumference * (1 - clampedProgress);
  // The end-of-fill marker sits at the ring start (top) and is rotated into place, so it animates in
  // lock-step with the fill.
  const markerBase = polar(cx, cy, rRing, START);

  // Orbit the connected security platforms; each node is a real platform.
  const orbit = platforms.slice(0, 8);
  const orbitCount = Math.max(orbit.length, 1);

  // Clicking the orb (or the verdict chip) opens the explanation dialog rather than jumping straight
  // to a raw list - the drill-down into breached validations lives as an explicit action inside that
  // dialog. These a11y props make the orb/chip keyboard-operable for the same "explain" action.
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

  return (
    <Box
      sx={{
        position: 'relative',
        flex: 1,
        minHeight: 0,
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Box sx={{
        position: 'relative',
        flex: 1,
        minHeight: 0,
        width: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
      >
        <svg
          ref={svgRef}
          className="noDrag"
          viewBox={`0 0 ${size} ${size}`}
          onClick={() => setExplainOpen(true)}
          {...explainA11yProps}
          style={{
            maxHeight: '100%',
            maxWidth: '100%',
            overflow: 'visible',
            cursor: 'pointer',
          }}
        >
          <defs>
            <linearGradient id={gradId} x1="0%" y1="100%" x2="100%" y2="0%">
              <stop offset="0%" stopColor={theme.palette.success.main} />
              <stop offset="45%" stopColor={theme.palette.warning.main} />
              <stop offset="100%" stopColor={theme.palette.error.main} />
            </linearGradient>
            <radialGradient id={glassId} cx="38%" cy="30%" r="75%">
              <stop offset="0%" stopColor={alpha(color, 0.32)} />
              <stop offset="55%" stopColor={alpha(color, 0.1)} />
              <stop offset="100%" stopColor={alpha(dark ? '#000000' : '#ffffff', 0.06)} />
            </radialGradient>
            {/* soft-edged gloss: a radial gradient instead of a blur() filter, which would
                be re-evaluated on every SMIL animation frame of this SVG */}
            <radialGradient id={glossId} cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor={alpha('#ffffff', dark ? 0.14 : 0.5)} />
              <stop offset="70%" stopColor={alpha('#ffffff', dark ? 0.06 : 0.2)} />
              <stop offset="100%" stopColor={alpha('#ffffff', 0)} />
            </radialGradient>
          </defs>

          {/* glassy translucent sphere */}
          <circle cx={cx} cy={cy} r={rRing - 6} fill={`url(#${glassId})`} stroke={alpha(color, 0.18)} strokeWidth={1} />
          {/* inner hairline rings for depth */}
          <circle cx={cx} cy={cy} r={rRing - 24} fill="none" stroke={dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)'} strokeWidth={1} />
          <ellipse cx={cx} cy={cy} rx={rRing - 10} ry={(rRing - 10) / 3} fill="none" stroke={alpha(color, 0.12)} strokeWidth={1} />
          {/* glossy top highlight */}
          <ellipse cx={cx - 14} cy={cy - 34} rx={40} ry={20} fill={`url(#${glossId})`} />

          {/* connected security platforms, slowly orbiting the sphere */}
          <ellipse cx={cx} cy={cy} rx={rOrbit} ry={rOrbit} fill="none" stroke={dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)'} strokeWidth={1} strokeDasharray="2 6" />
          <g>
            <animateTransform
              attributeName="transform"
              type="rotate"
              from={`0 ${cx} ${cy}`}
              to={`360 ${cx} ${cy}`}
              dur="90s"
              repeatCount="indefinite"
            />
            {orbit.map((p, i) => {
              const pos = polar(cx, cy, rOrbit, (360 / orbitCount) * i - 90);
              const c = PLATFORM_COLORS[p.type] ?? theme.palette.primary.main;
              return (
                <g key={p.id}>
                  {/* counter-rotation keeps each node label upright while orbiting */}
                  <animateTransform
                    attributeName="transform"
                    type="rotate"
                    from={`360 ${pos.x} ${pos.y}`}
                    to={`0 ${pos.x} ${pos.y}`}
                    dur="90s"
                    repeatCount="indefinite"
                  />
                  <title>{`${p.name} (${p.type})`}</title>
                  <circle cx={pos.x} cy={pos.y} r={13} fill={dark ? '#0c1526' : '#f4f8fc'} stroke={alpha(c, 0.7)} strokeWidth={1.2} />
                  <circle cx={pos.x} cy={pos.y} r={13} fill={dark ? alpha(c, 0.16) : alpha(c, 0.1)} />
                  <circle cx={pos.x} cy={pos.y} r={13} fill="none" stroke={alpha(c, 0.35)} strokeWidth={1}>
                    <animate attributeName="r" values="13;16;13" dur={`${3 + (i % 3)}s`} repeatCount="indefinite" />
                    <animate attributeName="opacity" values="0.5;0;0.5" dur={`${3 + (i % 3)}s`} repeatCount="indefinite" />
                  </circle>
                  {p.logo
                    ? (
                        <>
                          <clipPath id={`orb-node-${p.id}`}>
                            <circle cx={pos.x} cy={pos.y} r={10} />
                          </clipPath>
                          <image
                            href={p.logo}
                            x={pos.x - 10}
                            y={pos.y - 10}
                            width={20}
                            height={20}
                            clipPath={`url(#orb-node-${p.id})`}
                            preserveAspectRatio="xMidYMid slice"
                          />
                        </>
                      )
                    : (
                        <text
                          x={pos.x}
                          y={pos.y}
                          textAnchor="middle"
                          dominantBaseline="central"
                          fill={c}
                          style={{
                            fontSize: 8,
                            fontWeight: 700,
                            letterSpacing: '0.04em',
                          }}
                        >
                          {abbreviate(p.type)}
                        </text>
                      )}
                  {/* the real platform name, kept upright by the counter-rotation */}
                  <text
                    x={pos.x}
                    y={pos.y + 21}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fill={theme.palette.text.secondary}
                    style={{
                      fontSize: 7.5,
                      letterSpacing: '0.03em',
                    }}
                  >
                    {p.name.length > 22 ? `${p.name.slice(0, 21).trimEnd()}\u2026` : p.name}
                  </text>
                </g>
              );
            })}
          </g>

          {/* score ring: full-circle track + a dash-offset fill that grows from the top clockwise.
              The glow is a wider translucent stroke underneath (NOT a drop-shadow filter: the
              orbit animation repaints this SVG every frame, and re-evaluating a filter effect
              per frame is what made the ring glow stutter). */}
          <path d={ring} fill="none" stroke={dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'} strokeWidth={5} strokeLinecap="round" />
          <path
            d={ring}
            fill="none"
            stroke={`url(#${gradId})`}
            strokeWidth={11}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={dashOffset}
            opacity={0.28}
            style={{ transition: 'stroke-dashoffset 1.4s cubic-bezier(0.22, 1, 0.36, 1)' }}
          />
          <path
            d={ring}
            fill="none"
            stroke={`url(#${gradId})`}
            strokeWidth={5}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={dashOffset}
            style={{ transition: 'stroke-dashoffset 1.4s cubic-bezier(0.22, 1, 0.36, 1)' }}
          />
          <g
            style={{
              transform: `rotate(${clampedProgress * SWEEP}deg)`,
              transformOrigin: `${cx}px ${cy}px`,
              transition: 'transform 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
            }}
          >
            {/* halo ring drawn as geometry instead of a drop-shadow filter */}
            <circle cx={markerBase.x} cy={markerBase.y} r={8.5} fill={alpha(color, 0.3)} />
            <circle cx={markerBase.x} cy={markerBase.y} r={4.5} fill={color} />
          </g>

          {/* center readout (dominant-baseline keeps the number optically centered) */}
          <text
            x={cx}
            y={cy - 6}
            textAnchor="middle"
            dominantBaseline="central"
            fill={theme.palette.text.primary}
            style={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 52,
              fontWeight: 500,
            }}
          >
            {Math.round(animated)}
          </text>
          <text
            x={cx}
            y={cy + 28}
            textAnchor="middle"
            dominantBaseline="central"
            fill={color}
            style={{
              fontSize: 10,
              fontWeight: 600,
              letterSpacing: '0.24em',
              textTransform: 'uppercase',
            }}
          >
            {t('At risk')}
          </text>
        </svg>
      </Box>

      <Tooltip title={t('Exposure score {score} / 100 - click to understand how it is computed', { score: Math.round(score) })}>
        <Box
          className="noDrag"
          onClick={() => setExplainOpen(true)}
          {...explainA11yProps}
          sx={{
            'display': 'inline-flex',
            'alignItems': 'center',
            'gap': 0.75,
            'paddingInline': 1,
            'height': 22,
            'borderRadius': 999,
            'cursor': 'pointer',
            'border': `1px solid ${alpha(color, 0.3)}`,
            'background': alpha(color, 0.1),
            'transition': 'background-color 0.15s ease',
            '&:hover': { background: alpha(color, 0.2) },
          }}
        >
          <Box sx={{
            width: 6,
            height: 6,
            borderRadius: '50%',
            background: color,
            boxShadow: `0 0 6px ${color}`,
          }}
          />
          <Typography sx={{
            fontSize: 10,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color,
          }}
          >
            {band.label}
          </Typography>
          <Typography sx={{
            fontSize: 10,
            color: 'text.secondary',
          }}
          >
            {(() => {
              if (gaps === 0 && validations === 0) return t('No validations yet');
              if (gaps === 0) return t('All controls holding');
              return t('{count} gaps to remediate', { count: gaps });
            })()}
          </Typography>
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
          {t('Adversarial exposure score')}
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
              {Math.round(score)}
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
                {validations === 0
                  ? t('No validations have run yet.')
                  : t('{gaps} of {validations} validations breached your controls.', {
                      gaps,
                      validations,
                    })}
              </Typography>
            </Box>
          </Box>

          <Typography variant="h4" gutterBottom>{t('What it measures')}</Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            {t('The adversarial exposure score is the share of security validations your controls failed to stop. It runs from 0 to 100 and, unlike a resilience score, a HIGHER number is WORSE - it means you are more exposed.')}
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
            {t('exposure')}
            {' = '}
            <Box component="span" sx={{ color: theme.palette.error.main }}>{`${gaps} ${t('breached')}`}</Box>
            {' / '}
            <Box component="span">{`${validations} ${t('total')}`}</Box>
            {` x 100 = `}
            <Box
              component="span"
              sx={{
                color,
                fontWeight: 700,
              }}
            >
              {Math.round(score)}
            </Box>
          </Box>
          <Typography variant="body2" color="text.secondary" paragraph>
            {t('Every validation counts equally - there is no per-pillar weighting. Pillars that run more validations therefore weigh more on the overall score. It is the exact inverse of the resilience gauges below: exposure = 100 - overall resilience ({resilience}%).', { resilience })}
          </Typography>

          {/* Visual per-pillar breakdown: stacked stopped/breached bars */}
          {pillars.length > 0 && (
            <>
              <Typography variant="h4" gutterBottom sx={{ marginTop: 2 }}>{t('Breakdown by pillar')}</Typography>
              {pillars.map((p) => {
                const breachPct = Math.round((p.failed / p.total) * 100);
                return (
                  <Box key={p.key} sx={{ marginBottom: 1.5 }}>
                    <Box sx={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      marginBottom: 0.5,
                    }}
                    >
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {t(PILLAR_LABELS[p.key.toUpperCase()] ?? p.key)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t('{failed} / {total} breached ({pct}%)', {
                          failed: p.failed,
                          total: p.total,
                          pct: breachPct,
                        })}
                      </Typography>
                    </Box>
                    <Tooltip title={t('{stopped} stopped - {breached} breached', {
                      stopped: p.success,
                      breached: p.failed,
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
                          width: `${100 - breachPct}%`,
                          background: theme.palette.success.main,
                        }}
                        />
                        <Box sx={{
                          width: `${breachPct}%`,
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
                  <Typography variant="body2" color="text.secondary">{t('Stopped')}</Typography>
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
                  <Typography variant="body2" color="text.secondary">{t('Breached')}</Typography>
                </Box>
              </Box>
            </>
          )}

          {/* Severity scale: how the number maps to a verdict + ring color */}
          <Typography variant="h4" gutterBottom sx={{ marginTop: 2 }}>{t('Severity bands')}</Typography>
          {bands.map((b) => {
            const isCurrent = b.label === band.label;
            return (
              <Box
                key={b.range}
                sx={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 1.5,
                  paddingBlock: 0.75,
                  paddingInline: 1,
                  borderRadius: 1,
                  background: isCurrent ? alpha(b.color, 0.1) : 'transparent',
                  border: `1px solid ${isCurrent ? alpha(b.color, 0.4) : 'transparent'}`,
                }}
              >
                <Box sx={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  marginTop: 0.5,
                  flexShrink: 0,
                  background: b.color,
                  boxShadow: `0 0 6px ${alpha(b.color, 0.7)}`,
                }}
                />
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {`${b.range} - ${b.label}`}
                    {isCurrent ? ` - ${t('current')}` : ''}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">{b.desc}</Typography>
                </Box>
              </Box>
            );
          })}

        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={() => setExplainOpen(false)}>{t('Close')}</Button>
          {onInvestigate && (
            <Button
              variant="contained"
              color="primary"
              onClick={() => {
                setExplainOpen(false);
                onInvestigate();
              }}
            >
              {t('Investigate breached validations')}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default memo(ExposureConsole);
