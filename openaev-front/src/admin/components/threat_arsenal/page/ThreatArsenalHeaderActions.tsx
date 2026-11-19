import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { exportThreatArsenalCsvMapper } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import ExportButton from '../../../../components/common/ExportButton';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ImportUploaderThreatArsenal from '../ImportUploaderThreatArsenal';

interface Props {
  totalElements: number;
  actions: ThreatArsenalAction[];
  searchPaginationInput: SearchPaginationInput;
  onImport: (results: ThreatArsenalAction[]) => void;
}

// Export and import of the actions, in the list toolbar.
const ThreatArsenalHeaderActions: FunctionComponent<Props> = ({ totalElements, actions, searchPaginationInput, onImport }) => (
  <Box sx={{
    display: 'flex',
    gap: 1,
  }}
  >
    <ExportButton
      totalElements={totalElements}
      exportProps={{
        exportType: 'THREAT_ARSENAL_ACTIONS',
        exportKeys: [],
        exportData: actions,
        searchPaginationInput,
      }}
      exportCsvMapperFunction={exportThreatArsenalCsvMapper}
    />
    <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
      <ImportUploaderThreatArsenal onImport={onImport} />
    </Can>
  </Box>
);

export default ThreatArsenalHeaderActions;
