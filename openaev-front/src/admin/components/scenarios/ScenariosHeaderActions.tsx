import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import ExportButton from '../../../components/common/ExportButton';
import { useFormatter } from '../../../components/i18n';
import { type Scenario } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ImportUploaderScenario from './ImportUploaderScenario';

interface Props {
  totalElements: number;
  scenarios: Scenario[];
  onImported: () => void;
}

// Export and import of the Active list, in its toolbar; Import from Hub and Create are the Hero's section actions.
const ScenariosHeaderActions: FunctionComponent<Props> = ({ totalElements, scenarios, onImported }) => {
  const { t } = useFormatter();
  return (
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
  );
};

export default ScenariosHeaderActions;
