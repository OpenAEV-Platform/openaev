import { CloudUploadOutlined } from '@mui/icons-material';
import { ToggleButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { importThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import DragAndDropImportDialog from '../../../components/common/import/DragAndDropImportDialog';
import { useFormatter } from '../../../components/i18n';
import type { ThreatArsenalAction } from '../../../utils/api-types';

interface Props { onImport: (results: ThreatArsenalAction[]) => void }

const ImportUploaderThreatArsenal: FunctionComponent<Props> = ({ onImport }) => {
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const handleImport = (formData: FormData) => {
    return importThreatArsenalAction(formData).then((result) => {
      if (result.data) {
        onImport?.(Array.isArray(result.data) ? result.data : [result.data]);
      }
    });
  };

  return (
    <>
      <ToggleButton
        value="import"
        aria-label="import payloads"
        size="small"
        onClick={handleOpen}
      >
        <Tooltip title={t('Import payloads')} aria-label="import payloads">
          <CloudUploadOutlined color="primary" fontSize="small" />
        </Tooltip>
      </ToggleButton>
      <DragAndDropImportDialog
        open={open}
        onClose={handleClose}
        onImport={handleImport}
        maxFiles={1}
      />
    </>
  );
};

export default ImportUploaderThreatArsenal;
