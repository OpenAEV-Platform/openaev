import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { exportThreatArsenalCsvMapper } from '../../../actions/threat_arsenals/threatArsenal-actions';
import ExportButton from '../../../components/common/ExportButton';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ImportUploaderThreatArsenal from './ImportUploaderThreatArsenal';

interface Props {
  totalElements: number;
  actions: ThreatArsenalAction[];
  searchPaginationInput: SearchPaginationInput;
  onAdd: (results: ThreatArsenalAction[]) => void;
}

// Export and import of the Active list, in its toolbar; Create is the Hero's section action.
const ThreatArsenalHeaderActions: FunctionComponent<Props> = ({
  totalElements,
  actions,
  searchPaginationInput,
  onAdd,
}) => (
  // A plain row of actions: this was a ToggleButtonGroup used as a frame,
  // which announced a group of choices that never existed.
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
      <ImportUploaderThreatArsenal onImport={onAdd} />
    </Can>
  </Box>
);

export default ThreatArsenalHeaderActions;
