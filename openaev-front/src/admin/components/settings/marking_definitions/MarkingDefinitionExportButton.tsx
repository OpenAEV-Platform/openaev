import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { FileDownloadOutlined } from '@mui/icons-material';
import { type FunctionComponent, useState } from 'react';

import { exportMarkingDefinitions } from '../../../../actions/marking_definitions/marking-definition-actions';
import { useFormatter } from '../../../../components/i18n';
import { download } from '../../../../utils/utils';

interface Props { disabled?: boolean }

const MarkingDefinitionExportButton: FunctionComponent<Props> = ({ disabled = false }) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);

  const handleExport = () => {
    setLoading(true);
    exportMarkingDefinitions()
      .then(result => download(result.data, result.filename, 'application/json'))
      // The API layer already notified the user of a failed call.
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span style={{ display: 'inline-flex' }}>
          <IconButton
            priority="secondary"
            size="md"
            aria-label={t('Export this list')}
            icon={<FileDownloadOutlined fontSize="small" />}
            disabled={disabled || loading}
            onClick={handleExport}
          />
        </span>
      </TooltipTrigger>
      <TooltipContent>{t('Export this list')}</TooltipContent>
    </Tooltip>
  );
};

export default MarkingDefinitionExportButton;
