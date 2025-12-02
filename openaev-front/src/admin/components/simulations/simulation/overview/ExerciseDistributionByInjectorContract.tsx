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
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionByInjectorContract: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, tPick } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const injectsMap = useSelectorHelper(getInjectsMapSelector);
  const injectExpectations = useSelectorHelper(state => getExerciseInjectExpectationsSelector(exerciseId, state));

  const sortedInjectorContractsByTotalScore = R.pipe(
    R.filter((n: InjectExpectation) => !R.isEmpty(n.inject_expectation_results)),
    R.map((n: InjectExpectation) => R.assoc(
      'inject_expectation_inject',
      injectsMap[n.inject_expectation_inject ?? ''] || {},
      n,
    )),
    R.groupBy(R.path(['inject_expectation_inject', 'inject_type'])),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => ({
      inject_type: n[0],
      inject_total_score: R.sum(R.map((o: InjectExpectation) => o.inject_expectation_score ?? 0, n[1])),
    })),
    R.sortWith([R.descend(R.prop('inject_total_score'))]),
    R.take(10),
  )(injectExpectations);

  const totalScoreByInjectorContractData = [
    {
      name: t('Total score'),
      data: sortedInjectorContractsByTotalScore.map((i: Inject & { inject_total_score: number }) => ({
        x: tPick(i.inject_injector_contract?.injector_contract_labels),
        y: i.inject_total_score,
        fillColor: (i.inject_injector_contract?.convertedContent as InjectorContractConverted['convertedContent'])?.config?.[`color_${theme.palette.mode}`],
      })),
    },
  ];

  return (
    <>
      {sortedInjectorContractsByTotalScore.length > 0 ? (
        <Chart
          id="exercise_distribution_total_score_by_inject_type"
          options={horizontalBarsChartOptions({
            theme,
            distributed: true,
          })}
          series={totalScoreByInjectorContractData}
          type="bar"
          width="100%"
          height={50 + sortedInjectorContractsByTotalScore.length * 50}
        />
      ) : (
        <Empty
          id="exercise_distribution_total_score_by_inject_type"
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default ExerciseDistributionByInjectorContract;
