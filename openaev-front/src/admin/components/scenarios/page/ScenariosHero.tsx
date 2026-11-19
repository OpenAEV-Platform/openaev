import { Box } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchScenarios } from '../../../../actions/scenarios/scenario-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';
import useAuth from '../../../../utils/hooks/useAuth';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ImportFromHubButton from '../../common/ImportFromHubButton';
import ScenarioCreation from '../ScenarioCreation';

const ScenariosHero: FunctionComponent = () => {
  const { t } = useFormatter();
  const { isXTMHubAccessible } = useAuth();
  // Every scenario, never the current page or filters.
  const [active, setActive] = useState(0);
  useEffect(() => {
    searchScenarios(buildSearchPagination({ size: 1 }))
      .then(({ data }: { data: { totalElements?: number } }) => setActive(data?.totalElements ?? 0))
      .catch(() => undefined);
  }, []);

  return (
    <MarketplaceHero
      title={t('Scenarios')}
      subtitle={t('Browse and version ready-to-use scenarios.')}
      stats={[{
        id: 'active',
        label: t('Active'),
        value: active,
        icon: 'route',
        to: '/admin/scenarios',
      }]}
      action={(
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            {isXTMHubAccessible && <ImportFromHubButton serviceIdentifier="openaev_scenarios" />}
            <ScenarioCreation />
          </Box>
        </Can>
      )}
    />
  );
};

export default ScenariosHero;
