import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type Inject, type InjectExpectation } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreByInject: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectsMap, injectExpectations } = useHelper((helper: InjectHelper) => ({
    injectsMap: helper.getInjectsMap(),
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
  }));

  const injectsTotalScores = R.pipe(
    R.filter((n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results)),
    R.groupBy(R.prop('inject_expectation_inject')),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => ({
      ...injectsMap[n[0]],
      inject_total_score: R.sum(R.map((o: InjectExpectation) => o.inject_expectation_score, n[1])),
    })),
  )(injectExpectations);

  const sortedInjectsByTotalScore = R.pipe(
    R.sortWith([R.descend(R.prop('inject_total_score'))]),
    R.filter((n: InjectExpectation & { inject_total_score: number }) => n.inject_total_score > 0),
    R.take(10),
  )(injectsTotalScores);

  const totalScoreByInjectData = [
    {
      name: t('Total score'),
      data: sortedInjectsByTotalScore.map((i: Inject & { inject_total_score: number }) => ({
        x: i.inject_title,
        y: i.inject_total_score,
      })),
    },
  ];

  return (
    <>
      {sortedInjectsByTotalScore.length > 0 ? (
        <Chart
          id="exercise_distribution_total_score_by_inject"
          options={horizontalBarsChartOptions({
            theme,
            distributed: true,
          })}
          series={totalScoreByInjectData}
          type="bar"
          width="100%"
          height={50 + sortedInjectsByTotalScore.length * 50}
        />
      ) : (
        <Empty
          id="exercise_distribution_total_score_by_inject"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};
export default ExerciseDistributionScoreByInject;
