import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useEffect, useId, useMemo, useState } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import useCountUp from '../../../../../../../utils/hooks/useCountUp';

interface OrbitPlatform {
  id: string;
  name: string;
  type: string;
  logo?: string;
}

interface Props {
  score: number; // 0..100, higher = more exposed
  gaps: number;
  validations: number;
  platforms: OrbitPlatform[];
}

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
const ExposureConsole: FunctionComponent<Props> = ({ score, gaps, validations, platforms }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const gradId = useId();
  const glassId = useId();

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

  const track = arc(cx, cy, rRing, START, START + 359.99);
  const valueEnd = START + SWEEP * Math.max(progress, 0.001);
  const value = arc(cx, cy, rRing, START, Math.min(valueEnd, START + 359.99));
  const marker = polar(cx, cy, rRing, valueEnd);

  // Orbit the connected security platforms; each node is a real platform.
  const orbit = platforms.slice(0, 8);
  const orbitCount = Math.max(orbit.length, 1);

  return (
    <Box
      sx={{
        'position': 'relative',
        'flex': 1,
        'minHeight': 0,
        'width': '100%',
        'display': 'flex',
        'flexDirection': 'column',
        'alignItems': 'center',
        'justifyContent': 'center',
        '@keyframes orb-orbit': { to: { transform: 'rotate(360deg)' } },
        '@keyframes orb-breathe': {
          '0%, 100%': { opacity: 0.55 },
          '50%': { opacity: 0.9 },
        },
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
          viewBox={`0 0 ${size} ${size}`}
          style={{
            maxHeight: '100%',
            maxWidth: '100%',
            overflow: 'visible',
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
          </defs>

          {/* glassy translucent sphere */}
          <circle cx={cx} cy={cy} r={rRing - 6} fill={`url(#${glassId})`} stroke={alpha(color, 0.18)} strokeWidth={1} />
          {/* inner hairline rings for depth */}
          <circle cx={cx} cy={cy} r={rRing - 24} fill="none" stroke={dark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)'} strokeWidth={1} />
          <ellipse cx={cx} cy={cy} rx={rRing - 10} ry={(rRing - 10) / 3} fill="none" stroke={alpha(color, 0.12)} strokeWidth={1} />
          {/* glossy top highlight */}
          <ellipse cx={cx - 14} cy={cy - 34} rx={34} ry={16} fill={alpha('#ffffff', dark ? 0.14 : 0.5)} style={{ filter: 'blur(6px)' }} />

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

          {/* score ring */}
          <path d={track} fill="none" stroke={dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'} strokeWidth={5} strokeLinecap="round" />
          <path
            d={value}
            fill="none"
            stroke={`url(#${gradId})`}
            strokeWidth={5}
            strokeLinecap="round"
            style={{
              transition: 'all 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
              filter: `drop-shadow(0 0 5px ${alpha(color, 0.7)})`,
            }}
          />
          <circle
            cx={marker.x}
            cy={marker.y}
            r={4.5}
            fill={color}
            style={{
              transition: 'all 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
              filter: `drop-shadow(0 0 7px ${color})`,
            }}
          />

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

      <Tooltip title={t('Exposure score {score} / 100 - share of validations your controls failed', { score: Math.round(score) })}>
        <Box sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 0.75,
          paddingInline: 1,
          height: 22,
          borderRadius: 999,
          cursor: 'help',
          border: `1px solid ${alpha(color, 0.3)}`,
          background: alpha(color, 0.1),
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
    </Box>
  );
};

export default memo(ExposureConsole);
