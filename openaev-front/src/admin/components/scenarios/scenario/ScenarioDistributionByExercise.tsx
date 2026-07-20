import { deepPurple } from '@mui/material/colors';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type ApexOptions } from 'apexcharts';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchScenarioStatistic } from '../../../../actions/scenarios/scenario-actions';
import Chart from '../../../../components/Chart';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type GlobalScoreBySimulationEndDate, type ScenarioStatistic } from '../../../../utils/api-types';

interface Props { scenarioId: string }

// One stable color per expectation type, so a series keeps its color no matter
// which types are present (the old code reused a 3-color palette positionally,
// so Detection could render with Prevention's color when Prevention was absent).
const typeColor = (theme: Theme): Record<string, string> => ({
  PREVENTION: theme.palette.primary.main,
  DETECTION: theme.palette.secondary.main,
  VULNERABILITY: theme.palette.warning.main,
  HUMAN_RESPONSE: deepPurple[theme.palette.mode === 'dark' ? 300 : 500],
});

function generateFakeDataFromDates(dates: string[], percentage: number): GlobalScoreBySimulationEndDate[] {
  return dates.map(date => ({
    simulation_end_date: date,
    global_score_success_percentage: percentage,
  }));
}

const generateFakeData = (): Record<string, GlobalScoreBySimulationEndDate[]> => {
  const now = new Date();
  const dates = Array.from({ length: 5 }, (_, i) => {
    const newDate = new Date(now);
    newDate.setHours(now.getHours() + i + 1);
    return newDate.toISOString();
  });
  return ({
    PREVENTION: generateFakeDataFromDates(dates, 69.0),
    DETECTION: generateFakeDataFromDates(dates, 84.0),
    VULNERABILITY: generateFakeDataFromDates(dates, 24.0),
    HUMAN_RESPONSE: generateFakeDataFromDates(dates, 46.0),
  });
};

function generateSeriesData(globalScores: GlobalScoreBySimulationEndDate[]) {
  return globalScores.map((globalScore, index) => ({
    // Keep the index in the key so two runs sharing a date stay distinct points.
    x: `${index}|${globalScore.simulation_end_date}`,
    y: Math.round(globalScore.global_score_success_percentage),
  }));
}

// The x category encodes "index|iso-date"; render only the human date.
function getXFormatter(fsd: (date: string) => string) {
  return (rawData: string) => {
    if (!rawData) {
      return rawData;
    }
    const splitRawData = rawData.split('|');
    return splitRawData.length > 1 ? fsd(splitRawData[1]) : rawData;
  };
}

const EXPECTATION_TYPES = [
  ['PREVENTION', 'Prevention', 'Prevented'],
  ['DETECTION', 'Detection', 'Detected'],
  ['VULNERABILITY', 'Vulnerability', 'Not vulnerable'],
  ['HUMAN_RESPONSE', 'Human Response', 'Successful'],
] as const;

const ScenarioDistributionByExercise: FunctionComponent<Props> = ({ scenarioId }) => {
  const { fsd, t } = useFormatter();
  const theme = useTheme();

  const [loadingScenarioStatistics, setLoadingScenarioStatistics] = useState(true);
  const [statistic, setStatistic] = useState<ScenarioStatistic>();
  const fetchStatistics = () => {
    setLoadingScenarioStatistics(true);
    fetchScenarioStatistic(scenarioId).then((result: { data: ScenarioStatistic }) => setStatistic(result.data)).finally(() => setLoadingScenarioStatistics(false));
  };
  useEffect(() => {
    fetchStatistics();
  }, []);

  const rawScores = statistic?.simulations_results_latest.global_scores_by_expectation_type || {};
  const hasRealData = EXPECTATION_TYPES.some(([type]) => Array.isArray(rawScores[type]) && rawScores[type].length > 0);
  const globalScoresByExpectationType = hasRealData ? rawScores : generateFakeData();
  const isFakeData = !hasRealData;

  const colorMap = typeColor(theme);
  const presentTypes = EXPECTATION_TYPES.filter(([type]) => globalScoresByExpectationType[type]?.length > 0);
  const series = presentTypes.map(([type, name]) => ({
    name: t(name),
    data: generateSeriesData(globalScoresByExpectationType[type]),
  }));
  const chartColors = presentTypes.map(([type]) => colorMap[type]);
  const hasData = series.length > 0 && series[0].data.length > 0;
  // A single run per type would render as a lone marker on a line; a spark of
  // area under it keeps the card from looking empty.
  const singlePoint = series.every(s => s.data.length <= 1);

  const options: ApexOptions = {
    chart: {
      type: 'area',
      background: 'transparent',
      toolbar: { show: false },
      zoom: { enabled: false },
      foreColor: theme.palette.text.secondary,
      fontFamily: '"IBM Plex Sans", sans-serif',
      parentHeightOffset: 0,
    },
    theme: { mode: theme.palette.mode },
    colors: chartColors,
    dataLabels: { enabled: false },
    stroke: {
      curve: 'smooth',
      width: 2.5,
      lineCap: 'round',
    },
    fill: {
      type: 'gradient',
      gradient: {
        shadeIntensity: 1,
        opacityFrom: isFakeData ? 0.12 : 0.32,
        opacityTo: 0,
        stops: [0, 95],
      },
    },
    markers: {
      size: singlePoint ? 5 : 0,
      strokeWidth: 2,
      strokeColors: theme.palette.background.paper,
      hover: { size: 6 },
    },
    grid: {
      borderColor: alpha(theme.palette.text.primary, 0.08),
      strokeDashArray: 4,
      padding: {
        left: 8,
        right: 8,
        top: 0,
      },
      xaxis: { lines: { show: false } },
      yaxis: { lines: { show: true } },
    },
    legend: {
      show: true,
      position: 'bottom',
      horizontalAlign: 'center',
      fontSize: '12px',
      markers: { size: 6 },
      itemMargin: {
        horizontal: 10,
        vertical: 4,
      },
    },
    xaxis: {
      type: 'category',
      tickPlacement: 'on',
      axisBorder: { show: false },
      axisTicks: { show: false },
      labels: {
        rotate: 0,
        hideOverlappingLabels: true,
        formatter: getXFormatter(fsd),
        style: { fontSize: '11px' },
      },
      tooltip: { enabled: false },
    },
    yaxis: {
      min: 0,
      max: 100,
      tickAmount: 5,
      labels: {
        formatter: (value: number) => `${Math.round(value)}%`,
        style: { fontSize: '11px' },
      },
    },
    tooltip: {
      theme: theme.palette.mode,
      shared: true,
      intersect: false,
      x: { formatter: value => getXFormatter(fsd)(String(value)) },
      y: { formatter: (value: number) => `${Math.round(value)}%` },
    },
    noData: { text: t('No data to display') },
  };

  if (loadingScenarioStatistics) {
    return <Loader variant="inElement" />;
  }

  if (!hasData) {
    return <Empty message={t('No data to display')} />;
  }

  return (
    <Chart
      options={options}
      series={series}
      type="area"
      width="100%"
      height={280}
    />
  );
};
export default ScenarioDistributionByExercise;
