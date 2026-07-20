import { useTheme } from '@mui/material/styles';
import { useSearchParams } from 'react-router';

import Breadcrumbs, { type BreadcrumbsElement } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import type { Exercise as ExerciseType, InjectResultOverviewOutput } from '../../../../../utils/api-types';
import InjectHero from '../../../atomic_testings/atomic_testing/InjectHero';
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
    <header style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
      marginBottom: theme.spacing(2),
    }}
    >
      <Breadcrumbs variant="object" elements={breadcrumbs} />
      <InjectHero injectResultOverview={injectResultOverview} />
      <InjectIndexTabs
        injectResultOverview={injectResultOverview}
        exercise={exercise}
        backlabel={backlabel}
        backuri={backuri}
      />
    </header>
  );
};

export default InjectIndexHeader;
