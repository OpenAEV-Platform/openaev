import { TrendingDownOutlined, TrendingFlatOutlined, TrendingUpOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchScenarioStatistic } from '../../../../actions/scenarios/scenario-actions';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type GlobalScoreBySimulationEndDate, type ScenarioStatistic } from '../../../../utils/api-types';
import { expectationTypeColor, expectationTypeIcon } from '../../common/ExpectationIconByType';
import SamplePreview from '../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';

interface Props { scenarioId: string }

const EXPECTATION_TYPES = [
  ['PREVENTION', 'Prevention'],
  ['DETECTION', 'Detection'],
  ['VULNERABILITY', 'Vulnerability'],
  ['HUMAN_RESPONSE', 'Human Response'],
] as const;

function generateFakeDataFromDates(dates: string[], values: number[]): GlobalScoreBySimulationEndDate[] {
  return dates.map((date, index) => ({
    simulation_end_date: date,
    global_score_success_percentage: values[index % values.length],
  }));
}

// Illustrative, gently rising curves so the "never run" preview shows the exact
// shape a real trend produces, per type.
const generateFakeData = (): Record<string, GlobalScoreBySimulationEndDate[]> => {
  const now = new Date();
  const dates = Array.from({ length: 6 }, (_, i) => {
    const newDate = new Date(now);
    newDate.setDate(now.getDate() - (6 - i));
    return newDate.toISOString();
  });
  return {
    PREVENTION: generateFakeDataFromDates(dates, [41, 48, 52, 58, 63, 69]),
    DETECTION: generateFakeDataFromDates(dates, [60, 66, 71, 77, 81, 84]),
    VULNERABILITY: generateFakeDataFromDates(dates, [12, 15, 18, 20, 22, 24]),
    HUMAN_RESPONSE: generateFakeDataFromDates(dates, [30, 33, 38, 41, 44, 46]),
  };
};

const SPARK_W = 100;
const SPARK_H = 40;
const SPARK_PAD = 4;

