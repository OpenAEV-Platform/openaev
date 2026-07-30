import { BugReportOutlined, CrisisAlertOutlined, GppGoodOutlined, VisibilityOutlined } from '@mui/icons-material';
import { Box, ButtonBase, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, type ReactElement, useContext, useMemo } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { type StructuralHistogramWidget } from '../../../../../../utils/api-types';
import useCountUp from '../../../../../../utils/hooks/useCountUp';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { type SerieData } from '../WidgetViz';
import SamplePreview from './sample/SamplePreview';

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  datas: SerieData[];
}

/**
 * The statuses the ring charts, and therefore the only ones its click-through may
 * drill into. Keeping one list for both is what stops the total and the drilled
 * list from disagreeing (#7079): UNKNOWN expectations - those with no expected
 * score - are charted by neither arc nor total, so they must not be listed here.
 */
const RING_STATUSES = ['SUCCESS', 'FAILED', 'PENDING'];

const SAMPLE = [
  {
    x: 'SUCCESS',
    y: 64,
    meta: 'SUCCESS',
  },
  {
    x: 'FAILED',
    y: 27,
    meta: 'FAILED',
  },
  {
    x: 'PENDING',
    y: 9,
    meta: 'PENDING',
  },
];

/**
 * Thin, modern resilience ring for status breakdowns. A single hairline track
 * with a gradient progress arc (successes over resolved), a large light-weight
 * number, a domain glyph, and a compact legend. Strokes over fills - designed
 * to feel like a precision instrument, not a chunky gauge.
 */
const ResilienceGaugeWidget: FunctionComponent<Props> = ({ widgetId, widgetConfig, datas }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { openWidgetResults } = useContext(CustomDashboardContext);

  // Drill into the expectations behind the gauge, optionally scoped by status.
  const investigate = (statuses: string[]) => {
    openWidgetResults({
      widgetId,
      filter_values_map: { inject_expectation_status: statuses },
      series_index: 0,
    });
  };

  const isSample = datas.length === 0 || datas.every(d => (d.y ?? 0) === 0);
  const rows = isSample ? SAMPLE : datas;

  const domainIcon = useMemo<ReactElement>(() => {
    const type = widgetConfig.series?.[0]?.filter?.filters
      ?.find(f => f.key === 'inject_expectation_type')
      ?.values?.[0]?.toUpperCase() ?? '';
    if (type.includes('PREVENTION')) return <GppGoodOutlined sx={{ fontSize: 14 }} />;
    if (type.includes('DETECTION')) return <VisibilityOutlined sx={{ fontSize: 14 }} />;
    if (type.includes('VULNERABILITY')) return <BugReportOutlined sx={{ fontSize: 14 }} />;
    return <CrisisAlertOutlined sx={{ fontSize: 14 }} />;
  }, [widgetConfig]);

  const counts = useMemo(() => {
    const get = (needle: string) => rows
      .filter(d => (d.x ?? '').toUpperCase().includes(needle))
      .reduce((acc, d) => acc + Number(d.y ?? 0), 0);
    const success = get('SUCCESS');
    const failed = get('FAIL');
    const pending = get('PENDING');
    const resolved = success + failed;
    return {
      success,
      failed,
      pending,
      resilience: resolved > 0 ? Math.round((success / resolved) * 100) : 0,
    };
  }, [rows]);

  const accent = useMemo(() => {
    if (counts.resilience >= 75) return theme.palette.success.main;
    if (counts.resilience >= 50) return theme.palette.warning.main;
    if (counts.resilience >= 25) return '#ff7043';
    return theme.palette.error.main;
  }, [counts.resilience, theme]);

  const animated = useCountUp(counts.resilience, 1200);

  // Full-circle thin ring, starting at top (12 o'clock).
  const size = 132;
  const cx = size / 2;
  const cy = size / 2;
  const radius = 58;
  const circumference = 2 * Math.PI * radius;
  const dark = theme.palette.mode === 'dark';

  // Segmented composition: success / failed / pending arcs around the ring.
  const total = counts.success + counts.failed + counts.pending;
  const gapDeg = total > 0 ? 3 : 0;
  const segments = useMemo(() => {
    if (total === 0) return [];
    const defs = [
      {
        value: counts.success,
        color: theme.palette.success.main,
      },
      {
        value: counts.failed,
        color: theme.palette.error.main,
      },
      {
        value: counts.pending,
        color: theme.palette.text.disabled,
      },
    ].filter(s => s.value > 0);
    let acc = 0;
    return defs.map((s) => {
      const frac = s.value / total;
      const startFrac = acc;
      acc += frac;
      return {
        color: s.color,
        // dash length for this arc, minus a small visual gap
        len: Math.max(frac * circumference - (gapDeg / 360) * circumference, 0.5),
        offset: -(startFrac * circumference),
      };
    });
  }, [counts, total, circumference, gapDeg, theme]);

  const dot = (label: string, count: number, c: string) => (
    <ButtonBase
      className="noDrag"
      onClick={() => investigate([label])}
      sx={{
        'display': 'flex',
        'alignItems': 'center',
        'gap': 0.5,
        'paddingInline': 0.5,
        'borderRadius': 0.75,
        'transition': 'background-color 0.15s ease',
        '&:hover': { backgroundColor: alpha(c, 0.15) },
      }}
    >
      <span style={{
        width: 6,
        height: 6,
        borderRadius: '50%',
        background: c,
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
    </ButtonBase>
  );

  return (
    <SamplePreview active={isSample}>
      <div style={{
        position: 'relative',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        {/* domain glyph pinned to the card's top-right, on the title row */}
        <Box sx={{
          position: 'absolute',
          top: -32,
          right: 0,
          zIndex: 1,
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
          {domainIcon}
        </Box>
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
            className="noDrag"
            viewBox={`0 0 ${size} ${size}`}
            onClick={() => investigate(RING_STATUSES)}
            style={{
              maxHeight: '100%',
              maxWidth: '100%',
              overflow: 'visible',
              cursor: 'pointer',
            }}
          >
            <title>{t('click to investigate')}</title>
            <circle
              cx={cx}
              cy={cy}
              r={radius}
              fill="none"
              stroke={dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}
              strokeWidth={5}
            />
            {segments.map((s, i) => (
              <circle
                key={i}
                cx={cx}
                cy={cy}
                r={radius}
                fill="none"
                stroke={s.color}
                strokeWidth={5}
                strokeLinecap="round"
                strokeDasharray={`${s.len} ${circumference - s.len}`}
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
              y={cy - 6}
              textAnchor="middle"
              dominantBaseline="central"
              fill={accent}
              style={{
                fontFamily: '"Geologica", sans-serif',
                fontSize: 30,
                fontWeight: 500,
              }}
            >
              {`${Math.round(animated)}%`}
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
        </Box>
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: 14,
          flexWrap: 'wrap',
          paddingBottom: 4,
        }}
        >
          {dot('SUCCESS', counts.success, theme.palette.success.main)}
          {dot('FAILED', counts.failed, theme.palette.error.main)}
          {counts.pending > 0 && dot('PENDING', counts.pending, theme.palette.text.disabled)}
        </div>
      </div>
    </SamplePreview>
  );
};

export default memo(ResilienceGaugeWidget);
