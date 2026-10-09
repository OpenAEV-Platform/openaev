import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

import { exportThreatArsenalCsvMapper } from '../../../actions/threat_arsenals/threatArsenal-actions';
import ExportButton from '../../../components/common/ExportButton';
import MarketplaceViewToggle from '../../../components/common/marketplace/MarketplaceViewToggle';
import { type ViewMode } from '../../../components/common/marketplace/useMarketplaceView';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import ImportUploaderThreatArsenal from './ImportUploaderThreatArsenal';

interface Props {
  viewMode: ViewMode;
  onViewModeChange: (value: string) => void;
  totalElements: number;
  actions: ThreatArsenalAction[];
  searchPaginationInput: SearchPaginationInput;
  onAdd: (results: ThreatArsenalAction[]) => void;
}

const ThreatArsenalHeaderActions: FunctionComponent<Props> = ({
  viewMode,
  onViewModeChange,
  totalElements,
  actions,
  searchPaginationInput,
  onAdd,
}) => (
  <>
    <MarketplaceViewToggle value={viewMode} onChange={onViewModeChange} />
    {/* A plain row of actions: this was a ToggleButtonGroup used as a frame,
        which announced a group of choices that never existed. */}
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
    {/* Beside the import icons, one instance for both views: in the pagination
        row the card view's sort select pushed it out of reach (#7340). */}
    <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
      <CreateThreatArsenalAction onCreate={(result: ThreatArsenalAction) => onAdd([result])} />
    </Can>
  </>
);

export default ThreatArsenalHeaderActions;
