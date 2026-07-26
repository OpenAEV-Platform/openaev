import { InfoOutlined } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { expectationTypeIcon } from '../../../admin/components/common/ExpectationIconByType';
import { expectationResultTypes } from '../../../admin/components/common/injects/expectations/Expectation';
import { type ExpectationResultsByType } from '../../../utils/api-types';
import useCountUp from '../../../utils/hooks/useCountUp';
import { getStatusColor } from '../../../utils/statusUtils';
import { useFormatter } from '../../i18n';

interface Props {
  expectationResultsByTypes?: ExpectationResultsByType[] | null;
  humanValidationLink?: string;
  /**
   * When set, gauges become clickable and drill down to the expectations
   * behind the ring (same actionability as the dashboard widgets). Leave
   * unset for sample/preview data.
   */
  onTypeClick?: (type: string) => void;
}

interface Buckets {
  success: number;
  partial: number;
  failed: number;
  pending: number;
}

// Classify each distribution slice into success / partial / failed / pending by
// its semantic status color, so the ring speaks the same language as the rest
// of the app (green = success, warning = partial, red = failed, grey = pending).
const bucketize = (
  distribution: ExpectationResultsByType['distribution'],
  theme: Theme,
): Buckets => {
  const buckets: Buckets = {
    success: 0,
    partial: 0,
    failed: 0,
    pending: 0,
  };
  (distribution ?? []).forEach((item) => {
    const value = item.value ?? 0;
    if (value <= 0) return;
    const color = getStatusColor(theme, item.label);
    if (color === theme.palette.success.main) buckets.success += value;
    else if (color === theme.palette.warning.main) buckets.partial += value;
    else if (color === theme.palette.error.main) buckets.failed += value;
    else buckets.pending += value;
  });
  return buckets;
};

const SIZE = 128;
const RADIUS = 56;
const STROKE = 5;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const Gauge: FunctionComponent<{
  type: string;
  buckets: Buckets;
  onClick?: () => void;
}> = ({ type, buckets, onClick }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const Icon = expectationTypeIcon(type);

  const resolved = buckets.success + buckets.partial + buckets.failed;
  const resilience = resolved > 0 ? Math.round((buckets.success / resolved) * 100) : 0;
  const animated = useCountUp(resilience, 1200);
  const hasData = resolved + buckets.pending > 0;

  const accent = (() => {
    if (!hasData) return theme.palette.text.disabled;
    if (resilience >= 75) return theme.palette.success.main;
    if (resilience >= 50) return theme.palette.warning.main;
    if (resilience >= 25) return '#ff7043';
    return theme.palette.error.main;
  })();

  const total = buckets.success + buckets.partial + buckets.failed + buckets.pending;
  const gapDeg = total > 0 ? 3 : 0;
  const dark = theme.palette.mode === 'dark';
  const cx = SIZE / 2;
  const cy = SIZE / 2;

  const segmentDefs = [
    {
      value: buckets.success,
      color: theme.palette.success.main,
    },
    {
      value: buckets.partial,
      color: theme.palette.warning.main,
    },
    {
      value: buckets.failed,
      color: theme.palette.error.main,
    },
    {
      value: buckets.pending,
      color: theme.palette.text.disabled,
    },
  ].filter(s => s.value > 0);

  let acc = 0;
  const segments = total === 0
    ? []
    : segmentDefs.map((s) => {
        const frac = s.value / total;
        const startFrac = acc;
        acc += frac;
        return {
          color: s.color,
          len: Math.max(frac * CIRCUMFERENCE - (gapDeg / 360) * CIRCUMFERENCE, 0.5),
          offset: -(startFrac * CIRCUMFERENCE),
        };
      });

  return (
    <Box
      onClick={onClick}
      sx={{
        'display': 'flex',
        'flexDirection': 'column',
        'alignItems': 'center',
        'gap': 1,
        'cursor': onClick ? 'pointer' : 'default',
        'borderRadius': 1,
        'padding': 1,
        'transition': 'background 0.15s',
        '&:hover': onClick ? { backgroundColor: alpha(accent, 0.06) } : undefined,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 22,
          height: 22,
          borderRadius: 1,
          color: accent,
          background: alpha(accent, 0.14),
        }}
        >
          <Icon sx={{ fontSize: 14 }} />
        </Box>
        <Typography sx={{
          fontSize: 11,
          fontWeight: 600,
          fontFamily: '"Geologica", sans-serif',
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
        >
          {t(type)}
        </Typography>
      </Box>
      <svg
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        style={{
          width: SIZE,
          maxWidth: '100%',
          overflow: 'visible',
        }}
      >
        <circle
          cx={cx}
          cy={cy}
          r={RADIUS}
          fill="none"
          stroke={dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}
          strokeWidth={STROKE}
        />
        {segments.map((s, i) => (
          <circle
            key={i}
            cx={cx}
            cy={cy}
            r={RADIUS}
            fill="none"
            stroke={s.color}
            strokeWidth={STROKE}
            strokeLinecap="round"
            strokeDasharray={`${s.len} ${CIRCUMFERENCE - s.len}`}
            strokeDashoffset={s.offset}
            transform={`rotate(-90 ${cx} ${cy})`}
            style={{
              transition: 'stroke-dasharray 1.1s cubic-bezier(0.22, 1, 0.36, 1), stroke-dashoffset 1.1s cubic-bezier(0.22, 1, 0.36, 1)',
              filter: `drop-shadow(0 0 2px ${alpha(s.color, 0.5)})`,
            }}
          />
        ))}
        <text
          x={cx}
          y={cy - 5}
          textAnchor="middle"
          dominantBaseline="central"
          fill={accent}
          style={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 30,
            fontWeight: 500,
          }}
        >
          {hasData ? `${Math.round(animated)}%` : '-'}
        </text>
        <text
          x={cx}
          y={cy + 16}
          textAnchor="middle"
          dominantBaseline="central"
          fill={theme.palette.text.secondary}
          style={{
            fontSize: 8,
            letterSpacing: '0.16em',
            textTransform: 'uppercase',
          }}
        >
          {t('Resilience')}
        </text>
      </svg>
      <Box sx={{
        display: 'flex',
        justifyContent: 'center',
        gap: 1.25,
        flexWrap: 'wrap',
      }}
      >
        {([
          ['Success', buckets.success, theme.palette.success.main],
          ['Partial', buckets.partial, theme.palette.warning.main],
          ['Failed', buckets.failed, theme.palette.error.main],
          ['Pending', buckets.pending, theme.palette.text.disabled],
        ] as const)
          .filter(([, count]) => count > 0)
          .map(([label, count, color]) => (
            <Box
              key={label}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
              }}
            >
              <span style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: color,
              }}
              />
              <Typography sx={{
                fontSize: 10.5,
                color: 'text.secondary',
              }}
              >
                {t(label)}
              </Typography>
              <Typography sx={{
                fontSize: 11,
                fontWeight: 600,
                fontFamily: '"Geologica", sans-serif',
              }}
              >
                {count}
              </Typography>
            </Box>
          ))}
      </Box>
    </Box>
  );
};

