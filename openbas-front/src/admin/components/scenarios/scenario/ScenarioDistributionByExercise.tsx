import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import Chart from 'react-apexcharts';

import { fetchScenarioStatistic } from '../../../../actions/scenarios/scenario-actions';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type GlobalScoreBySimulationEndDate, type ScenarioStatistic } from '../../../../utils/api-types';
import { type CustomTooltipFunction, type CustomTooltipOptions, verticalBarsChartOptions } from '../../../../utils/Charts';

interface Props { scenarioId: string }

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
    ...({ PREVENTION: generateFakeDataFromDates(dates, 69.0) }),
    ...({ DETECTION: generateFakeDataFromDates(dates, 84.0) }),
    ...({ HUMAN_RESPONSE: generateFakeDataFromDates(dates, 46.0) }),
  });
};

function generateSeriesData(globalScores: GlobalScoreBySimulationEndDate[], successfulExpectationLabel: string) {
  const { fldt } = useFormatter();
  return globalScores.map((globalScore, index) => ({
    x: `${index}|${globalScore.simulation_end_date}`,
    y: globalScore.global_score_success_percentage / 100,
    simulationEndDate: fldt(globalScore.simulation_end_date),
    simulationSuccessPercentage: globalScore.global_score_success_percentage,
    successfulExpectationLabel: successfulExpectationLabel,
  }));
}

type SeriesData = {
  simulationEndDate: string;
  simulationSuccessPercentage: string;
  successfulExpectationLabel: string;
};

const customTooltip = (simulationEndDateLabel: string): CustomTooltipFunction => {
  return function ({ _, seriesIndex, dataPointIndex, w }: CustomTooltipOptions) {
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
  return (value: number) => `${value * 100}%`;
}

const ScenarioDistributionByExercise: FunctionComponent<Props> = ({ scenarioId }) => {
  // Standard hooks
  const { fsd, t } = useFormatter();
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

  const preventionData = statistic?.simulations_results_latest.global_scores_by_expectation_type['PREVENTION'];
  const globalScoresByExpectationType = preventionData && preventionData.length > 0 ? statistic?.simulations_results_latest.global_scores_by_expectation_type : generateFakeData();
  const isStatisticsDataEmpty = preventionData && preventionData.length === 0;

  const series = [
    {
      name: t('Prevention'),
      data: generateSeriesData(globalScoresByExpectationType['PREVENTION'], t('Blocked')),
    },
    {
      name: t('Detection'),
      data: generateSeriesData(globalScoresByExpectationType['DETECTION'], t('Detected')),
    },
    {
      name: t('Human Response'),
      data: generateSeriesData(globalScoresByExpectationType['HUMAN_RESPONSE'], t('Successful')),
    },
  ];

  return (
    <>
      {loadingScenarioStatistics && (<Loader variant="inElement" />)}
      {(!loadingScenarioStatistics && series[0].data.length > 0) && (
        <Chart
          options={verticalBarsChartOptions(
            theme,
            getXFormatter(fsd),
            getYFormatter(),
            false,
            false,
            false,
            true,
            'dataPoints',
            true,
            isStatisticsDataEmpty,
            1,
            t('No data to display'),
            customTooltip(simulationEndDateLabel),
          )}
          series={series}
          type="bar"
          width="100%"
          height={300}
        />
      )}
      {(!loadingScenarioStatistics && series[0].data.length === 0) && (
        <Empty
          message={t(
            'No data to display',
          )}
        />
      )}
    </>
  );
};
export default ScenarioDistributionByExercise;
