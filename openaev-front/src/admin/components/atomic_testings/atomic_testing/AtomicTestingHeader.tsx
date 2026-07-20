import { useTheme } from '@mui/material/styles';

import Breadcrumbs, { type BreadcrumbsElement } from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import AtomicTestingHeaderActions from './AtomicTestingHeaderActions';
import AtomicTestingTabs from './AtomicTestingTabs';
import InjectHero from './InjectHero';

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  setInjectResultOverview: (injectResultOverviewOutput: InjectResultOverviewOutput) => void;
}

const AtomicTestingHeader = ({ injectResultOverview, setInjectResultOverview }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const breadcrumbs: BreadcrumbsElement[] = [
    {
      label: t('Atomic testings'),
      link: '/admin/atomic_testings',
    },
    {
      label: injectResultOverview.inject_title,
      current: true,
    },
  ];

  return (
    <header style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
      marginBottom: theme.spacing(2),
    }}
    >
      <Breadcrumbs
        variant="object"
        elements={breadcrumbs}
      />
      <InjectHero
        injectResultOverview={injectResultOverview}
        actions={(
          <AtomicTestingHeaderActions
            injectResultOverview={injectResultOverview}
            setInjectResultOverview={setInjectResultOverview}
          />
        )}
      />
      <AtomicTestingTabs injectResultOverview={injectResultOverview} />
    </header>
  );
};

export default AtomicTestingHeader;