/**
 * Home-dashboard-style posture: one thin segmented "resilience ring" per
 * expectation type (Prevention / Detection / Vulnerability / Human response).
 * Drop-in replacement for the ResponsePie donuts, reusing the exact ring visual
 * from the default home dashboard's ResilienceGaugeWidget.
 */
const PostureGauges: FunctionComponent<Props> = ({ expectationResultsByTypes, humanValidationLink, onTypeClick }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const entries = (expectationResultsByTypes ?? [])
    .filter(entry => entry?.type)
    .toSorted((a, b) => expectationResultTypes.indexOf(a.type) - expectationResultTypes.indexOf(b.type));

  if (entries.length === 0) {
    return null;
  }

  const humanResponse = entries.find(e => e.type === 'HUMAN_RESPONSE');
  const pendingHumanValidations = humanResponse
    ? bucketize(humanResponse.distribution, theme).pending
    : 0;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      width: '100%',
    }}
    >
      {/* Distribute gauges across the full available width; wrap to an
          (equally distributed) extra row only when they no longer fit. */}
      <Box sx={{
        display: 'flex',
        flexWrap: 'wrap',
        justifyContent: 'space-evenly',
        alignItems: 'flex-start',
        columnGap: 2,
        rowGap: 3,
        width: '100%',
      }}
      >
        {entries.map(entry => (
          <Gauge
            key={entry.type}
            type={entry.type}
            buckets={bucketize(entry.distribution, theme)}
            onClick={onTypeClick ? () => onTypeClick(entry.type) : undefined}
          />
        ))}
      </Box>
      {humanValidationLink && pendingHumanValidations > 0 && (
        <Box sx={{
          display: 'flex',
          justifyContent: 'center',
        }}
        >
          <Button
            startIcon={<InfoOutlined />}
            color="primary"
            size="small"
            component={Link}
            to={humanValidationLink}
          >
            {t('{count} validations needed', { count: pendingHumanValidations })}
          </Button>
        </Box>
      )}
    </Box>
  );
};

export default PostureGauges;
