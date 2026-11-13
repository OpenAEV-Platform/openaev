import { useTheme } from '@mui/material/styles';

import Breadcrumbs, { type BreadcrumbsElement } from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import type { InjectHeroOutput, InjectResultOverviewOutput } from '../../../../utils/api-types';
import AtomicTestingHeaderActions from './AtomicTestingHeaderActions';
import AtomicTestingTabs from './AtomicTestingTabs';
import InjectHero from './InjectHero';

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  injectHero: InjectHeroOutput;
  setInjectResultOverview: (injectResultOverviewOutput: InjectResultOverviewOutput) => void;
}

const AtomicTestingHeader = ({ injectResultOverview, injectHero, setInjectResultOverview }: Props) => {
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
        injectHero={injectHero}
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
