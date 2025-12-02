import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { getExerciseInjectExpectationsSelector, getInjectsMapSelector } from '../../../../../actions/selectors';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useSelectorHelper } from '../../../../../store';
import { type Exercise, type Inject, type InjectExpectation } from '../../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../../utils/api-types-custom';
import { lineChartOptions } from '../../../../../utils/Charts';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreOverTimeByInjectorContract: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, nsdt, tPick } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const injectsMap = useSelectorHelper(getInjectsMapSelector);
  const injectExpectations = useSelectorHelper(state => getExerciseInjectExpectationsSelector(exerciseId, state));

  let cumulation = 0;
  const injectsTypesScores = R.pipe(
    R.filter((n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results) && n?.inject_expectation_team && n?.inject_expectation_user === null),
    R.map((n: InjectExpectation & { inject_expectation_inject: string }) => R.assoc(
      'inject_expectation_inject',
      injectsMap[n.inject_expectation_inject] || {},
      n,
    )),
    R.groupBy(R.path(['inject_expectation_inject', 'inject_contract'])),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => {
      cumulation = 0;
      return [
        n[0],
        R.pipe(
          R.sortWith([R.ascend(R.prop('inject_expectation_updated_at'))]),
          R.map((i: InjectExpectation) => {
            cumulation += i.inject_expectation_score ?? 0;
            return R.assoc('inject_expectation_cumulated_score', cumulation, i);
          }),
        )(n[1]),
      ];
    }),
    R.map((n: [string, Array<InjectExpectation & {
      inject_expectation_cumulated_score: number;
      inject_expectation_inject: Inject;
    }>]) => ({
      name: tPick(n[1][0].inject_expectation_inject.inject_injector_contract?.injector_contract_labels),
      color: (n[1][0].inject_expectation_inject.inject_injector_contract?.convertedContent as InjectorContractConverted['convertedContent'])?.config?.[`color_${theme.palette.mode}`],
      data: n[1].map((i: InjectExpectation & { inject_expectation_cumulated_score: number }) => ({
        x: i.inject_expectation_updated_at,
        y: i.inject_expectation_cumulated_score,
      })),
    })),
  )(injectExpectations);

  return (
    <>
      {injectsTypesScores.length > 0 ? (
        <Chart
          id="exercise_distribution_score_over_time_by_inject"
          options={lineChartOptions({
            theme,
            isTimeSeries: true,
            xFormatter: nsdt,
          })}
          series={injectsTypesScores}
          type="line"
          width="100%"
          height={350}
        />
      ) : (
        <Empty
          id="exercise_distribution_score_over_time_by_inject"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default ExerciseDistributionScoreOverTimeByInjectorContract;
