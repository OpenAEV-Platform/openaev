import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useEffect, useMemo, useState } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { type EsSeries } from '../../../../../../utils/api-types';
import useCountUp from '../../../../../../utils/hooks/useCountUp';
import { isSeriesEmpty, sampleExposureSeries } from './sample/sampleData';
import SamplePreview from './sample/SamplePreview';

interface Props {
  widgetId: string;
  series: EsSeries[];
}

interface BucketScore {
  label: string;
  success: number;
  failed: number;
  exposure: number; // 0..100, higher = worse
}

const GAUGE_START_ANGLE = -225; // degrees, gauge sweeps 270°
const GAUGE_SWEEP = 270;

const polarToCartesian = (cx: number, cy: number, r: number, angleDeg: number) => {
  const rad = (angleDeg * Math.PI) / 180;
  return {
    x: cx + r * Math.cos(rad),
    y: cy + r * Math.sin(rad),
  };
};

const describeArc = (cx: number, cy: number, r: number, startAngle: number, endAngle: number) => {
  const start = polarToCartesian(cx, cy, r, startAngle);
  const end = polarToCartesian(cx, cy, r, endAngle);
  const largeArc = endAngle - startAngle > 180 ? 1 : 0;
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 1 ${end.x} ${end.y}`;
};

/**
 * Computes per-bucket and overall exposure from SUCCESS / FAILED series.
 * Exposure = share of failed expectations (0 = fully protected, 100 = fully exposed).
 */
const computeScores = (series: EsSeries[]): {
  overall: number;
  buckets: BucketScore[];
} => {
  const successSeries = series.find(s => (s.label ?? '').toUpperCase().includes('SUCCESS')) ?? series[0];
  const failedSeries = series.find(s => (s.label ?? '').toUpperCase().includes('FAIL')) ?? series[1];
  const labels = new Map<string, {
    success: number;
    failed: number;
  }>();
  (successSeries?.data ?? []).forEach((d) => {
    if (!d.label) return;
    const entry = labels.get(d.label) ?? {
      success: 0,
      failed: 0,
    };
    entry.success += d.value ?? 0;
    labels.set(d.label, entry);
  });
  (failedSeries?.data ?? []).forEach((d) => {
    if (!d.label) return;
    const entry = labels.get(d.label) ?? {
      success: 0,
      failed: 0,
    };
    entry.failed += d.value ?? 0;
    labels.set(d.label, entry);
  });
  const buckets: BucketScore[] = [...labels.entries()].map(([label, v]) => ({
    label,
    success: v.success,
    failed: v.failed,
    exposure: v.success + v.failed > 0 ? Math.round((v.failed / (v.success + v.failed)) * 100) : 0,
  }));
  const totalSuccess = buckets.reduce((acc, b) => acc + b.success, 0);
  const totalFailed = buckets.reduce((acc, b) => acc + b.failed, 0);
  const overall = totalSuccess + totalFailed > 0
    ? Math.round((totalFailed / (totalSuccess + totalFailed)) * 100)
    : 0;
  return {
    overall,
    buckets,
  };
};

const ExposureScoreWidget: FunctionComponent<Props> = ({ widgetId, series }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const isSample = isSeriesEmpty(series);
  const displaySeries = isSample ? sampleExposureSeries : series;

  const { overall, buckets } = useMemo(() => computeScores(displaySeries), [displaySeries]);

  const band = useMemo(() => {
    if (overall < 25) {
      return {
        label: t('Low exposure'),
        color: theme.palette.success.main,
      };
    }
    if (overall < 50) {
      return {
        label: t('Moderate exposure'),
        color: theme.palette.warning.main,
      };
    }
    if (overall < 75) {
      return {
        label: t('High exposure'),
        color: '#ff7043',
      };
    }
    return {
      label: t('Critical exposure'),
      color: theme.palette.error.main,
    };
  }, [overall, t, theme]);

  const animatedScore = useCountUp(overall, 1400);

  // Animate the arc after mount so the gauge "draws" itself
  const [arcProgress, setArcProgress] = useState(0);
  useEffect(() => {
    const timeout = setTimeout(() => setArcProgress(overall / 100), 60);
    return () => clearTimeout(timeout);
  }, [overall]);

  const size = 200;
  const cx = size / 2;
  const cy = size / 2;
  const radius = 82;
  const trackPath = describeArc(cx, cy, radius, GAUGE_START_ANGLE, GAUGE_START_ANGLE + GAUGE_SWEEP);
  const valueEndAngle = GAUGE_START_ANGLE + GAUGE_SWEEP * Math.max(arcProgress, 0.001);
  const valuePath = describeArc(cx, cy, radius, GAUGE_START_ANGLE, valueEndAngle);
  const gradientId = `exposure-gradient-${widgetId}`;

  return (
    <SamplePreview active={isSample}>
      <div
        style={{
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'space-between',
          overflow: 'hidden',
        }}
      >
        <Box
          sx={{
            'position': 'relative',
            'flex': 1,
            'minHeight': 0,
            'width': '100%',
            'display': 'flex',
            'alignItems': 'center',
            'justifyContent': 'center',
            // slow rotating radar sweep behind the gauge
            '&::before': {
              content: '""',
              position: 'absolute',
              width: 260,
              height: 260,
              borderRadius: '50%',
              background: `conic-gradient(from 0deg, transparent 0deg, ${band.color}22 40deg, transparent 80deg)`,
              animation: 'exposure-sweep 6s linear infinite',
            },
            '@keyframes exposure-sweep': { to: { transform: 'rotate(360deg)' } },
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
              <linearGradient id={gradientId} x1="0%" y1="100%" x2="100%" y2="0%">
                <stop offset="0%" stopColor={theme.palette.success.main} />
                <stop offset="45%" stopColor={theme.palette.warning.main} />
                <stop offset="100%" stopColor={theme.palette.error.main} />
              </linearGradient>
            </defs>
            {/* track */}
            <path
              d={trackPath}
              fill="none"
              stroke={theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)'}
              strokeWidth={12}
              strokeLinecap="round"
            />
            {/* value arc */}
            <path
              d={valuePath}
              fill="none"
              stroke={`url(#${gradientId})`}
              strokeWidth={12}
              strokeLinecap="round"
              style={{
                transition: 'all 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
                filter: `drop-shadow(0 0 6px ${band.color}88)`,
              }}
            />
            {/* needle dot */}
            {(() => {
              const tip = polarToCartesian(cx, cy, radius, valueEndAngle);
              return (
                <circle
                  cx={tip.x}
                  cy={tip.y}
                  r={5}
                  fill={band.color}
                  style={{
                    transition: 'all 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
                    filter: `drop-shadow(0 0 8px ${band.color})`,
                  }}
                />
              );
            })()}
            <text
              x={cx}
              y={cy - 2}
              textAnchor="middle"
              fill={theme.palette.text.primary}
              style={{
                fontFamily: '"Geologica", sans-serif',
                fontSize: 44,
                fontWeight: 600,
              }}
            >
              {Math.round(animatedScore)}
            </text>
            <text
              x={cx}
              y={cy + 20}
              textAnchor="middle"
              fill={theme.palette.text.secondary}
              style={{
                fontSize: 11,
                letterSpacing: '0.14em',
                textTransform: 'uppercase',
              }}
            >
              {t('/ 100')}
            </text>
            <text
              x={cx}
              y={cy + 46}
              textAnchor="middle"
              fill={band.color}
              style={{
                fontFamily: '"Geologica", sans-serif',
                fontSize: 12,
                fontWeight: 600,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
              }}
            >
              {band.label}
            </text>
          </svg>
        </Box>
        {/* per-bucket breakdown */}
        <div
          style={{
            width: '100%',
            display: 'flex',
            gap: theme.spacing(1.5),
            paddingBottom: theme.spacing(0.5),
          }}
        >
          {buckets.slice(0, 4).map((b) => {
            const bucketColor = (() => {
              if (b.exposure < 25) return theme.palette.success.main;
              if (b.exposure < 50) return theme.palette.warning.main;
              if (b.exposure < 75) return '#ff7043';
              return theme.palette.error.main;
            })();
            return (
              <div
                key={b.label}
                style={{
                  flex: 1,
                  minWidth: 0,
                }}
              >
                <Typography
                  variant="body2"
                  sx={{
                    fontSize: 10,
                    textTransform: 'uppercase',
                    letterSpacing: '0.08em',
                    color: 'text.secondary',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {t(b.label)}
                </Typography>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: theme.spacing(0.75),
                  }}
                >
                  <div
                    style={{
                      flex: 1,
                      height: 4,
                      borderRadius: 2,
                      background: theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        width: `${b.exposure}%`,
                        height: '100%',
                        borderRadius: 2,
                        background: bucketColor,
                        transition: 'width 1.4s cubic-bezier(0.22, 1, 0.36, 1)',
                      }}
                    />
                  </div>
                  <Typography
                    variant="body2"
                    sx={{
                      fontSize: 11,
                      fontWeight: 600,
                      color: bucketColor,
                      fontFamily: '"Geologica", sans-serif',
                    }}
                  >
                    {b.exposure}
                  </Typography>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </SamplePreview>
  );
};

export default memo(ExposureScoreWidget);
