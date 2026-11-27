import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { searchScenarios } from '../../../../actions/scenarios/scenario-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import useCatalogStat from '../../../../components/common/marketplace/page/useCatalogStat';
import { useFormatter } from '../../../../components/i18n';
import useAuth from '../../../../utils/hooks/useAuth';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import ImportFromHubButton from '../../common/ImportFromHubButton';
import { availableUrlIn, NOT_INSTALLED, searchCatalogScenariosIn, UPDATE_AVAILABLE } from '../catalog/catalogScenarioInstallation';
import ScenarioCreation from '../ScenarioCreation';

const AVAILABLE_URL = '/admin/scenarios/available';
const searchNotInstalled = searchCatalogScenariosIn(NOT_INSTALLED);

interface Props { updatesAvailable: number }

const ScenariosHero: FunctionComponent<Props> = ({ updatesAvailable }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { isXTMHubAccessible } = useAuth();
  // Every scenario and the whole catalogue, never the current page or filters.
  const active = useSearchTotal(searchScenarios) ?? 0;
  const notInstalled = useCatalogStat(searchNotInstalled, {
    id: 'not-installed',
    label: t('Not installed'),
    icon: 'download',
    to: AVAILABLE_URL,
  });
  const updates = isFeatureEnabled('CATALOG')
    ? [{
        id: 'updates',
        label: t('Updates available'),
        icon: 'refresh-cw' as const,
        to: availableUrlIn(UPDATE_AVAILABLE),
        value: updatesAvailable,
      }]
    : [];

  return (
    <MarketplaceHero
      title={t('Scenarios')}
      subtitle={t('Browse, install and version ready-to-run scenarios.')}
      stats={[
        {
          id: 'active',
          label: t('Active'),
          value: active,
          icon: 'route',
          to: '/admin/scenarios',
        },
        ...notInstalled,
        ...updates,
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
