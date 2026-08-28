import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { fetchExerciseInjects } from '../../../../actions/Inject';
import { type InjectorContractHelper } from '../../../../actions/injector_contracts/injector-contract-helper';
import { type InjectStore } from '../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import Chart from '../../../../components/Chart';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise, type InjectExpectationOutput } from '../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../utils/Charts';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { sampleHorizontalBarHeight, sampleHorizontalBarSeries } from '../../../../utils/SampleCharts';
import SamplePreview from '../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';

interface Props { exerciseId: Exercise['exercise_id'] }

const InjectDistributionByType: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { injects } = useHelper((helper: InjectHelper & InjectorContractHelper) => ({ injects: helper.getExerciseInjects(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
  });

  const injectsByType = R.pipe(
    R.filter((n: InjectStore) => n.inject_sent_at !== null),
    R.groupBy(R.prop('inject_type')),
    R.toPairs,
    R.map((n: [string, InjectExpectationOutput[]]) => ({
      inject_type: n[0],
      number: n[1].length,
    })),
    R.sortWith([R.descend(R.prop('number'))]),
  )(injects);
  const injectsByInjectorContractData = [
    {
      name: t('Number of injects'),
      data: injectsByType.map((a: InjectStore & { number: number }) => ({
        x: tPick(a.inject_injector_contract?.injector_contract_labels),
        y: a.number,
        fillColor: a.inject_injector_contract?.convertedContent?.config?.[`color_${theme.palette.mode}`],
      })),
    },
  ];

  // Dashboard convention: charts without real data render a greyed-out sample
  // (with a "Sample" chip) instead of a bare empty message.
  const isSample = injectsByType.length === 0;
  const sampleLabels = ['Email', 'Command execution', 'HTTP request'];

  return (
    <SamplePreview active={isSample}>
      <Chart
        options={horizontalBarsChartOptions({ theme })}
        series={isSample
          ? sampleHorizontalBarSeries(t('Number of injects'), sampleLabels, theme)
          : injectsByInjectorContractData}
        type="bar"
        width="100%"
        height={isSample ? sampleHorizontalBarHeight(sampleLabels) : 50 + injectsByType.length * 50}
      />
    </SamplePreview>
  );
};

export default InjectDistributionByType;
