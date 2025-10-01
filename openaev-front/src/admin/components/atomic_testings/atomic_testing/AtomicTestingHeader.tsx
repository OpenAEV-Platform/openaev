import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import Breadcrumbs, { type BreadcrumbsElement } from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import ResponsePie from '../../common/injects/ResponsePie';
import AtomicTestingHeaderActions from './AtomicTestingHeaderActions';
import AtomicTestingTabs from './AtomicTestingTabs';
import AtomicTestingTitle from './AtomicTestingTitle';

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
    <Box
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
        marginBottom: 2,
      }}
    >
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr auto auto',
        gap: theme.spacing(2),
        alignItems: 'start',
      }}
      >
        <Box display="flex" flexDirection="column" justifyContent="left" alignItems="flex-start">
          <Breadcrumbs
            variant="object"
            elements={breadcrumbs}
          />
          <AtomicTestingTitle injectResultOverview={injectResultOverview} />
          <AtomicTestingTabs injectResultOverview={injectResultOverview} />
        </Box>
        <ResponsePie hasTitles={false} forceSize={112} expectationResultsByTypes={injectResultOverview.inject_expectation_results} />
        <AtomicTestingHeaderActions injectResultOverview={injectResultOverview} setInjectResultOverview={setInjectResultOverview} />
      </div>
    </Box>
  );
};

export default AtomicTestingHeader;
