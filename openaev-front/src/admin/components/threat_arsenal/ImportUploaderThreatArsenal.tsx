import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CloudUploadOutlined } from '@mui/icons-material';
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
      <Tooltip>
        <TooltipTrigger asChild>
          <span style={{ display: 'inline-flex' }}>
            <IconButton
              priority="secondary"
              size="md"
              aria-label={t('Import actions')}
              icon={<CloudUploadOutlined fontSize="small" />}
              onClick={handleOpen}
            />
          </span>
        </TooltipTrigger>
        <TooltipContent>{t('Import actions')}</TooltipContent>
      </Tooltip>
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