// Map a series of 0-100 scores to a smooth-enough SVG polyline + closed area path.
// preserveAspectRatio="none" stretches horizontally; vector-effect keeps the
// stroke crisp, so we can use a fixed logical viewBox and let it fill the tile.
const buildSparkPaths = (values: number[]) => {
  const usableH = SPARK_H - SPARK_PAD * 2;
  const toX = (index: number) => (values.length <= 1 ? SPARK_W : (index / (values.length - 1)) * SPARK_W);
  const toY = (value: number) => SPARK_PAD + (1 - Math.max(0, Math.min(100, value)) / 100) * usableH;

  if (values.length === 1) {
    const y = toY(values[0]);
    return {
      line: `M 0 ${y} L ${SPARK_W} ${y}`,
      area: `M 0 ${y} L ${SPARK_W} ${y} L ${SPARK_W} ${SPARK_H} L 0 ${SPARK_H} Z`,
      last: {
        x: SPARK_W,
        y,
      },
    };
  }

  const points = values.map((value, index) => [toX(index), toY(value)] as const);
  const line = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x} ${y}`).join(' ');
  const area = `${line} L ${SPARK_W} ${SPARK_H} L 0 ${SPARK_H} Z`;
  const [lastX, lastY] = points[points.length - 1];
  return {
    line,
    area,
    last: {
      x: lastX,
      y: lastY,
    },
  };
};

const TrendTile: FunctionComponent<{
  type: string;
  label: string;
  scores: GlobalScoreBySimulationEndDate[];
}> = ({ type, label, scores }) => {
  const theme = useTheme();
  const { fsd, t } = useFormatter();
  const color = expectationTypeColor(type);
  const Icon = expectationTypeIcon(type);
  const gradientId = `spark-${type}`;

  const sorted = [...scores].sort(
    (a, b) => new Date(a.simulation_end_date ?? 0).getTime() - new Date(b.simulation_end_date ?? 0).getTime(),
  );
  const values = sorted.map(s => Math.round(s.global_score_success_percentage ?? 0));
  const latest = values[values.length - 1] ?? 0;
  const first = values[0] ?? 0;
  const delta = latest - first;
  const { line, area, last } = buildSparkPaths(values);

  const TrendIcon = (() => {
    if (delta > 0) return TrendingUpOutlined;
    if (delta < 0) return TrendingDownOutlined;
    return TrendingFlatOutlined;
  })();

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        padding: 1.5,
        borderRadius: 1,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.06)}`,
        background: `linear-gradient(180deg, ${alpha(color, 0.07)}, transparent 70%)`,
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
          flexShrink: 0,
          color,
          background: alpha(color, 0.14),
        }}
        >
          <Icon sx={{ fontSize: 14 }} />
        </Box>
        <Typography sx={{
          flex: 1,
          minWidth: 0,
          fontSize: 11,
          fontWeight: 600,
          fontFamily: '"Geologica", sans-serif',
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'text.secondary',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        >
          {t(label)}
        </Typography>
        <Typography sx={{
          fontSize: 22,
          lineHeight: 1,
          fontWeight: 500,
          fontFamily: '"Geologica", sans-serif',
          color,
        }}
        >
          {`${latest}%`}
        </Typography>
      </Box>

      <svg
        viewBox={`0 0 ${SPARK_W} ${SPARK_H}`}
        preserveAspectRatio="none"
        style={{
          width: '100%',
          height: 44,
          display: 'block',
        }}
      >
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.28} />
            <stop offset="100%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <path d={area} fill={`url(#${gradientId})`} stroke="none" />
        <path
          d={line}
          fill="none"
          stroke={color}
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
        <circle cx={last.x} cy={last.y} r={2.5} fill={color} vectorEffect="non-scaling-stroke" />
      </svg>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 1,
      }}
      >
        <Typography sx={{
          fontSize: 10,
          color: 'text.disabled',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        >
          {sorted.length > 1
            ? `${fsd(sorted[0].simulation_end_date)} - ${fsd(sorted[sorted.length - 1].simulation_end_date)}`
            : fsd(sorted[0]?.simulation_end_date)}
        </Typography>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.25,
          flexShrink: 0,
          color: 'text.secondary',
        }}
        >
          <TrendIcon sx={{ fontSize: 14 }} />
          <Typography sx={{
            fontSize: 11,
            fontWeight: 600,
          }}
          >
            {sorted.length > 1 ? t('{count} pts', { count: `${delta > 0 ? '+' : ''}${delta}` }) : '-'}
          </Typography>
        </Box>
      </Box>
    </Box>
  );
};

const ScenarioDistributionByExercise: FunctionComponent<Props> = ({ scenarioId }) => {
  const { t } = useFormatter();

  const [loadingScenarioStatistics, setLoadingScenarioStatistics] = useState(true);
  const [statistic, setStatistic] = useState<ScenarioStatistic>();
  useEffect(() => {
    setLoadingScenarioStatistics(true);
    fetchScenarioStatistic(scenarioId)
      .then((result: { data: ScenarioStatistic }) => setStatistic(result.data))
      .finally(() => setLoadingScenarioStatistics(false));
  }, [scenarioId]);

  const rawScores = statistic?.simulations_results_latest.global_scores_by_expectation_type || {};
  const hasRealData = EXPECTATION_TYPES.some(([type]) => Array.isArray(rawScores[type]) && rawScores[type].length > 0);
  const scores = hasRealData ? rawScores : generateFakeData();
  const isFakeData = !hasRealData;

  const presentTypes = EXPECTATION_TYPES.filter(([type]) => scores[type]?.length > 0);

  if (loadingScenarioStatistics) {
    return <Loader variant="inElement" />;
  }

  if (presentTypes.length === 0) {
    return <Empty message={t('No data to display')} />;
  }

  const grid = (
    <Box sx={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
      gap: 1.5,
    }}
    >
      {presentTypes.map(([type, label]) => (
        <TrendTile
          key={type}
          type={type}
          label={label}
          scores={scores[type]}
        />
      ))}
    </Box>
  );

  // No real trend yet (scenario never ran, or every run so far produced no
  // results): the tiles above are an illustrative sample so the widget previews
  // its final shape. Mark it explicitly as a sample (greyed + "Sample" chip),
  // like the other overview widgets, so it can never be mistaken for real posture.
  return isFakeData ? <SamplePreview active variant="subtle">{grid}</SamplePreview> : grid;
};

export default ScenarioDistributionByExercise;
