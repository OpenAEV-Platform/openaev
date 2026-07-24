import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { fetchExerciseInjects } from '../../../../../actions/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Chart from '../../../../../components/Chart';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type Inject } from '../../../../../utils/api-types';
import { horizontalBarsChartOptions } from '../../../../../utils/Charts';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { sampleMailsByInject } from './mailsSampleData';

interface Props { exerciseId: Exercise['exercise_id'] }

const MailDistributionByInject: FunctionComponent<Props> = ({ exerciseId }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { injects } = useHelper((helper: InjectHelper) => ({ injects: helper.getScenarioInjects(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseInjects(exerciseId));
  });

  const sortedInjectsByCommunicationNumber = R.pipe(
    R.sortWith([R.descend(R.prop('inject_communications_number'))]),
    R.take(10),
  )(injects || []);
  const totalMailsByInjectData = [
    {
      name: t('Total mails'),
      data: sortedInjectsByCommunicationNumber.map((i: Inject) => ({
        x: i.inject_title,
        y: i.inject_communications_number,
      })),
    },
  ];

  // Injects may exist before any mail is sent: only render the real chart
  // once at least one inject has mail traffic, otherwise preview sample data.
  const hasData = sortedInjectsByCommunicationNumber.some(
    (inject: Inject) => (inject.inject_communications_number ?? 0) > 0,
  );

  return (
    <>
      {hasData ? (
        <Chart
          options={horizontalBarsChartOptions({ theme })}
          series={totalMailsByInjectData}
          type="bar"
          width="100%"
          height={50 + sortedInjectsByCommunicationNumber.length * 50}
        />
      ) : (
        // No mail traffic yet: preview the widget with greyed sample data
        // (like every widget of the platform) instead of an empty box.
        <SamplePreview active>
          <Chart
            options={horizontalBarsChartOptions({ theme })}
            series={sampleMailsByInject(t('Total mails'))}
            type="bar"
            width="100%"
            height={250}
          />
        </SamplePreview>
      )}
    </>
  );
};

export default MailDistributionByInject;
