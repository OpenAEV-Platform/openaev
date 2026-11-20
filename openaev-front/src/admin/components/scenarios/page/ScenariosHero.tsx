import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { searchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import { searchScenarios } from '../../../../actions/scenarios/scenario-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import useNotInstalledStat from '../../../../components/common/marketplace/page/useNotInstalledStat';
import { useFormatter } from '../../../../components/i18n';
import useAuth from '../../../../utils/hooks/useAuth';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ImportFromHubButton from '../../common/ImportFromHubButton';
import ScenarioCreation from '../ScenarioCreation';

const ScenariosHero: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { isXTMHubAccessible } = useAuth();
  // Every scenario and the whole catalogue, never the current page or filters.
  const active = useSearchTotal(searchScenarios) ?? 0;
  const notInstalled = useNotInstalledStat(searchCatalogScenarios, '/admin/scenarios/available');

  return (
    <MarketplaceHero
      title={t('Scenarios')}
      subtitle={t('Browse and version ready-to-use scenarios.')}
      stats={[
        {
          id: 'active',
          label: t('Active'),
          value: active,
          icon: 'route',
          to: '/admin/scenarios',
        },
        ...notInstalled,
      ]}
      action={(
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
          }}
          >
            {isXTMHubAccessible && <ImportFromHubButton serviceIdentifier="openaev_scenarios" />}
            <ScenarioCreation />
          </div>
        </Can>
      )}
    />
  );
};

export default ScenariosHero;
