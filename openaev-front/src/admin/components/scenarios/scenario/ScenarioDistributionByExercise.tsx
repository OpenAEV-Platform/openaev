import { deepPurple } from '@mui/material/colors';
import { type Theme, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchScenarioStatistic } from '../../../../actions/scenarios/scenario-actions';
import Chart from '../../../../components/Chart';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type GlobalScoreBySimulationEndDate, type ScenarioStatistic } from '../../../../utils/api-types';
import { type CustomTooltipFunction, type CustomTooltipOptions, verticalBarsChartOptions } from '../../../../utils/Charts';

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

function generateSeriesData(
  globalScores: GlobalScoreBySimulationEndDate[],
  successfulExpectationLabel: string,
  fldt: (date: string) => string,
) {
  return globalScores.map((globalScore, index) => ({
    x: `${index}|${globalScore.simulation_end_date}`,
    y: globalScore.global_score_success_percentage / 100,
    simulationEndDate: fldt(globalScore.simulation_end_date),
    simulationSuccessPercentage: globalScore.global_score_success_percentage,
    successfulExpectationLabel,
  }));
}

type SeriesData = {
  simulationEndDate: string;
  simulationSuccessPercentage: string;
  successfulExpectationLabel: string;
};

const customTooltip = (simulationEndDateLabel: string): CustomTooltipFunction => {
  return function ({ seriesIndex, dataPointIndex, w }: CustomTooltipOptions) {
    const { simulationEndDate, simulationSuccessPercentage, successfulExpectationLabel } = w.globals.initialSeries[seriesIndex].data[dataPointIndex] as SeriesData;

    return `<div class="apexcharts-tooltip-title" style="font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
            ${simulationEndDateLabel}: <b>${simulationEndDate}</b>
          </div>
          <div class="apexcharts-tooltip-series-group" style="order: 1; display: flex;">
            <div class="apexcharts-tooltip-text" style="font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
              <div class="apexcharts-tooltip-y-group">
                <span class="apexcharts-tooltip-text-y-label">${successfulExpectationLabel}: </span>
                <span class="apexcharts-tooltip-text-y-value">${Number.parseFloat(simulationSuccessPercentage).toFixed(1)}%</span>
              </div>
           </div>
          </div>`;
  };
};

function getXFormatter(fsd: (date: string) => string) {
  return (rawData: string) => {
    if (!rawData) {
      return rawData;
    }
    const splitRawData = rawData.split('|');
    return splitRawData.length > 0 ? fsd(splitRawData[1]) : rawData;
  };
}

function getYFormatter() {
  return (value: number) => `${Math.round(value * 100)}%`;
}

const EXPECTATION_TYPES = [
  ['PREVENTION', 'Prevention', 'Prevented'],
  ['DETECTION', 'Detection', 'Detected'],
  ['VULNERABILITY', 'Vulnerability', 'Not vulnerable'],
  ['HUMAN_RESPONSE', 'Human Response', 'Successful'],
] as const;

const ScenarioDistributionByExercise: FunctionComponent<Props> = ({ scenarioId }) => {
  const { fsd, fldt, t } = useFormatter();
  const theme = useTheme();

  const simulationEndDateLabel = t('Simulation end date');

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
  const series = presentTypes.map(([type, name, label]) => ({
    name: t(name),
    data: generateSeriesData(globalScoresByExpectationType[type], t(label), fldt),
  }));
  const chartColors = presentTypes.map(([type]) => colorMap[type]);
  const hasData = series.length > 0 && series[0].data.length > 0;

  if (loadingScenarioStatistics) {
    return <Loader variant="inElement" />;
  }

  if (!hasData) {
    return <Empty message={t('No data to display')} />;
  }

  return (
    <Chart
      options={verticalBarsChartOptions({
        theme,
        xFormatter: getXFormatter(fsd),
        yFormatter: getYFormatter(),
        legend: true,
        tickAmount: 'dataPoints',
        chartColors,
        isFakeData,
        max: 1,
        emptyChartText: t('No data to display'),
        customTooltip: customTooltip(simulationEndDateLabel),
      })}
      series={series}
      type="bar"
      width="100%"
      height={300}
    />
  );
};
export default ScenarioDistributionByExercise;
