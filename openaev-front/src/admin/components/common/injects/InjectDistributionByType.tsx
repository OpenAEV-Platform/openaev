import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import Chart from 'react-apexcharts';

import { fetchExerciseInjects } from '../../../../actions/inject';
import { getExerciseInjectsSelector } from '../../../../actions/selectors';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { useSelectorHelper } from '../../../../store';
import { type Exercise, type Inject, type InjectExpectation } from '../../../../utils/api-types';
import { type InjectorContractConverted } from '../../../../utils/api-types-custom';
import { horizontalBarsChartOptions } from '../../../../utils/Charts';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

interface Props { exerciseId: Exercise['exercise_id'] }

const InjectDistributionByType: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t, tPick } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const injects = useSelectorHelper(state => getExerciseInjectsSelector(exerciseId, state));
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
  });

  const injectsByType = R.pipe(
    R.filter((n: Inject) => n.inject_sent_at !== null),
    R.groupBy(R.prop('inject_type')),
    R.toPairs,
    R.map((n: [string, InjectExpectation[]]) => ({
      inject_type: n[0],
      number: n[1].length,
    })),
    R.sortWith([R.descend(R.prop('number'))]),
  )(injects);
  const injectsByInjectorContractData = [
    {
      name: t('Number of injects'),
      data: injectsByType.map((a: Inject & { number: number }) => ({
        x: tPick(a.inject_injector_contract?.injector_contract_labels),
        y: a.number,
        fillColor: (a.inject_injector_contract?.convertedContent as InjectorContractConverted['convertedContent'])?.config?.[`color_${theme.palette.mode}`],
      })),
    },
  ];

  return (
    <>
      {injectsByType.length > 0 ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={injectsByInjectorContractData}
          type="bar"
          width="100%"
          height={50 + injectsByType.length * 50}
        />
      ) : (
        <Empty
          message={t(
            'No data to display or the simulation has not started yet',
          )}
        />
      )}
    </>
  );
};

export default InjectDistributionByType;
