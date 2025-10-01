import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useSearchParams } from 'react-router';

import Breadcrumbs, { type BreadcrumbsElement } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import type { Exercise as ExerciseType, InjectResultOverviewOutput } from '../../../../../utils/api-types';
import AtomicTestingTitle from '../../../atomic_testings/atomic_testing/AtomicTestingTitle';
import ResponsePie from '../../../common/injects/ResponsePie';
import InjectIndexTabs from './InjectIndexTabs';

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  exercise: ExerciseType;
}

const InjectIndexHeader = ({ injectResultOverview, exercise }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [searchParams] = useSearchParams();
  const backlabel = searchParams.get('backlabel');
  const backuri = searchParams.get('backuri');

  const breadcrumbs: BreadcrumbsElement[] = [
    {
      label: t('Simulations'),
      link: '/admin/simulations',
    },
    {
      label: t(exercise.exercise_name),
      link: `/admin/simulations/${exercise.exercise_id}`,
    },
  ];

  if (backlabel && backuri) {
    breadcrumbs.push({
      label: backlabel,
      link: backuri,
    });
  }
  breadcrumbs.push({ label: t('Injects') });
  breadcrumbs.push({
    label: injectResultOverview.inject_title,
    current: true,
  });

  return (
    <Box
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
        marginBottom: 2,
      }}
    >
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr auto',
        gap: theme.spacing(2),
        alignItems: 'start',
      }}
      >
        <Box display="flex" flexDirection="column" justifyContent="left" alignItems="flex-start">
          <Breadcrumbs variant="object" elements={breadcrumbs} />
          <AtomicTestingTitle injectResultOverview={injectResultOverview} />
          <InjectIndexTabs injectResultOverview={injectResultOverview} exercise={exercise} backlabel={backlabel} backuri={backuri} />
        </Box>
        <ResponsePie hasTitles={false} forceSize={112} expectationResultsByTypes={injectResultOverview.inject_expectation_results} />
      </div>
    </Box>
  );
};

export default InjectIndexHeader;
