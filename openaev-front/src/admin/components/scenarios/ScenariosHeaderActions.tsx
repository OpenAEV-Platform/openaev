import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import ExportButton from '../../../components/common/ExportButton';
import MarketplaceViewToggle from '../../../components/common/marketplace/MarketplaceViewToggle';
import { type ViewMode } from '../../../components/common/marketplace/useMarketplaceView';
import { useFormatter } from '../../../components/i18n';
import { type Scenario } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ImportFromHubButton from '../common/ImportFromHubButton';
import ImportUploaderScenario from './ImportUploaderScenario';
import ScenarioCreation from './ScenarioCreation';

interface Props {
  viewMode: ViewMode;
  onViewModeChange: (value: string) => void;
  totalElements: number;
  scenarios: Scenario[];
  onImported: () => void;
}

const ScenariosHeaderActions: FunctionComponent<Props> = ({ viewMode, onViewModeChange, totalElements, scenarios, onImported }) => {
  const { t } = useFormatter();
  const { isXTMHubAccessible } = useAuth();
  return (
    <>
      <MarketplaceViewToggle value={viewMode} onChange={onViewModeChange} />
      {isXTMHubAccessible && (
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
          <ImportFromHubButton serviceIdentifier="openaev_scenarios" />
        </Can>
      )}
      <Box sx={{
        display: 'flex',
        gap: 1,
      }}
      >
        <ExportButton
          totalElements={totalElements}
          exportProps={{
            exportType: 'scenario',
            exportKeys: [
              'scenario_name',
              'scenario_severity',
              'scenario_category',
              'scenario_main_focus',
              'scenario_platforms',
              'scenario_tags',
              'scenario_updated_at',
            ],
            exportData: scenarios,
            exportFileName: `${t('Scenarios')}.csv`,
          }}
        />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
          <ImportUploaderScenario refresh={onImported} />
        </Can>
      </Box>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <ScenarioCreation />
      </Can>
    </>
  );
};

export default ScenariosHeaderActions;
