import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { type InjectStore } from '../../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectExpectationOutput } from '../../../../../utils/api-types';
import { lineChartOptions } from '../../../../../utils/Charts';
import { sampleScoreOverTimeSeries } from '../../../../../utils/SampleCharts';
import { ascendBy } from '../../../../../utils/Sorting';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { injectorContractKey, isTopLevelScoredExpectation } from './scoreByInjectorContractExpectations';

interface Props { exerciseId: Exercise['exercise_id'] }

const ExerciseDistributionScoreOverTimeByInjectorContract: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { nsdt, tPick } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { injectsMap, injectExpectations }: {
    injectsMap: Record<string, InjectStore>;
    injectExpectations: InjectExpectationOutput[];
  } = useHelper((helper: InjectHelper) => ({
    injectsMap: helper.getInjectsMap(),
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
  }));

  let cumulation = 0;
  const injectsTypesScores = R.pipe(
    // One series per injector contract, technical injects included (#8396).
    (expectationsList: InjectExpectationOutput[]) => expectationsList.filter(n => isTopLevelScoredExpectation(n)),
    (expectationsList: Array<InjectExpectationOutput & { inject_expectation_inject: string }>) => expectationsList.map(n => ({
      ...n,
      inject_expectation_inject: injectsMap[n.inject_expectation_inject] || {},
    })),
    (list: Array<Omit<InjectExpectationOutput, 'inject_expectation_inject'> & { inject_expectation_inject: InjectStore }>) => Object.groupBy(list, injectorContractKey),
    Object.entries,
    (pairs: [string, InjectExpectationOutput[]][]) => pairs.map((n: [string, InjectExpectationOutput[]]) => {
      cumulation = 0;
      return [
        n[0],
        n[1].toSorted(ascendBy('inject_expectation_updated_at')).map((i: InjectExpectationOutput) => {
          cumulation += i.inject_expectation_score ?? 0;
          return {
            ...i,
            inject_expectation_cumulated_score: cumulation,
          };
        }),
      ];
    }),
    (pairs: [string, Array<InjectExpectationOutput & {
      inject_expectation_cumulated_score: number;
      inject_expectation_inject: InjectStore;
    }>][]) => pairs.map((n: [string, Array<InjectExpectationOutput & {
      inject_expectation_cumulated_score: number;
      inject_expectation_inject: InjectStore;
    }>]) => ({
      name: tPick(n[1][0].inject_expectation_inject.inject_injector_contract?.injector_contract_labels),
      color: n[1][0].inject_expectation_inject.inject_injector_contract?.convertedContent?.config?.[`color_${theme.palette.mode}`],
      data: n[1].map((i: InjectExpectationOutput & { inject_expectation_cumulated_score: number }) => ({
        x: i.inject_expectation_updated_at,
        y: i.inject_expectation_cumulated_score,
      })),
    })),
  )(injectExpectations);

  // Dashboard convention: charts without real data render a greyed-out sample
  // (with a "Sample" chip) instead of a bare empty message.
  const isSample = injectsTypesScores.length === 0;

  return (
    <SamplePreview active={isSample}>
      <Chart
        id="exercise_distribution_score_over_time_by_inject"
        options={lineChartOptions({
          theme,
          isTimeSeries: true,
          xFormatter: nsdt,
        })}
        series={isSample
          ? sampleScoreOverTimeSeries(['Email', 'Command execution'], theme)
          : injectsTypesScores}
        type="line"
        width="100%"
        height={350}
      />
    </SamplePreview>
  );
};

export default ExerciseDistributionScoreOverTimeByInjectorContract;
